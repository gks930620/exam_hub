package com.test.test.exam.collect;

import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.CertificateDetail;
import com.test.test.exam.repository.CertificateDetailRepository;
import com.test.test.exam.repository.CertificateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.test.test.exam.common.TimeUtil.now;

/**
 * 시험의 <b>날짜가 아닌 정보</b>를 큐넷에서 받아 온다 — 응시료·시험과목·검정방법·합격기준.
 *
 * <p>상세 화면에 회차 일정표밖에 없어서 취준생이 "이 시험 볼까"를 여기서 못 정하고 큐넷으로
 * 나갔다. 그 정보가 큐넷 API 에 있다 — 다만 구조화돼 있지 않아 {@link QnetExamInfoParser} 가 가른다.
 *
 * <h3>왜 조금씩 받나</h3>
 * 두 API 모두 <b>종목코드 하나씩</b>이라 전량이면 1,000콜이 넘는다. 그런데 큐넷 하루 한도가
 * 1,000회이고 일정 수집이 <b>이미 매일 613콜을 쓴다</b>. 한도가 계정당인지 오퍼레이션당인지는
 * 문서마다 다르게 적혀 있어서(README 는 오퍼레이션당, 운영/02 는 계정당) 확실하지 않다.
 * 모르는 채로 한 번에 다 부르면 <b>그날 일정 수집까지 같이 죽는다</b> — 일정이 이 서비스의 심장이다.
 *
 * <p>그래서 한 번에 {@code qnet.info.batch-size} 만큼만 받고, 안 받은 것부터 채운다.
 * 이 정보는 일정과 달리 거의 안 변해서 매달 조금씩이면 충분하다.
 *
 * <h3>기본은 꺼져 있다</h3>
 * {@code QNET_INFO_ENABLED} 를 켜야 돈다. 새 환경에서 첫 기동에 남의 API 를 두드리지 않는다
 * (수집 기동 스위치와 같은 원칙).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QnetExamInfoCollector {

    /** 이 정보가 있는 시행처. 비큐넷은 어디서 얻을지 아직 조사된 바가 없다. */
    static final String AGENCY = "한국산업인력공단";

    /**
     * 한 종목을 몇 번까지 다시 물어볼 것인가.
     *
     * <p><b>2026-09-22 실호출: 5종 중 3종이 첫 시도에 실패했다.</b>
     * {@code resultCode=99 "Failed to validate a newly established connection"} — 같은 주소를
     * 다시 부르면 멀쩡히 온다. 서버 쪽 일시 오류다. 재시도가 없으면 채움률이 반토막 난다.
     */
    static final int ATTEMPTS = 3;
    static final long RETRY_DELAY_MS = 1_500;

    /** 이보다 오래되면 다시 받는다. 응시료·과목은 해가 바뀔 때나 변한다. */
    static final int STALE_DAYS = 180;

    @Value("${qnet.info.enabled:false}")
    private boolean enabled;

    @Value("${qnet.api.key:}")
    private String apiKey;

    /** API#5 종목별 자격정보 — 시험과목·검정방법·합격기준 */
    @Value("${qnet.info.trade-url:http://openapi.q-net.or.kr/api/service/rest/InquiryInformationTradeNTQSVC/getList}")
    private String tradeUrl;

    /** API#3 종목별 응시수수료 — 국가기술자격 전용 */
    @Value("${qnet.info.fee-url:http://openapi.q-net.or.kr/api/service/rest/InquiryTestInformationNTQSVC/getFeeList}")
    private String feeUrl;

    /** 한 번에 받을 종목 수. 두 API 를 부르므로 실제 호출은 이 값의 두 배다. */
    @Value("${qnet.info.batch-size:50}")
    private int batchSize;

    private final CertificateRepository certificateRepository;
    private final CertificateDetailRepository certificateDetailRepository;

    private final RestClient http = RestClient.builder()
            .requestFactory(timeoutFactory())
            .build();

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000);
        f.setReadTimeout(20_000);
        return f;
    }

    /**
     * 아직 안 받았거나 오래된 것부터 {@code batchSize} 만큼 채운다.
     *
     * @return 실제로 저장한 종목 수
     */
    public int collectNext() {
        if (!enabled) {
            log.info("[시험정보] 꺼져 있다 — QNET_INFO_ENABLED=true 로 켜면 돈다");
            return 0;
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[시험정보] 큐넷 API 키가 없다 — 받을 수 없다");
            return 0;
        }
        List<String> codes = certificateDetailRepository.findCodesNeedingDetail(
                AGENCY, now().minusDays(STALE_DAYS), PageRequest.of(0, Math.max(1, batchSize)));
        if (codes.isEmpty()) {
            log.info("[시험정보] 더 받을 것이 없다");
            return 0;
        }
        return collect(codes);
    }

    /** 종목코드를 직접 줘서 받는다(표본 확인·수동 보정용). */
    public int collect(List<String> codes) {
        int saved = 0;
        for (String code : codes) {
            try {
                if (save(code)) {
                    saved++;
                }
            } catch (Exception e) {
                if (isQuotaExceeded(e)) {
                    // 한도를 넘겼으면 나머지를 계속 불러도 다 실패한다 — 남은 예산만 축낸다.
                    log.error("[시험정보] 일일 호출 한도 초과 — {}종에서 멈춘다", saved);
                    return saved;
                }
                log.warn("[시험정보] {} 실패: {}", code, e.toString());
            }
        }
        log.info("[시험정보] {}종 저장(요청 {}종)", saved, codes.size());
        return saved;
    }

    /**
     * 한 종목을 받아 저장한다.
     *
     * <p><b>{@code @Transactional} 을 붙이지 않는다.</b> 같은 빈 안에서 {@code this.save(...)} 로
     * 부르면 프록시를 안 거쳐 트랜잭션이 아예 안 열린다. 그 상태로 엔티티만 고치면 더티 체킹이
     * 안 돌아 <b>아무것도 저장되지 않는다</b> — 로그는 "저장했다"고 찍히는데 DB 는 그대로인
     * 부류다. 그래서 고친 뒤 명시적으로 저장한다.
     *
     * <p>건별로 독립이라 한 종목이 실패해도 나머지는 들어간다.
     */
    boolean save(String code) {
        Certificate cert = certificateRepository.findBySourceCode(code).orElse(null);
        if (cert == null) {
            return false;
        }

        Map<String, String> trade = fetchContents(tradeUrl, code);
        QnetExamInfoParser.Acquisition acq =
                QnetExamInfoParser.parseAcquisition(trade.get("취득방법"));
        QnetExamInfoParser.Fee fee =
                QnetExamInfoParser.parseFee(fetchContents(feeUrl, code).get("응시수수료"));

        CertificateDetail detail = certificateDetailRepository.findByCertificateId(cert.getId())
                .orElseGet(() -> certificateDetailRepository.save(
                        CertificateDetail.builder().certificate(cert).collectedAt(now()).build()));

        detail.apply(fee.getWritten(), fee.getPractical(), fee.getRaw(),
                acq.getRelatedMajor(), acq.getSubjects(), acq.getMethod(),
                acq.getPassStandard(), acq.getRaw(), trade.get("출제경향"));
        certificateDetailRepository.save(detail);   // 트랜잭션 밖이라 더티 체킹에 기대지 않는다
        return true;
    }

    /**
     * {@code items > item > (infogb, contents)} 를 {@code {정보구분: 원문}} 으로 읽는다.
     *
     * <p>{@code infogb} 값을 미리 정해 두지 않는다 — 가이드엔 없던 "출제기준"이 실제로 왔다.
     * 없는 구분이 오면 그냥 안 쓰면 되고, 하드코딩해 두면 새 구분이 올 때 조용히 버려진다.
     */
    /**
     * 몇 번 다시 물어본다. 첫 시도 실패가 흔해서(실측 5종 중 3종) 한 번으로 포기하면
     * 채움률이 반토막 난다. <b>한도 초과(429)는 다시 묻지 않는다</b> — 다시 불러도 안 되고
     * 남은 예산만 축낸다.
     */
    private Map<String, String> fetchContents(String baseUrl, String jmCd) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                Map<String, String> got = fetchOnce(baseUrl, jmCd);
                if (!got.isEmpty()) {
                    return got;
                }
            } catch (Exception e) {
                if (isQuotaExceeded(e)) {
                    throw e;   // 위에서 배치를 멈춘다
                }
                log.debug("[시험정보] {} {}차 실패: {}", jmCd, attempt, e.toString());
            }
            if (attempt < ATTEMPTS) {
                sleepQuietly(RETRY_DELAY_MS);
            }
        }
        log.warn("[시험정보] {} 를 {}번 물어도 못 받았다", jmCd, ATTEMPTS);
        return Map.of();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Map<String, String> fetchOnce(String baseUrl, String jmCd) {
        String url = baseUrl + "?ServiceKey=" + apiKey + "&jmCd=" + jmCd;

        // ⚠️ body(String.class) 로 받으면 안 된다. 이 호스트는 Content-Type 에 charset 을 주지 않아
        //    스프링이 ISO-8859-1 로 읽고 한글이 전부 깨진다. contents 는 통째로 한글 원문이라
        //    같은 함정을 100% 밟는다(종목목록에서 이미 겪었다).
        byte[] raw = http.get().uri(URI.create(url)).retrieve().body(byte[].class);
        String xml = raw == null ? null : new String(raw, StandardCharsets.UTF_8);
        if (xml == null || xml.isBlank()) {
            return Map.of();
        }

        Map<String, String> out = new java.util.LinkedHashMap<>();
        try {
            var dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            var doc = dbf.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            // 파싱 전에 결과 코드를 본다 — 안 보면 오류 응답을 "0건"으로 조용히 넘긴다.
            String resultCode = firstText(doc.getElementsByTagName("resultCode"));
            if (!resultCode.isBlank() && !"00".equals(resultCode)) {
                log.warn("[시험정보] {} 응답 오류 resultCode={} msg={}", jmCd, resultCode,
                        firstText(doc.getElementsByTagName("resultMsg")));
                return Map.of();
            }

            NodeList nodes = doc.getElementsByTagName("item");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                String gb = text(el, "infogb");
                String contents = text(el, "contents");
                if (!gb.isBlank() && !contents.isBlank()) {
                    out.put(gb, contents);
                }
            }
        } catch (Exception e) {
            log.warn("[시험정보] {} XML 파싱 실패: {}", jmCd, e.toString());
        }
        return out;
    }

    private static String firstText(NodeList nl) {
        return nl.getLength() > 0 ? nl.item(0).getTextContent().trim() : "";
    }

    private static String text(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        return nl.getLength() > 0 ? nl.item(0).getTextContent().trim() : "";
    }

    private boolean isQuotaExceeded(Exception e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        return m.contains("429") || m.contains("LIMITED_NUMBER_OF_SERVICE_REQUESTS");
    }

    /** 다음에 받을 것이 얼마나 남았나 — 매니저 화면이 진행을 보여줄 수 있게. */
    public long collectedCount() {
        return certificateDetailRepository.countByAgency(AGENCY);
    }

    LocalDateTime staleBefore() {
        return now().minusDays(STALE_DAYS);
    }
}

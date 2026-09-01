package com.test.test.exam.collect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 실 큐넷(공공데이터포털 한국산업인력공단) 국가자격 시험일정 API 어댑터.
 * {@code qnet.api.enabled=true}(운영 기본) 일 때만 활성.
 *
 * <p>동작(근거: {@code 설계/시험데이터/큐넷_API/}):
 * <ol>
 *   <li><b>종목목록</b>(API#2, XML, {@code openapi.q-net.or.kr}, HTTP)로 jmCd·종목명·자격구분·계열 확보</li>
 *   <li>각 jmCd 로 <b>시험일정</b>(API#1, JSON, {@code apis.data.go.kr}, HTTPS)을 조회 → 필기/실기 회차를 {@link CollectedSchedule} 로 정규화</li>
 * </ol>
 *
 * <p><b>실측으로 확인된 것</b>(2026-08-10):
 * <ul>
 *   <li>종목목록은 <b>1콜로 613종 전부</b> 온다(응답 190KB). 종목은 거의 안 바뀌어 연 1회면 충분.</li>
 *   <li>일정은 {@code jmCd} 를 붙여야 <b>종목별 회차</b>가 온다. 안 붙이면 등급 단위
 *       ({@code 국가기술자격 기사 (2026년도 제3회)})만 오고, 국가전문자격은 자격명조차 없어 못 붙인다.</li>
 *   <li>따라서 <b>613콜/갱신</b>. 개발계정 일 1,000회 한도 안이라 주 1회 갱신이면 넉넉하다.</li>
 *   <li>⚠️ {@code numOfRows} 는 <b>50 이하</b>. 넘기면 {@code resultCode 930} 이 온다 —
 *       이 코드는 jmCd 누락이 아니라 <b>파라미터 오류</b>라는 뜻이다(한동안 오해했다).</li>
 *   <li>⚠️ 한 회차에 행이 둘 올 수 있다(일반접수/빈자리접수로 추정). 접수 시작이 이른 쪽을 쓴다.</li>
 * </ul>
 *
 * <p>키 인코딩 함정({@code 설계/시험데이터/큐넷_API/README.md} §0): URI 를 직접 조립하므로
 * <b>Encoding 키</b>를 {@code QNET_API_KEY} 에 넣는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "qnet.api.enabled", havingValue = "true")
public class QnetApiScheduleSource implements ScheduleSource {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 종목목록은 1콜인데 실패하면 그날 수집이 통째로 0건이 된다 → 몇 번 다시 시도한다. */
    private static final int ITEM_LIST_ATTEMPTS = 3;
    private static final long ITEM_LIST_RETRY_DELAY_MS = 2_000;

    @Value("${qnet.api.key:}")
    private String apiKey;

    /** API#1 시험일정 (getQualExamSchdList) */
    @Value("${qnet.api.base-url:https://apis.data.go.kr/B490007/qualExamSchd}")
    private String scheduleBaseUrl;

    /** API#2 종목목록 (InquiryListNationalQualifcationSVC/getList, HTTP 전용) */
    @Value("${qnet.api.item-list-url:http://openapi.q-net.or.kr/api/service/rest/InquiryListNationalQualifcationSVC/getList}")
    private String itemListUrl;

    /** 수집 대상 시행년도 (미지정 시 올해) */
    @Value("${qnet.api.impl-year:0}")
    private int implYear;

    /** 조회할 최대 종목 수 (0=전체). 배치 시간 조절용. */
    @Value("${qnet.api.max-items:0}")
    private int maxItems;

    private final RestClient http = RestClient.builder()
            .requestFactory(timeoutFactory())
            .build();

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000);
        f.setReadTimeout(20_000);
        return f;
    }

    @Override
    public String sourceId() {
        return "QNET_API";
    }

    @Override
    public List<CollectedSchedule> fetchAll() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[QnetApiScheduleSource] QNET_API_KEY 미설정 — 빈 목록 반환");
            return List.of();
        }
        List<JmItem> items = fetchItemListWithRetry();
        if (maxItems > 0 && items.size() > maxItems) {
            items = items.subList(0, maxItems);
        }
        int year = implYear > 0 ? implYear : TimeUtil.today().getYear();
        List<CollectedSchedule> out = new ArrayList<>();
        int done = 0;
        for (JmItem item : items) {
            try {
                out.addAll(fetchSchedules(item, year));
                done++;
            } catch (Exception e) {
                if (isQuotaExceeded(e)) {
                    // 한도를 넘긴 뒤에도 계속 두드리면 낭비일 뿐 아니라 키가 막힐 수 있다.
                    // 실제로 613종을 전부 429 로 두드린 적이 있다(2026-08-10).
                    log.error("[QnetApiScheduleSource] 일일 호출 한도 초과 — {}종에서 중단합니다. "
                            + "받은 것까지만 반영하고 내일 이어서 받습니다. (개발계정 일 1,000회)", done);
                    break;
                }
                log.warn("[QnetApiScheduleSource] jmCd={} 일정 조회 실패: {}", item.jmcd(), e.getMessage());
            }
        }
        log.info("[QnetApiScheduleSource] 종목 {}/{}종 → 일정 {}건 수집 (year={})",
                done, items.size(), out.size(), year);
        return out;
    }

    @Override
    public List<CollectedSchedule> fetchByCertificateCodes(List<String> sourceCodes) {
        int year = implYear > 0 ? implYear : TimeUtil.today().getYear();
        List<CollectedSchedule> out = new ArrayList<>();
        for (JmItem item : fetchItemListWithRetry()) {
            if (!sourceCodes.contains(item.jmcd())) {
                continue;
            }
            try {
                out.addAll(fetchSchedules(item, year));
            } catch (Exception e) {
                log.warn("[QnetApiScheduleSource] (임박) jmCd={} 조회 실패: {}", item.jmcd(), e.getMessage());
            }
        }
        return out;
    }

    /**
     * 공공데이터포털의 일일 호출 한도 초과인가.
     *
     * <p>HTTP 429 로 오지만 본문의 {@code LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR} 도 같이 본다 —
     * 포털이 200 으로 내려주면서 본문에만 에러를 담는 경우가 있어서다.
     */
    private boolean isQuotaExceeded(Exception e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        return m.contains("429") || m.contains("LIMITED_NUMBER_OF_SERVICE_REQUESTS");
    }

    // ===== API#2 종목목록 (XML) =====

    /**
     * 종목목록을 받는다. <b>실패하면 그날 수집 전체가 0건</b>이 되므로 몇 번 다시 시도한다.
     *
     * <p>포털이 이런 걸 돌려줄 때가 있다(실제로 겪었다):
     * <pre>&lt;resultCode&gt;99&lt;/resultCode&gt;&lt;resultMsg&gt;Failed to validate a newly established connection.&lt;/resultMsg&gt;</pre>
     * 같은 주소를 curl 로 부르면 멀쩡한 걸 보면 서버 쪽 일시 오류다. 한 번 실패로 하루를 날릴 이유가 없다.
     * <b>호출 한도(429)는 재시도하지 않는다</b> — 그건 다시 불러도 안 되고, 남은 예산만 축낸다.
     */
    private List<JmItem> fetchItemListWithRetry() {
        for (int attempt = 1; attempt <= ITEM_LIST_ATTEMPTS; attempt++) {
            try {
                List<JmItem> items = fetchItemList();
                if (!items.isEmpty()) {
                    return items;
                }
            } catch (Exception e) {
                if (isQuotaExceeded(e)) {
                    log.error("[QnetApiScheduleSource] 일일 호출 한도 초과 — 종목목록도 못 받는다");
                    return List.of();
                }
                log.warn("[QnetApiScheduleSource] 종목목록 조회 실패({}차): {}", attempt, e.toString());
            }
            if (attempt < ITEM_LIST_ATTEMPTS) {
                sleepQuietly(ITEM_LIST_RETRY_DELAY_MS);
            }
        }
        log.error("[QnetApiScheduleSource] 종목목록을 {}번 시도해도 못 받았다 — 이번 수집은 0건이다",
                ITEM_LIST_ATTEMPTS);
        return List.of();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<JmItem> fetchItemList() {
        String url = itemListUrl + "?ServiceKey=" + apiKey;
        // ⚠️ body(String.class) 로 받으면 안 된다. 이 엔드포인트는 Content-Type 에 charset 을
        //    주지 않아서 스프링이 ISO-8859-1 로 읽고, 종목명이 전부 깨진다("가스기술사" → "ê°ì¤...").
        //    깨진 이름은 기존 종목과 매칭되지 않아 **같은 시험이 하나 더 생긴다**(실제로 겪음).
        //    XML 선언이 UTF-8 이므로 바이트로 받아 직접 디코딩한다.
        byte[] raw = http.get().uri(URI.create(url)).retrieve().body(byte[].class);
        String xml = raw == null ? null : new String(raw, StandardCharsets.UTF_8);
        List<JmItem> items = new ArrayList<>();
        if (xml == null || xml.isBlank()) {
            // 종목목록이 비면 그 뒤 일정 수집이 통째로 0건이 된다. 조용히 넘어가면
            // "왜 아무것도 안 들어왔지"를 알 방법이 없어 반드시 남긴다.
            log.error("[QnetApiScheduleSource] 종목목록 응답이 비었다 — 이후 일정 수집이 0건이 된다. url={}",
                    url.replace(apiKey, "***"));
            return items;
        }
        try {
            var dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            var doc = dbf.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList nodes = doc.getElementsByTagName("item");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                items.add(new JmItem(
                        text(el, "jmcd"), text(el, "jmfldnm"),
                        text(el, "qualgbcd"), text(el, "qualgbnm"), text(el, "seriesnm"),
                        text(el, "obligfldnm")));
            }
        } catch (Exception e) {
            log.error("[QnetApiScheduleSource] 종목목록 XML 파싱 실패 (본문 {}바이트, 앞부분: {})",
                    raw.length, xml.substring(0, Math.min(200, xml.length())), e);
        }
        if (items.isEmpty()) {
            log.error("[QnetApiScheduleSource] 종목목록에서 종목을 하나도 못 뽑았다 "
                    + "(본문 {}바이트, 앞부분: {})", raw.length, xml.substring(0, Math.min(200, xml.length())));
        }
        return items;
    }

    private static String text(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        return nl.getLength() > 0 ? nl.item(0).getTextContent().trim() : "";
    }

    // ===== API#1 시험일정 (JSON) =====

    private List<CollectedSchedule> fetchSchedules(JmItem item, int year) {
        String url = scheduleBaseUrl + "/getQualExamSchdList"
                + "?serviceKey=" + apiKey
                + "&dataFormat=json&numOfRows=50&pageNo=1"
                + "&implYy=" + year
                + "&qualgbCd=" + item.qualgbcd()
                + "&jmCd=" + item.jmcd();
        SchedResponse res = http.get().uri(URI.create(url)).retrieve().body(SchedResponse.class);
        List<CollectedSchedule> out = new ArrayList<>();
        if (res == null || res.body() == null || res.body().items() == null) {
            return out;
        }
        Series series = mapSeries(item);
        // 시드(seed/qnet_master.json)와 같은 분류 체계를 써야 한다 — 성긴 값을 주면
        // DiffService 가 마스터를 덮어써서 613종의 세분류가 통째로 뭉개진다.
        String category = QnetFieldCategory.of(item.qualgbnm(), item.obligfldnm());
        for (SchedItem s : res.body().items()) {
            // 필기(doc*)
            add(out, item, series, category, s.implSeq(), ExamType.WRITTEN,
                    s.docRegStartDt(), s.docRegEndDt(), s.docExamStartDt(), s.docExamEndDt(), s.docPassDt());
            // 실기(prac*) — 국가전문자격은 실기 개념 없음(빈 값이면 add 내부에서 스킵)
            add(out, item, series, category, s.implSeq(), ExamType.PRACTICAL,
                    s.pracRegStartDt(), s.pracRegEndDt(), s.pracExamStartDt(), s.pracExamEndDt(), s.pracPassDt());
        }
        return dedupeByEarliestRegistration(out, item);
    }

    /**
     * <b>같은 회차가 두 줄로 오는 경우</b>를 한 줄로 줄인다.
     *
     * <p>실측(정보처리기사 2026년 3회)에서 접수기간만 다른 행이 둘 왔다 —
     * {@code 0720~0723} 과 {@code 0801~0802}. 일반접수와 빈자리접수로 보인다.
     * 그대로 두면 뒤엣것이 앞엣것을 덮어써서 <b>사용자에게 늦은 접수일만 보인다</b>.
     * 원서접수는 시작하자마자 마감되는 일이 흔해서, <b>이른 쪽</b>을 알려주는 게 맞다.
     */
    private List<CollectedSchedule> dedupeByEarliestRegistration(List<CollectedSchedule> rows, JmItem item) {
        Map<String, CollectedSchedule> best = new LinkedHashMap<>();
        int dropped = 0;
        for (CollectedSchedule r : rows) {
            String key = r.year() + "|" + r.round() + "|" + r.examType();
            CollectedSchedule prev = best.get(key);
            if (prev == null) {
                best.put(key, r);
                continue;
            }
            dropped++;
            boolean newerIsEarlier = r.regStartAt() != null
                    && (prev.regStartAt() == null || r.regStartAt().isBefore(prev.regStartAt()));
            if (newerIsEarlier) {
                best.put(key, r);
            }
        }
        if (dropped > 0) {
            // 버리지 않고 남긴다 — 빈자리접수인지 정정 공고인지 실제로 확인해야 한다.
            log.info("[QnetApiScheduleSource] jmCd={} 같은 회차 중복 {}건 — 접수 시작이 이른 쪽만 남김",
                    item.jmcd(), dropped);
        }
        return new ArrayList<>(best.values());
    }

    private void add(List<CollectedSchedule> out, JmItem item, Series series, String category,
                     int round, ExamType type,
                     String regStart, String regEnd, String examStart, String examEnd, String pass) {
        LocalDateTime regStartAt = dateTime(regStart, 10);
        LocalDateTime regEndAt = dateTime(regEnd, 18);
        LocalDate examStartDate = date(examStart);
        LocalDate examEndDate = date(examEnd);
        LocalDate resultDate = date(pass);
        // 접수/시험 정보가 전혀 없으면 스킵
        if (regStartAt == null && examStartDate == null && resultDate == null) {
            return;
        }
        out.add(new CollectedSchedule(
                item.jmcd(), item.jmfldnm(), series, "한국산업인력공단", category,
                examStartDate != null ? examStartDate.getYear()
                        : (regStartAt != null ? regStartAt.getYear() : TimeUtil.today().getYear()),
                round, type,
                regStartAt, regEndAt, examStartDate, examEndDate, resultDate,
                "https://www.q-net.or.kr/", ScheduleProvenance.API));
    }

    private LocalDateTime dateTime(String yyyymmdd, int hour) {
        LocalDate d = date(yyyymmdd);
        return d == null ? null : d.atTime(hour, 0);
    }

    private LocalDate date(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.isBlank() || yyyymmdd.length() != 8) {
            return null;
        }
        try {
            return LocalDate.parse(yyyymmdd, YMD);
        } catch (Exception e) {
            return null;
        }
    }

    private Series mapSeries(JmItem item) {
        if (!"T".equals(item.qualgbcd())) {
            return Series.ETC; // 전문/과정평가/일학습
        }
        String s = item.seriesnm() == null ? "" : item.seriesnm();
        return switch (s) {
            case "기술사" -> Series.PROFESSIONAL;
            case "기능장" -> Series.MASTER;
            case "기사" -> Series.TECHNICIAN;
            case "산업기사" -> Series.INDUSTRIAL;
            case "기능사" -> Series.CRAFTSMAN;
            default -> Series.SERVICE;
        };
    }

    // ===== DTO =====

    private record JmItem(String jmcd, String jmfldnm, String qualgbcd, String qualgbnm, String seriesnm,
                          String obligfldnm) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SchedResponse(Body body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Body(List<SchedItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SchedItem(
            int implSeq,
            String docRegStartDt, String docRegEndDt, String docExamStartDt, String docExamEndDt, String docPassDt,
            String pracRegStartDt, String pracRegEndDt, String pracExamStartDt, String pracExamEndDt, String pracPassDt) {
    }
}

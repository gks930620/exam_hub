package com.test.test.exam.collect;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 시행처 HTML 을 읽어 일정을 뽑는 수집 소스의 공통 뼈대 (어학시험용).
 *
 * <p>공공 API 가 없는 시험(토익·텝스·JLPT 등)은 시행처의 일정 페이지가 유일한 원본이다.
 * 요청 시마다 긁지 않고 <b>수집 배치가 하루 1회</b> 읽어 DB 에 적재한다(설계 07 §4-5 가드레일).
 *
 * <p><b>기본은 켜져 있다</b>({@code scrape.enabled} 기본 true — 운영에서도 05:00 배치에 API 와 같이 돈다).
 * 다만 <b>기동 때는 돌지 않는다</b> — {@code collect.on-startup} 이 기본 false 라 파일 시드만 읽는다.
 * 테스트 설정(yml)에는 이 키가 없어 구현체 빈이 안 뜨므로 테스트는 네트워크를 타지 않는다.
 * 파싱 로직({@link #parse})은 빈과 무관하게 순수 함수라 저장해 둔 HTML 픽스처로 단위 테스트한다.
 *
 * <p>수집 예의: 사실(날짜)만 저장하고, 원문 URL 을 함께 남긴다. 설명 문구·표 구조는 복제하지 않는다.
 */
@Slf4j
public abstract class AbstractHtmlScheduleSource implements ScheduleSource {

    private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*[\"']?([A-Za-z0-9_-]+)");

    /** 스크래퍼 — 시드 뒤, 큐넷 API 앞. */
    @Override
    public int priority() {
        return PRIORITY_SCRAPER;
    }

    private final RestClient http = RestClient.builder()
            .requestFactory(timeoutFactory())
            // 헤더 값은 아스키여야 한다(RFC 7230). 한글을 넣어 뒀는데, 서버에 따라 깨져 보이거나
            // 아예 거절당한다 — 표준 HTTP 클라이언트는 이 값으로 요청 자체를 못 만든다(2026-09-08 실측).
            .defaultHeader("User-Agent", "exam-hub/1.0 (+exam schedule collector; stores facts only)")
            .defaultHeader("Accept", "text/html,application/xhtml+xml")
            .build();

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000);
        f.setReadTimeout(20_000);
        return f;
    }

    /** 일정이 실린 페이지 주소. */
    protected abstract String pageUrl();

    /** 스크래퍼가 읽는 그 화면이 곧 사람이 열어 볼 화면이다 — 따로 적어 두면 어긋난다. */
    @Override
    public String siteUrl() {
        return pageUrl();
    }

    /** HTML → 일정 목록. 네트워크와 무관한 순수 함수 (픽스처로 테스트한다). */
    public abstract List<CollectedSchedule> parse(String html);

    @Override
    public List<CollectedSchedule> fetchAll() {
        try {
            String html = get(pageUrl());
            List<CollectedSchedule> out = parse(html);
            log.info("[{}] {} → 일정 {}건", sourceId(), pageUrl(), out.size());
            if (out.isEmpty()) {
                // 사이트 개편이면 조용히 0건이 된다 — 사라진 걸 알아채려면 로그가 필요하다.
                log.warn("[{}] 일정을 하나도 못 뽑았다 — 페이지 구조가 바뀌었을 수 있다", sourceId());
            }
            return out;
        } catch (Exception e) {
            log.error("[{}] 수집 실패 — 이번 회차는 건너뛴다: {}", sourceId(), e.toString());
            return List.of();   // 한 소스가 죽어도 다른 소스 수집은 계속돼야 한다
        }
    }

    /** 페이지를 받아 문자셋을 판별해 문자열로 만든다. 국내 시행처는 EUC-KR 이 아직 섞여 있다. */
    protected String get(String url) {
        return get(url, Map.of());
    }

    /**
     * 헤더를 얹어 받는 판 — TOPIK 처럼 <b>쿠키가 없으면 인트로 셸만 주는</b> 사이트가 있다
     * (timezone 쿠키를 놓고 리다이렉트하는 구조라, 처음부터 보내야 본문이 온다).
     */
    protected String get(String url, Map<String, String> headers) {
        // set 이다(add 아님) — 기본 헤더를 <b>덮어쓸</b> 수 있어야 한다.
        // add 면 User-Agent 가 두 줄이 되어 서버가 어느 쪽을 볼지 알 수 없다.
        byte[] body = http.get().uri(URI.create(url))
                .headers(h -> headers.forEach(h::set))
                .retrieve().body(byte[].class);
        if (body == null) {
            return "";
        }
        String asUtf8 = new String(body, StandardCharsets.UTF_8);
        String declared = declaredCharset(asUtf8);
        if (declared == null || declared.equalsIgnoreCase("utf-8")) {
            return asUtf8;
        }
        try {
            return new String(body, Charset.forName(declared));
        } catch (Exception e) {
            return asUtf8;
        }
    }

    /**
     * 폼 값을 실어 POST 한다 — <b>시행처 화면이 자기 서버에 묻는 그 주소를 그대로 부를 때</b> 쓴다.
     *
     * <p>금융투자협회·금융연수원처럼 일정 표를 서버렌더로 안 그리고 자바스크립트가 조회 결과(JSON)로
     * 그리는 곳이 있다. 그때는 HTML 을 긁는 것보다 <b>같은 조회 주소를 그대로 부르는 편이 정직하고 가볍다</b> —
     * 로그인도 키도 필요 없고, 페이지 한 장을 통째로 받지 않으니 서버 부담도 적다.
     * 응답이 JSON 이어도 이 메서드는 문자열까지만 책임진다(파싱은 각 소스의 몫).
     */
    protected String post(String url, String formBody) {
        byte[] body = http.post().uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json, text/javascript, */*")
                .header("X-Requested-With", "XMLHttpRequest")
                .body(formBody)
                .retrieve().body(byte[].class);
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }

    private String declaredCharset(String head) {
        Matcher m = CHARSET.matcher(head.length() > 2000 ? head.substring(0, 2000) : head);
        return m.find() ? m.group(1) : null;
    }

    /**
     * <b>주석 처리된 markup 을 걷어낸다.</b> 파싱 전에 반드시 한 번 통과시킨다.
     *
     * <p>시행처는 지난 연도의 일정표를 지우지 않고 {@code <!-- -->} 로 묶어 두는 일이 잦다.
     * 공인회계사회가 그랬다(2026-09-08): 주석 안에 남아 있던 <b>제45~50회</b> 표가 살아 있는
     * 제88~95회 표를 덮어써서, 사용자에게 <b>지난해 접수일</b>이 올해 일정으로 나갔다.
     * 사람 눈에는 안 보이는 값이라 화면을 봐도 못 잡는다.
     */
    protected static String stripComments(String html) {
        return html == null ? "" : html.replaceAll("(?s)<!--.*?-->", " ");
    }

    /** 태그를 걷어내고 공백을 정규화한 평문. 표 구조가 아니라 문장에서 뽑을 때 쓴다. */
    protected static String toText(String html) {
        if (html == null) {
            return "";
        }
        return html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?s)<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ");
    }
}

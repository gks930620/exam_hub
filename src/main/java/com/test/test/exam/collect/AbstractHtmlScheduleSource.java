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
            .defaultHeader("User-Agent", "exam-hub/1.0 (+시험일정 수집; 사실 데이터만 저장)")
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
        byte[] body = http.get().uri(URI.create(url))
                .headers(h -> headers.forEach(h::add))
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

    private String declaredCharset(String head) {
        Matcher m = CHARSET.matcher(head.length() > 2000 ? head.substring(0, 2000) : head);
        return m.find() ? m.group(1) : null;
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

package com.test.test.exam.collect;

import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TOPIK(한국어능력시험) 일정 수집 — 국립국제교육원.
 *
 * <p>메인 페이지의 <b>인라인 JS 가 만드는 회차 슬라이드</b>에 국내시험일·접수기간·성적발표가
 * 들어 있다. 표가 아니라서 표 파서가 아니라 블록 단위 정규식으로 읽는다.
 *
 * <h3>함정 둘 — 실측(2026-09-01)</h3>
 * <ul>
 *   <li><b>쿠키 없이는 1.6KB 인트로 셸만 온다.</b> 페이지가 timezone 쿠키를 놓고 리다이렉트하는
 *       구조라, {@code Cookie: timezone=Asia/Seoul} 을 처음부터 보내야 본문이 온다.</li>
 *   <li><b>토픽 IBT·말하기 평가가 같은 슬라이드에 섞여 있다.</b> 마스터에 없는 상품이라 넣으면
 *       이름 매칭으로 새 시험이 생겨 버린다 — "한국어능력시험 제N회" 블록만 읽는다.</li>
 * </ul>
 *
 * <p>지필 I·II 는 같은 날 시행이라 회차마다 두 건을 만든다(코드 M0355, TOPIK-2 —
 * 이미 마스터에 붙어 있는 값이라야 중복이 안 생긴다).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class TopikScheduleSource extends AbstractHtmlScheduleSource {

    static final String URL = "https://www.topik.go.kr/TWMAIN/TWMAIN0010.do";
    static final String AGENCY = "국립국제교육원";
    private static final String CATEGORY = "한국어";

    /** 같은 회차가 두 시험으로 펼쳐진다 — 코드는 마스터에 이미 붙어 있는 값. */
    private static final Map<String, String> LEVELS = Map.of(
            "TOPIK 한국어능력시험(I)", "M0355",
            "TOPIK 한국어능력시험(II)", "TOPIK-2");

    /** "한국어능력시험 제109회" 블록 — 토픽 IBT·말하기 평가는 여기 안 걸린다. */
    private static final Pattern BLOCK = Pattern.compile(
            "<strong>한국어능력시험 제(\\d+)회</strong>([\\s\\S]{0,1200}?)</dl>");
    private static final Pattern EXAM_DATE = Pattern.compile(
            "국내시험일</dt>[\\s\\S]{0,120}?<dd>\\s*(20\\d{2})\\.\\s*(\\d{1,2})\\.\\s*(\\d{1,2})");
    private static final Pattern REG_RANGE = Pattern.compile(
            "접수기간</dt>[\\s\\S]{0,120}?<dd>\\s*(20\\d{2})\\.\\s*(\\d{1,2})\\.\\s*(\\d{1,2})\\.\\s*~\\s*(?:(20\\d{2})\\.\\s*)?(\\d{1,2})\\.\\s*(\\d{1,2})");
    private static final Pattern RESULT_DATE = Pattern.compile(
            "성적발표</dt>[\\s\\S]{0,120}?<dd>\\s*(20\\d{2})\\.\\s*(\\d{1,2})\\.\\s*(\\d{1,2})");

    @Override
    public String sourceId() {
        return "TOPIK_WEB";
    }

    @Override
    public java.util.Set<String> coveredAgencies() {
        return java.util.Set.of(AGENCY);
    }

    @Override
    protected String pageUrl() {
        return URL;
    }

    @Override
    public List<CollectedSchedule> fetchAll() {
        try {
            // 쿠키가 없으면 인트로 셸(1.6KB)만 온다 — 본문을 받으려면 처음부터 보내야 한다
            String html = get(URL, Map.of("Cookie", "timezone=Asia/Seoul"));
            List<CollectedSchedule> out = parse(html);
            log.info("[{}] {} → 일정 {}건", sourceId(), URL, out.size());
            if (out.isEmpty()) {
                log.warn("[{}] 일정을 하나도 못 뽑았다 — 페이지 구조가 바뀌었을 수 있다", sourceId());
            }
            return out;
        } catch (Exception e) {
            log.error("[{}] 수집 실패 — 이번 회차는 건너뛴다: {}", sourceId(), e.toString());
            return List.of();
        }
    }

    @Override
    public List<CollectedSchedule> parse(String html) {
        List<CollectedSchedule> out = new ArrayList<>();
        if (html == null) {
            return out;
        }
        Matcher block = BLOCK.matcher(html);
        while (block.find()) {
            int round = Integer.parseInt(block.group(1));
            String body = block.group(2);

            LocalDate exam = date(EXAM_DATE.matcher(body));
            LocalDate[] reg = range(body);
            LocalDate result = date(RESULT_DATE.matcher(body));
            if (exam == null) {
                continue;   // 날짜가 없는 블록은 아직 공고 전 — 억지로 넣지 않는다
            }

            for (Map.Entry<String, String> level : LEVELS.entrySet()) {
                out.add(new CollectedSchedule(
                        level.getValue(), level.getKey(), Series.ETC, AGENCY, CATEGORY,
                        exam.getYear(), round, ExamType.WRITTEN,
                        reg == null ? null : reg[0].atTime(9, 0),
                        reg == null ? null : reg[1].atTime(18, 0),
                        exam, exam, result,
                        URL, ScheduleProvenance.SCRAPED));
            }
        }
        return out;
    }

    private LocalDate date(Matcher m) {
        if (!m.find()) {
            return null;
        }
        return LocalDate.of(Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    }

    private LocalDate[] range(String body) {
        Matcher m = REG_RANGE.matcher(body);
        if (!m.find()) {
            return null;
        }
        LocalDate start = LocalDate.of(Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        int endYear = m.group(4) != null ? Integer.parseInt(m.group(4)) : start.getYear();
        LocalDate end = LocalDate.of(endYear, Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)));
        if (end.isBefore(start) && m.group(4) == null) {
            end = end.plusYears(1);   // 12월 접수 ~ 1월 마감처럼 연도 생략된 채 해를 넘는 경우
        }
        return new LocalDate[]{start, end};
    }
}

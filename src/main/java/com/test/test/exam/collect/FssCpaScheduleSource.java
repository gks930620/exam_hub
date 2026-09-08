package com.test.test.exam.collect;

import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 금융감독원 공인회계사시험 일정 수집 — <b>1종</b>(1차·2차).
 *
 * <p>시험 전용 사이트({@code cpa.fss.or.kr})가 <b>지금 회차 하나</b>를 첫 화면에 크게 싣는다.
 * robots.txt 는 {@code /bos/ /upload/} 등만 막는다(2026-09-08 확인).
 * <pre>
 * 61회 공인회계사 시험 일정 안내
 *   제1차 시험  시험일 2026년 03월 02일 (월)
 *              응시원서접수 2026년 01월 08일 (목) ~ 01월 20일 (화) 18:00
 *              합격자발표일 2026년 03월 31일 (화)
 *   제2차 시험  시험일 2026년 06월 27일 (토) ~ 06월 28일 (일)  …
 * </pre>
 *
 * <h3>1차·2차를 필기·실기로 넣는다</h3>
 * 우리 스키마의 회차 키는 (시험, 연도, 회차, <b>구분</b>)이다. 1차·2차가 같은 회차 번호를 쓰므로
 * 구분을 달리하지 않으면 <b>두 줄이 서로를 덮는다</b>. 큐넷이 전문자격을 다루는 방식과 같게
 * 1차 → {@link ExamType#WRITTEN}, 2차 → {@link ExamType#PRACTICAL} 로 넣는다.
 *
 * <h3>접수 마감에 연도가 없다</h3>
 * {@code 2026년 01월 08일 (목) ~ 01월 20일 (화)} — 뒤쪽은 월·일뿐이다. 시작 연도를 붙이되
 * 마감이 시작보다 앞서면 해를 넘긴 것으로 본다(12월 시작 → 1월 마감).
 *
 * <h3>안 넣은 것</h3>
 * 같은 시행처의 <b>보험계리사·손해사정사·보험중개사</b>는 시험 사이트를 못 찾았다
 * (2026-09-08: {@code in.fss.or.kr}·{@code exam.fss.or.kr} 모두 없는 도메인). 수기로 남는다.
 */
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class FssCpaScheduleSource extends AbstractHtmlScheduleSource {

    static final String URL = "https://cpa.fss.or.kr/cpa/main/main.do?menuNo=1200000";
    static final String AGENCY = "금융감독원";
    static final String CODE = "M0287";
    static final String NAME = "공인회계사";
    private static final String CATEGORY = "국가전문자격";

    /** 접수 시작 시각은 화면에 없다(마감만 18:00). 이 시행처의 통상 개시 시각으로 둔다. */
    private static final int REG_OPEN_HOUR = 9;
    private static final int REG_CLOSE_HOUR = 18;

    /** "61회 공인회계사 시험 일정 안내" */
    private static final Pattern ROUND = Pattern.compile("(\\d{1,3})\\s*회\\s*공인회계사\\s*시험\\s*일정");
    /** "제1차 시험" ~ 다음 차수 전까지 */
    private static final Pattern STAGE = Pattern.compile("제\\s*([12])\\s*차\\s*시험");
    /** 2026년 03월 02일 (월) — 요일은 있을 때만 */
    private static final String D = "(20\\d\\d)\\s*년\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일";
    /** 연도가 빠진 뒤쪽 날짜 — 03월 02일 */
    private static final String MD = "(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일";
    private static final Pattern EXAM = Pattern.compile(
            "시험일\\s*" + D + "\\s*(?:\\([일월화수목금토]\\))?\\s*(?:~\\s*" + MD + ")?");
    private static final Pattern REG = Pattern.compile(
            "응시원서접수\\s*" + D + "\\s*(?:\\([일월화수목금토]\\))?\\s*~\\s*" + MD);
    private static final Pattern RESULT = Pattern.compile("합격자발표일\\s*" + D);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    @Override
    public String sourceId() {
        return "FSS_CPA_WEB";
    }

    @Override
    public Set<String> coveredAgencies() {
        return Set.of(AGENCY);
    }

    /** 공인회계사 하나만 맡는다 — 같은 시행처의 보험계리사 등은 사이트를 못 찾아 수기다. */
    @Override
    public Set<String> coveredExamCodes() {
        return Set.of(CODE);
    }

    @Override
    protected String pageUrl() {
        return URL;
    }

    @Override
    public List<CollectedSchedule> parse(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        String text = clean(stripComments(html));
        Matcher r = ROUND.matcher(text);
        if (!r.find()) {
            return List.of();   // 회차를 모르면 어느 회차인지 못 정한다 — 지어내지 않는다
        }
        int round = Integer.parseInt(r.group(1));

        List<CollectedSchedule> out = new ArrayList<>();
        Matcher stages = STAGE.matcher(text);
        List<int[]> spans = new ArrayList<>();
        while (stages.find()) {
            spans.add(new int[]{Integer.parseInt(stages.group(1)), stages.start(), stages.end()});
        }
        for (int i = 0; i < spans.size(); i++) {
            int stage = spans.get(i)[0];
            int from = spans.get(i)[2];
            int to = i + 1 < spans.size() ? spans.get(i + 1)[1] : text.length();
            String block = text.substring(from, to);

            LocalDate[] exam = examDates(block);
            if (exam == null) {
                continue;   // 시험일이 없으면 일정이 아니다
            }
            LocalDate[] reg = registration(block);
            LocalDate result = single(RESULT, block);

            out.add(new CollectedSchedule(
                    CODE, NAME, Series.ETC, AGENCY, CATEGORY,
                    exam[0].getYear(), round,
                    stage == 1 ? ExamType.WRITTEN : ExamType.PRACTICAL,
                    reg == null ? null : reg[0].atTime(REG_OPEN_HOUR, 0),
                    reg == null ? null : reg[1].atTime(REG_CLOSE_HOUR, 0),
                    exam[0], exam[1], result,
                    URL, ScheduleProvenance.SCRAPED));
        }
        return out;
    }

    /** 시험일 — 이틀에 걸치면 종료일까지. 종료일에는 연도가 없다. */
    private LocalDate[] examDates(String block) {
        Matcher m = EXAM.matcher(block);
        if (!m.find()) {
            return null;
        }
        LocalDate start = date(m.group(1), m.group(2), m.group(3));
        LocalDate end = m.group(4) == null ? start : rollForward(start, m.group(4), m.group(5));
        return new LocalDate[]{start, end};
    }

    /** 응시원서접수 — 마감에 연도가 없다. */
    private LocalDate[] registration(String block) {
        Matcher m = REG.matcher(block);
        if (!m.find()) {
            return null;
        }
        LocalDate start = date(m.group(1), m.group(2), m.group(3));
        return new LocalDate[]{start, rollForward(start, m.group(4), m.group(5))};
    }

    /** 월·일에 시작 연도를 붙인다. 시작보다 앞서면 해를 넘긴 것이다(12월 시작 → 1월 마감). */
    private LocalDate rollForward(LocalDate start, String month, String day) {
        LocalDate d = LocalDate.of(start.getYear(), Integer.parseInt(month), Integer.parseInt(day));
        return d.isBefore(start) ? d.plusYears(1) : d;
    }

    private LocalDate single(Pattern p, String block) {
        Matcher m = p.matcher(block);
        return m.find() ? date(m.group(1), m.group(2), m.group(3)) : null;
    }

    private LocalDate date(String y, String m, String d) {
        return LocalDate.of(Integer.parseInt(y), Integer.parseInt(m), Integer.parseInt(d));
    }

    private String clean(String html) {
        return TAG.matcher(html).replaceAll(" ").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
    }
}

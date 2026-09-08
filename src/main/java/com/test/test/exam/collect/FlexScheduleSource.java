package com.test.test.exam.collect;

import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 한국외국어대학교 FLEX 일정 수집 — 영어·일본어·중국어 등 <b>7개 언어</b>.
 *
 * <p>첫 화면({@code flex.hufs.ac.kr})의 안내 띠에 그 해 회차가 그대로 있다.
 * <pre>2026년 1회차 시험 - 2026.5.31(일)   접수 : 4.30(목) ~ 5.6(수)</pre>
 * 일정 전용 페이지({@code /flex/14799/subview.do})에는 날짜가 없다 — 첫 화면이 원본이다.
 * robots.txt 는 {@code /bbs/} 등 몇 경로만 막고 첫 화면은 열려 있다(2026-09-08 확인).
 *
 * <h3>연도가 반쪽만 있다</h3>
 * 시험일에는 연도가 붙지만(2026.5.31) <b>접수 기간에는 없다</b>(4.30 ~ 5.6).
 * 시험 연도를 붙이되, 접수 월이 시험 월보다 뒤면 <b>전해</b>다(12월 접수 → 이듬해 1월 시험).
 *
 * <h3>7개 언어가 한 회차를 같이 친다</h3>
 * 응시자가 언어를 고를 뿐 날짜는 하나다. 한 회차를 7종에 똑같이 넣는다.
 *
 * <p><b>이 화면은 손으로 고치는 배너다.</b> 시행처가 문구를 바꾸면 조용히 0건이 된다 —
 * {@link AbstractHtmlScheduleSource#fetchAll()} 이 0건일 때 경고를 남기니 그걸로 알아챈다.
 */
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class FlexScheduleSource extends AbstractHtmlScheduleSource {

    static final String URL = "https://flex.hufs.ac.kr/flex/index.do";
    static final String AGENCY = "한국외국어대학교";

    /** 언어별 종목 — 코드·이름·분류는 {@code seed/exam_master.json} 과 같아야 한다. */
    private record Language(String sourceCode, String name, String category) {
    }

    private static final List<Language> LANGUAGES = List.of(
            new Language("M0335", "FLEX 영어", "어학-영어"),
            new Language("M0340", "FLEX 일본어", "어학-일본어"),
            new Language("M0345", "FLEX 중국어", "어학-중국어"),
            new Language("M0351", "FLEX 스페인어", "어학-기타"),
            new Language("M0352", "FLEX 프랑스어", "어학-기타"),
            new Language("M0353", "FLEX 독일어", "어학-기타"),
            new Language("M0354", "FLEX 러시아어", "어학-기타"));

    /** 2026년 1회차 시험 - 2026.5.31(일) 접수 : 4.30(목) ~ 5.6(수) */
    private static final Pattern ROW = Pattern.compile(
            "(20\\d\\d)\\s*년\\s*(\\d{1,2})\\s*회차\\s*시험[^0-9]{0,10}"
                    + "(20\\d\\d)\\.(\\d{1,2})\\.(\\d{1,2})\\s*\\([일월화수목금토]\\)"
                    + "[\\s\\S]{0,60}?접수\\s*:?\\s*(\\d{1,2})\\.(\\d{1,2})\\s*\\([일월화수목금토]\\)"
                    + "\\s*~\\s*(\\d{1,2})\\.(\\d{1,2})\\s*\\([일월화수목금토]\\)");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    @Override
    public String sourceId() {
        return "FLEX_WEB";
    }

    @Override
    public Set<String> coveredAgencies() {
        return Set.of(AGENCY);
    }

    @Override
    public Set<String> coveredExamCodes() {
        return LANGUAGES.stream().map(Language::sourceCode).collect(java.util.stream.Collectors.toSet());
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
        List<CollectedSchedule> out = new ArrayList<>();
        Map<Integer, Boolean> seen = new LinkedHashMap<>();
        Matcher m = ROW.matcher(clean(stripComments(html)));
        while (m.find()) {
            int round = Integer.parseInt(m.group(2));
            LocalDate exam = LocalDate.of(Integer.parseInt(m.group(3)),
                    Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)));
            if (seen.put(exam.getYear() * 100 + round, true) != null) {
                continue;
            }
            LocalDate regStart = withYear(m.group(6), m.group(7), exam);
            LocalDate regEnd = withYear(m.group(8), m.group(9), exam);

            for (Language l : LANGUAGES) {
                out.add(new CollectedSchedule(
                        l.sourceCode(), l.name(), Series.ETC, AGENCY, l.category(),
                        exam.getYear(), round, ExamType.WRITTEN,
                        regStart == null ? null : regStart.atTime(10, 0),
                        regEnd == null ? null : regEnd.atTime(18, 0),
                        exam, exam, null,
                        URL, ScheduleProvenance.SCRAPED));
            }
        }
        return out;
    }

    /**
     * 월·일에 시험 연도를 붙인다. 접수가 시험보다 <b>뒤 달</b>이면 전해다
     * (12월 접수 → 이듬해 1월 시험). 안 그러면 접수가 시험보다 11개월 늦은 값이 된다.
     */
    private LocalDate withYear(String month, String day, LocalDate exam) {
        try {
            LocalDate d = LocalDate.of(exam.getYear(), Integer.parseInt(month), Integer.parseInt(day));
            return d.isAfter(exam) ? d.minusYears(1) : d;
        } catch (Exception e) {
            return null;
        }
    }

    private String clean(String html) {
        return TAG.matcher(html).replaceAll(" ").replace("&nbsp;", " ").replaceAll("\\s+", " ");
    }
}

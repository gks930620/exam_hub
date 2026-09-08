package com.test.test.exam.collect;

import com.test.test.exam.common.TimeUtil;
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
 * DIAT 디지털정보활용능력 일정 수집 — <b>1종</b>, 연 14회차.
 *
 * <p>자격은 한국정보통신진흥협회(KAIT) 것이고, 검정 시행과 일정 공개는
 * 한국정보통신인력개발센터({@code ihd.or.kr})가 한다. 일정은
 * {@code /guidecert.do} 표 하나에 그 해 회차가 다 있다.
 * <pre>종목 | 등급 | 회차 | 접수일자 | 시험일자 | 합격자 발표
 * 디지털정보 활용능력 | 초급/중급/고급 | 2601회 | '25.12.08.(월) ~ 12.17.(수) | '26.01.24.(토) | 02.13.(금)</pre>
 *
 * <h3>연도가 바뀔 때만 적힌다</h3>
 * 이 표의 함정이다. 연도는 <b>바뀌는 자리에만</b> 두 자리로 적히고({@code '25}) 나머지는 월·일뿐이다.
 * 게다가 <b>12월에 접수해 이듬해 1~2월에 치는 회차</b>가 여럿이라, 한 줄 안에서도 연도가 갈린다.
 * 그래서 시험일을 기준으로 앞뒤로 넘긴다 — 접수가 시험보다 뒤 달이면 전해, 발표가 앞 달이면 이듬해.
 * 못 넘기면 접수가 시험보다 10개월 늦은 값이 된다.
 *
 * <h3>첫 칸이 줄마다 다르다</h3>
 * 종목·등급 칸이 세로로 합쳐져 있어(rowspan) 첫 줄만 6칸이고 나머지는 4칸이다.
 * 그래서 <b>칸을 뒤에서부터</b> 센다 — 회차·접수·시험·발표는 언제나 마지막 넷이다.
 *
 * <p>같은 사이트에 리눅스마스터·SNS광고마케터 등이 있지만 이 표에는 DIAT 만 실린다.
 * 인터넷정보관리사(M0392)는 여기 없다 — 아직 수기다.
 */
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class IhdScheduleSource extends AbstractHtmlScheduleSource {

    static final String URL = "https://www.ihd.or.kr/guidecert.do";
    /** 마스터가 적은 시행처와 같아야 한다 — 판정이 기관으로도 붙는다. */
    static final String AGENCY = "한국정보통신진흥협회(KAIT)";
    private static final String CATEGORY = "사무-IT";
    private static final String SOURCE_CODE = "M0391";
    private static final String NAME = "DIAT 디지털정보활용능력";

    private static final Pattern TABLE = Pattern.compile("<table[\\s\\S]*?</table>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROW = Pattern.compile("<tr[\\s\\S]*?</tr>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL =
            Pattern.compile("<t[dh]([^>]*)>([\\s\\S]*?)(?=</t[dh]>|<t[dh][\\s>]|</tr>|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROUND = Pattern.compile("(\\d{3,4})\\s*회");
    /** '26.01.24. 또는 01.24. — 연도는 바뀔 때만 붙는다 */
    private static final Pattern DAY = Pattern.compile("(?:'(\\d{2})\\.)?\\s*(\\d{1,2})\\.\\s*(\\d{1,2})\\.");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    @Override
    public String sourceId() {
        return "IHD_WEB";
    }

    @Override
    public Set<String> coveredAgencies() {
        return Set.of(AGENCY);
    }

    @Override
    public Set<String> coveredExamCodes() {
        return Set.of(SOURCE_CODE);
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
        Matcher table = TABLE.matcher(stripComments(html));
        String schedule = null;
        while (table.find()) {
            if (table.group().contains("회차") && table.group().contains("접수일자")) {
                schedule = table.group();
                break;
            }
        }
        if (schedule == null) {
            return List.of();
        }

        List<CollectedSchedule> out = new ArrayList<>();
        int lastYear = TimeUtil.today().getYear();   // 연도가 한 번도 안 적힌 표를 만나도 멈추지 않게
        Matcher rows = ROW.matcher(schedule);
        while (rows.find()) {
            List<String> cells = cells(rows.group());
            if (cells.size() < 4) {
                continue;
            }
            // 종목·등급 칸이 세로로 합쳐져 있어 줄마다 칸 수가 다르다 — 뒤에서 넷을 쓴다
            int n = cells.size();
            Matcher round = ROUND.matcher(cells.get(n - 4));
            if (!round.find()) {
                continue;   // 머리글 줄
            }
            String regCell = cells.get(n - 3);
            String examCell = cells.get(n - 2);
            String resultCell = cells.get(n - 1);

            Integer examYear = yearOf(examCell);
            if (examYear == null) {
                examYear = lastYear;
            }
            LocalDate exam = day(examCell, 0, examYear);
            if (exam == null) {
                continue;
            }
            lastYear = exam.getYear();

            LocalDate regStart = shift(day(regCell, 0, yearOr(regCell, examYear)), exam, -1);
            LocalDate regEnd = shift(day(regCell, 1, yearOr(regCell, examYear)), exam, -1);
            LocalDate result = shift(day(resultCell, 0, yearOr(resultCell, examYear)), exam, +1);

            out.add(new CollectedSchedule(
                    SOURCE_CODE, NAME, Series.ETC, AGENCY, CATEGORY,
                    exam.getYear(), Integer.parseInt(round.group(1)), ExamType.WRITTEN,
                    regStart == null ? null : regStart.atTime(10, 0),
                    regEnd == null ? null : regEnd.atTime(18, 0),
                    exam, exam, result,
                    URL, ScheduleProvenance.SCRAPED));
        }
        return out;
    }

    /**
     * 시험일을 기준으로 해를 넘긴다.
     *
     * @param years 접수처럼 <b>앞</b>에 와야 하는 날짜는 -1, 발표처럼 <b>뒤</b>에 와야 하는 날짜는 +1
     */
    private LocalDate shift(LocalDate date, LocalDate exam, int years) {
        if (date == null) {
            return null;
        }
        if (years < 0 && date.isAfter(exam)) {
            return date.plusYears(years);
        }
        if (years > 0 && date.isBefore(exam)) {
            return date.plusYears(years);
        }
        return date;
    }

    /** 칸에 적힌 연도({@code '26}). 없으면 null. */
    private Integer yearOf(String text) {
        Matcher m = DAY.matcher(text);
        return m.find() && m.group(1) != null ? 2000 + Integer.parseInt(m.group(1)) : null;
    }

    private int yearOr(String text, int fallback) {
        Integer y = yearOf(text);
        return y == null ? fallback : y;
    }

    /** 한 칸에서 {@code nth} 번째 날짜. 접수 칸은 시작·마감 둘이 들어 있다. */
    private LocalDate day(String text, int nth, int year) {
        Matcher m = DAY.matcher(text);
        for (int i = 0; i <= nth; i++) {
            if (!m.find()) {
                return null;
            }
        }
        int y = m.group(1) != null ? 2000 + Integer.parseInt(m.group(1)) : year;
        try {
            return LocalDate.of(y, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> cells(String row) {
        List<String> out = new ArrayList<>();
        Matcher m = CELL.matcher(row);
        while (m.find()) {
            out.add(clean(m.group(2)));
        }
        return out;
    }

    private String clean(String html) {
        return TAG.matcher(html).replaceAll(" ").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
    }
}

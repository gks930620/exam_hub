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
 * HSK 중국어능력시험 일정 수집 — IBT 필기 <b>1종</b>.
 *
 * <p>{@code hsk.or.kr/?c1=100&c2=002&c3=002&c4=006} 에 그 해 IBT 일정이 표로 있다.
 * <pre>시험일자 | 등급 | 실시 지역 | 접수 일정 | 성적조회 예정일
 * 1월 10일(토) | HSK IBT 1~6급 | 서울… | 인터넷 접수 : 2025년 11월 26일(수) ~ 2025년 12월 31일(수) | 1월 26일(월)</pre>
 * robots.txt 는 {@code Allow: /} 뿐이다(2026-09-08 확인).
 *
 * <h3>시험일자에 연도가 없다</h3>
 * 연도는 제목("2026년 HSK IBT 시험일정")에만 있다. 그런데 <b>접수 칸에는 연도가 붙어 있고</b>
 * 1월 시험은 전해 11월에 접수한다 — 그래서 시험일은 제목의 연도로, 접수는 칸에 적힌 연도로 읽는다.
 * 접수에 연도가 없는 줄은 시험 연도를 쓰되 시험일보다 뒤면 전해로 넘긴다.
 *
 * <h3>회차 번호가 없다</h3>
 * 시행처가 회차를 안 매긴다. <b>지어내지 않는다</b> — 시험일을 {@code YYYYMMDD} 로 바꿔 멱등 키로만
 * 쓰고 화면에는 회차로 안 보인다({@link com.test.test.exam.domain.ExamSchedule#roundLabel()}).
 *
 * <p>HSKK(회화)·PBT·BCT 는 같은 사이트의 <b>다른 탭</b>이다. HSKK 는 마스터에 있지만
 * 표 구조를 따로 확인해야 해서 아직 안 담는다.
 */
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class HskScheduleSource extends AbstractHtmlScheduleSource {

    static final String URL = "https://www.hsk.or.kr/?c1=100&c2=002&c3=002&c4=006";
    static final String AGENCY = "HSK한국사무국";
    private static final String CATEGORY = "어학-중국어";
    private static final String SOURCE_CODE = "M0341";
    private static final String NAME = "HSK 중국어능력시험(필기)";

    private static final Pattern TABLE = Pattern.compile("<table[\\s\\S]*?</table>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROW = Pattern.compile("<tr[\\s\\S]*?</tr>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL =
            Pattern.compile("<t[dh]([^>]*)>([\\s\\S]*?)(?=</t[dh]>|<t[dh][\\s>]|</tr>|$)", Pattern.CASE_INSENSITIVE);
    /** 2026년 HSK IBT 시험일정 */
    private static final Pattern HEADING = Pattern.compile("(20\\d\\d)\\s*년[^<]{0,20}시험일정");
    /** 1월 10일 */
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    /** 2025년 11월 26일 */
    private static final Pattern FULL = Pattern.compile("(20\\d\\d)\\s*년\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    @Override
    public String sourceId() {
        return "HSK_WEB";
    }

    @Override
    public Set<String> coveredAgencies() {
        return Set.of(AGENCY);
    }

    @Override
    public Set<String> coveredExamCodes() {
        // 비큐넷 시드가 같은 시험에 다른 코드를 붙인다 — 자세한 사정은 SeedCodeAlias.
        return SeedCodeAlias.plus(Set.of(SOURCE_CODE), "HSK");
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
        String stripped = stripComments(html);
        Matcher heading = HEADING.matcher(clean(stripped));
        if (!heading.find()) {
            return List.of();   // 연도를 모르면 날짜를 만들지 않는다
        }
        int year = Integer.parseInt(heading.group(1));

        Matcher table = TABLE.matcher(stripped);
        String schedule = null;
        while (table.find()) {
            if (table.group().contains("시험일자")) {
                schedule = table.group();
                break;
            }
        }
        if (schedule == null) {
            return List.of();
        }

        List<CollectedSchedule> out = new ArrayList<>();
        Matcher rows = ROW.matcher(schedule);
        while (rows.find()) {
            List<String> cells = cells(rows.group());
            if (cells.size() < 3) {
                continue;
            }
            LocalDate exam = monthDay(cells.get(0), year);
            if (exam == null) {
                continue;   // 머리글 줄
            }
            // 접수 칸은 연도가 붙어 있기도 하고 없기도 하다 — 붙어 있으면 그걸 쓴다
            String regCell = cells.stream().filter(c -> c.contains("접수")).findFirst().orElse("");
            LocalDate regStart = dateIn(regCell, 0, exam);
            LocalDate regEnd = dateIn(regCell, 1, exam);
            LocalDate result = monthDay(cells.get(cells.size() - 1), year);
            if (result != null && result.isBefore(exam)) {
                result = result.plusYears(1);   // 12월 시험 → 이듬해 발표
            }
            // 회차를 지어내지 않는다 — 시험일이 멱등 키다
            int key = exam.getYear() * 10000 + exam.getMonthValue() * 100 + exam.getDayOfMonth();

            out.add(new CollectedSchedule(
                    SOURCE_CODE, NAME, Series.ETC, AGENCY, CATEGORY,
                    exam.getYear(), key, ExamType.WRITTEN,
                    regStart == null ? null : regStart.atTime(10, 0),
                    regEnd == null ? null : regEnd.atTime(18, 0),
                    exam, exam, result,
                    URL, ScheduleProvenance.SCRAPED));
        }
        return out;
    }

    /** {@code nth} 번째 접수 날짜. 연도가 적혀 있으면 그걸 쓰고, 없으면 시험 연도에서 앞으로 넘긴다. */
    private LocalDate dateIn(String text, int nth, LocalDate exam) {
        Matcher full = FULL.matcher(text);
        int seen = 0;
        while (full.find()) {
            if (seen++ == nth) {
                try {
                    return LocalDate.of(Integer.parseInt(full.group(1)),
                            Integer.parseInt(full.group(2)), Integer.parseInt(full.group(3)));
                } catch (Exception e) {
                    return null;
                }
            }
        }
        Matcher m = MONTH_DAY.matcher(text);
        for (int i = 0; i <= nth; i++) {
            if (!m.find()) {
                return null;
            }
        }
        try {
            LocalDate d = LocalDate.of(exam.getYear(),
                    Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            return d.isAfter(exam) ? d.minusYears(1) : d;
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate monthDay(String text, int year) {
        Matcher m = MONTH_DAY.matcher(text);
        if (!m.find()) {
            return null;
        }
        try {
            return LocalDate.of(year, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
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

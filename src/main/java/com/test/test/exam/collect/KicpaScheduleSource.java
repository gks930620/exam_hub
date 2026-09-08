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
 * 한국공인회계사회 AT 자격시험 일정 수집 — FAT 1·2급, TAT 1·2급 <b>4종</b>.
 *
 * <p>첫 화면({@code at.kicpa.or.kr/home/main.jsp})의 연간 일정표를 읽는다.
 * robots.txt 는 {@code /cmd/ /com/ …} 같은 내부 경로만 막고 {@code /home/} 은 열려 있다(2026-09-08 확인).
 *
 * <h3>표가 눕혀 있다</h3>
 * 보통은 한 줄이 한 회차인데 이 표는 <b>회차가 열</b>이고 항목이 행이다.
 * <pre>
 * 구분      | 제88회      | 제89회    | … | 제95회
 * 원서접수  | 2.11.~2.19. | 4.2~4.9   | … | 12.3~12.10
 * 시험일자  | 2.28(토)    | 4.18(토)  | … | 12.19(토)
 * 합격자발표| 3.6(금)     | 4.24(금)  | … | 12.25(금)
 * </pre>
 * 그래서 행을 먼저 모으고 <b>열 번호로 세워서</b> 회차를 만든다.
 *
 * <h3>연도가 표 안에 없다</h3>
 * 날짜가 전부 {@code 2.28(토)} 꼴이다. 연도는 표 위 제목("2026년 국가공인 AT자격시험일정")에서 가져온다.
 * 같은 화면에 "2024년 일정보기" 같은 다른 연도 문구가 있어, <b>제목 형태를 콕 집어</b> 찾는다.
 * 12월 접수 → 이듬해 1월 시험 같은 경우를 위해 접수·발표는 시험일 기준으로 해를 넘긴다.
 */
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class KicpaScheduleSource extends AbstractHtmlScheduleSource {

    static final String URL = "https://at.kicpa.or.kr/home/main.jsp";
    static final String AGENCY = "한국공인회계사회";
    private static final String CATEGORY = "회계-세무";

    /** 한 회차가 네 종목에 똑같이 붙는다 — 같은 날 등급만 골라 친다. */
    private static final Map<String, String> EXAMS = new LinkedHashMap<>();

    static {
        EXAMS.put("M0433", "FAT 회계실무 1급");
        EXAMS.put("M0434", "FAT 회계실무 2급");
        EXAMS.put("M0435", "TAT 세무실무 1급");
        EXAMS.put("M0436", "TAT 세무실무 2급");
    }

    /** "2026년 국가공인 AT자격시험일정" — 화면의 다른 연도 문구에 안 속으려고 제목 형태를 콕 집는다. */
    private static final Pattern TITLE_YEAR = Pattern.compile("(20\\d\\d)\\s*년\\s*국가공인");
    private static final Pattern TABLE = Pattern.compile("<table[\\s\\S]*?</table>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROW = Pattern.compile("<tr[\\s\\S]*?</tr>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL =
            Pattern.compile("<t[dh]([^>]*)>([\\s\\S]*?)(?=</t[dh]>|<t[dh][\\s>]|</tr>|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern ROUND = Pattern.compile("제\\s*(\\d{1,4})\\s*회");
    /** 2.28(토) / 2.11. — 요일과 끝점은 있을 때도 없을 때도 있다 */
    private static final Pattern DAY = Pattern.compile("(\\d{1,2})\\.\\s*(\\d{1,2})");

    @Override
    public String sourceId() {
        return "KICPA_WEB";
    }

    @Override
    public Set<String> coveredAgencies() {
        return Set.of(AGENCY);
    }

    @Override
    public Set<String> coveredExamCodes() {
        return Set.copyOf(EXAMS.keySet());
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
        // 지난해 표가 주석에 남아 있다 — 안 걷어내면 그쪽이 이긴다(실측 2026-09-08)
        html = stripComments(html);
        Matcher y = TITLE_YEAR.matcher(clean(html));
        if (!y.find()) {
            return List.of();   // 연도를 모르면 날짜를 만들 수 없다 — 지어내지 않는다
        }
        int year = Integer.parseInt(y.group(1));

        // 첫 화면에는 표가 여럿이다(채용공고 등). 문서 전체를 훑으면 나중 표의 "구분" 행이
        // 회차 머리글을 덮어써 회차가 줄어든다 — 실측으로 8회차가 6회차가 됐다(2026-09-08).
        String table = scheduleTable(html);
        if (table == null) {
            return List.of();
        }

        List<String> rounds = null, regs = null, exams = null, results = null;
        Matcher rows = ROW.matcher(table);
        while (rows.find()) {
            List<String> cells = cells(rows.group());
            if (cells.size() < 2) {
                continue;
            }
            String head = cells.get(0);
            List<String> rest = cells.subList(1, cells.size());
            if (head.contains("구분")) {
                rounds = rest;
            } else if (head.contains("원서접수")) {
                regs = rest;
            } else if (head.contains("시험일자")) {
                exams = rest;
            } else if (head.contains("합격자발표")) {
                results = rest;
            }
        }
        if (rounds == null || exams == null) {
            return List.of();
        }

        List<CollectedSchedule> out = new ArrayList<>();
        for (int col = 0; col < rounds.size(); col++) {
            Matcher r = ROUND.matcher(rounds.get(col));
            if (!r.find()) {
                continue;
            }
            int round = Integer.parseInt(r.group(1));
            LocalDate exam = day(at(exams, col), year);
            if (exam == null) {
                continue;   // 시험일이 없으면 일정이 아니다
            }
            LocalDate[] reg = range(at(regs, col), year, exam);
            // 발표가 시험보다 앞선 달이면 이듬해다(12월 시험 → 1월 발표)
            LocalDate result = shift(day(at(results, col), year), exam, +1);

            for (Map.Entry<String, String> e : EXAMS.entrySet()) {
                out.add(new CollectedSchedule(
                        e.getKey(), e.getValue(), Series.ETC, AGENCY, CATEGORY,
                        year, round, ExamType.WRITTEN,
                        reg == null ? null : reg[0].atTime(10, 0),
                        reg == null ? null : reg[1].atTime(18, 0),
                        exam, exam, result,
                        URL, ScheduleProvenance.SCRAPED));
            }
        }
        return out;
    }

    /** 일정표를 콕 집는다 — 원서접수와 시험일자가 <b>둘 다</b> 있는 표만 일정표다. */
    private String scheduleTable(String html) {
        Matcher t = TABLE.matcher(html);
        while (t.find()) {
            String table = t.group();
            if (table.contains("원서접수") && table.contains("시험일자")) {
                return table;
            }
        }
        return null;
    }

    private String at(List<String> row, int col) {
        return row != null && col < row.size() ? row.get(col) : null;
    }

    /** "10.1~10.8" — 두 날짜를 뽑는다. 접수는 시험보다 앞이므로 뒤 달이면 전해다. */
    private LocalDate[] range(String raw, int year, LocalDate exam) {
        if (raw == null) {
            return null;
        }
        int split = raw.indexOf('~');
        if (split < 0) {
            return null;
        }
        LocalDate start = shift(day(raw.substring(0, split), year), exam, -1);
        LocalDate end = shift(day(raw.substring(split + 1), year), exam, -1);
        return start == null || end == null ? null : new LocalDate[]{start, end};
    }

    /**
     * 시험일을 기준으로 해를 넘긴다.
     *
     * @param years 접수처럼 <b>앞</b>에 오는 날짜는 -1, 발표처럼 <b>뒤</b>에 오는 날짜는 +1
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

    private LocalDate day(String raw, int year) {
        if (raw == null) {
            return null;
        }
        Matcher m = DAY.matcher(raw);
        if (!m.find()) {
            return null;
        }
        try {
            return LocalDate.of(year, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        } catch (Exception e) {
            return null;   // 2.30 같은 값이 오면 그 칸만 버린다
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

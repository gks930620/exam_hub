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
 * 보험연수원 보험심사역 일정 수집 — 개인(AIU)·기업(CIU) <b>2종</b>.
 *
 * <p>{@code in.or.kr/main/certification/aiu/schedule.do} 에 그 해 회차가 있다.
 * 개인·기업 부문은 <b>같은 날 같이</b> 치르므로 일정 페이지가 하나다.
 * robots.txt 는 {@code Allow: /main/sukang/} 한 줄뿐이라 막는 경로가 없다(2026-09-08 확인).
 *
 * <h3>표가 가로로 눕혀 있다</h3>
 * 회차가 <b>칸(열)</b>이고 시험일·원서접수기간·합격자발표가 <b>줄(행)</b>이다.
 * <pre>구분        | 제32회 시험            | 제33회 시험
 * 시험일      | 2026. 4. 4(토)        | 2026. 9. 12(토)
 * 원서접수기간 | 2026. 3. 3(화) 10:00 ~ 3. 12(목) 18:00 | …</pre>
 * 그대로 읽으면 한 회차도 못 만든다 — 세워서 읽는다.
 *
 * <h3>마감 날짜는 연도를 생략한다</h3>
 * {@code 2026. 3. 3(화) 10:00 ~ 3. 12(목) 18:00} — 마감에 연도가 없다. 시작의 연도를 이어받는다.
 *
 * <p>같은 사이트의 {@code cifi}·{@code ica}·{@code irp}·{@code cip} 는 보험조사분석사 등
 * 우리 마스터에 없는 시험이라 읽지 않는다.
 */
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class InsuranceScheduleSource extends AbstractHtmlScheduleSource {

    static final String URL = "https://www.in.or.kr/main/certification/aiu/schedule.do";
    static final String AGENCY = "보험연수원";
    private static final String CATEGORY = "금융";

    /** 한 회차가 두 부문에 똑같이 붙는다 — 같은 날 부문만 골라 친다. */
    private static final Map<String, String> EXAMS = new LinkedHashMap<>();

    static {
        EXAMS.put("M0426", "보험심사역(AIU)");
        EXAMS.put("M0427", "보험심사역(CIU)");
    }

    private static final Pattern TABLE = Pattern.compile("<table[\\s\\S]*?</table>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROW = Pattern.compile("<tr[\\s\\S]*?</tr>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL =
            Pattern.compile("<t[dh]([^>]*)>([\\s\\S]*?)(?=</t[dh]>|<t[dh][\\s>]|</tr>|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROUND = Pattern.compile("제\\s*(\\d{1,4})\\s*회");
    /** 2026. 4. 4 — 점 뒤에 공백이 끼어 있다 */
    private static final Pattern FULL = Pattern.compile("(20\\d\\d)\\.\\s*(\\d{1,2})\\.\\s*(\\d{1,2})");
    /** 3. 12 — 연도가 생략된 마감일 */
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})\\.\\s*(\\d{1,2})");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    @Override
    public String sourceId() {
        return "INSURANCE_WEB";
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
        Matcher table = TABLE.matcher(stripComments(html));
        List<String> rounds = null, exams = null, regs = null, results = null;
        while (table.find()) {
            Matcher rows = ROW.matcher(table.group());
            while (rows.find()) {
                List<String> cells = cells(rows.group());
                if (cells.isEmpty()) {
                    continue;
                }
                String head = cells.get(0);
                if (head.contains("구분")) {
                    rounds = cells;
                } else if (head.contains("시험일")) {
                    exams = cells;
                } else if (head.contains("원서접수")) {
                    regs = cells;
                } else if (head.contains("합격자발표") || head.contains("합격자 발표")) {
                    results = cells;
                }
            }
            if (rounds != null && exams != null) {
                break;
            }
        }
        if (rounds == null || exams == null) {
            return List.of();
        }

        List<CollectedSchedule> out = new ArrayList<>();
        for (int col = 1; col < rounds.size(); col++) {
            Matcher round = ROUND.matcher(rounds.get(col));
            if (!round.find()) {
                continue;
            }
            LocalDate exam = full(at(exams, col));
            if (exam == null) {
                continue;
            }
            LocalDate regStart = full(at(regs, col));
            LocalDate regEnd = tail(at(regs, col), regStart);
            LocalDate result = full(at(results, col));

            for (Map.Entry<String, String> e : EXAMS.entrySet()) {
                out.add(new CollectedSchedule(
                        e.getKey(), e.getValue(), Series.ETC, AGENCY, CATEGORY,
                        exam.getYear(), Integer.parseInt(round.group(1)), ExamType.WRITTEN,
                        regStart == null ? null : regStart.atTime(10, 0),
                        regEnd == null ? null : regEnd.atTime(18, 0),
                        exam, exam, result,
                        URL, ScheduleProvenance.SCRAPED));
            }
        }
        return out;
    }

    private String at(List<String> row, int col) {
        return row != null && col < row.size() ? row.get(col) : "";
    }

    private LocalDate full(String text) {
        Matcher m = FULL.matcher(text);
        if (!m.find()) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * {@code ~} 뒤의 마감일. 연도가 생략돼 있어 시작의 연도를 이어받고,
     * 시작보다 앞 달이면 이듬해다(12월 접수 → 1월 마감).
     */
    private LocalDate tail(String text, LocalDate start) {
        int split = text.indexOf('~');
        if (split < 0 || start == null) {
            return null;
        }
        String rest = text.substring(split + 1);
        LocalDate dated = full(rest);
        if (dated != null) {
            return dated;   // 마감에도 연도가 붙어 있는 해가 있다
        }
        Matcher m = MONTH_DAY.matcher(rest);
        if (!m.find()) {
            return null;
        }
        try {
            LocalDate end = LocalDate.of(start.getYear(),
                    Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            return end.isBefore(start) ? end.plusYears(1) : end;
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

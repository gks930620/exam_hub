package com.test.test.exam.collect;

import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.Series;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TEPS 정기시험 일정 수집 (서울대 TEPS관리위원회).
 *
 * <p>원본: {@code teps.or.kr} 첫 화면의 "시험일정안내" 표. 일정 전용 페이지는 스크립트로 그리지만
 * 첫 화면 표에는 값이 서버렌더로 들어 있다.
 *
 * <p>표의 한 행: 시험명 · D-day · 접수기간 · 시험일 · 발표일 · 상태
 * <pre>제408회 TEPS 정기시험 | D-2 | 2026-07-13 ~ 2026-08-09 | 2026-08-29 (토) 14:20 | 2026-09-07 (월) 16:00</pre>
 *
 * <p><b>"(추가접수)" 행은 버린다.</b> 같은 회차의 늦은 접수 창구라, 회차로 잡으면 중복이 된다.
 */
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class TepsScheduleSource extends AbstractHtmlScheduleSource {

    /**
     * 종목코드는 <b>종목</b>을 가리킨다 — 회차가 아니다.
     *
     * <p>전에는 회차를 붙여("TOEIC-" + round) 회차마다 새 종목코드가 됐다. 수집은 이 코드로
     * 기존 시험을 찾으므로, <b>회차 수만큼 같은 이름의 시험이 생겼다</b>(토익이 10개였다).
     * 회차는 (연도, 회차, 구분)으로 이미 구분되니 코드에 넣을 이유가 없다.
     * 값은 비큐넷 시드(seed/non_qnet_exams.json)와 같아야 그 시험에 일정이 붙는다.
     */
    static final String SOURCE_CODE = "TEPS";

    static final String NAME = "TEPS 텝스";
    static final String AGENCY = "서울대학교 TEPS관리위원회";
    static final String CATEGORY = "어학-영어";
    static final String URL = "https://www.teps.or.kr/";

        /** 닫는 {@code </td>} 가 없어도 읽는다 — 시행처 페이지가 실제로 그렇게 깨져 있었다(KCA, 2026-09-04). */
    private static final Pattern CELL =
            Pattern.compile("(?is)<t[dh][^>]*>(.*?)(?=</t[dh]>|<t[dh][\\s>]|</tr>|$)");
    private static final Pattern ROUND = Pattern.compile("제\\s*(\\d+)\\s*회");
    private static final Pattern RANGE = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})\\s*~\\s*(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern DATE = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");

    @Override
    public String sourceId() {
        return "TEPS_WEB";
    }

    @Override
    public java.util.Set<String> coveredAgencies() {
        return java.util.Set.of(AGENCY);
    }

    /**
     * 이름을 대고 찾아가는 종목. 기관 이름은 표기가 흔들려 재수집이 안 걸리는 일이 있다
     * (JLPT: 소스는 "JEES / 국제교류기금", 시험 쪽은 "일본국제교류기금·JEES" — 2026-09-09).
     * 코드는 흔들리지 않는다. 비큐넷 시드가 다른 코드를 쓰면 둘 다 밝힌다({@link SeedCodeAlias}).
     */
    @Override
    public java.util.Set<String> coveredExamCodes() {
        return SeedCodeAlias.plus(java.util.Set.of("M0327"), SOURCE_CODE);
    }

    @Override
    protected String pageUrl() {
        return URL;
    }

    @Override
    public List<CollectedSchedule> parse(String html) {
        String table = scheduleTable(html);
        if (table == null) {
            return List.of();
        }

        // 회차 기준으로 모은다 — 같은 회차가 본접수/추가접수로 두 번 나오므로 앞의 것(본접수)만 남긴다.
        Map<Integer, CollectedSchedule> byRound = new LinkedHashMap<>();

        for (String row : table.split("(?i)<tr[^>]*>")) {
            List<String> cells = new ArrayList<>();
            Matcher c = CELL.matcher(row);
            while (c.find()) {
                cells.add(c.group(1).replaceAll("(?s)<[^>]*>", " ").replace("&nbsp;", " ")
                        .replaceAll("\\s+", " ").trim());
            }
            if (cells.size() < 5) {
                continue;
            }
            String title = cells.get(0);
            if (title.contains("추가접수")) {
                continue;
            }

            Matcher rd = ROUND.matcher(title);
            Matcher reg = RANGE.matcher(cells.get(2));
            Matcher exam = DATE.matcher(cells.get(3));
            if (!rd.find() || !reg.find() || !exam.find()) {
                continue;
            }
            Matcher res = DATE.matcher(cells.get(4));

            int round = Integer.parseInt(rd.group(1));
            LocalDate examDate = date(exam, 1);

            byRound.putIfAbsent(round, new CollectedSchedule(
                    SOURCE_CODE, NAME, Series.ETC, AGENCY, CATEGORY,
                    examDate.getYear(), round, ExamType.WRITTEN,
                    date(reg, 1).atTime(LocalTime.of(0, 0)),
                    date(reg, 4).atTime(LocalTime.of(23, 59)),
                    examDate, examDate,
                    res.find() ? date(res, 1) : null,
                    URL));
        }
        return new ArrayList<>(byRound.values());
    }

    /** 표를 통째로 뽑는다 — summary 속성에 "시험일정안내" 가 있는 표가 그것이다. */
    private String scheduleTable(String html) {
        int marker = html.indexOf("시험일정안내");
        if (marker < 0) {
            return null;
        }
        int start = html.lastIndexOf("<table", marker);
        int end = html.indexOf("</table>", marker);
        if (start < 0 || end < 0) {
            return null;
        }
        return html.substring(start, end);
    }

    private LocalDate date(Matcher m, int base) {
        return LocalDate.of(Integer.parseInt(m.group(base)),
                Integer.parseInt(m.group(base + 1)), Integer.parseInt(m.group(base + 2)));
    }
}

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
 * 한국경제신문 TESAT 경제이해력검증시험 일정 수집 — <b>1종</b>.
 *
 * <p>{@code tesat.or.kr/app.frm.general.exam/schedule.regular} 에 그 해 회차가 표 하나로 다 있다.
 * <pre>회차 | 시험일자 | 접수기간 | 성적발표일
 * 110 | 2026년 12월 19일(토) | 2026년 11월 10일~2026년 12월 07일 | 2026년 12월 24일</pre>
 * 날짜에 연도가 다 붙어 있어 손댈 게 없다. robots.txt 는 없다(404, 2026-09-08 확인).
 *
 * <p><b>주니어 TESAT 은 안 담는다</b> — 별도 페이지({@code /app.frm.junior.exam/...})이고
 * 우리 마스터에 없는 시험이다.
 *
 * <h3>접수 시각</h3>
 * 표는 날짜만 준다. 시작 10:00 / 마감 18:00 으로 둔다 — 비워 두면 "접수 중" 판정이 안 서고,
 * 이 서비스의 다른 시행처와 같은 기준이다.
 */
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class TesatScheduleSource extends AbstractHtmlScheduleSource {

    static final String URL = "https://www.tesat.or.kr/app.frm.general.exam/schedule.regular";
    static final String AGENCY = "한국경제신문";
    private static final String CATEGORY = "경제-경영";
    private static final String SOURCE_CODE = "M0440";
    private static final String NAME = "TESAT 경제이해력검증시험";

    private static final Pattern ROW = Pattern.compile("<tr[\\s\\S]*?</tr>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL =
            Pattern.compile("<t[dh]([^>]*)>([\\s\\S]*?)(?=</t[dh]>|<t[dh][\\s>]|</tr>|$)", Pattern.CASE_INSENSITIVE);
    /** 2026년 12월 19일 */
    private static final Pattern DATE = Pattern.compile("(20\\d\\d)\\s*년\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    @Override
    public String sourceId() {
        return "TESAT_WEB";
    }

    @Override
    public Set<String> coveredAgencies() {
        return Set.of(AGENCY);
    }

    @Override
    public Set<String> coveredExamCodes() {
        // 비큐넷 시드가 같은 시험에 다른 코드를 붙인다 — 로컬은 시드 코드, 운영은 마스터 코드다.
        // 시드 코드는 마스터 코드로 통일했다(2026-09-21) — 예전엔 같은 시험에 두 코드가 붙어 둘 다 밝혀야 했다.
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
        List<CollectedSchedule> out = new ArrayList<>();
        Matcher rows = ROW.matcher(stripComments(html));
        while (rows.find()) {
            List<String> cells = cells(rows.group());
            if (cells.size() < 4 || !cells.get(0).matches("\\d{1,4}")) {
                continue;   // 머리글 줄
            }
            int round = Integer.parseInt(cells.get(0));
            LocalDate exam = date(cells.get(1), 0);
            LocalDate regStart = date(cells.get(2), 0);
            LocalDate regEnd = date(cells.get(2), 1);
            LocalDate result = date(cells.get(3), 0);
            if (exam == null && regStart == null) {
                continue;
            }
            out.add(new CollectedSchedule(
                    SOURCE_CODE, NAME, Series.ETC, AGENCY, CATEGORY,
                    exam != null ? exam.getYear() : regStart.getYear(), round, ExamType.WRITTEN,
                    regStart == null ? null : regStart.atTime(10, 0),
                    regEnd == null ? null : regEnd.atTime(18, 0),
                    exam, exam, result,
                    URL, ScheduleProvenance.SCRAPED));
        }
        return out;
    }

    private List<String> cells(String row) {
        List<String> out = new ArrayList<>();
        Matcher m = CELL.matcher(row);
        while (m.find()) {
            out.add(clean(m.group(2)));
        }
        return out;
    }

    /** 한 칸에서 {@code nth} 번째 날짜. 접수기간 칸은 시작·마감 둘이 들어 있다. */
    private LocalDate date(String text, int nth) {
        Matcher m = DATE.matcher(text);
        for (int i = 0; i <= nth; i++) {
            if (!m.find()) {
                return null;
            }
        }
        return LocalDate.of(Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    }

    private String clean(String html) {
        return TAG.matcher(html).replaceAll(" ").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
    }
}

package com.test.test.exam.collect;

import com.test.test.exam.common.TimeUtil;
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
 * 한국세무사회 전산세무회계 일정 수집 — 전산세무 1·2급, 전산회계 1·2급 <b>4종</b>.
 *
 * <p>일정표는 첫 화면({@code license.kacta.or.kr/web/home/Default.aspx})에 있다.
 * 사이트가 프레임셋이라 루트({@code license.kacpta.or.kr})는 790B 짜리 껍데기다 —
 * 몸통 프레임을 직접 읽어야 한다. 페이지는 EUC-KR 이다.
 * <pre>원서접수 | 장소공고·수험표출력 | 시험일자 | 발표
 * 08.27 ∼ 09.02 | 09.28 ∼ 10.03 | 10.03(토) | 10.29(목)</pre>
 *
 * <h3>연도가 없다</h3>
 * 표는 <b>월·일만</b> 준다. 시행처가 그 해 계획만 싣기 때문이다 — <b>올해</b>로 읽는다.
 * (첫 회차 접수가 1월 초라 연초에는 이미 새해 표로 바뀌어 있다.)
 *
 * <h3>회차 번호가 없다</h3>
 * 시행처는 공지에서 "제128회"라고 부르지만 <b>이 표에는 회차가 없다.</b> 공지 제목에서
 * 역산할 수도 있지만, 공지 목록이 바뀌면 회차가 통째로 어긋나 <b>눈에 보이는 오류</b>가 된다.
 * 그래서 <b>지어내지 않는다</b> — 시험일을 {@code YYYYMMDD} 로 바꿔 멱등 키로만 쓰고,
 * 화면에는 회차로 안 보인다({@link com.test.test.exam.domain.ExamSchedule#roundLabel()}).
 *
 * <h3>띄어쓰기가 제각각이다</h3>
 * {@code 07.02∼ 07.08}, {@code 11. 30 ∼12.05} 처럼 구분선·공백이 줄마다 다르다.
 * 숫자만 보고 읽는다.
 */
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class KactaScheduleSource extends AbstractHtmlScheduleSource {

    static final String URL = "https://license.kacta.or.kr/web/home/Default.aspx";
    static final String AGENCY = "한국세무사회";
    private static final String CATEGORY = "회계-세무";

    /** 한 회차를 네 종목이 같이 친다. */
    private static final Map<String, String> EXAMS = new LinkedHashMap<>();

    static {
        EXAMS.put("M0429", "전산세무 1급");
        EXAMS.put("M0430", "전산세무 2급");
        EXAMS.put("M0431", "전산회계 1급");
        EXAMS.put("M0432", "전산회계 2급");
    }

    private static final Pattern TABLE = Pattern.compile(
            "<table[^>]*class=[\"'][^\"']*table_schedule[^\"']*[\"'][\\s\\S]*?</table>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROW = Pattern.compile("<tr[\\s\\S]*?</tr>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL =
            Pattern.compile("<t[dh]([^>]*)>([\\s\\S]*?)(?=</t[dh]>|<t[dh][\\s>]|</tr>|$)", Pattern.CASE_INSENSITIVE);
    /** 08.27 — 공백이 어디에 끼어도 읽는다 */
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})\\s*\\.\\s*(\\d{1,2})");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    @Override
    public String sourceId() {
        return "KACTA_WEB";
    }

    @Override
    public Set<String> coveredAgencies() {
        return Set.of(AGENCY);
    }

    @Override
    public Set<String> coveredExamCodes() {
        // 비큐넷 시드가 같은 시험에 다른 코드를 붙인다 — 로컬은 시드 코드, 운영은 마스터 코드다.
        // 시드 코드는 마스터 코드로 통일했다(2026-09-21) — 예전엔 같은 시험에 두 코드가 붙어 둘 다 밝혀야 했다.
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
        if (!table.find()) {
            return List.of();
        }
        int year = TimeUtil.today().getYear();

        List<CollectedSchedule> out = new ArrayList<>();
        Matcher rows = ROW.matcher(table.group());
        while (rows.find()) {
            List<String> cells = cells(rows.group());
            if (cells.size() < 4) {
                continue;
            }
            LocalDate regStart = monthDay(cells.get(0), 0, year);
            LocalDate regEnd = monthDay(cells.get(0), 1, year);
            LocalDate exam = monthDay(cells.get(2), 0, year);
            LocalDate result = monthDay(cells.get(3), 0, year);
            if (exam == null || regStart == null) {
                continue;   // 머리글 줄
            }
            // 회차를 지어내지 않는다 — 시험일이 멱등 키다
            int key = exam.getYear() * 10000 + exam.getMonthValue() * 100 + exam.getDayOfMonth();

            for (Map.Entry<String, String> e : EXAMS.entrySet()) {
                out.add(new CollectedSchedule(
                        e.getKey(), e.getValue(), Series.ETC, AGENCY, CATEGORY,
                        year, key, ExamType.WRITTEN,
                        regStart.atTime(10, 0),
                        regEnd == null ? null : regEnd.atTime(18, 0),
                        exam, exam, result,
                        URL, ScheduleProvenance.SCRAPED));
            }
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

    /** 한 칸에서 {@code nth} 번째 월·일. 접수 칸은 시작·마감 둘이 들어 있다. */
    private LocalDate monthDay(String text, int nth, int year) {
        Matcher m = MONTH_DAY.matcher(text);
        for (int i = 0; i <= nth; i++) {
            if (!m.find()) {
                return null;
            }
        }
        try {
            return LocalDate.of(year, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        } catch (Exception e) {
            return null;   // 13.45 같은 값이 오면 그 줄만 버린다
        }
    }

    private String clean(String html) {
        return TAG.matcher(html).replaceAll(" ").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
    }
}

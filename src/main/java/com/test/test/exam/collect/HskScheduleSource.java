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
 * HSK 한국사무국 중국어능력시험(필기) 일정 수집 — <b>1종</b>.
 *
 * <p>{@code hsk.or.kr/?c1=100&c2=002&c3=002&c4=006} 에 그 해 12회차가 표 하나로 다 있다.
 * <pre>시험일자 | 등급 | 접수 일정 | 성적조회 예정일
 * 3월 22일(일) | HSK IBT 1~6급 | 인터넷 접수 : 2월 5일(목) ~ 3월12일(목) | 4월 7일(화)</pre>
 *
 * <h3>⚠️ User-Agent 를 바꿔서 읽는다 — 그 판단의 근거</h3>
 * 이 사이트는 기본 UA({@code exam-hub/1.0 (...)})를 {@code /error.htm} 으로 302 시킨다(2026-09-21 실측).
 * 그래서 <b>크롤러 표준 형식</b>으로 바꿔 보낸다:
 * <pre>Mozilla/5.0 (compatible; exam-hub/1.0; +https://github.com/gks930620/exam_hub)</pre>
 *
 * <p><b>이건 브라우저인 척하는 게 아니다.</b> 둘을 갈라 둔다:
 * <ul>
 *   <li>하지 않는 것 — {@code Chrome/120.0.0.0 Safari/537.36} 처럼 <b>사람이 쓰는 브라우저를 사칭</b>하는 것.
 *       우리가 누구인지 숨기게 되고, 독일문화원(403)처럼 <b>봇을 명시적으로 막는 곳</b>을 뚫는 일이 된다.</li>
 *   <li>하는 것 — {@code Mozilla/5.0 (compatible; 이름; +주소)}. Googlebot·bingbot 이 쓰는 그 형식이고,
 *       <b>이름과 연락처를 밝힌다.</b> 앞의 {@code Mozilla/5.0} 은 거의 모든 UA 가 달고 다니는 역사적 접두어다.</li>
 * </ul>
 *
 * <p>근거는 <b>시행처 자신의 robots.txt</b> 다 — {@code User-agent: * / Allow: /}. 기계가 읽으라고
 * 내놓은 정책이 "전부 허용"이고, UA 문자열 필터는 그 정책과 어긋난다. 우리는 그 정책을 따르고,
 * 하루 한 번만 읽고, 이름을 밝힌다. 되돌리려면 {@link #headers()} 를 비우면 된다.
 * (사용자 결정 2026-09-21 — 그전에는 "우회하지 않는다"며 이 시험을 수기로 두고 있었다.)
 *
 * <h3>연도가 없다</h3>
 * 표는 <b>"3월 22일"</b> 처럼 월·일만 준다. 그래서 두 가지를 해야 한다.
 * <ol>
 *   <li><b>시험일의 연도</b>: 표가 그 해 것이라는 보장이 없다. 12행이 1월→12월로 <b>오름차순</b>인
 *       성질을 쓴다 — 첫 행보다 뒤로 가지 않으면 같은 해다. 기준 연도는 오늘의 해로 잡되,
 *       표의 마지막 시험일이 오늘보다 두 달 넘게 지났으면 <b>다음 해 표</b>로 본다(연말에 다음 해 표가 먼저 올라온다).</li>
 *   <li><b>접수 시작의 연도</b>: 접수는 시험보다 앞이다. 달이 시험달보다 <b>크면 전년도</b>다
 *       (1월 10일 시험의 접수는 전해 11~12월). 표에 "2025년" 처럼 연도가 박혀 있으면 그게 이긴다.</li>
 * </ol>
 *
 * <h3>회차가 없다</h3>
 * HSK 는 회차를 안 매긴다. 시험일을 {@code YYYYMMDD} 로 넣어 <b>멱등 키</b>로 쓴다 —
 * 화면에는 회차로 안 보인다({@link com.test.test.exam.domain.ExamSchedule#roundLabel()} 가 걸러 준다).
 *
 * <h3>안 담는 것</h3>
 * <b>HSKK(회화)</b> 는 이 표에 없다 — 같은 사무국이지만 메뉴가 따로다. 주소를 아직 못 찾았다
 * (설계/시험데이터/07_못붙인_시행처.md). 여기서 HSKK 코드를 밝히지 않는 이유가 그것이다 —
 * 밝히면 매니저 화면이 "기다리면 자동으로 들어온다"고 거짓 안내를 한다.
 */
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class HskScheduleSource extends AbstractHtmlScheduleSource {

    static final String URL = "https://www.hsk.or.kr/?c1=100&c2=002&c3=002&c4=006";
    static final String AGENCY = "HSK한국사무국";
    private static final String CATEGORY = "어학-중국어";
    private static final String SOURCE_CODE = "M0341";
    private static final String NAME = "HSK 중국어능력시험(필기)";

    private static final Pattern ROW = Pattern.compile("<tr[\\s\\S]*?</tr>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL =
            Pattern.compile("<t[dh]([^>]*)>([\\s\\S]*?)(?=</t[dh]>|<t[dh][\\s>]|</tr>|$)", Pattern.CASE_INSENSITIVE);
    /** "2025년 11월 26일" 또는 "3월12일" — 연도는 있을 때만 잡는다 */
    private static final Pattern DATE = Pattern.compile("(?:(20\\d\\d)\\s*년\\s*)?(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    /** 표의 마지막 시험일이 이만큼 넘게 지났으면 다음 해 표로 본다 */
    private static final int STALE_MONTHS = 2;

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
        return Set.of(SOURCE_CODE);
    }

    @Override
    protected String pageUrl() {
        return URL;
    }

    /** 이름과 연락처를 밝히는 크롤러 표준 UA. 사연은 클래스 주석에 있다. */
    private static java.util.Map<String, String> headers() {
        return java.util.Map.of("User-Agent",
                "Mozilla/5.0 (compatible; exam-hub/1.0; +https://github.com/gks930620/exam_hub)");
    }

    @Override
    public List<CollectedSchedule> fetchAll() {
        return parse(get(URL, headers()));
    }

    @Override
    public List<CollectedSchedule> parse(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<Raw> raws = rawRows(stripComments(html));
        if (raws.isEmpty()) {
            return List.of();
        }
        int year = guessYear(raws);

        List<CollectedSchedule> out = new ArrayList<>();
        for (Raw r : raws) {
            LocalDate exam = LocalDate.of(year, r.examMonth, r.examDay);
            LocalDate regStart = regDate(r.regStartYear, r.regStartMonth, r.regStartDay, exam);
            LocalDate regEnd = regDate(r.regEndYear, r.regEndMonth, r.regEndDay, exam);
            LocalDate result = r.resultMonth == 0 ? null
                    : after(exam, r.resultMonth, r.resultDay);

            out.add(new CollectedSchedule(
                    SOURCE_CODE, NAME, Series.ETC, AGENCY, CATEGORY,
                    exam.getYear(), idempotentKey(exam), ExamType.WRITTEN,
                    regStart == null ? null : regStart.atTime(10, 0),
                    regEnd == null ? null : regEnd.atTime(18, 0),
                    exam, exam, result,
                    URL, ScheduleProvenance.SCRAPED));
        }
        return out;
    }

    /**
     * 표가 어느 해 것인가. 마지막 시험일이 오늘보다 {@value #STALE_MONTHS} 달 넘게 지났으면 다음 해다 —
     * 연말엔 다음 해 표가 먼저 올라온다. 그 전에는 올해 표로 본다.
     */
    private int guessYear(List<Raw> raws) {
        LocalDate today = TimeUtil.today();
        Raw last = raws.get(raws.size() - 1);
        LocalDate lastThisYear = LocalDate.of(today.getYear(), last.examMonth, last.examDay);
        return lastThisYear.isBefore(today.minusMonths(STALE_MONTHS))
                ? today.getYear() + 1
                : today.getYear();
    }

    /**
     * 접수일의 연도. 표에 연도가 박혀 있으면 그걸 쓰고, 없으면 <b>시험보다 앞</b>이라는 성질로 정한다 —
     * 접수 달이 시험 달보다 크면 전년도다(1월 시험의 접수는 전해 11~12월).
     */
    private LocalDate regDate(int year, int month, int day, LocalDate exam) {
        if (month == 0) {
            return null;
        }
        if (year > 0) {
            return LocalDate.of(year, month, day);
        }
        int y = month > exam.getMonthValue() ? exam.getYear() - 1 : exam.getYear();
        return LocalDate.of(y, month, day);
    }

    /** 발표일은 시험 뒤다 — 달이 시험 달보다 작으면 다음 해다(12월 시험의 발표가 1월). */
    private LocalDate after(LocalDate exam, int month, int day) {
        int y = month < exam.getMonthValue() ? exam.getYear() + 1 : exam.getYear();
        return LocalDate.of(y, month, day);
    }

    /** 시행처가 회차를 안 매긴다 — 시험일을 키로 쓴다. 화면에는 회차로 안 보인다. */
    private int idempotentKey(LocalDate exam) {
        return exam.getYear() * 10000 + exam.getMonthValue() * 100 + exam.getDayOfMonth();
    }

    private List<Raw> rawRows(String html) {
        List<Raw> out = new ArrayList<>();
        Matcher rows = ROW.matcher(html);
        while (rows.find()) {
            List<String> cells = cells(rows.group());
            if (cells.isEmpty()) {
                continue;
            }
            // 칸이 붙어 오는 줄이 있다(시험일자와 등급이 한 칸). 줄 전체에서 날짜를 순서대로 읽는다.
            String line = String.join(" | ", cells);
            List<int[]> dates = dates(line);
            // 시험일 + 접수 시작 + 접수 마감 (+ 발표) — 셋은 있어야 회차다
            if (dates.size() < 3 || !line.contains("접수")) {
                continue;
            }
            Raw r = new Raw();
            r.examMonth = dates.get(0)[1];
            r.examDay = dates.get(0)[2];
            r.regStartYear = dates.get(1)[0];
            r.regStartMonth = dates.get(1)[1];
            r.regStartDay = dates.get(1)[2];
            r.regEndYear = dates.get(2)[0];
            r.regEndMonth = dates.get(2)[1];
            r.regEndDay = dates.get(2)[2];
            if (dates.size() >= 4) {
                r.resultMonth = dates.get(3)[1];
                r.resultDay = dates.get(3)[2];
            }
            out.add(r);
        }
        return out;
    }

    /** 한 줄의 날짜를 순서대로. 각 원소는 {연도(없으면 0), 월, 일}. */
    private List<int[]> dates(String text) {
        List<int[]> out = new ArrayList<>();
        Matcher m = DATE.matcher(text);
        while (m.find()) {
            out.add(new int[]{
                    m.group(1) == null ? 0 : Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3))});
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

    private String clean(String html) {
        return TAG.matcher(html).replaceAll(" ").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
    }

    /** 표에서 읽어 낸 날것 — 연도를 정하기 전 상태다. */
    private static final class Raw {
        int examMonth;
        int examDay;
        int regStartYear;
        int regStartMonth;
        int regStartDay;
        int regEndYear;
        int regEndMonth;
        int regEndDay;
        int resultMonth;
        int resultDay;
    }
}

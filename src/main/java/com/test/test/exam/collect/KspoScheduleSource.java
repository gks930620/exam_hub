package com.test.test.exam.collect;

import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 국민체육진흥공단 체육지도자 자격검정 일정 수집 — 생활2급·전문2급·유소년·노인 <b>4종</b>.
 *
 * <h3>급수마다 접수 창구가 다르다</h3>
 * 이 시행처의 함정이다. 시험일은 넷 다 같은 날인데 <b>원서접수는 급수별로 갈린다</b>(2026년 실측).
 * <pre>
 * 2급 전문            2026.03.19 10:00 ~ 03.23 18:00
 * 2급 생활·유소년·노인   2026.04.02 10:00 ~ 04.02 18:00 (하루)
 * 시험일              넷 다 2026.04.18
 * </pre>
 * 하나를 넷에 붙이면 <b>셋이 틀린 접수일을 보고 놓친다.</b> 그래서 급수마다 페이지를 따로 읽는다.
 *
 * <p>일정 페이지는 라디오 버튼으로 급수를 고르고 폼을 POST 하는 구조인데,
 * <b>같은 값을 쿼리스트링으로 GET 해도 똑같은 응답이 온다</b>(쿠키·세션 불필요, 2026-09-08 실측).
 * 그래서 급수코드를 붙인 GET 4번이면 끝난다.
 *
 * <p>robots.txt 는 {@code /mypage} {@code /apply} {@code /license} 를 막는다 —
 * 우리가 읽는 {@code /info/} 는 열려 있다.
 *
 * <h3>필기만 담는다</h3>
 * 같은 페이지에 실기·구술 일정도 있지만 <b>동계(설상)</b>과 <b>하계/동계(빙상)</b> 두 갈래로 나뉜다.
 * 어느 쪽인지는 응시 종목(스키냐 수영이냐)에 달렸는데 우리 마스터에는 그 구분이 없다.
 * 둘 중 하나를 골라 넣으면 반대편 응시자에게 <b>틀린 날짜</b>가 간다 — 그래서 넣지 않는다.
 * 필기 접수가 첫 관문이고, 실기는 필기 합격자만 본다.
 *
 * <h3>연도 파라미터는 없다</h3>
 * 이 페이지는 <b>그 해 계획만</b> 준다(연도 파라미터를 붙여 봐도 응답이 같다). 이듬해 계획은
 * 보통 1월에 올라온다 — 그때 배치가 자동으로 새 회차를 가져온다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class KspoScheduleSource extends AbstractHtmlScheduleSource {

    static final String BASE = "https://sqms.kspo.or.kr/info/schedPlan.kspo";
    static final String AGENCY = "국민체육진흥공단";
    private static final String CATEGORY = "서비스-기타";

    /**
     * 급수 하나 = 페이지 하나.
     *
     * @param code       시행처의 급수코드({@code QF_GRADE_CD}) — 이 값으로 표가 갈린다
     * @param sourceCode 우리 마스터 코드. {@code seed/exam_master.json} 과 같아야 한다
     */
    public record Grade(String code, String sourceCode, String name) {
    }

    private static final List<Grade> GRADES = List.of(
            new Grade("LSC2", "M0473", "생활스포츠지도사 2급"),
            new Grade("PSC2", "M0474", "전문스포츠지도사 2급"),
            new Grade("YUSC", "M0475", "유소년스포츠지도사"),
            new Grade("OLSC", "M0476", "노인스포츠지도사"));

    /** 2026년도 연간일정 계획 */
    private static final Pattern YEAR = Pattern.compile("(20\\d\\d)\\s*년도\\s*연간일정");
    /** 필기시험 표 — 실기·구술 표가 뒤에 또 있으므로 여기서 끊는다. */
    private static final Pattern WRITTEN_TABLE = Pattern.compile(
            "필기시험[\\s\\S]{0,400}?(<table[\\s\\S]*?</table>)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROW = Pattern.compile("<tr[\\s\\S]*?</tr>", Pattern.CASE_INSENSITIVE);
    /** 2026.04.02 10:00 (목) ~ 2026.04.02 18:00 (목) */
    private static final Pattern RANGE = Pattern.compile(
            "(20\\d\\d)\\.(\\d{1,2})\\.(\\d{1,2})\\s*(\\d{1,2}):(\\d{2})\\s*\\([일월화수목금토]\\)\\s*~\\s*"
                    + "(20\\d\\d)\\.(\\d{1,2})\\.(\\d{1,2})\\s*(\\d{1,2}):(\\d{2})\\s*\\([일월화수목금토]\\)");
    /** 2026.04.18 (토) — 시각이 붙기도 한다(합격자발표 16:00) */
    private static final Pattern DATE = Pattern.compile("(20\\d\\d)\\.(\\d{1,2})\\.(\\d{1,2})");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    public static List<Grade> grades() {
        return GRADES;
    }

    public static Grade grade(String code) {
        return GRADES.stream().filter(g -> g.code().equals(code)).findFirst().orElse(null);
    }

    static String url(Grade g) {
        return BASE + "?QF_GRADE_CD=" + g.code();
    }

    @Override
    public String sourceId() {
        return "KSPO_WEB";
    }

    @Override
    public Set<String> coveredAgencies() {
        return Set.of(AGENCY);
    }

    @Override
    public Set<String> coveredExamCodes() {
        return GRADES.stream().map(Grade::sourceCode).collect(Collectors.toSet());
    }

    /** 안 쓴다 — 급수마다 페이지가 따로라 {@link #fetchAll()} 이 직접 돈다. */
    @Override
    protected String pageUrl() {
        return BASE;
    }

    /** 안 쓴다 — 어느 급수인지 알아야 파싱이 된다. {@link #parse(String, Grade)} 를 쓴다. */
    @Override
    public List<CollectedSchedule> parse(String html) {
        return List.of();
    }

    @Override
    public List<CollectedSchedule> fetchAll() {
        List<CollectedSchedule> out = new ArrayList<>();
        for (Grade g : GRADES) {
            try {
                List<CollectedSchedule> one = parse(get(url(g)), g);
                if (one.isEmpty()) {
                    log.warn("[{}] {} 에서 한 건도 못 뽑았다 — 표 구조나 급수코드가 바뀌었을 수 있다",
                            sourceId(), g.name());
                }
                out.addAll(one);
            } catch (Exception e) {
                log.warn("[{}] {} 조회 실패: {}", sourceId(), g.name(), e.toString());
            }
        }
        log.info("[{}] 급수 {}개 → 일정 {}건", sourceId(), GRADES.size(), out.size());
        return out;
    }

    public List<CollectedSchedule> parse(String html, Grade grade) {
        if (html == null || html.isBlank() || grade == null) {
            return List.of();
        }
        Matcher year = YEAR.matcher(clean(html));
        Matcher table = WRITTEN_TABLE.matcher(html);
        if (!year.find() || !table.find()) {
            return List.of();   // 점검 중이거나 "해당 데이터가 없습니다" 화면
        }

        Matcher rows = ROW.matcher(table.group(1));
        while (rows.find()) {
            String row = clean(rows.group());
            if (!row.contains("일반과정")) {
                continue;   // 추가취득·특별과정은 안 열린 해엔 빈칸으로 온다
            }
            LocalDateTime[] reg = range(section(row, "원서접수", "서류접수"));
            LocalDate exam = date(section(row, "시험일", "합격자발표"));
            LocalDate result = date(section(row, "합격자발표", null));
            if (reg == null && exam == null) {
                continue;   // 날짜가 하나도 없으면 일정이 아니다
            }
            return List.of(new CollectedSchedule(
                    grade.sourceCode(), grade.name(), Series.ETC, AGENCY, CATEGORY,
                    Integer.parseInt(year.group(1)), 1, ExamType.WRITTEN,
                    reg == null ? null : reg[0], reg == null ? null : reg[1],
                    exam, exam, result,
                    url(grade), ScheduleProvenance.SCRAPED));
        }
        return List.of();
    }

    /**
     * 칸 하나만 떼어 낸다. 이 표는 칸마다 항목 이름이 같이 들어 있어서
     * ({@code <div class="tit">시험일</div><div class="cont">2026.04.18 (토)</div>})
     * 이름과 이름 사이를 자르면 그 칸의 값만 남는다.
     */
    private String section(String text, String from, String until) {
        int i = text.indexOf(from);
        if (i < 0) {
            return "";
        }
        i += from.length();
        int end = until == null ? text.length() : text.indexOf(until, i);
        return text.substring(i, end < 0 ? text.length() : end);
    }

    private LocalDateTime[] range(String text) {
        Matcher m = RANGE.matcher(text);
        if (!m.find()) {
            return null;   // 안 열린 과정은 " ~ " 로 비어 온다 — 날짜를 지어내지 않는다
        }
        return new LocalDateTime[]{
                LocalDateTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5))),
                LocalDateTime.of(Integer.parseInt(m.group(6)), Integer.parseInt(m.group(7)),
                        Integer.parseInt(m.group(8)), Integer.parseInt(m.group(9)), Integer.parseInt(m.group(10)))};
    }

    private LocalDate date(String text) {
        Matcher m = DATE.matcher(text);
        if (!m.find()) {
            return null;
        }
        return LocalDate.of(Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    }

    private String clean(String html) {
        return TAG.matcher(stripComments(html)).replaceAll(" ")
                .replace("&nbsp;", " ").replaceAll("\\s+", " ");
    }
}

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YBM 어학시험 일정 수집 — TOEIC·TOEIC S&amp;W·TOEIC Bridge·JPT·SJPT·TSC <b>7종</b>.
 *
 * <p>도메인은 시험마다 다르지만 {@code /receipt/examSchList.php} 한 장짜리 표는 <b>전부 같은 모양</b>이라
 * 파서 하나로 여섯 페이지를 읽는다. robots.txt 는 {@code /_common/ /design/ /sample/ /webhook/ /wellconn/}
 * 만 막는다 — {@code /receipt/} 는 열려 있다(2026-09-08 확인).
 *
 * <h3>표가 두 모양이다</h3>
 * <ul>
 *   <li><b>회차가 있는 표</b> — TOEIC·JPT·SJPT·TSC: {@code 회차 | 시험일시 | 성적발표일시 | 접수기간}.
 *       시행처가 매긴 "제576회"를 그대로 쓴다.</li>
 *   <li><b>회차가 없는 표</b> — TOEIC S&amp;W·Bridge: {@code 시험일시 | 성적발표일 | 접수기간}.
 *       시행처가 회차를 안 매기므로 <b>지어내지 않는다</b>. 시험일을 {@code YYYYMMDD} 로 바꿔 회차 자리에
 *       넣되(멱등 키로만 쓴다), 화면에는 "…회"로 안 보인다
 *       ({@link com.test.test.exam.domain.ExamSchedule#roundLabel()} 가 걸러 준다).</li>
 * </ul>
 *
 * <h3>정기접수를 쓴다</h3>
 * TOEIC·JPT 는 접수 칸에 {@code 정기접수 : … 특별추가 : …} 가 같이 온다. 특별추가는 정기 마감 뒤 며칠
 * 열리는 보충 창구다. 그걸 접수기간으로 보여 주면 <b>사용자가 정규 접수를 놓친다</b> — 정기접수만 담는다.
 *
 * <h3>안 넣은 것</h3>
 * <ul>
 *   <li><b>BCT 비즈니스중국어(M0344)</b> — 시행처 사이트가 열리지 않는다(bct.co.kr 타임아웃,
 *       ybmbct.co.kr 도메인 없음, 2026-09-08). 수기로 남는다.</li>
 *   <li>TOEIC S&amp;W 표는 Speaking 전용 회차와 Writing 이 같이 있는 회차가 섞여 있는데,
 *       그 구분이 <b>접수 버튼의 링크에만</b> 있어 마감된 회차는 알 길이 없다. 그래서 시험일마다
 *       Speaking·Writing 둘 다에 넣는다 — 같은 날 둘 다 치르는 게 이 시험의 기본 운영이다.</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class YbmScheduleSource extends AbstractHtmlScheduleSource {

    static final String AGENCY = "YBM";

    /** 한 페이지가 만들 시험. 코드는 {@code seed/exam_master.json} 과 같아야 한다(다르면 시험이 하나 더 생긴다). */
    public record Exam(String sourceCode, String name) {
    }

    /**
     * 일정 페이지 하나.
     *
     * @param hasRound 시행처가 회차를 매기는가. 아니면 시험일을 멱등 키로 쓴다.
     */
    public record Target(String code, String url, String category, boolean hasRound, List<Exam> exams) {
    }

    private static final List<Target> TARGETS = List.of(
            new Target("TOEIC", "https://exam.toeic.co.kr/receipt/examSchList.php",
                    "어학-영어", true, List.of(new Exam("M0323", "TOEIC 토익"))),
            new Target("TOEIC-SW", "https://www.toeicswt.co.kr/receipt/examSchList.php",
                    "어학-영어", false, List.of(
                    new Exam("M0324", "TOEIC Speaking 토익스피킹"),
                    new Exam("M0325", "TOEIC Writing 토익라이팅"))),
            new Target("TOEIC-BRIDGE", "https://www.toeicbridge.co.kr/receipt/examSchList.php",
                    "어학-영어", false, List.of(new Exam("M0326", "TOEIC Bridge"))),
            new Target("JPT", "https://www.jpt.co.kr/receipt/examSchList.php",
                    "어학-일본어", true, List.of(new Exam("M0337", "JPT 일본어능력시험"))),
            new Target("SJPT", "https://www.ybmsjpt.co.kr/receipt/examSchList.php",
                    "어학-일본어", true, List.of(new Exam("M0338", "SJPT 일본어 말하기시험"))),
            new Target("TSC", "https://www.ybmtsc.co.kr/receipt/examSchList.php",
                    "어학-중국어", true, List.of(new Exam("M0343", "TSC 중국어 말하기시험"))));

    private static final Pattern ROW = Pattern.compile("<tr[\\s\\S]*?</tr>", Pattern.CASE_INSENSITIVE);
    /** 칸 하나. 닫는 태그가 없어도 읽는다 — 국내 시행처 HTML 은 {@code </td>} 를 자주 빼먹는다. */
    private static final Pattern CELL =
            Pattern.compile("<t[dh]([^>]*)>([\\s\\S]*?)(?=</t[dh]>|<t[dh][\\s>]|</tr>|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final String DOW = "\\s*(?:\\([일월화수목금토]\\))?\\s*";
    /** 제576회 — ★ 같은 표시가 앞에 붙는다. */
    private static final Pattern ROUND = Pattern.compile("제\\s*(\\d{1,4})\\s*회");
    /** 2026.08.22 (토) — 뒤의 시각은 있을 때도 없을 때도 있다. */
    private static final Pattern DATE = Pattern.compile("(20\\d\\d)\\.(\\d{1,2})\\.(\\d{1,2})");
    /** 2026.07.13 (월) 10:00~2026.08.20 (목) 23:59 */
    private static final Pattern RANGE = Pattern.compile(
            "(20\\d\\d)\\.(\\d{1,2})\\.(\\d{1,2})" + DOW + "(\\d{1,2}):(\\d{2})"
                    + "\\s*~\\s*(20\\d\\d)\\.(\\d{1,2})\\.(\\d{1,2})" + DOW + "(\\d{1,2}):(\\d{2})");

    public static List<Target> targets() {
        return TARGETS;
    }

    public static Target target(String code) {
        return TARGETS.stream().filter(t -> t.code().equals(code)).findFirst().orElse(null);
    }

    @Override
    public String sourceId() {
        return "YBM_WEB";
    }

    @Override
    public Set<String> coveredAgencies() {
        return Set.of(AGENCY);
    }

    /** 이름을 대고 찾아가는 7종. BCT 는 사이트가 안 열려 여기 없다(수기). */
    @Override
    public Set<String> coveredExamCodes() {
        return TARGETS.stream().flatMap(t -> t.exams().stream()).map(Exam::sourceCode)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** 안 쓴다 — 시험마다 페이지가 따로라 {@link #fetchAll()} 이 직접 돈다. */
    @Override
    protected String pageUrl() {
        return TARGETS.get(0).url();
    }

    /** 안 쓴다 — 어느 시험인지 알아야 파싱이 된다. {@link #parse(String, Target)} 를 쓴다. */
    @Override
    public List<CollectedSchedule> parse(String html) {
        return List.of();
    }

    @Override
    public List<CollectedSchedule> fetchAll() {
        List<CollectedSchedule> out = new ArrayList<>();
        for (Target t : TARGETS) {
            try {
                List<CollectedSchedule> one = parse(get(t.url()), t);
                if (one.isEmpty()) {
                    log.warn("[{}] {} 에서 한 건도 못 뽑았다 — 표 구조가 바뀌었을 수 있다", sourceId(), t.code());
                }
                out.addAll(one);
            } catch (Exception e) {
                log.warn("[{}] {} 조회 실패: {}", sourceId(), t.code(), e.toString());
            }
        }
        log.info("[{}] 페이지 {}개 → 일정 {}건", sourceId(), TARGETS.size(), out.size());
        return out;
    }

    public List<CollectedSchedule> parse(String html, Target target) {
        if (html == null || target == null) {
            return List.of();
        }
        List<CollectedSchedule> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();   // 같은 시험일이 여러 줄로 오는 표가 있다(S&W)
        Matcher rows = ROW.matcher(html);
        while (rows.find()) {
            List<String> cells = cells(rows.group());
            if (cells.size() < (target.hasRound() ? 4 : 3)) {
                continue;   // 머리글·빈 줄
            }
            int at = 0;
            Integer round = null;
            if (target.hasRound()) {
                Matcher r = ROUND.matcher(cells.get(at++));
                if (!r.find()) {
                    continue;
                }
                round = Integer.parseInt(r.group(1));
            }
            LocalDate examDate = date(cells.get(at++));
            LocalDate result = date(cells.get(at++));
            LocalDateTime[] reg = range(regularOnly(cells.get(at)));
            if (examDate == null) {
                continue;   // 시험일이 없으면 일정이 아니다
            }

            // 시행처가 회차를 안 매기는 시험은 시험일을 멱등 키로 쓴다 — 회차를 지어내지 않는다.
            int key = round != null ? round
                    : examDate.getYear() * 10000 + examDate.getMonthValue() * 100 + examDate.getDayOfMonth();
            if (!seen.add(examDate.getYear() + "/" + key)) {
                continue;   // 같은 회차가 여러 줄로 오는 표가 있다(S&W 는 스피킹·라이팅·통합이 각각 한 줄)
            }
            for (Exam e : target.exams()) {
                out.add(new CollectedSchedule(
                        e.sourceCode(), e.name(), Series.ETC, AGENCY, target.category(),
                        examDate.getYear(), key, ExamType.WRITTEN,
                        reg == null ? null : reg[0], reg == null ? null : reg[1],
                        examDate, examDate, result,
                        target.url(), ScheduleProvenance.SCRAPED));
            }
        }
        return out;
    }

    /**
     * 접수 칸에서 <b>정기접수 구간만</b> 남긴다.
     *
     * <p>"정기접수 : … 특별추가 : …" 에서 특별추가를 잘라낸다. 특별추가는 정기 마감 뒤에 열리는
     * 보충 창구라, 그 날짜를 접수기간으로 보여 주면 정규 접수를 놓친다.
     */
    private String regularOnly(String text) {
        int start = text.indexOf("정기접수");
        if (start < 0) {
            return text;
        }
        int end = text.indexOf("특별추가", start);
        return end > 0 ? text.substring(start, end) : text.substring(start);
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

    private LocalDate date(String text) {
        Matcher m = DATE.matcher(text);
        if (!m.find()) {
            return null;
        }
        return LocalDate.of(Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    }

    private LocalDateTime[] range(String text) {
        Matcher m = RANGE.matcher(text);
        if (!m.find()) {
            return null;
        }
        return new LocalDateTime[]{
                LocalDateTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5))),
                LocalDateTime.of(Integer.parseInt(m.group(6)), Integer.parseInt(m.group(7)),
                        Integer.parseInt(m.group(8)), Integer.parseInt(m.group(9)), Integer.parseInt(m.group(10)))};
    }
}

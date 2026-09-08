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
 * 한국생산성본부(KPC) 자격시험 일정 수집 — ITQ·GTQ·ERP정보관리사 등 <b>12종</b>.
 *
 * <p>원본: {@code license.kpc.or.kr/nasec/rceptexmncnfirm/orgrcept/selectItemfx.do}(원서접수 안내).
 * 종목별 표가 하나씩 있고 <b>한 줄이 곧 한 회차</b>다:
 * <pre>2026년 제10회 ITQ정기시험 [D-2]  시험일 2026-10-17  인터넷접수 2026-09-10 오전 10:00 ~ 2026-09-16</pre>
 *
 * <p><b>이 시행처는 "수기 필수"로 분류돼 있었다.</b> 2026-09-08 전수조사에서 그 판단이 틀렸음이 드러났다 —
 * 예전 조사가 지금은 없는 주소(404)를 열어 보고 "JS 셸"이라고 기록했을 뿐이다. 실제로는 첫 화면에도,
 * 이 페이지에도 날짜가 그대로 있다. 근거: {@code 설계/시험데이터/06_시행처_전수조사.md}.
 *
 * <p>주의할 점 둘.
 * <ul>
 *   <li><b>한 줄이 여러 종목에 붙는다.</b> 표에는 "ITQ정기시험" 한 줄뿐이지만 우리 마스터는
 *       한글·엑셀·파워포인트·액세스·인터넷 5종이다. 같은 날짜를 5종에 똑같이 넣는다.</li>
 *   <li><b>필기·실기 구분이 없다.</b> 전부 {@link ExamType#WRITTEN} 으로 넣는다(컨벤션: 구분이 없으면 필기).</li>
 * </ul>
 *
 * <p>접수 마감 시각은 표에 없다(시작만 "오전 10:00"). 마감은 18:00 으로 둔다 — 이 시행처의 통상 마감이고,
 * 비워 두면 사용자 화면에 "접수 중" 판정이 안 선다.
 */
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class KpcScheduleSource extends AbstractHtmlScheduleSource {

    static final String URL = "https://license.kpc.or.kr/nasec/rceptexmncnfirm/orgrcept/selectItemfx.do";
    static final String AGENCY = "한국생산성본부";
    private static final String CATEGORY = "사무-IT";

    /**
     * 표의 시험명(공백 제거) → 우리 마스터의 종목들.
     *
     * <p>코드는 {@code seed/exam_master.json} 과 <b>똑같아야 한다</b> — 다르면 같은 시험이 하나 더 생긴다.
     * 표기가 흔들리므로("GTQ/GTQi 정기시험") 공백을 지우고 <b>포함</b>으로 맞춘다.
     */
    private static final Map<String, List<Exam>> EXAMS = new LinkedHashMap<>();

    static {
        EXAMS.put("ITQ", List.of(
                new Exam("M0375", "ITQ 정보기술자격(한글)"),
                new Exam("M0376", "ITQ 정보기술자격(엑셀)"),
                new Exam("M0377", "ITQ 정보기술자격(파워포인트)"),
                new Exam("M0378", "ITQ 정보기술자격(액세스)"),
                new Exam("M0379", "ITQ 정보기술자격(인터넷)")));
        EXAMS.put("GTQ-AI", List.of());                       // 마스터에 없는 종목 — 만들지 않는다
        EXAMS.put("GTQ/GTQi", List.of(
                new Exam("M0380", "GTQ 그래픽기술자격 1급"),
                new Exam("M0381", "GTQ 그래픽기술자격 2급"),
                new Exam("M0382", "GTQi 일러스트 1급")));
        EXAMS.put("ERP정보관리사", List.of(
                new Exam("M0383", "ERP정보관리사 회계"),
                new Exam("M0384", "ERP정보관리사 인사"),
                new Exam("M0385", "ERP정보관리사 물류"),
                new Exam("M0386", "ERP정보관리사 생산")));
    }

    /** "2026년 제10회 ITQ정기시험" — 연도·회차·시험명 */
    private static final Pattern TITLE = Pattern.compile("(20\\d\\d)년\\s*제\\s*(\\d+)\\s*회\\s*([^<]{2,40}?)\\s*(?:시험|검정)?\\s*$");
    /** 시험일 2026-10-17 */
    private static final Pattern EXAM_DATE = Pattern.compile("시험일[^0-9]{0,20}(20\\d\\d-\\d{2}-\\d{2})");
    /** 인터넷접수 2026-09-10 오전 10:00 ~ 2026-09-16 */
    private static final Pattern REG = Pattern.compile("접수[^0-9]{0,20}(20\\d\\d-\\d{2}-\\d{2})[^~]*~\\s*(20\\d\\d-\\d{2}-\\d{2})");
    private static final Pattern ROW = Pattern.compile("<tr[\\s\\S]*?</tr>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    @Override
    public String sourceId() {
        return "KPC_WEB";
    }

    @Override
    public Set<String> coveredAgencies() {
        return Set.of(AGENCY);
    }

    @Override
    protected String pageUrl() {
        return URL;
    }

    @Override
    public List<CollectedSchedule> parse(String html) {
        List<CollectedSchedule> out = new ArrayList<>();
        Matcher rows = ROW.matcher(html);
        while (rows.find()) {
            String row = rows.group();
            // 제목은 <h4> 안에 있다. 배지(D-2)는 태그로 갈라져 있어 태그를 지운 뒤 읽는다.
            String title = firstText(row, "<h4[^>]*>([\\s\\S]*?)</h4>");
            if (title == null) {
                continue;
            }
            Matcher t = TITLE.matcher(clean(title).replaceAll("D-\\s*\\d+", "").trim());
            if (!t.find()) {
                continue;
            }
            int year = Integer.parseInt(t.group(1));
            int round = Integer.parseInt(t.group(2));
            List<Exam> exams = examsFor(t.group(3));
            if (exams.isEmpty()) {
                continue;   // 우리 마스터에 없는 종목 — 만들어 내지 않는다
            }

            String text = clean(row);
            LocalDate examDate = date(EXAM_DATE, text, 1);
            Matcher r = REG.matcher(text);
            LocalDate regStart = null, regEnd = null;
            if (r.find()) {
                regStart = LocalDate.parse(r.group(1));
                regEnd = LocalDate.parse(r.group(2));
            }
            if (examDate == null && regStart == null) {
                continue;   // 날짜가 하나도 없으면 일정이 아니다
            }

            for (Exam e : exams) {
                out.add(new CollectedSchedule(
                        e.code(), e.name(), Series.ETC, AGENCY, CATEGORY,
                        year, round, ExamType.WRITTEN,
                        regStart == null ? null : regStart.atTime(10, 0),
                        regEnd == null ? null : regEnd.atTime(18, 0),
                        examDate, examDate, null,
                        URL, ScheduleProvenance.SCRAPED));
            }
        }
        return out;
    }

    /**
     * 표의 시험명으로 우리 종목을 고른다.
     *
     * <p>"GTQ-AI" 는 "GTQ" 를 포함하므로 <b>더 긴 열쇠를 먼저</b> 본다 — 안 그러면 GTQ-AI 일정이
     * GTQ 1·2급에 붙는다(실제로 다른 회차다).
     */
    private List<Exam> examsFor(String rawName) {
        String name = rawName.replaceAll("\\s+", "");
        if (name.contains("GTQ-AI") || name.contains("GTQAI")) {
            return List.of();
        }
        for (Map.Entry<String, List<Exam>> e : EXAMS.entrySet()) {
            if (name.contains(e.getKey().replaceAll("\\s+", ""))) {
                return e.getValue();
            }
        }
        return List.of();
    }

    private String firstText(String html, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(html);
        return m.find() ? m.group(1) : null;
    }

    private String clean(String html) {
        return TAG.matcher(html).replaceAll(" ").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
    }

    private LocalDate date(Pattern p, String text, int group) {
        Matcher m = p.matcher(text);
        return m.find() ? LocalDate.parse(m.group(group)) : null;
    }

    /** 표 한 줄이 가리키는 우리 종목. */
    private record Exam(String code, String name) {
    }
}

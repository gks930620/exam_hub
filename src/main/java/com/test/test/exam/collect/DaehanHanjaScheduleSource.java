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
 * 대한검정회 한자급수자격검정시험 일정 수집 — 준1급·2급 <b>2종</b>.
 *
 * <p>첫 화면({@code hanja.ne.kr/index_original.asp})의 일정 안내 블록에 <b>다음 회차 하나</b>가 있다.
 * <pre>
 * 제112회 한자급수자격검정시험
 *   시행일     2026.08.22(토)
 *   접수기간   2026.06.29(월) ~ 2026.07.10(금)
 *   합격자발표 2026.09.14(월) 오전 10시
 * </pre>
 * 페이지는 EUC-KR 이다 — 문자셋 판별은 {@link AbstractHtmlScheduleSource#get} 이 한다.
 * robots.txt 는 {@code /board/} 만 막는다(2026-09-08 확인).
 *
 * <h3>주석에 지난해가 남아 있다</h3>
 * 이 화면은 담당자가 해마다 손으로 고치는데, <b>옛 문구를 지우지 않고 주석으로 감싸 둔다.</b>
 * <pre>&lt;!--&lt;li&gt;방문 접수기간 2024.09.23(월) ~ 2024.10.11(금)&lt;/li&gt;--&gt;</pre>
 * 걷어내지 않으면 <b>2024년 날짜가 올해 접수기간</b>으로 들어간다. 사람 눈에는 안 보이는 값이라
 * 화면을 봐도 못 잡는다 — {@link AbstractHtmlScheduleSource#stripComments} 로 먼저 지운다.
 *
 * <h3>같은 블록에 다른 시험이 있다</h3>
 * "제92회 한자·한문전문지도사 시험"이 나란히 적혀 있다. 우리 마스터에 없는 시험이라,
 * <b>회차는 "한자급수자격검정시험" 바로 앞의 것</b>만 집는다. 안 그러면 회차가 92회로 들어간다.
 *
 * <h3>접수 시각</h3>
 * 화면에 시각이 없다. 시작 10:00 / 마감 18:00 으로 둔다.
 */
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class DaehanHanjaScheduleSource extends AbstractHtmlScheduleSource {

    static final String URL = "https://www.hanja.ne.kr/index_original.asp";
    static final String AGENCY = "대한검정회";
    private static final String CATEGORY = "한자";

    /** 한 회차가 급수 둘에 똑같이 붙는다 — 같은 날 급수만 골라 친다. */
    private static final Map<String, String> EXAMS = new LinkedHashMap<>();

    static {
        EXAMS.put("M0455", "대한검정회 한자 준1급");
        EXAMS.put("M0456", "대한검정회 한자 2급");
    }

    /** 제112회 한자급수자격검정시험 — 옆에 있는 "제92회 한자·한문전문지도사"를 집지 않도록 시험명까지 묶는다. */
    private static final Pattern ROUND =
            Pattern.compile("제\\s*(\\d{1,4})\\s*회\\s*한자급수자격검정");
    private static final Pattern EXAM_DATE = Pattern.compile("시행일\\s*(20\\d\\d)\\.(\\d{1,2})\\.(\\d{1,2})");
    private static final Pattern REG = Pattern.compile(
            "(?<!방문\\s)접수기간\\s*(20\\d\\d)\\.(\\d{1,2})\\.(\\d{1,2})\\s*\\([일월화수목금토]\\)"
                    + "\\s*~\\s*(20\\d\\d)\\.(\\d{1,2})\\.(\\d{1,2})");
    private static final Pattern RESULT = Pattern.compile("합격자발표\\s*(20\\d\\d)\\.(\\d{1,2})\\.(\\d{1,2})");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    @Override
    public String sourceId() {
        return "DAEHAN_HANJA_WEB";
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
        // 주석을 먼저 걷어낸다 — 지난해 문구가 그대로 남아 있다
        String text = clean(stripComments(html));

        Matcher round = ROUND.matcher(text);
        Matcher exam = EXAM_DATE.matcher(text);
        if (!round.find() || !exam.find()) {
            return List.of();
        }
        LocalDate examDate = date(exam, 1);
        LocalDate regStart = null, regEnd = null;
        Matcher reg = REG.matcher(text);
        if (reg.find()) {
            regStart = date(reg, 1);
            regEnd = date(reg, 4);
        }
        Matcher result = RESULT.matcher(text);
        LocalDate resultDate = result.find() ? date(result, 1) : null;

        List<CollectedSchedule> out = new ArrayList<>();
        for (Map.Entry<String, String> e : EXAMS.entrySet()) {
            out.add(new CollectedSchedule(
                    e.getKey(), e.getValue(), Series.ETC, AGENCY, CATEGORY,
                    examDate.getYear(), Integer.parseInt(round.group(1)), ExamType.WRITTEN,
                    regStart == null ? null : regStart.atTime(10, 0),
                    regEnd == null ? null : regEnd.atTime(18, 0),
                    examDate, examDate, resultDate,
                    URL, ScheduleProvenance.SCRAPED));
        }
        return out;
    }

    private LocalDate date(Matcher m, int group) {
        return LocalDate.of(Integer.parseInt(m.group(group)),
                Integer.parseInt(m.group(group + 1)), Integer.parseInt(m.group(group + 2)));
    }

    private String clean(String html) {
        return TAG.matcher(html).replaceAll(" ").replace("&nbsp;", " ").replaceAll("\\s+", " ");
    }
}

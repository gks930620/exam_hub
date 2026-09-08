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
 * 한국어문회 전국한자능력검정시험 일정 수집 — 1·2·3·4급 <b>4종</b>.
 *
 * <p>첫 화면({@code hanja.re.kr})에 회차 카드가 나란히 있다. 표가 아니라 안내 문구다.
 * <pre>
 * 제115회 정기 전국한자능력검정시험
 *   접수기간 : 2026.10.19 ~ 2026.10.23 (인터넷 접수만 시행)
 *   시험일시 : 2026.11.21 / 시간 : 11:00, 15:00
 *   합격발표 : 2026.12.18
 * </pre>
 * robots.txt 는 없다(404, 2026-09-08 확인).
 *
 * <h3>수시 시험은 담지 않는다</h3>
 * 같은 화면에 <b>수시</b> 시험(제17·18회)이 섞여 있다. 회차 번호 체계가 다르고 응시 가능한 급수도
 * 제한된다. 우리 급수 넷에 그대로 붙이면 <b>못 치는 급수까지 "접수하세요"가 된다.</b>
 * 그래서 "정기"라고 적힌 카드만 읽는다.
 *
 * <h3>접수 시각</h3>
 * 화면에 시각이 없다. 시작 10:00 / 마감 18:00 으로 둔다 — 이 시행처의 통상 시각이고,
 * 비워 두면 "접수 중" 판정이 안 선다.
 */
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class HanjaScheduleSource extends AbstractHtmlScheduleSource {

    static final String URL = "https://www.hanja.re.kr/";
    static final String AGENCY = "한국어문회";
    private static final String CATEGORY = "한자";

    /** 한 회차가 급수 넷에 똑같이 붙는다 — 같은 날 같은 자리에서 급수만 골라 친다. */
    private static final Map<String, String> EXAMS = new LinkedHashMap<>();

    static {
        EXAMS.put("M0451", "한자능력검정시험(1급)");
        EXAMS.put("M0452", "한자능력검정시험(2급)");
        EXAMS.put("M0453", "한자능력검정시험(3급)");
        EXAMS.put("M0454", "한자능력검정시험(4급)");
    }

    /** 회차 카드 하나 — 다음 회차 제목이 나오거나 끝날 때까지. */
    private static final Pattern CARD = Pattern.compile(
            "제\\s*(\\d{1,4})\\s*회\\s*(정기|수시)[\\s\\S]{0,600}?합격발표\\s*:?\\s*(20\\d\\d\\.\\d{1,2}\\.\\d{1,2})");
    private static final Pattern REG = Pattern.compile(
            "접수기간\\s*:?\\s*(20\\d\\d\\.\\d{1,2}\\.\\d{1,2})\\s*~\\s*(20\\d\\d\\.\\d{1,2}\\.\\d{1,2})");
    private static final Pattern EXAM = Pattern.compile("시험일시\\s*:?\\s*(20\\d\\d\\.\\d{1,2}\\.\\d{1,2})");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    @Override
    public String sourceId() {
        return "HANJA_WEB";
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
        String text = clean(stripComments(html));
        List<CollectedSchedule> out = new ArrayList<>();
        Matcher cards = CARD.matcher(text);
        while (cards.find()) {
            if (!"정기".equals(cards.group(2))) {
                continue;   // 수시는 급수가 제한된다 — 우리 급수 넷에 붙이면 사실과 달라진다
            }
            int round = Integer.parseInt(cards.group(1));
            String card = cards.group();
            LocalDate exam = date(EXAM, card, 1);
            Matcher reg = REG.matcher(card);
            LocalDate regStart = null, regEnd = null;
            if (reg.find()) {
                regStart = date(reg.group(1));
                regEnd = date(reg.group(2));
            }
            if (exam == null && regStart == null) {
                continue;
            }
            LocalDate result = date(cards.group(3));
            int year = exam != null ? exam.getYear() : regStart.getYear();

            for (Map.Entry<String, String> e : EXAMS.entrySet()) {
                out.add(new CollectedSchedule(
                        e.getKey(), e.getValue(), Series.ETC, AGENCY, CATEGORY,
                        year, round, ExamType.WRITTEN,
                        regStart == null ? null : regStart.atTime(10, 0),
                        regEnd == null ? null : regEnd.atTime(18, 0),
                        exam, exam, result,
                        URL, ScheduleProvenance.SCRAPED));
            }
        }
        return out;
    }

    private String clean(String html) {
        return TAG.matcher(html).replaceAll(" ").replace("&nbsp;", " ").replaceAll("\\s+", " ");
    }

    private LocalDate date(Pattern p, String text, int group) {
        Matcher m = p.matcher(text);
        return m.find() ? date(m.group(group)) : null;
    }

    private LocalDate date(String raw) {
        String[] parts = raw.split("\\.");
        return LocalDate.of(Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
    }
}

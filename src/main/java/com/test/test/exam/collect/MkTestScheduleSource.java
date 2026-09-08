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
 * 매일경제신문 매경TEST 일정 수집 — <b>1종</b>.
 *
 * <p>{@code exam.mk.co.kr/online_main.php?MM=2} 에 그 해 회차가 표 하나로 다 있다.
 * <pre>회차 | 주니어 | 시험일 | 접수기간 | 실시지역 | 발표일
 * 116회 | - | 2026.09.05(토) | 2026.07.13(월) ~ 2026.08.24(월) | 서울… | 2026.09.11(금)</pre>
 * robots.txt 는 없다(404, 2026-09-08 확인).
 *
 * <h3>보이지 않는 문자가 끼어 있다</h3>
 * 날짜와 요일 사이에 <b>폭 없는 공백</b>({@code &#8203;})이 들어간 칸이 있다
 * ({@code 2025.12.22&#8203;(월)&#8203;}). 눈에는 안 보이지만 {@code (월)} 을 함께 찾는 정규식은
 * 그 줄을 통째로 놓친다. 그래서 <b>요일은 안 보고 숫자만</b> 읽는다.
 *
 * <p>주니어 매경TEST 회차는 두 번째 칸에 따로 있는데 우리 마스터에 없는 시험이라 안 담는다.
 *
 * <h3>접수 시각</h3>
 * 표는 날짜만 준다. 시작 10:00 / 마감 18:00 으로 둔다 — 다른 시행처와 같은 기준이다.
 */
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class MkTestScheduleSource extends AbstractHtmlScheduleSource {

    static final String URL = "https://exam.mk.co.kr/online_main.php?MM=2";
    static final String AGENCY = "매일경제신문";
    private static final String CATEGORY = "경제-경영";
    private static final String SOURCE_CODE = "M0441";
    private static final String NAME = "매경TEST 경제경영이해력시험";

    private static final Pattern ROW = Pattern.compile("<tr[\\s\\S]*?</tr>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL =
            Pattern.compile("<t[dh]([^>]*)>([\\s\\S]*?)(?=</t[dh]>|<t[dh][\\s>]|</tr>|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROUND = Pattern.compile("^(\\d{1,4})\\s*회?$");
    /** 2026.09.05 — 뒤의 요일은 보지 않는다(폭 없는 공백이 끼어 있다) */
    private static final Pattern DATE = Pattern.compile("(20\\d\\d)\\.(\\d{1,2})\\.(\\d{1,2})");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    @Override
    public String sourceId() {
        return "MKTEST_WEB";
    }

    @Override
    public Set<String> coveredAgencies() {
        return Set.of(AGENCY);
    }

    @Override
    public Set<String> coveredExamCodes() {
        // 비큐넷 시드가 같은 시험에 다른 코드를 붙인다 — 자세한 사정은 SeedCodeAlias.
        return SeedCodeAlias.plus(Set.of(SOURCE_CODE), "MK-TEST");
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
            if (cells.size() < 5) {
                continue;
            }
            Matcher round = ROUND.matcher(cells.get(0));
            if (!round.find()) {
                continue;   // 머리글 줄
            }
            LocalDate exam = date(cells.get(2), 0);
            LocalDate regStart = date(cells.get(3), 0);
            LocalDate regEnd = date(cells.get(3), 1);
            LocalDate result = date(cells.get(cells.size() - 1), 0);
            if (exam == null && regStart == null) {
                continue;
            }
            out.add(new CollectedSchedule(
                    SOURCE_CODE, NAME, Series.ETC, AGENCY, CATEGORY,
                    exam != null ? exam.getYear() : regStart.getYear(),
                    Integer.parseInt(round.group(1)), ExamType.WRITTEN,
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
        try {
            return LocalDate.of(Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (Exception e) {
            return null;
        }
    }

    /** 폭 없는 공백까지 지운다 — 눈에 안 보이는 채로 날짜 사이에 끼어 있다. */
    private String clean(String html) {
        return TAG.matcher(html).replaceAll(" ")
                .replace("&nbsp;", " ").replace("\u200b", "").replace("&#8203;", "")
                .replaceAll("\\s+", " ").trim();
    }
}

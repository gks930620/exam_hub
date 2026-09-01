package com.test.test.exam.collect;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 한국보건의료인국가시험원(국시원) 시험일정 수집 — 간호사·물리치료사 등 보건의료 16종.
 *
 * <p>"일정 없음 190" 의 <b>최대 덩어리</b>였다(설계/시험데이터/04_마스터_실사 §3).
 * 직종마다 페이지가 따로 있고({@code /subcnt/<cms>/1/view.do?seq=7&itm_seq=<직종코드>}),
 * 표 하나에 응시원서 접수 · 시험시행 · 최종합격자 발표가 들어 있다. 연 1회라 한 건씩이다.
 *
 * <h3>요양보호사는 뺐다</h3>
 * 상시(기간제) 컴퓨터시험이라 접수 기간이 "시험 개시일로부터 7일 전까지" 다 —
 * 절대 날짜가 없어서 알릴 마감이 없다(컴활·TOEFL 과 같은 부류).
 * 파서도 <b>상시 문구를 만나면 아무것도 만들지 않는다</b> — 국시원이 다른 직종을 상시로
 * 전환해도 엉뚱한 날짜가 들어가지 않게.
 *
 * <h3>접수 시각</h3>
 * 페이지가 명시한다: "원서접수 시작일 09:00부터 접수 마감일 18:00까지". 그대로 쓴다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class KuksiwonScheduleSource extends AbstractHtmlScheduleSource {

    static final String BASE = "https://www.kuksiwon.or.kr";
    static final String AGENCY = "한국보건의료인국가시험원";
    private static final String CATEGORY = "보건의료";

    /** 한 직종 페이지에서 만들 시험. 코드는 <b>이미 마스터에 붙어 있는 값</b>이어야 한다(중복 방지). */
    public record Exam(String name, String sourceCode) {
    }

    /** 직종 페이지 하나 — 응급구조사처럼 급수 둘이 한 페이지를 쓰기도 한다. */
    public record Target(String cms, String jjCode, List<Exam> exams) {
    }

    public static final List<Target> TARGETS = List.of(
            new Target("c_2005", "05", List.of(new Exam("간호사", "M0303"))),
            new Target("c_2012", "08", List.of(new Exam("임상병리사", "M0304"))),
            new Target("c_2013", "09", List.of(new Exam("방사선사", "M0305"))),
            new Target("c_2014", "11", List.of(new Exam("물리치료사", "M0306"))),
            new Target("c_2015", "13", List.of(new Exam("작업치료사", "M0307"))),
            new Target("c_2017", "12", List.of(new Exam("치과위생사", "M0308"))),
            new Target("c_2016", "10", List.of(new Exam("치과기공사", "M0309"))),
            new Target("c_2022", "17", List.of(
                    new Exam("응급구조사 1급", "M0310"),
                    new Exam("응급구조사 2급", "M0311"))),
            new Target("c_2020", "07", List.of(new Exam("영양사", "M0312"))),
            new Target("c_2021", "21", List.of(new Exam("위생사", "M0313"))),
            new Target("c_2026", "31", List.of(new Exam("보건교육사", "M0314"))),
            new Target("c_2019", "15", List.of(new Exam("안경사", "M0315"))),
            // 의무기록사는 2018년에 '보건의료정보관리사' 로 개칭됐다 — 코드로 이어 붙는다
            new Target("c_2018", "14", List.of(new Exam("의무기록사", "M0316"))),
            new Target("c_2009", "60", List.of(new Exam("약사", "M0317"))),
            new Target("c_2010", "24", List.of(new Exam("한약사", "M0318"))));

    /** 2026. 9. 2.(수) — 연도는 범위 뒷쪽에서 생략되기도 한다(~ 9. 9.) */
    private static final Pattern FULL_DATE = Pattern.compile("(20\\d{2})\\.\\s*(\\d{1,2})\\.\\s*(\\d{1,2})\\.");
    private static final Pattern RANGE = Pattern.compile(
            "(20\\d{2})\\.\\s*(\\d{1,2})\\.\\s*(\\d{1,2})\\.[^~]{0,10}~\\s*(?:(20\\d{2})\\.\\s*)?(\\d{1,2})\\.\\s*(\\d{1,2})\\.");

    @Override
    public String sourceId() {
        return "KUKSIWON_WEB";
    }

    /** 안 쓴다 — 직종마다 페이지가 따로라 {@link #fetchAll()} 이 직접 돈다. */
    @Override
    protected String pageUrl() {
        return BASE;
    }

    /** 안 쓴다 — 어느 직종인지 알아야 파싱이 된다. {@link #parse(String, Target)} 를 쓴다. */
    @Override
    public List<CollectedSchedule> parse(String html) {
        return List.of();
    }

    @Override
    public List<CollectedSchedule> fetchAll() {
        List<CollectedSchedule> out = new ArrayList<>();
        for (Target t : TARGETS) {
            try {
                out.addAll(parse(get(url(t)), t));
            } catch (Exception e) {
                log.warn("[{}] {} 조회 실패: {}", sourceId(), t.exams().get(0).name(), e.toString());
            }
        }
        log.info("[{}] 직종 페이지 {}개 → 일정 {}건", sourceId(), TARGETS.size(), out.size());
        if (out.isEmpty()) {
            log.warn("[{}] 일정을 하나도 못 뽑았다 — 페이지 구조가 바뀌었을 수 있다", sourceId());
        }
        return out;
    }

    static String url(Target t) {
        return BASE + "/subcnt/" + t.cms() + "/1/view.do?seq=7&itm_seq=" + t.jjCode();
    }

    public List<CollectedSchedule> parse(String html, Target target) {
        Matcher table = Pattern.compile("<table[\\s\\S]*?</table>").matcher(html == null ? "" : html);
        if (!table.find()) {
            return List.of();
        }
        String text = toText(table.group());

        // 상시(기간제) 직종은 절대 날짜가 없다 — 억지로 뽑으면 엉뚱한 값이 들어간다
        if (text.contains("상시접수") || text.contains("상시(기간제)")) {
            return List.of();
        }

        LocalDate[] reg = range(section(text, "응시원서", "시험장 공고", "시험시행"));
        LocalDate exam = first(section(text, "시험시행", "최종합격자", null));
        LocalDate result = first(section(text, "최종합격자", null, null));
        if (reg == null && exam == null) {
            return List.of();
        }

        int year = exam != null ? exam.getYear()
                : reg != null ? reg[0].getYear() : TimeUtil.today().getYear();

        List<CollectedSchedule> out = new ArrayList<>();
        for (Exam e : target.exams()) {
            out.add(new CollectedSchedule(
                    e.sourceCode(), e.name(), Series.ETC, AGENCY, CATEGORY,
                    year, 1, ExamType.WRITTEN,
                    reg == null ? null : reg[0].atTime(9, 0),
                    reg == null ? null : reg[1].atTime(18, 0),
                    exam, exam, result,
                    url(target), ScheduleProvenance.SCRAPED));
        }
        return out;
    }

    /** from 이후 ~ (있다면) 다음 구분 전까지. 표의 구분 이름이 그대로 경계다. */
    private String section(String text, String from, String until, String until2) {
        int i = text.indexOf(from);
        if (i < 0) {
            return "";
        }
        int end = text.length();
        for (String u : new String[]{until, until2}) {
            if (u == null) {
                continue;
            }
            int j = text.indexOf(u, i + from.length());
            if (j > 0) {
                end = Math.min(end, j);
            }
        }
        return text.substring(i, end);
    }

    private LocalDate[] range(String text) {
        Matcher m = RANGE.matcher(text);
        if (!m.find()) {
            return null;
        }
        LocalDate start = LocalDate.of(Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        int endYear = m.group(4) != null ? Integer.parseInt(m.group(4)) : start.getYear();
        LocalDate end = LocalDate.of(endYear, Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)));
        // 12월 접수 ~ 1월 마감처럼 연도가 생략된 채 해를 넘는 경우
        if (end.isBefore(start) && m.group(4) == null) {
            end = end.plusYears(1);
        }
        return new LocalDate[]{start, end};
    }

    private LocalDate first(String text) {
        Matcher m = FULL_DATE.matcher(text);
        if (!m.find()) {
            return null;
        }
        return LocalDate.of(Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    }
}

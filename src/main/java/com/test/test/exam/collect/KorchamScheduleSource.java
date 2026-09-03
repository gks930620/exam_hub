package com.test.test.exam.collect;

import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 대한상공회의소 자격평가사업단 <b>정기시험</b> 일정 수집.
 *
 * <p>여기 있는 종목들은 <b>국가기술자격인데 시행처가 큐넷이 아니다</b> — 큐넷 종목목록(613종)에
 * 없어서 큐넷 API 를 아무리 잘 연동해도 안 들어온다
 * (근거: {@code 설계/시험데이터/04_마스터_실사.md} §3).
 *
 * <p><b>컴퓨터활용능력은 여기 없다.</b> 2021년부터 정기검정이 폐지되고 <b>상시검정</b>만 한다 —
 * 응시자가 원하는 날짜·시험장을 골라 접수하므로 <b>"접수 마감"이라는 게 없다.</b>
 * 이 서비스는 마감 D-day 를 알리는 게 목적이라 알림 대상이 아니다(TOEFL·IELTS 와 같은 부류).
 * 실제로 컴활 일정 페이지를 열면 표가 비어 있다.
 *
 * <p><b>일정표 이미지에 속지 말 것</b>: 시행일정 안내 페이지({@code /co/examschedule.do})는
 * 일정을 <b>PNG 이미지와 이미지 PDF</b> 로만 준다(폰트 0개 — 텍스트 레이어가 없다).
 * 텍스트가 있는 곳은 <b>종목별 일정 페이지</b>({@code /co/examguide03.do?cd=…&mm=…}) 뿐이다.
 *
 * <p>표 구조: {@code 종목 | 회별 | 구분 | 등급 | 인터넷접수 | 시험일자 | 발표일자}.
 * <b>등급 칸에 "1, 2, 3" 처럼 여러 급수가 묶여</b> 온다 — 우리 마스터는 급수별로 나뉘어 있어
 * 한 행을 급수 수만큼 펼친다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class KorchamScheduleSource implements ScheduleSource {

    static final String BASE = "https://license.korcham.net/co/examguide03.do";
    static final String AGENCY = "대한상공회의소";
    private static final String CATEGORY = "국가기술자격-경영사무";

    /**
     * 긁을 종목. {@code cd}·{@code mm} 은 사이트 메뉴에서 뽑은 값이고,
     * 급수별 종목코드는 <b>이미 그 시험에 붙어 있는 코드와 같아야 한다</b> — 새 코드를 주면 같은 시험이
     * 하나 더 생긴다. 유통관리사 2급만 비큐넷 시드가 먼저 `KORCHAM-DIST2` 로 잡고 있어 그걸 따른다.
     *
     * <p>여기 없는 상의 종목(컴활·워드프로세서·비서·전산회계운용사·무역영어)은 <b>상시시험</b>이라
     * 일정 페이지가 비어 있다. 넣어 봐야 0건이라 아예 부르지 않는다.
     */
    static final List<Target> TARGETS = List.of(
            new Target("0201", "31", "유통관리사",
                    Map.of("1", "M0043", "2", "KORCHAM-DIST2", "3", "M0045"),
                    Map.of("1", "유통관리사 1급", "2", "유통관리사 2급", "3", "유통관리사 3급")),
            new Target("0101", "33", "전자상거래관리사",
                    Map.of("1", "M0040", "2", "M0041"),
                    Map.of("1", "전자상거래관리사 1급", "2", "전자상거래관리사 2급")),
            new Target("0105", "32", "전자상거래운용사",
                    Map.of("", "M0042"),
                    Map.of("", "전자상거래운용사")),
            new Target("0106", "61", "한글속기",
                    Map.of("1", "M0037", "2", "M0038", "3", "M0039"),
                    Map.of("1", "한글속기 1급", "2", "한글속기 2급", "3", "한글속기 3급")));

    private static final Pattern TABLE = Pattern.compile("<table[\\s\\S]*?</table>");
    private static final Pattern ROW = Pattern.compile("<tr[\\s\\S]*?</tr>");
    private static final Pattern CELL = Pattern.compile("<t[dh][^>]*>([\\s\\S]*?)</t[dh]>");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern DATE = Pattern.compile("(20[0-9]{2})[.]([0-9]{1,2})[.]([0-9]{1,2})");

    private final HtmlFetcher fetcher = new HtmlFetcher();

    @Override
    public String sourceId() {
        return "KORCHAM_WEB";
    }

    @Override
    public java.util.Set<String> coveredAgencies() {
        return java.util.Set.of(AGENCY);
    }

    @Override
    public List<CollectedSchedule> fetchAll() {
        List<CollectedSchedule> out = new ArrayList<>();
        for (Target t : TARGETS) {
            String url = url(t);
            try {
                out.addAll(parse(fetcher.fetch(url), t));
            } catch (Exception e) {
                // 종목 하나가 막혀도 나머지는 받는다 — 상의는 접수 시즌에 대기열(netfunnel)이 걸린다.
                log.warn("[{}] {} 조회 실패: {}", sourceId(), t.name(), e.toString());
            }
        }
        log.info("[{}] 종목 {}개 → 일정 {}건", sourceId(), TARGETS.size(), out.size());
        if (out.isEmpty()) {
            log.warn("[{}] 일정을 하나도 못 뽑았다 — 페이지 구조가 바뀌었을 수 있다", sourceId());
        }
        return out;
    }

    static String url(Target t) {
        return BASE + "?cd=" + t.cd() + "&mm=" + t.mm();
    }

    /** HTML → 일정. 네트워크와 무관한 순수 함수(픽스처로 테스트한다). */
    public List<CollectedSchedule> parse(String html, Target target) {
        List<CollectedSchedule> out = new ArrayList<>();
        Matcher table = TABLE.matcher(html);
        if (!table.find()) {
            return out;
        }

        for (List<String> row : rows(table.group())) {
            if (row.size() < 7) {
                continue;
            }
            // 종목 | 회별 | 구분 | 등급 | 인터넷접수 | 시험일자 | 발표일자
            String round = squeeze(row.get(1));
            if (!round.matches("[0-9]+")) {
                continue;   // 머리글("회별")이나 "시험일정이 없습니다" 행
            }

            LocalDate[] reg = range(row.get(4));
            LocalDate examDate = date(row.get(5));
            LocalDate result = date(row.get(6));
            if (reg == null && examDate == null) {
                continue;
            }
            ExamType type = row.get(2).contains("실기") ? ExamType.PRACTICAL : ExamType.WRITTEN;
            int year = examDate != null ? examDate.getYear() : reg[0].getYear();

            // 등급 칸이 "1, 2, 3" 처럼 묶여 온다 → 급수별로 펼친다
            for (String grade : grades(row.get(3))) {
                String code = target.codeByGrade().get(grade);
                String name = target.nameByGrade().get(grade);
                if (code == null || name == null) {
                    log.debug("[{}] {} 등급 '{}' 은 우리 마스터에 없다 — 건너뜀", sourceId(), target.name(), grade);
                    continue;
                }
                out.add(new CollectedSchedule(
                        code, name, Series.ETC, AGENCY, CATEGORY,
                        year, Integer.parseInt(round), type,
                        reg == null ? null : reg[0].atTime(10, 0),
                        reg == null ? null : reg[1].atTime(18, 0),
                        examDate, examDate, result,
                        url(target), ScheduleProvenance.SCRAPED));
            }
        }
        return out;
    }

    /**
     * "1, 2, 3" → [1, 2, 3]. 등급이 없는 종목(전자상거래운용사)은 빈 문자열 하나.
     *
     * <p>셀 안에 줄바꿈·탭이 잔뜩 끼어 있어 숫자만 골라낸다.
     */
    private List<String> grades(String cell) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("[0-9]").matcher(cell);
        while (m.find()) {
            if (!out.contains(m.group())) {
                out.add(m.group());
            }
        }
        return out.isEmpty() ? List.of("") : out;
    }

    /** "2026.03.05 ~ 2026.03.11" — 사이에 공백·줄바꿈이 많다. */
    private LocalDate[] range(String cell) {
        Matcher m = DATE.matcher(cell);
        List<LocalDate> found = new ArrayList<>();
        while (m.find() && found.size() < 2) {
            found.add(toDate(m));
        }
        if (found.isEmpty()) {
            return null;
        }
        LocalDate start = found.get(0);
        LocalDate end = found.size() > 1 ? found.get(1) : start;
        return new LocalDate[]{start, end};
    }

    private LocalDate date(String cell) {
        Matcher m = DATE.matcher(cell);
        return m.find() ? toDate(m) : null;
    }

    private LocalDate toDate(Matcher m) {
        try {
            return LocalDate.of(Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (Exception e) {
            return null;
        }
    }

    private String squeeze(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }

    private List<List<String>> rows(String table) {
        List<List<String>> out = new ArrayList<>();
        Matcher tr = ROW.matcher(table);
        while (tr.find()) {
            List<String> cells = new ArrayList<>();
            Matcher td = CELL.matcher(tr.group());
            while (td.find()) {
                cells.add(TAG.matcher(td.group(1)).replaceAll("").replace("&nbsp;", " ").trim());
            }
            out.add(cells);
        }
        return out;
    }

    /**
     * @param codeByGrade 등급 → 우리 마스터의 종목코드
     * @param nameByGrade 등급 → 우리 마스터의 종목명
     */
    public record Target(String cd, String mm, String name,
                         Map<String, String> codeByGrade,
                         Map<String, String> nameByGrade) {
    }

    /**
     * 종목마다 페이지가 따로라 {@link AbstractHtmlScheduleSource}(한 페이지 전제)를 그대로 못 쓴다.
     * <b>가져오는 부분만</b> 빌려 쓴다 — 국내 시행처는 EUC-KR 이 섞여 있어 문자셋 판별이 이미 들어 있다.
     *
     * <p>빈으로 등록하지 않는다. 등록하면 수집 목록에 소스가 하나 더 잡혀 매번 빈 결과를 낸다.
     */
    static class HtmlFetcher extends AbstractHtmlScheduleSource {

        @Override
        public String sourceId() {
            return "KORCHAM_FETCH";
        }

        @Override
        protected String pageUrl() {
            return BASE;
        }

        @Override
        public List<CollectedSchedule> parse(String html) {
            return List.of();
        }

        String fetch(String url) {
            return get(url);
        }
    }
}

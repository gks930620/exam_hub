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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 한국방송통신전파진흥원(KCA) 국가기술자격검정 정기시험 일정 수집.
 *
 * <p>정보보안기사·정보통신기사·무선설비기사·전파전자통신기사 등이 여기 있다.
 * <b>국가기술자격인데 시행처가 큐넷이 아니라</b> 큐넷 종목목록(613종)에 없다 —
 * 큐넷 API 를 아무리 잘 연동해도 안 들어온다({@code 설계/시험데이터/04_마스터_실사.md} §3).
 *
 * <h3>이 표가 까다로운 이유 — rowspan</h3>
 * 한 회차의 <b>필기 일정은 여러 분야가 공유</b>하고 실기만 갈라진다. HTML 은 그걸 rowspan 으로
 * 표현해서, 이어지는 행에는 실기 칸 몇 개만 있다.
 *
 * <pre>
 * 제1회 | 1.26~1.29 | 2.9~3.6 | 3.13 | … | 3.28~3.30 | 4.10 | 전파전자통신 분야   ← rowspan=2
 *                                          4.11~4.26 | 5.8                      ← 이어지는 행
 * 제2회 | 5.11~5.14 | 5.22~6.15 | 6.19 | … | 7.4~7.6  | 7.17 | 전파전자통신기능사  ← rowspan=2
 *                                          7.25~8.9  | 8.28 | 정보보안·정보통신·무선설비 분야
 * </pre>
 *
 * 그래서 <b>rowspan 을 이어받아</b> 읽는다. 태그를 걷어내고 셀만 세면 어느 회차의 실기인지
 * 알 수 없어 <b>엉뚱한 회차의 접수일</b>이 붙는다.
 *
 * <h3>분야 이름이 없는 행은 버린다</h3>
 * 비고 칸이 빈 이어지는 행이 있다(제1회·제4회의 두 번째 실기 묶음). 앞의 예로 미루어
 * 정보보안·정보통신·무선설비일 <b>가능성이 높지만 표가 그렇게 말하지 않는다.</b>
 * 추측으로 붙이면 접수일이 틀린 채 조용히 서비스된다 — <b>틀린 날짜는 없는 것보다 나쁘다.</b>
 * 버릴 때는 로그를 남겨 무엇을 놓쳤는지 알 수 있게 한다.
 */
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class KcaScheduleSource extends AbstractHtmlScheduleSource {

    static final String URL = "https://www.cq.or.kr/qh_quagm03_001.do";
    static final String AGENCY = "한국방송통신전파진흥원";
    private static final String CATEGORY = "국가기술자격-정보통신";

    /**
     * 비고 칸의 <b>분야</b> → 우리 마스터의 종목들.
     *
     * <p>종목코드는 <b>이미 그 시험에 붙어 있는 값</b>이어야 한다 — 새 코드를 주면 같은 시험이
     * 하나 더 생긴다. 정보보안기사는 비큐넷 시드가 먼저 {@code KCA-SEC} 로 잡고 있어 그걸 따른다
     * (실제로 M0002 를 줬다가 정보보안기사가 두 개가 됐다).
     */
    private static final Map<String, Map<String, String>> FIELD_EXAMS = Map.of(
            "정보보안", Map.of("정보보안기사", "KCA-SEC", "정보보안산업기사", "M0011"),
            "정보통신", Map.of("정보통신기사", "M0006", "정보통신산업기사", "M0013"),
            "무선설비", Map.of("무선설비기사", "M0007", "무선설비산업기사", "M0014"),
            "방송통신", Map.of("방송통신기사", "M0008", "방송통신산업기사", "M0015"),
            "전파전자통신", Map.of("전파전자통신기사", "M0009", "전파전자통신산업기사", "M0016"));

    /** 표의 열 순서: 회별·필기접수·필기시험·필기합격·서류제출·실기접수·실기시험·합격발표·비고 */
    private static final int COLUMNS = 9;
    private static final int COL_ROUND = 0;
    private static final int COL_REG = 1;
    private static final int COL_EXAM = 2;
    private static final int COL_PRAC_REG = 5;
    private static final int COL_PRAC_EXAM = 6;
    private static final int COL_RESULT = 7;
    private static final int COL_NOTE = 8;

    private static final Pattern TABLE = Pattern.compile("<table[\\s\\S]*?</table>");
    private static final Pattern ROW = Pattern.compile("<tr[\\s\\S]*?</tr>");
    private static final Pattern CELL = Pattern.compile("<t[dh]([^>]*)>([\\s\\S]*?)</t[dh]>");
    private static final Pattern ROWSPAN = Pattern.compile("rowspan=[\"']?([0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern YEAR = Pattern.compile("(20[0-9]{2})\\s*년");
    private static final Pattern ROUND_NO = Pattern.compile("제\\s*([0-9]+)\\s*회");
    /** 1.26(월)~1.29(목) — 끝에 월이 다시 붙기도 한다(2.9~3.6) */
    private static final Pattern RANGE = Pattern.compile(
            "([0-9]{1,2})[.]([0-9]{1,2})[^~]*~[^0-9]*([0-9]{1,2})[.]([0-9]{1,2})");

    @Override
    public String sourceId() {
        return "KCA_WEB";
    }

    @Override
    protected String pageUrl() {
        return URL;
    }

    @Override
    public List<CollectedSchedule> parse(String html) {
        List<CollectedSchedule> out = new ArrayList<>();
        int year = year(html);
        if (year == 0) {
            return out;   // 표에 연도가 없다. 엉뚱한 해로 넣느니 아무것도 안 넣는다
        }

        String table = mainTable(html);
        if (table == null) {
            return out;
        }

        int round = 0;
        for (List<String> row : expandRowspans(table)) {
            if (row.size() < COLUMNS) {
                continue;
            }
            Matcher r = ROUND_NO.matcher(row.get(COL_ROUND));
            if (r.find()) {
                round = Integer.parseInt(r.group(1));
            }
            if (round == 0) {
                continue;   // 머리글
            }

            Map<String, String> exams = examsFor(row.get(COL_NOTE));
            if (exams.isEmpty()) {
                continue;   // 분야를 안 밝힌 행 — 추측으로 붙이지 않는다
            }

            LocalDate[] reg = range(row.get(COL_REG), year);
            LocalDate[] exam = range(row.get(COL_EXAM), year);
            LocalDate[] pracReg = range(row.get(COL_PRAC_REG), year);
            LocalDate[] pracExam = range(row.get(COL_PRAC_EXAM), year);
            LocalDate result = single(row.get(COL_RESULT), year);

            for (Map.Entry<String, String> e : exams.entrySet()) {
                if (reg != null || exam != null) {
                    out.add(schedule(e.getValue(), e.getKey(), year, round, ExamType.WRITTEN,
                            reg, exam, null));
                }
                if (pracReg != null || pracExam != null) {
                    out.add(schedule(e.getValue(), e.getKey(), year, round, ExamType.PRACTICAL,
                            pracReg, pracExam, result));
                }
            }
        }
        return out;
    }

    private CollectedSchedule schedule(String code, String name, int year, int round, ExamType type,
                                       LocalDate[] reg, LocalDate[] exam, LocalDate result) {
        return new CollectedSchedule(
                code, name, Series.ETC, AGENCY, CATEGORY,
                year, round, type,
                reg == null ? null : reg[0].atTime(10, 0),
                reg == null ? null : reg[1].atTime(18, 0),
                exam == null ? null : exam[0],
                exam == null ? null : exam[1],
                result,
                URL, ScheduleProvenance.SCRAPED);
    }

    /**
     * 비고 칸의 분야 이름으로 종목을 고른다.
     *
     * <p>"전파전자통신기사·기능사" 처럼 <b>등급까지 적혀 있으면 그 등급만</b> 넣는다.
     * "정보보안 분야" 처럼 분야만 적혀 있으면 그 분야 전부.
     * 기능사는 우리 마스터에 해당 종목이 없어 자연히 빠진다.
     */
    private Map<String, String> examsFor(String note) {
        Map<String, String> out = new LinkedHashMap<>();
        if (note == null || note.isBlank()) {
            return out;
        }
        for (Map.Entry<String, Map<String, String>> field : FIELD_EXAMS.entrySet()) {
            String name = field.getKey();
            if (note.contains(name + " 분야") || note.contains(name + "분야")) {
                out.putAll(field.getValue());   // "정보보안 분야" → 그 분야 전부
                continue;
            }
            // "전파전자통신기사·기능사" 처럼 종목명이 통째로 적힌 것만 넣는다.
            // ⚠️ "기능사" 라는 낱말이 들어 있다고 등급 표기로 보면 안 된다 —
            //    "무선설비 분야(기능사제외)" 의 '기능사' 에 걸려 분야 전체를 놓친 적이 있다.
            for (Map.Entry<String, String> exam : field.getValue().entrySet()) {
                if (note.contains(exam.getKey())) {
                    out.put(exam.getKey(), exam.getValue());
                }
            }
        }
        return out;
    }

    /**
     * rowspan 을 아래 행으로 이어 붙여 <b>모든 행을 9칸으로</b> 만든다.
     *
     * <p>이걸 안 하면 이어지는 행(실기 칸 2~3개짜리)이 회차·필기 일정을 잃어버려
     * 엉뚱한 값이 붙는다.
     */
    private List<List<String>> expandRowspans(String table) {
        List<List<String>> out = new ArrayList<>();
        // 열별로 "앞 행에서 이어받을 값과 남은 횟수"
        String[] carry = new String[COLUMNS];
        int[] left = new int[COLUMNS];

        Matcher tr = ROW.matcher(table);
        while (tr.find()) {
            List<String> cells = new ArrayList<>();
            List<Integer> spans = new ArrayList<>();
            Matcher td = CELL.matcher(tr.group());
            while (td.find()) {
                Matcher rs = ROWSPAN.matcher(td.group(1));
                spans.add(rs.find() ? Integer.parseInt(rs.group(1)) : 1);
                cells.add(text(td.group(2)));
            }
            if (cells.isEmpty()) {
                continue;
            }

            List<String> full = new ArrayList<>();
            int next = 0;
            for (int col = 0; col < COLUMNS; col++) {
                if (left[col] > 0) {
                    full.add(carry[col]);
                    left[col]--;
                    continue;
                }
                if (next >= cells.size()) {
                    full.add("");
                    continue;
                }
                String value = cells.get(next);
                int span = spans.get(next);
                next++;
                full.add(value);
                if (span > 1) {
                    carry[col] = value;
                    left[col] = span - 1;
                }
            }
            out.add(full);
        }
        return out;
    }

    private String text(String html) {
        return TAG.matcher(html).replaceAll("").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
    }

    private String mainTable(String html) {
        // 기능장·기사·산업기사·기능사 표. 정보보안기사가 여기 있다.
        Matcher m = TABLE.matcher(html);
        while (m.find()) {
            String t = m.group();
            if (t.contains("기능장") && t.contains("산업기사") && t.contains("실기시험")) {
                return t;
            }
        }
        return null;
    }

    private int year(String html) {
        Matcher m = YEAR.matcher(TAG.matcher(html).replaceAll(" "));
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private LocalDate[] range(String cell, int year) {
        Matcher m = RANGE.matcher(cell);
        if (!m.find()) {
            LocalDate one = single(cell, year);
            return one == null ? null : new LocalDate[]{one, one};
        }
        LocalDate start = date(year, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        LocalDate end = date(year, Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
        return start == null || end == null ? null : new LocalDate[]{start, end};
    }

    private LocalDate single(String cell, int year) {
        Matcher m = Pattern.compile("([0-9]{1,2})[.]([0-9]{1,2})").matcher(cell);
        return m.find() ? date(year, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))) : null;
    }

    private LocalDate date(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }
}

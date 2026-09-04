package com.test.test.exam.collect;

import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 한국데이터산업진흥원(Kdata) 자격시험 일정 수집.
 *
 * <p>원본: {@code dataq.or.kr/www/accept/schedule.do} 의 <b>연간 일정표</b> 한 장.
 * 이 표 하나로 <b>7종</b>이 한꺼번에 들어온다 — SQLD·SQLP·ADsP·ADP·DAsP·DAP,
 * 그리고 <b>빅데이터분석기사</b>.
 *
 * <p><b>빅데이터분석기사가 여기 있는 게 핵심이다.</b> 국가기술자격인데 시행처가 큐넷이 아니라
 * 큐넷 종목목록(613종)에 없다 — 큐넷 API 를 아무리 잘 연동해도 안 들어온다
 * (근거: {@code 설계/시험데이터/04_마스터_실사.md} §3).
 *
 * <p>표는 {@code rowspan} 으로 종목·회차를 묶어 놓아 <b>행마다 셀 수가 다르다</b>.
 * 뒤에서 7칸(구분·접수·수험표·시험일·사전점수·발표·서류)은 항상 같으므로 뒤에서부터 읽고,
 * 앞에 남는 칸으로 종목·회차를 판단한다. 없으면 앞 행 값을 이어 쓴다.
 *
 * <p>날짜에 <b>연도가 없다</b>({@code 3.3~9}, {@code 9.28~10.13}, {@code 4.4(토)}).
 * 연도는 표 제목("2026년 …")에서 얻고, 못 읽으면 아무것도 넣지 않는다 —
 * 엉뚱한 연도로 넣으면 사용자가 1년 뒤 시험을 보고 준비한다.
 */
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class DataqScheduleSource extends AbstractHtmlScheduleSource {

    static final String URL = "https://www.dataq.or.kr/www/accept/schedule.do";
    static final String AGENCY = "한국데이터산업진흥원";

    /**
     * 표의 종목명 → 우리 마스터의 (종목코드, 이름, 분류).
     *
     * <p><b>종목코드를 기존 시드와 똑같이 맞춘 게 중요하다.</b> 수집은 코드로 기존 시험을 찾는데,
     * 새 코드를 주면 같은 시험이 하나 더 생긴다(큐넷 연동에서 실제로 겪었다).
     * 표기가 조금씩 달라("데이터분석 전문가" vs "ADP 데이터분석 전문가") 공백을 지우고 맞춘다.
     */
    private static final Map<String, Exam> EXAMS = Map.of(
            "빅데이터분석기사", new Exam("M0003", "빅데이터분석기사", "국가기술자격-정보통신", Series.TECHNICIAN),
            "데이터분석전문가", new Exam("DATAQ-ADP", "ADP 데이터분석 전문가", "IT-데이터", Series.ETC),
            "데이터분석준전문가", new Exam("DATAQ-ADSP", "ADsP 데이터분석 준전문가", "IT-데이터", Series.ETC),
            "SQL전문가", new Exam("DATAQ-SQLP", "SQLP SQL 전문가", "IT-데이터", Series.ETC),
            "SQL개발자", new Exam("DATAQ-SQLD", "SQLD SQL 개발자", "IT-데이터", Series.ETC),
            "데이터아키텍처전문가", new Exam("M0365", "DAP 데이터아키텍처 전문가", "IT-데이터", Series.ETC),
            "데이터아키텍처준전문가", new Exam("M0364", "DAsP 데이터아키텍처 준전문가", "IT-데이터", Series.ETC));

    private static final Pattern YEAR = Pattern.compile("(20[0-9]{2})년");
    private static final Pattern ROUND = Pattern.compile("제[ ]*([0-9]+)[ ]*회");
    /** 3.3~9 · 9.28~10.13 (월이 바뀌면 앞에 월이 한 번 더 붙는다) */
    private static final Pattern RANGE =
            Pattern.compile("([0-9]{1,2})[.]([0-9]{1,2})[ ]*~[ ]*(?:([0-9]{1,2})[.])?([0-9]{1,2})");
    /** 4.4(토) */
    private static final Pattern SINGLE = Pattern.compile("([0-9]{1,2})[.]([0-9]{1,2})");

    private static final Pattern TABLE = Pattern.compile("<table[\\s\\S]*?</table>");
    private static final Pattern ROW = Pattern.compile("<tr[\\s\\S]*?</tr>");
    /** 닫는 {@code </td>} 가 없어도 읽는다 — 시행처 페이지가 실제로 그렇게 깨져 있었다(KCA, 2026-09-04). */
    private static final Pattern CELL =
            Pattern.compile("<t[dh][^>]*>([\\s\\S]*?)(?=</t[dh]>|<t[dh][\\s>]|</tr>|$)");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    /** 뒤에서부터 고정으로 붙는 칸 수: 구분·접수·수험표·시험일·사전점수·발표·서류 */
    private static final int FIXED_TAIL = 7;

    @Override
    public String sourceId() {
        return "DATAQ_WEB";
    }

    @Override
    public java.util.Set<String> coveredAgencies() {
        return java.util.Set.of(AGENCY);
    }

    @Override
    protected String pageUrl() {
        return URL;
    }

    @Override
    public List<CollectedSchedule> parse(String html) {
        List<CollectedSchedule> out = new ArrayList<>();
        Matcher table = TABLE.matcher(html);
        if (!table.find()) {
            return out;
        }
        String annual = table.group();

        int year = year(annual);
        if (year == 0) {
            return out;   // 연도를 모르면 날짜를 만들 수 없다
        }

        Exam exam = null;
        int round = 0;

        for (List<String> row : rows(annual)) {
            if (row.size() < FIXED_TAIL) {
                continue;
            }
            List<String> tail = row.subList(row.size() - FIXED_TAIL, row.size());
            if (!isScheduleRow(tail)) {
                continue;   // 머리글이나 빈 행
            }

            // 앞에 남는 칸에서 종목·회차를 찾는다. 없으면 앞 행 값을 그대로 쓴다(rowspan).
            for (String cell : row.subList(0, row.size() - FIXED_TAIL)) {
                Exam found = EXAMS.get(squeeze(cell));
                if (found != null) {
                    exam = found;
                    continue;
                }
                Matcher r = ROUND.matcher(cell);
                if (r.find()) {
                    round = Integer.parseInt(r.group(1));
                }
            }
            if (exam == null || round == 0) {
                continue;
            }

            ExamType type = tail.get(0).contains("실기") ? ExamType.PRACTICAL : ExamType.WRITTEN;
            LocalDate[] reg = range(tail.get(1), year);
            LocalDate examDate = single(tail.get(3), year);
            LocalDate result = single(tail.get(5), year);
            if (reg == null && examDate == null) {
                continue;
            }

            out.add(new CollectedSchedule(
                    exam.code(), exam.name(), exam.series(), AGENCY, exam.category(),
                    year, round, type,
                    reg == null ? null : reg[0].atTime(10, 0),
                    reg == null ? null : reg[1].atTime(18, 0),
                    examDate, examDate, result,
                    URL, ScheduleProvenance.SCRAPED));
        }
        return out;
    }

    /**
     * 일정 행인가.
     *
     * <p>구분 칸이 늘 "필기/실기"인 건 아니다 — ADsP·SQLD 처럼 <b>단일 시험은 "-"</b> 로 온다.
     * 그것만 보고 거르면 이 종목들이 통째로 빠진다(실제로 그렇게 2종만 들어왔다).
     * 그래서 구분 칸에 더해 <b>접수 칸에 날짜가 있는지</b>로 머리글과 구분한다.
     */
    private boolean isScheduleRow(List<String> tail) {
        String type = tail.get(0);
        boolean typeLike = type.contains("필기") || type.contains("실기") || "-".equals(type.trim());
        return typeLike && RANGE.matcher(tail.get(1)).find();
    }

    private String squeeze(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }

    private int year(String table) {
        Matcher m = YEAR.matcher(TAG.matcher(table).replaceAll(" "));
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** "3.3~9" → [3/3, 3/9] · "9.28~10.13" → [9/28, 10/13] */
    private LocalDate[] range(String cell, int year) {
        Matcher m = RANGE.matcher(cell);
        if (!m.find()) {
            return null;
        }
        int startMonth = Integer.parseInt(m.group(1));
        int endMonth = m.group(3) != null ? Integer.parseInt(m.group(3)) : startMonth;
        LocalDate start = date(year, startMonth, Integer.parseInt(m.group(2)));
        LocalDate end = date(year, endMonth, Integer.parseInt(m.group(4)));
        return start == null || end == null ? null : new LocalDate[]{start, end};
    }

    private LocalDate single(String cell, int year) {
        Matcher m = SINGLE.matcher(cell);
        return m.find() ? date(year, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))) : null;
    }

    private LocalDate date(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;   // 표에 "-" 나 오타가 섞인 칸이 있다
        }
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

    private record Exam(String code, String name, String category, Series series) {
    }
}

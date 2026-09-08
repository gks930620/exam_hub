package com.test.test.exam.collect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 한국금융연수원(KBI) 자격검정 일정 수집 — 신용분석사·여신심사역 등 <b>8종</b>.
 *
 * <p>일정 페이지({@code Qual.do?cmd=openPage&pageName=qualTestScheduleList})는 표의 <b>머리만</b>
 * 서버렌더하고 몸통은 자바스크립트가 {@code Qual.do?cmd=qualTestScheduleList} 로 물어 채운다.
 * 그래서 그 조회 주소를 그대로 부른다. robots.txt 는 {@code User-agent: Yeti / Allow: /} 뿐이라
 * 막는 경로가 없다(2026-09-08 확인).
 *
 * <h3>연도가 없다</h3>
 * 응답의 날짜는 {@code 02.28 (토)} 처럼 <b>월·일만</b>이고 연도는 {@code D_YY} 한 곳에만 있다.
 * 그래서 <b>해를 넘는 회차</b>를 손으로 맞춰야 한다 — 접수 월이 시험 월보다 뒤면 접수는 전해이고,
 * 발표 월이 시험 월보다 앞이면 발표는 이듬해다. 안 그러면 "12월에 접수하는 1월 시험"이
 * 접수가 시험보다 11개월 늦은 것으로 들어간다.
 *
 * <h3>접수 시각</h3>
 * 표 머리에 시행처가 적어 뒀다 — <b>"원서접수 (시작일 10:00 ~ 마감일 20:00)"</b>. 그대로 쓴다.
 *
 * <h3>영업점 컴플라이언스 오피서</h3>
 * 시행처는 은행·보험·증권 셋으로 나누고 회차도 따로 매긴다(제35·30·30회). 우리 마스터는 한 종목이라
 * <b>은행 것만</b> 담는다 — 셋의 시험일·접수일이 같고, 시행처 공지도 은행 회차를 앞에 쓴다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class KbiScheduleSource extends AbstractHtmlScheduleSource {

    static final String URL = "https://www.kbi.or.kr/platformWeb/Qual.do?cmd=qualTestScheduleList";
    /** 사람이 보는 일정 페이지 — 화면에 출처로 보여 준다. */
    static final String PAGE =
            "https://www.kbi.or.kr/platformWeb/Qual.do?cmd=openPage&pageName=qualTestScheduleList";
    static final String AGENCY = "한국금융연수원";
    private static final String CATEGORY = "금융";

    /** 시행처가 표에 밝혀 둔 접수 시각. */
    private static final int REG_OPEN_HOUR = 10;
    private static final int REG_CLOSE_HOUR = 20;

    /** 시행처의 종목명 → (우리 이름, 마스터 코드). 이름·코드는 {@code seed/exam_master.json} 과 같아야 한다. */
    private static final Map<String, Exam> EXAMS = new LinkedHashMap<>();

    static {
        EXAMS.put("신용분석사", new Exam("M0416", "신용분석사"));
        EXAMS.put("여신심사역", new Exam("M0417", "여신심사역"));
        EXAMS.put("자산관리사(FP)", new Exam("M0418", "자산관리사(FP)"));
        EXAMS.put("국제금융역", new Exam("M0419", "국제금융역"));
        // 시행처는 로마숫자(U+2160)를 쓴다 — 그대로 두면 같은 시험이 하나 더 생긴다
        EXAMS.put("외환전문역Ⅰ종", new Exam("M0420", "외환전문역 1종"));
        EXAMS.put("외환전문역Ⅱ종", new Exam("M0421", "외환전문역 2종"));
        EXAMS.put("은행텔러", new Exam("M0422", "은행텔러"));
        EXAMS.put("영업점 컴플라이언스 오피서(은행)", new Exam("M0423", "영업점컴플라이언스오피서"));
    }

    /** 02.28 (토) */
    private static final Pattern DAY = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})");
    private static final ObjectMapper JSON = new ObjectMapper();

    private record Exam(String sourceCode, String name) {
    }

    @Override
    public String sourceId() {
        return "KBI_WEB";
    }

    @Override
    public Set<String> coveredAgencies() {
        return Set.of(AGENCY);
    }

    /** 이름을 대고 찾아가는 8종. */
    @Override
    public Set<String> coveredExamCodes() {
        return EXAMS.values().stream().map(Exam::sourceCode).collect(java.util.stream.Collectors.toSet());
    }

    @Override
    protected String pageUrl() {
        return PAGE;
    }

    /**
     * 올해와 내년을 각각 한 번씩 묻는다.
     *
     * <p>이 시행처는 <b>연도별로만</b> 답한다. 연말에 올해만 물으면 이듬해 1~2월 회차를 통째로 놓치는데,
     * 그 회차의 접수는 12월에 열린다 — 우리가 알려야 할 바로 그 마감이다.
     */
    @Override
    public List<CollectedSchedule> fetchAll() {
        List<CollectedSchedule> out = new ArrayList<>();
        int thisYear = TimeUtil.today().getYear();
        for (int year : new int[]{thisYear, thisYear + 1}) {
            try {
                out.addAll(parse(post(URL, form(year))));
            } catch (Exception e) {
                log.warn("[{}] {}년 조회 실패: {}", sourceId(), year, e.toString());
            }
        }
        log.info("[{}] {}·{}년 → 일정 {}건", sourceId(), thisYear, thisYear + 1, out.size());
        if (out.isEmpty()) {
            log.warn("[{}] 일정을 하나도 못 뽑았다 — 조회 주소나 응답 모양이 바뀌었을 수 있다", sourceId());
        }
        return out;
    }

    static String form(int year) {
        return "l_pageno=1&l_listscale=100&p_dYy=" + year + "&p_qualType=&p_nQlfn="
                + "&p_sortorder=" + URLEncoder.encode("QUAL_TYPE, D_YY, I_QLFN, Q_NUM", StandardCharsets.UTF_8);
    }

    @Override
    public List<CollectedSchedule> parse(String json) {
        JsonNode list = read(json).path("ds");
        if (!list.isArray()) {
            return List.of();
        }
        List<CollectedSchedule> out = new ArrayList<>();
        for (JsonNode row : list) {
            Exam exam = EXAMS.get(row.path("N_QLFN").asText("").trim());
            if (exam == null) {
                continue;   // 위탁자격(농협·수협) 등 우리 목록에 없는 것 — 만들어 내지 않는다
            }
            int year = row.path("D_YY").asInt(0);
            int round = row.path("Q_SEQ").asInt(0);
            LocalDate examDate = day(row.path("D_OF_APPR").asText(null), year);
            if (year == 0 || round == 0 || examDate == null) {
                continue;
            }

            // 발표가 시험보다 앞선 달이면 이듬해다(12월 시험 → 1월 발표)
            LocalDate result = shift(day(row.path("D_SUCC_ANNO").asText(null), year), examDate, +1);
            LocalDate[] reg = registration(row.path("D_INT_ACPT_DT").asText(null), year, examDate);

            out.add(new CollectedSchedule(
                    exam.sourceCode(), exam.name(), Series.ETC, AGENCY, CATEGORY,
                    year, round, ExamType.WRITTEN,
                    reg == null ? null : reg[0].atTime(REG_OPEN_HOUR, 0),
                    reg == null ? null : reg[1].atTime(REG_CLOSE_HOUR, 0),
                    examDate, examDate, result,
                    PAGE, ScheduleProvenance.SCRAPED));
        }
        return out;
    }

    /** "01.20 (화)~01.27 (화)" — 아직 안 정해진 회차는 {@code " ~ "} 로 온다. 그때는 비워 둔다. */
    private LocalDate[] registration(String raw, int year, LocalDate examDate) {
        if (raw == null) {
            return null;
        }
        int split = raw.indexOf('~');
        if (split < 0) {
            return null;
        }
        // 접수가 시험보다 뒤 달이면 전해다(12월 접수 → 이듬해 1월 시험)
        LocalDate start = shift(day(raw.substring(0, split), year), examDate, -1);
        LocalDate end = shift(day(raw.substring(split + 1), year), examDate, -1);
        return start == null || end == null ? null : new LocalDate[]{start, end};
    }

    /**
     * 시험일을 기준으로 해를 넘긴다.
     *
     * @param years 접수처럼 <b>앞</b>에 오는 날짜는 -1, 발표처럼 <b>뒤</b>에 오는 날짜는 +1
     */
    private LocalDate shift(LocalDate date, LocalDate examDate, int years) {
        if (date == null) {
            return null;
        }
        if (years < 0 && date.isAfter(examDate)) {
            return date.plusYears(years);
        }
        if (years > 0 && date.isBefore(examDate)) {
            return date.plusYears(years);
        }
        return date;
    }

    private LocalDate day(String raw, int year) {
        if (raw == null) {
            return null;
        }
        Matcher m = DAY.matcher(raw);
        if (!m.find()) {
            return null;
        }
        try {
            return LocalDate.of(year, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        } catch (Exception e) {
            return null;   // 02.30 같은 값이 오면 그 행만 버린다
        }
    }

    private JsonNode read(String json) {
        try {
            return JSON.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            log.warn("[{}] JSON 이 아니다 — 점검 중이거나 주소가 바뀌었다", sourceId());
            return JSON.createObjectNode();
        }
    }
}

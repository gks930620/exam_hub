package com.test.test.exam.collect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 금융투자협회 자격시험 일정 수집 — 투자권유자문인력·투자자산운용사 등 <b>9종</b>.
 *
 * <p>일정 페이지({@code license.kofia.or.kr/examInfo/examYearly.do})는 표를 서버렌더로 그리지 않는다.
 * 화면이 자기 서버에 {@code /examInfo/ajax/examYearlyMstInfo.do} 로 물어 JSON 을 받아 그린다.
 * 그래서 <b>같은 주소를 그대로 부른다</b> — 로그인도 키도 필요 없고, 40KB 짜리 페이지를 통째로
 * 받지 않으니 시행처 서버에도 가볍다. robots.txt 는 없다(2026-09-08 확인).
 *
 * <p>{@code rcptSttType=ALL&licenseCd=ALL} 이 <b>그 해 전체 일정</b>이다. 값을 안 주면 접수 중인
 * 회차만 와서 다음 회차를 놓친다.
 *
 * <p>필요한 것만 읽는다.
 * <pre>
 * koreanExamNm   증권투자권유자문인력      timeCnt        35   ← 시행처가 매긴 회차
 * standardY      2026                    examinationDt  20261018
 * receiptSrtDtTm 20260914100000          receiptEndDtTm 20260918180000
 * successAnnDt   20261029
 * </pre>
 *
 * <p><b>파생상품투자권유대행인</b>은 우리 목록에 있지만 올해 일정이 없다 — 지어내지 않고 비워 둔다.
 * 시행처가 다시 열면 이름이 같으니 그대로 들어온다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class KofiaScheduleSource extends AbstractHtmlScheduleSource {

    static final String URL = "https://license.kofia.or.kr/examInfo/ajax/examYearlyMstInfo.do";
    /** 사람이 보는 일정 페이지 — 화면에 출처로 보여 준다(조회 주소가 아니라 이쪽이 사람에게 쓸모 있다). */
    static final String PAGE = "https://license.kofia.or.kr/examInfo/examYearly.do";
    static final String AGENCY = "금융투자협회";
    private static final String CATEGORY = "금융";
    private static final String FORM = "rcptSttType=ALL&licenseCd=ALL";

    /** 시행처의 종목명 → 우리 마스터 코드. 코드는 {@code seed/exam_master.json} 과 같아야 한다. */
    private static final Map<String, String> CODES = new LinkedHashMap<>();

    static {
        CODES.put("투자자산운용사", "M0407");
        CODES.put("펀드투자권유대행인", "M0408");
        CODES.put("증권투자권유대행인", "M0409");
        CODES.put("파생상품투자권유대행인", "M0410");
        CODES.put("펀드투자권유자문인력", "M0411");
        CODES.put("증권투자권유자문인력", "M0412");
        CODES.put("파생상품투자권유자문인력", "M0413");
        CODES.put("금융투자분석사", "M0414");
        CODES.put("재무위험관리사", "M0415");
    }

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YMDHMS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public String sourceId() {
        return "KOFIA_WEB";
    }

    @Override
    public Set<String> coveredAgencies() {
        return Set.of(AGENCY);
    }

    /** 이름을 대고 찾아가는 9종. 올해 회차가 없어도 "자동"이다 — 시행처가 열면 그대로 들어온다. */
    @Override
    public Set<String> coveredExamCodes() {
        return Set.copyOf(CODES.values());
    }

    @Override
    protected String pageUrl() {
        return PAGE;
    }

    @Override
    public List<CollectedSchedule> fetchAll() {
        try {
            List<CollectedSchedule> out = parse(post(URL, FORM));
            log.info("[{}] {} → 일정 {}건", sourceId(), URL, out.size());
            if (out.isEmpty()) {
                log.warn("[{}] 일정을 하나도 못 뽑았다 — 조회 주소나 응답 모양이 바뀌었을 수 있다", sourceId());
            }
            return out;
        } catch (Exception e) {
            log.error("[{}] 수집 실패 — 이번 회차는 건너뛴다: {}", sourceId(), e.toString());
            return List.of();
        }
    }

    @Override
    public List<CollectedSchedule> parse(String json) {
        JsonNode list = read(json).path("examSchedList");
        if (!list.isArray()) {
            return List.of();
        }
        List<CollectedSchedule> out = new ArrayList<>();
        for (JsonNode row : list) {
            String name = row.path("koreanExamNm").asText("").trim();
            String code = CODES.get(name);
            if (code == null) {
                continue;   // 우리 목록에 없는 종목 — 만들어 내지 않는다
            }
            LocalDate exam = date(row.path("examinationDt").asText(null));
            LocalDateTime regStart = dateTime(row.path("receiptSrtDtTm").asText(null));
            LocalDateTime regEnd = dateTime(row.path("receiptEndDtTm").asText(null));
            if (exam == null && regStart == null) {
                continue;   // 날짜가 하나도 없으면 일정이 아니다
            }
            int year = row.path("standardY").asInt(exam != null ? exam.getYear() : 0);
            int round = row.path("timeCnt").asInt(0);
            if (year == 0 || round == 0) {
                continue;
            }
            out.add(new CollectedSchedule(
                    code, name, Series.ETC, AGENCY, CATEGORY,
                    year, round, ExamType.WRITTEN,
                    regStart, regEnd,
                    exam, exam, date(row.path("successAnnDt").asText(null)),
                    PAGE, ScheduleProvenance.SCRAPED));
        }
        return out;
    }

    private JsonNode read(String json) {
        try {
            return JSON.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            // 점검 중이면 JSON 자리에 HTML 이 온다 — 그날 수집만 거르고 다음 소스로 넘어간다
            log.warn("[{}] JSON 이 아니다 — 점검 중이거나 주소가 바뀌었다", sourceId());
            return JSON.createObjectNode();
        }
    }

    private LocalDate date(String raw) {
        return raw == null || raw.length() < 8 ? null : LocalDate.parse(raw.substring(0, 8), YMD);
    }

    private LocalDateTime dateTime(String raw) {
        if (raw == null || raw.length() < 8) {
            return null;
        }
        return raw.length() >= 14
                ? LocalDateTime.parse(raw.substring(0, 14), YMDHMS)
                : LocalDate.parse(raw.substring(0, 8), YMD).atStartOfDay();
    }
}

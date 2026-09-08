package com.test.test.exam.collect;

import com.test.test.exam.domain.ExamType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 금융권 자격시험 일정 파싱 — 금융투자협회 9종 · 한국금융연수원 8종.
 *
 * <p>두 곳 다 <b>일정 페이지가 표를 서버렌더로 안 그린다.</b> 화면이 부르는 자기네 조회 주소가
 * JSON 을 주고 그걸 자바스크립트가 표로 그린다. 그래서 HTML 을 긁는 대신 <b>같은 주소를 그대로 부른다</b> —
 * 로그인도 키도 필요 없고, 서버 부담은 오히려 페이지 한 장보다 가볍다.
 *
 * <p>둘의 생김새가 다르다.
 * <ul>
 *   <li><b>금융투자협회</b> — 날짜가 {@code 20261018} · {@code 20260914100000} 처럼 <b>연도까지</b> 들어 있다. 그대로 읽으면 된다.</li>
 *   <li><b>금융연수원</b> — {@code 02.28 (토)} 처럼 <b>연도가 없다.</b> 별도 필드({@code D_YY})의 연도를 붙이되,
 *       12월 접수 → 1월 시험처럼 <b>해를 넘는 경우</b>를 손으로 맞춰야 한다.</li>
 * </ul>
 */
class FinanceExamParserTest {

    private static String fixture(String name) throws IOException {
        return new String(new ClassPathResource("fixtures/" + name).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private static CollectedSchedule find(List<CollectedSchedule> list, String name, int round) {
        return list.stream()
                .filter(s -> s.certificateName().equals(name) && s.round() == round)
                .findFirst().orElse(null);
    }

    // ===== 금융투자협회 =====

    @Test
    @DisplayName("협회: 회차·접수·시험일·발표일을 읽는다")
    void kofia_reads_a_round() throws IOException {
        List<CollectedSchedule> out = new KofiaScheduleSource().parse(fixture("kofia_schedule.json"));

        assertFalse(out.isEmpty(), "한 건도 못 뽑았다 — 응답 모양이 바뀌었나?");
        CollectedSchedule s = find(out, "증권투자권유자문인력", 35);
        assertNotNull(s, "제35회가 없다");
        assertEquals("M0412", s.sourceCode(), "마스터와 다른 코드를 주면 같은 시험이 하나 더 생긴다");
        assertEquals(2026, s.year());
        assertEquals("2026-10-18", s.examStartDate().toString());
        assertEquals("2026-09-14T10:00", s.regStartAt().toString());
        assertEquals("2026-09-18T18:00", s.regEndAt().toString());
        assertEquals("2026-10-29", s.resultDate().toString());
        assertEquals(ExamType.WRITTEN, s.examType(), "이 시험들은 필기·실기 구분이 없다");
    }

    /** 아홉 종목이 한 응답에 섞여 온다 — 이름으로 갈라야 한다. */
    @Test
    @DisplayName("협회: 종목을 이름으로 갈라 담는다")
    void kofia_splits_by_exam_name() throws IOException {
        List<CollectedSchedule> out = new KofiaScheduleSource().parse(fixture("kofia_schedule.json"));

        for (String name : new String[]{
                "증권투자권유자문인력", "펀드투자권유자문인력", "파생상품투자권유자문인력",
                "증권투자권유대행인", "펀드투자권유대행인",
                "투자자산운용사", "금융투자분석사", "재무위험관리사"}) {
            assertTrue(out.stream().anyMatch(s -> s.certificateName().equals(name)), name + " 이 없다");
        }
        assertTrue(out.stream().allMatch(s -> s.sourceCode().startsWith("M0")),
                "마스터에 없는 코드가 들어왔다");
    }

    /** 모르는 이름이 오면 새 시험을 만들지 않는다 — 시행처가 종목을 늘려도 우리 목록이 오염되지 않는다. */
    @Test
    @DisplayName("협회: 모르는 종목명은 버린다")
    void kofia_drops_unknown_names() {
        String json = """
                {"examSchedList":[{"koreanExamNm":"한번도 본 적 없는 시험","standardY":"2026","timeCnt":1,
                "examinationDt":"20261018","receiptSrtDtTm":"20260914100000","receiptEndDtTm":"20260918180000",
                "successAnnDt":"20261029"}]}""";

        assertTrue(new KofiaScheduleSource().parse(json).isEmpty());
    }

    @Test
    @DisplayName("협회: 응답이 비거나 깨져도 예외 없이 0건")
    void kofia_survives_garbage() {
        assertTrue(new KofiaScheduleSource().parse("").isEmpty());
        assertTrue(new KofiaScheduleSource().parse("<html>점검 중</html>").isEmpty());
        assertTrue(new KofiaScheduleSource().parse("{}").isEmpty());
    }

    // ===== 한국금융연수원 =====

    @Test
    @DisplayName("연수원: 연도 없는 날짜에 연도를 붙여 읽는다")
    void kbi_reads_a_round() throws IOException {
        List<CollectedSchedule> out = new KbiScheduleSource().parse(fixture("kbi_schedule.json"));

        assertFalse(out.isEmpty(), "한 건도 못 뽑았다");
        CollectedSchedule s = find(out, "신용분석사", 66);
        assertNotNull(s, "제66회가 없다");
        assertEquals("M0416", s.sourceCode());
        assertEquals(2026, s.year());
        assertEquals("2026-10-31", s.examStartDate().toString());
        // 시행처가 표 머리에 적어 둔 시각 — "시작일 10:00 ~ 마감일 20:00"
        assertEquals("2026-09-22T10:00", s.regStartAt().toString());
        assertEquals("2026-09-29T20:00", s.regEndAt().toString());
        assertEquals("2026-11-13", s.resultDate().toString());
    }

    /** 여덟 종목이 한 응답에 섞여 온다. 위탁자격(농협·수협)처럼 우리 목록에 없는 건 버린다. */
    @Test
    @DisplayName("연수원: 여덟 종목만 담고 나머지는 버린다")
    void kbi_keeps_only_our_exams() throws IOException {
        List<CollectedSchedule> out = new KbiScheduleSource().parse(fixture("kbi_schedule.json"));

        for (String name : new String[]{
                "신용분석사", "여신심사역", "자산관리사(FP)", "국제금융역",
                "외환전문역 1종", "외환전문역 2종", "은행텔러", "영업점컴플라이언스오피서"}) {
            assertTrue(out.stream().anyMatch(s -> s.certificateName().equals(name)), name + " 이 없다");
        }
        assertTrue(out.stream().noneMatch(s -> s.certificateName().contains("농협")),
                "우리 목록에 없는 위탁자격이 들어왔다");
        assertTrue(out.stream().allMatch(s -> s.sourceCode().startsWith("M0")));
    }

    /**
     * 로마숫자 Ⅰ·Ⅱ(U+2160)로 적힌 이름을 우리 이름("외환전문역 1종")에 붙여야 한다.
     * 안 그러면 같은 시험이 하나 더 생긴다.
     */
    @Test
    @DisplayName("연수원: 로마숫자 종목명을 우리 이름에 맞춘다")
    void kbi_maps_roman_numeral_names() throws IOException {
        List<CollectedSchedule> out = new KbiScheduleSource().parse(fixture("kbi_schedule.json"));

        CollectedSchedule first = find(out, "외환전문역 1종", 57);
        CollectedSchedule second = find(out, "외환전문역 2종", 57);
        assertNotNull(first, "외환전문역 1종 제57회가 없다");
        assertNotNull(second, "외환전문역 2종 제57회가 없다");
        assertEquals("M0420", first.sourceCode());
        assertEquals("M0421", second.sourceCode());
        assertEquals("2026-11-21", first.examStartDate().toString());
    }

    /**
     * <b>접수 기간이 아직 안 정해진 회차가 온다</b>(프라이빗뱅커 92회의 {@code "~"}).
     * 날짜를 지어내지 않고 비워 둔다 — 시험일만으로도 D-day 는 선다.
     */
    @Test
    @DisplayName("연수원: 접수기간이 비어 있으면 비운 채로 둔다")
    void kbi_leaves_empty_registration_empty() {
        String json = """
                {"ds":[{"N_QLFN":"은행텔러","D_YY":"2026","Q_SEQ":"58","D_OF_APPR":"09.19 (토)",
                "D_SUCC_ANNO":"10.02 (금)","D_INT_ACPT_DT":" ~ "}]}""";

        List<CollectedSchedule> out = new KbiScheduleSource().parse(json);

        assertEquals(1, out.size(), "접수기간이 없다고 회차를 버리면 안 된다");
        assertNull(out.get(0).regStartAt(), "없는 접수일을 지어냈다");
        assertNull(out.get(0).regEndAt());
        assertEquals("2026-09-19", out.get(0).examStartDate().toString());
    }

    /**
     * <b>해를 넘는 회차.</b> 12월에 접수해 이듬해 1월에 치르는 회차가 있는데, 응답에는 연도가
     * {@code D_YY} 하나뿐이다. 접수 월이 시험 월보다 뒤면 <b>접수는 전해</b>다.
     */
    @Test
    @DisplayName("연수원: 12월 접수 → 1월 시험도 제대로 읽는다")
    void kbi_handles_year_rollover() {
        String json = """
                {"ds":[{"N_QLFN":"은행텔러","D_YY":"2027","Q_SEQ":"59","D_OF_APPR":"01.16 (토)",
                "D_SUCC_ANNO":"01.29 (금)","D_INT_ACPT_DT":"12.01 (화)~12.08 (화)"}]}""";

        CollectedSchedule s = new KbiScheduleSource().parse(json).get(0);

        assertEquals("2027-01-16", s.examStartDate().toString());
        assertEquals("2026-12-01T10:00", s.regStartAt().toString(), "접수가 시험 이듬해로 갔다");
        assertEquals("2026-12-08T20:00", s.regEndAt().toString());
    }

    @Test
    @DisplayName("연수원: 응답이 비거나 깨져도 예외 없이 0건")
    void kbi_survives_garbage() {
        assertTrue(new KbiScheduleSource().parse("").isEmpty());
        assertTrue(new KbiScheduleSource().parse("<html>점검 중</html>").isEmpty());
        assertTrue(new KbiScheduleSource().parse("{\"ds\":[]}").isEmpty());
    }

    @Test
    @DisplayName("두 곳 다 접수가 시험보다 늦지 않다")
    void registration_precedes_exam() throws IOException {
        List<CollectedSchedule> all = new java.util.ArrayList<>();
        all.addAll(new KofiaScheduleSource().parse(fixture("kofia_schedule.json")));
        all.addAll(new KbiScheduleSource().parse(fixture("kbi_schedule.json")));

        assertFalse(all.isEmpty());
        all.stream()
                .filter(s -> s.regStartAt() != null && s.examStartDate() != null)
                .forEach(s -> assertFalse(s.examStartDate().isBefore(s.regStartAt().toLocalDate()),
                        s.certificateName() + " " + s.round() + "회 시험일이 접수 시작보다 앞이다"));
    }
}

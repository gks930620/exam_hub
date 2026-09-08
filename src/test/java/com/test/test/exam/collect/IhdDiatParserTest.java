package com.test.test.exam.collect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DIAT 디지털정보활용능력 — 연 14회차.
 *
 * <h3>연도가 바뀔 때만 적힌다</h3>
 * 표는 {@code '25.12.08.(월) ~ 12.17.(수)} 처럼 <b>연도가 바뀌는 자리에만</b> 두 자리 연도를 쓴다.
 * 나머지는 월·일뿐이다. 그래서 <b>12월에 접수해 이듬해 2월에 치는 회차</b>가 여럿이다 —
 * 앞 회차의 연도를 이어받고 접수·발표를 시험일 기준으로 앞뒤로 넘겨야 한다.
 * 못 넘기면 접수가 시험보다 10개월 늦은 값이 된다.
 */
class IhdDiatParserTest {

    private static List<CollectedSchedule> parsed() throws IOException {
        String html = new String(
                new ClassPathResource("fixtures/ihd_diat_schedule.html").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        return new IhdScheduleSource().parse(html);
    }

    private static CollectedSchedule round(List<CollectedSchedule> list, int round) {
        return list.stream().filter(s -> s.round() == round).findFirst().orElse(null);
    }

    @Test
    @DisplayName("회차·접수·시험일·발표일을 읽는다")
    void reads_a_round() throws IOException {
        List<CollectedSchedule> out = parsed();

        assertFalse(out.isEmpty(), "한 건도 못 뽑았다");
        CollectedSchedule s = round(out, 2608);
        assertNotNull(s, "제2608회가 없다");
        assertEquals("M0391", s.sourceCode());
        assertEquals("DIAT 디지털정보활용능력", s.certificateName());
        assertEquals(2026, s.year());
        assertEquals("2026-07-13T10:00", s.regStartAt().toString());
        assertEquals("2026-07-22T18:00", s.regEndAt().toString());
        assertEquals("2026-08-22", s.examStartDate().toString());
        assertEquals("2026-09-11", s.resultDate().toString());
    }

    /**
     * 첫 줄은 <b>12월에 접수해 이듬해 1월에 치는</b> 회차다. 접수 연도가 시험 연도와 다르다.
     */
    @Test
    @DisplayName("12월 접수 → 이듬해 시험을 제대로 읽는다")
    void handles_registration_in_the_previous_year() throws IOException {
        CollectedSchedule s = round(parsed(), 2601);

        assertNotNull(s, "제2601회가 없다");
        assertEquals("2026-01-24", s.examStartDate().toString());
        assertEquals("2025-12-08T10:00", s.regStartAt().toString(), "접수가 시험과 같은 해로 들어갔다");
        assertEquals("2025-12-17T18:00", s.regEndAt().toString());
    }

    /**
     * 연도 표시가 <b>아예 없는 줄</b>이 있다(제2622회). 앞 회차의 연도를 이어받아야 한다 —
     * 접수는 12월, 시험은 2월이다.
     */
    @Test
    @DisplayName("연도 표시 없는 줄은 앞 회차의 연도를 이어받는다")
    void inherits_the_year_when_the_row_omits_it() throws IOException {
        CollectedSchedule s = round(parsed(), 2622);

        assertNotNull(s, "제2622회가 없다");
        assertEquals("2026-02-08", s.examStartDate().toString());
        assertEquals("2025-12-15T10:00", s.regStartAt().toString());
    }

    /** 마지막 줄은 12월 시험에 <b>이듬해 1월 발표</b>다 — 발표가 시험보다 앞서면 안 된다. */
    @Test
    @DisplayName("12월 시험 → 이듬해 발표를 제대로 읽는다")
    void handles_a_result_in_the_next_year() throws IOException {
        CollectedSchedule s = round(parsed(), 2612);

        assertNotNull(s, "제2612회가 없다");
        assertEquals("2026-12-19", s.examStartDate().toString());
        assertEquals("2027-01-08", s.resultDate().toString());
    }

    @Test
    @DisplayName("표에 실린 회차를 다 읽는다")
    void reads_every_row() throws IOException {
        assertEquals(14, parsed().size(), "회차를 놓쳤다");
    }

    @Test
    @DisplayName("접수·시험·발표 순서가 지켜진다")
    void dates_are_in_order() throws IOException {
        parsed().forEach(s -> {
            assertFalse(s.examStartDate().isBefore(s.regStartAt().toLocalDate()),
                    s.round() + "회 시험일이 접수 시작보다 앞이다");
            assertFalse(s.resultDate().isBefore(s.examStartDate()),
                    s.round() + "회 발표일이 시험일보다 앞이다");
        });
    }

    @Test
    @DisplayName("빈 화면이면 예외 없이 0건")
    void empty_page_yields_nothing() {
        assertTrue(new IhdScheduleSource().parse("").isEmpty());
        assertTrue(new IhdScheduleSource().parse("<html>점검 중</html>").isEmpty());
    }
}

package com.test.test.exam.collect;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TOPIK(한국어능력시험) 메인 페이지 파싱.
 *
 * <p>일정이 표가 아니라 <b>인라인 JS 가 만드는 슬라이드</b>에 있다 — 회차 블록마다
 * 국내시험일·접수기간·성적발표가 들어 있고, 지필(제104회~)과 토픽 IBT·말하기 평가가 섞여 있다.
 * 우리 마스터에 있는 것은 지필 I·II 뿐이라 <b>"한국어능력시험 제N회" 블록만</b> 읽는다.
 *
 * <p>저장해 둔 실제 응답(2026-09-01)으로만 검증한다 — 테스트가 네트워크를 타지 않는다.
 */
class TopikParserTest {

    private static List<CollectedSchedule> parsed;

    @BeforeAll
    static void parseFixture() throws IOException {
        String html = new String(
                new ClassPathResource("fixtures/topik_main.html").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        parsed = new TopikScheduleSource().parse(html);
    }

    /** I·II 는 같은 날 시행이라 회차마다 두 건이다 — 지필 6회차 × 2. */
    @Test
    @DisplayName("지필 회차가 I·II 로 펼쳐진다")
    void expands_each_round_into_both_levels() {
        assertEquals(12, parsed.size(), "지필 6회차 × (I, II) = 12건이어야 한다: " + parsed.size());
        assertTrue(parsed.stream().anyMatch(s -> s.sourceCode().equals("M0355")), "TOPIK I 이 없다");
        assertTrue(parsed.stream().anyMatch(s -> s.sourceCode().equals("TOPIK-2")), "TOPIK II 가 없다");
    }

    @Test
    @DisplayName("접수 기간·시험일·발표일을 읽는다 (제109회)")
    void reads_dates() {
        CollectedSchedule s = parsed.stream()
                .filter(x -> x.round() == 109 && x.sourceCode().equals("M0355"))
                .findFirst().orElse(null);

        assertNotNull(s, "제109회가 없다");
        assertEquals(2026, s.year());
        assertEquals("2026-09-01T09:00", s.regStartAt().toString());
        assertEquals("2026-09-07T18:00", s.regEndAt().toString());
        assertEquals("2026-11-15", s.examStartDate().toString());
        assertEquals("2026-12-22", s.resultDate().toString());
    }

    /**
     * 토픽 IBT 제16회·말하기 평가 제10회 같은 별개 상품이 섞여 들어오면 안 된다 —
     * 마스터에 없는 시험이라 이름 매칭으로 <b>새 시험을 만들어 버린다.</b>
     * 지필은 이미 100회를 넘겼으므로 회차가 그 밑이면 다른 상품이 샌 것이다.
     */
    @Test
    @DisplayName("토픽 IBT·말하기 평가는 넣지 않는다")
    void skips_ibt_and_speaking() {
        assertTrue(parsed.stream().allMatch(s -> s.round() >= 100),
                "지필이 아닌 상품(IBT·말하기)이 섞였다: "
                        + parsed.stream().filter(s -> s.round() < 100).map(CollectedSchedule::round).toList());
    }

    /** 제104회는 접수가 2025년 12월, 시험이 2026년 1월 — 해를 넘는 회차. */
    @Test
    @DisplayName("접수와 시험이 해를 넘어도 제 연도를 쓴다")
    void handles_year_crossing() {
        CollectedSchedule s = parsed.stream()
                .filter(x -> x.round() == 104 && x.sourceCode().equals("M0355"))
                .findFirst().orElse(null);

        assertNotNull(s, "제104회가 없다");
        assertEquals("2025-12-09T09:00", s.regStartAt().toString(), "접수 시작 연도가 틀렸다");
        assertEquals("2026-01-11", s.examStartDate().toString());
        assertEquals(2026, s.year(), "연도는 시험일 기준이다");
    }

    @Test
    @DisplayName("접수가 시험보다 앞선다")
    void registration_precedes_exam() {
        parsed.forEach(s -> assertTrue(s.regEndAt().toLocalDate().isBefore(s.examStartDate()),
                "제" + s.round() + "회: 접수 마감이 시험일 뒤다"));
    }

    /** 쿠키 없이 받으면 1.6KB 인트로 셸이 온다 — 그걸 일정으로 착각하면 안 된다. */
    @Test
    @DisplayName("인트로 셸 페이지에서는 0건")
    void intro_shell_yields_nothing() {
        assertTrue(new TopikScheduleSource().parse("<html><script>location.href='/TWMAIN/TWMAIN0010.do';</script></html>").isEmpty());
    }
}

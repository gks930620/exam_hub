package com.test.test.exam.collect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 어학시험 일정 파서 테스트.
 *
 * <p>실제 시행처에서 받아 둔 HTML 을 픽스처로 쓴다 — <b>테스트가 네트워크를 타지 않는다.</b>
 * 사이트가 개편되면 이 테스트가 아니라 실 수집이 조용히 실패하므로,
 * 픽스처를 갱신해 파서를 고치는 게 유지보수 흐름이다.
 *
 * <p>픽스처 수집 시점: 2026-08-06.
 *
 * <p>TOEIC 계열은 여기 없다 — YBM 여섯 페이지를 한 파서가 맡게 되면서 {@link YbmParserTest} 로 옮겼다(2026-09-08).
 */
class LanguageExamParserTest {

    private String fixture(String name) throws IOException {
        return new String(new ClassPathResource("fixtures/" + name).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    // ===== TEPS =====

    @Test
    @DisplayName("TEPS — 회차 번호와 접수기간·시험일·발표일을 뽑는다")
    void teps_parses_schedule_rows() throws IOException {
        List<CollectedSchedule> out = new TepsScheduleSource().parse(fixture("teps_home.html"));

        assertThat(out).isNotEmpty();

        CollectedSchedule r408 = out.stream()
                .filter(s -> s.round() == 408)
                .findFirst().orElseThrow(() -> new AssertionError("408회를 못 찾음"));

        assertThat(r408.certificateName()).isEqualTo("TEPS 텝스");
        assertThat(r408.regStartAt().toLocalDate().toString()).isEqualTo("2026-07-13");
        assertThat(r408.regEndAt().toLocalDate().toString()).isEqualTo("2026-08-09");
        assertThat(r408.examStartDate().toString()).isEqualTo("2026-08-29");
        assertThat(r408.resultDate().toString()).isEqualTo("2026-09-07");
    }

    /** 추가접수 행은 같은 회차의 중복이라 본접수만 남겨야 한다. */
    @Test
    @DisplayName("TEPS — 같은 회차가 중복으로 나오지 않는다")
    void teps_deduplicates_rounds() throws IOException {
        List<CollectedSchedule> out = new TepsScheduleSource().parse(fixture("teps_home.html"));
        long distinct = out.stream().map(CollectedSchedule::round).distinct().count();
        assertThat(out).hasSize((int) distinct);
    }

    // ===== JLPT =====

    @Test
    @DisplayName("JLPT — 연 2회 일정에서 접수기간과 시험일을 뽑는다")
    void jlpt_parses_schedule() throws IOException {
        List<CollectedSchedule> out = new JlptScheduleSource().parse(fixture("jlpt_main.html"));

        assertThat(out).isNotEmpty();
        CollectedSchedule s = out.get(0);

        assertThat(s.certificateName()).isEqualTo("JLPT 일본어능력시험");
        assertThat(s.year()).isEqualTo(2026);
        assertThat(s.round()).isEqualTo(2);
        assertThat(s.regStartAt().toLocalDate().toString()).isEqualTo("2026-09-01");
        assertThat(s.regEndAt().toLocalDate().toString()).isEqualTo("2026-09-20");
        assertThat(s.examStartDate().toString()).isEqualTo("2026-12-06");
    }

    // ===== 공통 계약 =====

    @Test
    @DisplayName("모든 파서 — 빈 HTML 이면 예외 없이 빈 목록")
    void parsers_return_empty_on_garbage() {
        assertThat(new TepsScheduleSource().parse("<html><body>없음</body></html>")).isEmpty();
        assertThat(new JlptScheduleSource().parse("<html></html>")).isEmpty();
    }
}

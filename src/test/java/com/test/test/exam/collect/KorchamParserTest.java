package com.test.test.exam.collect;

import com.test.test.exam.domain.ExamType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 대한상공회의소 종목별 일정표 파싱.
 *
 * <p>저장해 둔 실제 응답(2026-08-10)으로만 검증한다 — 테스트가 네트워크를 타지 않는다.
 *
 * <p>이 시험들이 중요한 이유: <b>국가기술자격인데 시행처가 큐넷이 아니라</b> 큐넷 API 로는
 * 영원히 안 들어온다. 유통관리사는 취준 수요도 크다.
 */
class KorchamParserTest {

    private final KorchamScheduleSource source = new KorchamScheduleSource();

    private static KorchamScheduleSource.Target target(String name) {
        return KorchamScheduleSource.TARGETS.stream()
                .filter(t -> t.name().equals(name))
                .findFirst().orElseThrow();
    }

    private static String fixture(String file) throws IOException {
        return new String(new ClassPathResource("fixtures/" + file).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private List<CollectedSchedule> parse(String file, String targetName) throws IOException {
        return source.parse(fixture(file), target(targetName));
    }

    /**
     * 등급 칸에 "2, 3" 처럼 여러 급수가 묶여 온다. 우리 마스터는 급수별로 나뉘어 있으니
     * <b>한 행이 급수 수만큼 펼쳐져야</b> 한다. 안 그러면 2급만 있고 3급은 영영 일정이 안 붙는다.
     */
    @Test
    @DisplayName("한 회차가 급수별로 펼쳐진다")
    void expands_one_round_into_grades() throws IOException {
        List<CollectedSchedule> rows = parse("korcham_yutong.html", "유통관리사");

        assertFalse(rows.isEmpty(), "유통관리사 일정을 못 뽑았다");
        Set<String> names = rows.stream()
                .map(CollectedSchedule::certificateName).collect(Collectors.toSet());
        assertTrue(names.contains("유통관리사 2급"), "2급이 없다: " + names);
        assertTrue(names.contains("유통관리사 3급"), "3급이 없다: " + names);
    }

    /**
     * 종목코드가 <b>이미 그 시험에 붙어 있는 코드</b>와 달라지면 같은 시험이 하나 더 생긴다.
     * 유통관리사 2급은 비큐넷 시드가 먼저 {@code KORCHAM-DIST2} 로 잡고 있어 그걸 따라야 한다.
     */
    @Test
    @DisplayName("종목코드가 기존 시드의 것과 같다")
    void uses_existing_source_codes() throws IOException {
        CollectedSchedule s = parse("korcham_yutong.html", "유통관리사").stream()
                .filter(x -> "유통관리사 2급".equals(x.certificateName()))
                .findFirst().orElse(null);

        assertNotNull(s);
        assertEquals("KORCHAM-DIST2", s.sourceCode());
    }

    /** 회차마다 코드가 달라지면 회차 수만큼 같은 시험이 생긴다(어학 스크래퍼에서 실제로 겪었다). */
    @Test
    @DisplayName("같은 종목은 회차가 달라도 코드가 같다")
    void source_code_does_not_vary_by_round() throws IOException {
        List<CollectedSchedule> rows = parse("korcham_yutong.html", "유통관리사").stream()
                .filter(x -> "유통관리사 2급".equals(x.certificateName()))
                .toList();

        assertTrue(rows.size() > 1, "회차가 하나뿐이라 검증이 안 된다");
        assertEquals(1, rows.stream().map(CollectedSchedule::sourceCode).distinct().count(),
                "회차마다 종목코드가 다르다");
    }

    @Test
    @DisplayName("접수 기간·시험일·발표일을 읽는다")
    void reads_dates() throws IOException {
        CollectedSchedule s = parse("korcham_yutong.html", "유통관리사").stream()
                .filter(x -> x.round() == 1 && "유통관리사 2급".equals(x.certificateName()))
                .findFirst().orElse(null);

        assertNotNull(s, "1회차를 못 찾았다");
        assertEquals(2026, s.year());
        assertNotNull(s.regStartAt());
        assertNotNull(s.regEndAt());
        assertNotNull(s.examStartDate());
        assertTrue(s.regStartAt().isBefore(s.regEndAt()), "접수 기간이 거꾸로다");
        assertTrue(s.regEndAt().toLocalDate().isBefore(s.examStartDate()), "접수가 시험일 뒤에 끝난다");
    }

    /** 한글속기는 실기만 있다 — 구분을 잘못 읽으면 필기로 들어간다. */
    @Test
    @DisplayName("구분(필기/실기)을 표에서 읽는다")
    void reads_exam_type() throws IOException {
        List<CollectedSchedule> rows = parse("korcham_sokgi.html", "한글속기");

        assertFalse(rows.isEmpty(), "한글속기 일정을 못 뽑았다");
        assertTrue(rows.stream().allMatch(s -> s.examType() == ExamType.PRACTICAL),
                "한글속기는 실기만 있는데 필기가 섞였다");
    }

    /**
     * 상시시험 종목(컴활 등)은 "시험일정이 없습니다" 한 줄만 온다.
     * 그걸 일정으로 착각해 넣으면 <b>날짜 없는 회차</b>가 생긴다.
     */
    @Test
    @DisplayName("'시험일정이 없습니다' 는 일정이 아니다")
    void empty_notice_is_not_a_schedule() {
        String html = "<table><tr><th>종목</th><th>회별</th><th>구분</th><th>등급</th>"
                + "<th>인터넷접수</th><th>시험일자</th><th>발표일자</th></tr>"
                + "<tr><td colspan='7'>시험일정이 없습니다.</td></tr></table>";

        assertTrue(source.parse(html, target("유통관리사")).isEmpty());
    }

    @Test
    @DisplayName("표가 없으면 예외 없이 0건")
    void no_table_yields_nothing() {
        assertTrue(source.parse("<html><body>점검 중입니다</body></html>", target("유통관리사")).isEmpty());
    }
}

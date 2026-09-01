package com.test.test.exam.collect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 국시원(보건의료인국가시험원) 직종 페이지 파싱 — <b>일정 없음 190 의 최대 덩어리(16종)</b>.
 *
 * <p>직종마다 페이지가 따로 있고, 표 하나에 응시원서 접수·시험시행·최종합격자 발표가 들어 있다.
 * 저장해 둔 실제 응답(2026-09-01)으로만 검증한다 — 테스트가 네트워크를 타지 않는다.
 */
class KuksiwonParserTest {

    private final KuksiwonScheduleSource source = new KuksiwonScheduleSource();

    private static String fixture(String file) throws IOException {
        return new String(new ClassPathResource("fixtures/" + file).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private static KuksiwonScheduleSource.Target target(String sourceCode) {
        return KuksiwonScheduleSource.TARGETS.stream()
                .flatMap(t -> t.exams().stream().anyMatch(e -> e.sourceCode().equals(sourceCode))
                        ? java.util.stream.Stream.of(t) : java.util.stream.Stream.empty())
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("접수 기간·시험일·발표일을 읽는다 (물리치료사)")
    void reads_dates() throws IOException {
        List<CollectedSchedule> rows = source.parse(fixture("kuksiwon_pt.html"), target("M0306"));

        assertEquals(1, rows.size(), "물리치료사는 연 1회 한 건이어야 한다");
        CollectedSchedule s = rows.get(0);
        assertEquals("M0306", s.sourceCode(), "기존 마스터의 코드와 다르면 같은 시험이 하나 더 생긴다");
        assertEquals(2026, s.year());
        // 접수시간은 페이지 명시대로 시작일 09:00 ~ 마감일 18:00
        assertEquals("2026-09-02T09:00", s.regStartAt().toString());
        assertEquals("2026-09-09T18:00", s.regEndAt().toString());
        assertEquals("2026-12-13", s.examStartDate().toString());
        assertEquals("2026-12-30", s.resultDate().toString());
    }

    /** 응급구조사는 1·2급이 한 페이지다 — 둘 다 나와야 2급이 영영 비지 않는다. */
    @Test
    @DisplayName("한 페이지에 두 급수가 있으면 둘 다 만든다 (응급구조사)")
    void expands_grades_sharing_one_page() throws IOException {
        List<CollectedSchedule> rows = source.parse(fixture("kuksiwon_emt.html"), target("M0310"));

        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(r -> r.sourceCode().equals("M0310")), "1급이 없다");
        assertTrue(rows.stream().anyMatch(r -> r.sourceCode().equals("M0311")), "2급이 없다");
        // 같은 페이지 = 같은 일정
        assertEquals(1, rows.stream().map(r -> String.valueOf(r.regStartAt())).distinct().count());
    }

    /**
     * 요양보호사는 <b>상시접수</b>다 — 날짜가 없는데 억지로 뽑으면 엉뚱한 값이 들어간다.
     * (실제 페이지가 "시험 개시일로부터 시험일 7일전까지" 라고만 적는다)
     */
    @Test
    @DisplayName("상시접수 페이지에서는 아무것도 만들지 않는다")
    void yields_nothing_for_rolling_admission() throws IOException {
        List<CollectedSchedule> rows = source.parse(fixture("kuksiwon_care.html"),
                new KuksiwonScheduleSource.Target("c_2027", "35",
                        List.of(new KuksiwonScheduleSource.Exam("요양보호사", "M0319"))));

        assertTrue(rows.isEmpty(), "상시접수인데 일정이 만들어졌다: " + rows);
    }

    @Test
    @DisplayName("접수가 시험보다 앞선다")
    void registration_precedes_exam() throws IOException {
        for (String f : List.of("kuksiwon_pt.html", "kuksiwon_emt.html", "kuksiwon_nurse.html")) {
            for (CollectedSchedule s : source.parse(fixture(f),
                    f.contains("emt") ? target("M0310") : f.contains("nurse") ? target("M0303") : target("M0306"))) {
                assertTrue(s.regEndAt().toLocalDate().isBefore(s.examStartDate()),
                        f + ": 접수 마감이 시험일 뒤다");
            }
        }
    }

    @Test
    @DisplayName("표가 없으면 예외 없이 0건")
    void no_table_yields_nothing() {
        assertTrue(source.parse("<html>점검 중</html>", target("M0306")).isEmpty());
    }

    /** 대상 16종이 전부 걸려 있는지 — 하나 빠지면 그 시험만 영영 안 들어온다. */
    @Test
    @DisplayName("우리 마스터의 국시원 16종이 전부 대상에 있다")
    void covers_all_sixteen_exams() {
        long count = KuksiwonScheduleSource.TARGETS.stream()
                .mapToLong(t -> t.exams().size()).sum();
        assertEquals(16, count, "요양보호사(상시)를 뺀 16종이어야 한다");
        assertFalse(KuksiwonScheduleSource.TARGETS.stream()
                        .flatMap(t -> t.exams().stream())
                        .anyMatch(e -> e.sourceCode().equals("M0319")),
                "요양보호사는 상시접수라 대상이 아니다");
    }
}

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
 * 매경TEST 1종 · 보험연수원 보험심사역 2종 · HSK 1종.
 *
 * <p>남은 소규모 시행처들이다. 함정이 각각 다르다.
 * <ul>
 *   <li><b>매경TEST</b> — 표는 깔끔한데 <b>보이지 않는 문자</b>가 날짜 사이에 끼어 있다
 *       ({@code 2025.12.22&#8203;(월)} — 폭 없는 공백). 숫자만 보고 읽어야 한다.</li>
 *   <li><b>보험연수원</b> — 표가 <b>가로로 눕혀</b> 있고(회차가 열), 두 번째 날짜는 연도를 생략한다
 *       ({@code 2026. 3. 3(화) 10:00 ~ 3. 12(목) 18:00}).</li>
 *   <li><b>HSK</b> — 시험일자에 <b>연도가 없다</b>({@code 1월 10일(토)}). 연도는 제목에만 있고,
 *       접수는 전해 11월에 열리는 회차가 있어 앞으로 넘겨야 한다.</li>
 * </ul>
 */
class MkInsuranceHskParserTest {

    private static String fixture(String name) throws IOException {
        return new String(new ClassPathResource("fixtures/" + name).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private static CollectedSchedule round(List<CollectedSchedule> list, int round) {
        return list.stream().filter(s -> s.round() == round).findFirst().orElse(null);
    }

    // ===== 매경TEST =====

    @Test
    @DisplayName("매경TEST: 회차·시험일·접수기간·발표일을 읽는다")
    void mktest_reads_a_round() throws IOException {
        List<CollectedSchedule> out = new MkTestScheduleSource().parse(fixture("mktest_schedule.html"));

        assertFalse(out.isEmpty(), "한 건도 못 뽑았다");
        CollectedSchedule s = round(out, 116);
        assertNotNull(s, "제116회가 없다");
        assertEquals("M0441", s.sourceCode());
        assertEquals("매경TEST 경제경영이해력시험", s.certificateName());
        assertEquals(2026, s.year());
        assertEquals("2026-09-05", s.examStartDate().toString());
        assertEquals("2026-07-13T10:00", s.regStartAt().toString());
        assertEquals("2026-08-24T18:00", s.regEndAt().toString());
        assertEquals("2026-09-11", s.resultDate().toString());
    }

    /**
     * <b>폭 없는 공백({@code &#8203;})이 날짜 뒤에 끼어 있다.</b> 눈에는 안 보이지만
     * 정규식이 {@code (월)} 을 못 찾으면 그 회차를 통째로 놓친다.
     */
    @Test
    @DisplayName("매경TEST: 보이지 않는 문자가 끼어도 읽는다")
    void mktest_survives_zero_width_spaces() throws IOException {
        String html = fixture("mktest_schedule.html");
        assertTrue(html.contains("&#8203;"), "픽스처에 폭 없는 공백이 없다 — 이 테스트가 아무것도 검증하지 않는다");

        List<CollectedSchedule> out = new MkTestScheduleSource().parse(html);

        assertTrue(out.size() >= 6, "회차를 놓쳤다: " + out.size());
        assertTrue(out.stream().allMatch(s -> s.regEndAt() != null), "마감일을 못 읽은 회차가 있다");
    }

    /** 12월에 접수해 이듬해 1월에 치는 회차가 있다 — 접수 연도가 시험 연도와 다르다. */
    @Test
    @DisplayName("매경TEST: 전해 12월 접수도 제대로 읽는다")
    void mktest_handles_registration_in_the_previous_year() throws IOException {
        CollectedSchedule s = round(new MkTestScheduleSource().parse(fixture("mktest_schedule.html")), 111);

        assertNotNull(s, "제111회가 없다");
        assertEquals("2026-01-03", s.examStartDate().toString());
        assertEquals("2025-12-01T10:00", s.regStartAt().toString());
    }

    // ===== 보험연수원 =====

    /** 표가 눕혀 있다 — 회차가 열, 항목이 행이다. KICPA 와 같은 모양. */
    @Test
    @DisplayName("보험연수원: 가로로 눕힌 표를 회차별로 세운다")
    void insurance_reads_the_transposed_table() throws IOException {
        List<CollectedSchedule> out =
                new InsuranceScheduleSource().parse(fixture("insurance_aiu_schedule.html"));

        assertFalse(out.isEmpty(), "한 건도 못 뽑았다");
        CollectedSchedule s = out.stream()
                .filter(x -> x.certificateName().equals("보험심사역(AIU)") && x.round() == 33)
                .findFirst().orElse(null);
        assertNotNull(s, "제33회가 없다");
        assertEquals("M0426", s.sourceCode());
        assertEquals(2026, s.year());
        assertEquals("2026-09-12", s.examStartDate().toString());
        assertEquals("2026-08-11T10:00", s.regStartAt().toString());
        assertEquals("2026-10-01", s.resultDate().toString());
    }

    /**
     * 접수 칸의 <b>마감 날짜는 연도를 생략한다</b>({@code 2026. 3. 3(화) 10:00 ~ 3. 12(목) 18:00}).
     * 시작의 연도를 이어받아야 한다.
     */
    @Test
    @DisplayName("보험연수원: 마감일의 생략된 연도를 이어받는다")
    void insurance_fills_in_the_omitted_year() throws IOException {
        List<CollectedSchedule> out =
                new InsuranceScheduleSource().parse(fixture("insurance_aiu_schedule.html"));

        CollectedSchedule s = out.stream()
                .filter(x -> x.certificateName().equals("보험심사역(AIU)") && x.round() == 32)
                .findFirst().orElse(null);
        assertNotNull(s, "제32회가 없다");
        assertEquals("2026-03-03T10:00", s.regStartAt().toString());
        assertEquals("2026-03-12T18:00", s.regEndAt().toString(), "마감의 연도를 못 이어받았다");
    }

    /** 개인(AIU)·기업(CIU) 부문이 <b>같은 날 같이</b> 치러진다 — 한 회차가 둘에 붙는다. */
    @Test
    @DisplayName("보험연수원: 한 회차가 AIU·CIU 둘에 붙는다")
    void insurance_round_applies_to_both_parts() throws IOException {
        List<CollectedSchedule> out =
                new InsuranceScheduleSource().parse(fixture("insurance_aiu_schedule.html"));

        for (String name : new String[]{"보험심사역(AIU)", "보험심사역(CIU)"}) {
            assertNotNull(out.stream().filter(s -> s.certificateName().equals(name)).findFirst().orElse(null),
                    name + " 이 없다");
        }
        assertEquals(4, out.size(), "2회차 × 2부문이어야 한다");
    }

    // ===== HSK =====

    /** 시험일자에 연도가 없다 — 제목의 "2026년 시험일정"에서 가져와야 한다. */
    @Test
    @DisplayName("HSK: 제목의 연도를 시험일에 붙인다")
    void hsk_takes_the_year_from_the_heading() throws IOException {
        List<CollectedSchedule> out = new HskScheduleSource().parse(fixture("hsk_schedule.html"));

        assertFalse(out.isEmpty(), "한 건도 못 뽑았다");
        assertTrue(out.stream().allMatch(s -> s.year() == 2026), "연도가 2026 이 아니다");
        assertEquals("M0341", out.get(0).sourceCode());
        assertEquals("HSK 중국어능력시험(필기)", out.get(0).certificateName());
    }

    /**
     * <b>1월 시험은 전해 11월에 접수한다.</b> 접수 칸에는 연도가 적혀 있지만
     * 시험일에는 없어서, 둘을 따로 읽어 붙여야 한다.
     */
    @Test
    @DisplayName("HSK: 1월 시험의 전해 접수를 제대로 읽는다")
    void hsk_handles_registration_in_the_previous_year() throws IOException {
        CollectedSchedule s = new HskScheduleSource().parse(fixture("hsk_schedule.html")).stream()
                .filter(x -> "2026-01-10".equals(String.valueOf(x.examStartDate())))
                .findFirst().orElse(null);

        assertNotNull(s, "1월 10일 회차가 없다");
        assertEquals("2025-11-26T10:00", s.regStartAt().toString());
        assertEquals("2025-12-31T18:00", s.regEndAt().toString());
    }

    /** 회차 번호가 없는 시험이다 — 지어내지 않고 시험일을 내부 키로 쓴다. */
    @Test
    @DisplayName("HSK: 없는 회차 번호를 지어내지 않는다")
    void hsk_does_not_invent_a_round_number() throws IOException {
        List<CollectedSchedule> out = new HskScheduleSource().parse(fixture("hsk_schedule.html"));

        assertTrue(out.stream().allMatch(s -> s.round() >= 1_000_000),
                "회차 자리에 시행처가 안 준 작은 숫자가 들어갔다 — 화면에 '1회'로 뜬다");
    }

    // ===== 공통 계약 =====

    @Test
    @DisplayName("셋 다 접수가 시험보다 늦지 않다")
    void registration_precedes_exam() throws IOException {
        List<CollectedSchedule> all = new java.util.ArrayList<>();
        all.addAll(new MkTestScheduleSource().parse(fixture("mktest_schedule.html")));
        all.addAll(new InsuranceScheduleSource().parse(fixture("insurance_aiu_schedule.html")));
        all.addAll(new HskScheduleSource().parse(fixture("hsk_schedule.html")));

        assertFalse(all.isEmpty());
        all.stream()
                .filter(s -> s.regStartAt() != null && s.examStartDate() != null)
                .forEach(s -> assertFalse(s.examStartDate().isBefore(s.regStartAt().toLocalDate()),
                        s.certificateName() + " " + s.round() + " 시험일이 접수 시작보다 앞이다"));
    }

    @Test
    @DisplayName("셋 다 빈 화면이면 예외 없이 0건")
    void empty_page_yields_nothing() {
        for (String html : new String[]{"", "<html>점검 중</html>", "<html><table></table></html>"}) {
            assertTrue(new MkTestScheduleSource().parse(html).isEmpty(), "매경: " + html);
            assertTrue(new InsuranceScheduleSource().parse(html).isEmpty(), "보험연수원: " + html);
            assertTrue(new HskScheduleSource().parse(html).isEmpty(), "HSK: " + html);
        }
    }
}

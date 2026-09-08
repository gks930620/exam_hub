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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 한국어문회 4종 · 한국공인회계사회(AT) 4종 · 한국외대 FLEX 7종.
 *
 * <p>셋 다 <b>표가 아니라 안내 문구</b>에 일정이 있다. 시행처마다 함정이 다르다.
 * <ul>
 *   <li><b>어문회</b> — 정기와 <b>수시</b> 시험이 한 화면에 섞여 있다. 수시는 급수가 제한돼
 *       우리 4개 급수에 그대로 붙일 수 없다 → <b>정기만</b> 담는다.</li>
 *   <li><b>공인회계사회</b> — 표가 <b>가로로 눕혀</b> 있다. 회차가 칸(열)이고 원서접수·시험일자가 줄(행)이다.
 *       게다가 표 안에 연도가 없어 <b>제목에서</b> 가져와야 한다.</li>
 *   <li><b>FLEX</b> — 접수 기간에 연도가 없다({@code 접수 : 4.30(목) ~ 5.6(수)}). 시험 연도를 붙인다.</li>
 * </ul>
 */
class HanjaFlexKicpaParserTest {

    private static String fixture(String name) throws IOException {
        return new String(new ClassPathResource("fixtures/" + name).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private static CollectedSchedule find(List<CollectedSchedule> list, String name, int round) {
        return list.stream()
                .filter(s -> s.certificateName().equals(name) && s.round() == round)
                .findFirst().orElse(null);
    }

    // ===== 한국어문회 =====

    @Test
    @DisplayName("어문회: 회차·접수·시험일·발표일을 읽는다")
    void hanja_reads_a_round() throws IOException {
        List<CollectedSchedule> out = new HanjaScheduleSource().parse(fixture("hanja_schedule.html"));

        assertFalse(out.isEmpty(), "한 건도 못 뽑았다");
        CollectedSchedule s = find(out, "한자능력검정시험(2급)", 115);
        assertNotNull(s, "제115회가 없다");
        assertEquals("M0452", s.sourceCode());
        assertEquals(2026, s.year());
        assertEquals("2026-10-19T10:00", s.regStartAt().toString());
        assertEquals("2026-10-23T18:00", s.regEndAt().toString());
        assertEquals("2026-11-21", s.examStartDate().toString());
        assertEquals("2026-12-18", s.resultDate().toString());
        assertEquals(ExamType.WRITTEN, s.examType());
    }

    /** 한 회차가 급수 넷에 똑같이 붙는다 — 같은 날 같은 자리에서 급수만 골라 친다. */
    @Test
    @DisplayName("어문회: 한 회차가 급수 넷에 붙는다")
    void hanja_round_applies_to_four_grades() throws IOException {
        List<CollectedSchedule> out = new HanjaScheduleSource().parse(fixture("hanja_schedule.html"));

        for (String name : new String[]{
                "한자능력검정시험(1급)", "한자능력검정시험(2급)",
                "한자능력검정시험(3급)", "한자능력검정시험(4급)"}) {
            assertNotNull(find(out, name, 114), name + " 제114회가 없다");
        }
    }

    /**
     * <b>수시 시험은 담지 않는다.</b> 정기와 회차 번호 체계가 다르고(제17·18회) 응시 가능한 급수도
     * 제한된다. 우리 급수 넷에 그대로 붙이면 못 치는 급수까지 "접수하세요"가 된다.
     */
    @Test
    @DisplayName("어문회: 수시 시험은 담지 않는다")
    void hanja_skips_the_irregular_sitting() throws IOException {
        List<CollectedSchedule> out = new HanjaScheduleSource().parse(fixture("hanja_schedule.html"));

        assertTrue(out.stream().noneMatch(s -> s.round() < 100),
                "수시 회차(제17·18회)가 섞여 들어왔다 — 정기 제114회대만 있어야 한다");
    }

    // ===== 한국공인회계사회 (AT) =====

    /** 표가 눕혀 있다 — 회차가 열, 항목이 행이다. 그대로 읽으면 한 회차도 못 만든다. */
    @Test
    @DisplayName("공인회계사회: 가로로 눕힌 표를 회차별로 세운다")
    void kicpa_reads_the_transposed_table() throws IOException {
        List<CollectedSchedule> out = new KicpaScheduleSource().parse(fixture("kicpa_schedule.html"));

        assertFalse(out.isEmpty(), "한 건도 못 뽑았다");
        CollectedSchedule s = find(out, "FAT 회계실무 1급", 93);
        assertNotNull(s, "제93회가 없다");
        assertEquals("M0433", s.sourceCode());
        assertEquals(2026, s.year(), "연도는 표 안에 없다 — 제목에서 가져와야 한다");
        assertEquals("2026-10-01T10:00", s.regStartAt().toString());
        assertEquals("2026-10-08T18:00", s.regEndAt().toString());
        assertEquals("2026-10-17", s.examStartDate().toString());
        assertEquals("2026-10-23", s.resultDate().toString());
    }

    /** 한 회차에 FAT 1·2급, TAT 1·2급 넷을 같이 친다. */
    @Test
    @DisplayName("공인회계사회: 한 회차가 네 종목에 붙는다")
    void kicpa_round_applies_to_four_exams() throws IOException {
        List<CollectedSchedule> out = new KicpaScheduleSource().parse(fixture("kicpa_schedule.html"));

        for (String name : new String[]{
                "FAT 회계실무 1급", "FAT 회계실무 2급", "TAT 세무실무 1급", "TAT 세무실무 2급"}) {
            assertNotNull(find(out, name, 95), name + " 제95회가 없다");
        }
    }

    /** 12월 회차까지 다 읽는다 — 지난 회차만 읽으면 다음 접수를 못 알린다. */
    @Test
    @DisplayName("공인회계사회: 여덟 회차를 다 읽는다")
    void kicpa_reads_every_round() throws IOException {
        List<CollectedSchedule> out = new KicpaScheduleSource().parse(fixture("kicpa_schedule.html"));

        assertEquals(8, out.stream().map(CollectedSchedule::round).distinct().count(),
                "회차 수가 다르다 — 표의 칸을 놓쳤나?");
        assertEquals(32, out.size(), "8회차 × 4종목이어야 한다");
    }

    /**
     * <b>지난해 일정표가 주석으로 남아 있다.</b> 이 화면에는 제45~50회 표가
     * {@code <!-- -->} 안에 그대로 있는데, 걷어내지 않으면 그쪽이 살아 있는 제88~95회를 덮어써
     * <b>지난해 접수일이 올해 일정으로</b> 나간다. 실제로 그렇게 들어갔다(2026-09-08).
     *
     * <p>사람 눈에는 안 보이는 값이라 화면을 봐도 못 잡는다 — 여기서 잡아야 한다.
     */
    @Test
    @DisplayName("공인회계사회: 주석에 남은 지난해 표에 속지 않는다")
    void kicpa_ignores_the_commented_out_old_table() throws IOException {
        String html = fixture("kicpa_schedule.html");
        assertTrue(html.contains("제45회"), "픽스처에 주석 처리된 옛 표가 없다 — 이 테스트가 아무것도 검증하지 않는다");

        List<CollectedSchedule> out = new KicpaScheduleSource().parse(html);

        assertTrue(out.stream().allMatch(s -> s.round() >= 88),
                "주석 속 옛 회차(제45~50회)가 살아 있는 회차를 덮었다");
    }

    // ===== FLEX =====

    @Test
    @DisplayName("FLEX: 연도 없는 접수기간에 시험 연도를 붙인다")
    void flex_fills_in_the_missing_year() throws IOException {
        List<CollectedSchedule> out = new FlexScheduleSource().parse(fixture("flex_schedule.html"));

        assertFalse(out.isEmpty(), "한 건도 못 뽑았다");
        CollectedSchedule s = find(out, "FLEX 영어", 2);
        assertNotNull(s, "2회차가 없다");
        assertEquals("M0335", s.sourceCode());
        assertEquals(2026, s.year());
        assertEquals("2026-11-08", s.examStartDate().toString());
        assertEquals("2026-10-08T10:00", s.regStartAt().toString());
        assertEquals("2026-10-14T18:00", s.regEndAt().toString());
    }

    /** 한 회차에 7개 언어를 같이 친다 — 응시자가 언어를 고를 뿐 날짜는 하나다. */
    @Test
    @DisplayName("FLEX: 한 회차가 7개 언어에 붙는다")
    void flex_round_applies_to_seven_languages() throws IOException {
        List<CollectedSchedule> out = new FlexScheduleSource().parse(fixture("flex_schedule.html"));

        for (String name : new String[]{
                "FLEX 영어", "FLEX 일본어", "FLEX 중국어", "FLEX 스페인어",
                "FLEX 프랑스어", "FLEX 독일어", "FLEX 러시아어"}) {
            assertNotNull(find(out, name, 1), name + " 1회차가 없다");
        }
        assertEquals(14, out.size(), "2회차 × 7개 언어여야 한다");
    }

    // ===== 공통 계약 =====

    @Test
    @DisplayName("셋 다 접수가 시험보다 늦지 않다")
    void registration_precedes_exam() throws IOException {
        List<CollectedSchedule> all = new java.util.ArrayList<>();
        all.addAll(new HanjaScheduleSource().parse(fixture("hanja_schedule.html")));
        all.addAll(new KicpaScheduleSource().parse(fixture("kicpa_schedule.html")));
        all.addAll(new FlexScheduleSource().parse(fixture("flex_schedule.html")));

        assertFalse(all.isEmpty());
        all.stream()
                .filter(s -> s.regStartAt() != null && s.examStartDate() != null)
                .forEach(s -> assertFalse(s.examStartDate().isBefore(s.regStartAt().toLocalDate()),
                        s.certificateName() + " " + s.round() + "회 시험일이 접수 시작보다 앞이다"));
    }

    @Test
    @DisplayName("셋 다 빈 화면이면 예외 없이 0건")
    void empty_page_yields_nothing() {
        assertTrue(new HanjaScheduleSource().parse("<html>점검 중</html>").isEmpty());
        assertTrue(new KicpaScheduleSource().parse("<html>점검 중</html>").isEmpty());
        assertTrue(new FlexScheduleSource().parse("<html>점검 중</html>").isEmpty());
        assertTrue(new HanjaScheduleSource().parse("").isEmpty());
        assertTrue(new KicpaScheduleSource().parse("").isEmpty());
        assertTrue(new FlexScheduleSource().parse("").isEmpty());
    }
}

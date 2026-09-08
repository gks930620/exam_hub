package com.test.test.exam.collect;

import com.test.test.exam.common.TimeUtil;
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
 * 한국경제신문 TESAT 1종 · 대한검정회 한자 2종 · 한국세무사회 전산세무회계 4종.
 *
 * <p>셋 다 함정이 다르다.
 * <ul>
 *   <li><b>TESAT</b> — 표가 깔끔하다(회차·시험일·접수기간·발표일). 연간 회차가 다 실려 있다.</li>
 *   <li><b>대한검정회</b> — 지난해 문구가 <b>주석</b>으로 남아 있다({@code <!-- 방문 접수기간 2024.09.23 -->}).
 *       걷어내지 않으면 2024년 날짜가 올해 접수기간으로 들어간다.</li>
 *   <li><b>한국세무사회</b> — 표에 <b>연도도 회차도 없다</b>({@code 01.02 ∼ 01.08}). 게다가
 *       구분선(∼)과 공백이 제각각이다({@code 07.02∼ 07.08}, {@code 11. 30 ∼12.05}).</li>
 * </ul>
 */
class TesatDaehanKactaParserTest {

    private static String fixture(String name) throws IOException {
        return new String(new ClassPathResource("fixtures/" + name).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private static CollectedSchedule find(List<CollectedSchedule> list, String name, int round) {
        return list.stream()
                .filter(s -> s.certificateName().equals(name) && s.round() == round)
                .findFirst().orElse(null);
    }

    // ===== 한국경제신문 TESAT =====

    @Test
    @DisplayName("TESAT: 회차·시험일·접수기간·발표일을 읽는다")
    void tesat_reads_a_round() throws IOException {
        List<CollectedSchedule> out = new TesatScheduleSource().parse(fixture("tesat_schedule.html"));

        assertFalse(out.isEmpty(), "한 건도 못 뽑았다");
        CollectedSchedule s = find(out, "TESAT 경제이해력검증시험", 110);
        assertNotNull(s, "제110회가 없다");
        assertEquals("M0440", s.sourceCode());
        assertEquals(2026, s.year());
        assertEquals("2026-12-19", s.examStartDate().toString());
        assertEquals("2026-11-10T10:00", s.regStartAt().toString());
        assertEquals("2026-12-07T18:00", s.regEndAt().toString());
        assertEquals("2026-12-24", s.resultDate().toString());
        assertEquals(ExamType.WRITTEN, s.examType());
    }

    /** 지난 회차만 읽으면 다음 접수를 못 알린다 — 표에 실린 회차를 다 읽어야 한다. */
    @Test
    @DisplayName("TESAT: 표에 실린 회차를 다 읽는다")
    void tesat_reads_every_row() throws IOException {
        List<CollectedSchedule> out = new TesatScheduleSource().parse(fixture("tesat_schedule.html"));

        assertTrue(out.size() >= 6, "회차를 놓쳤다: " + out.size());
        assertTrue(out.stream().allMatch(s -> s.round() >= 100 && s.round() < 200),
                "회차 번호가 이상하다: " + out.stream().map(CollectedSchedule::round).toList());
    }

    // ===== 대한검정회 =====

    @Test
    @DisplayName("대한검정회: 회차·시행일·접수기간·발표일을 읽는다")
    void daehan_reads_the_round() throws IOException {
        List<CollectedSchedule> out = new DaehanHanjaScheduleSource().parse(fixture("daehan_hanja_schedule.html"));

        assertEquals(2, out.size(), "한 회차가 급수 둘에 붙어야 한다");
        CollectedSchedule s = find(out, "대한검정회 한자 2급", 112);
        assertNotNull(s, "제112회가 없다");
        assertEquals("M0456", s.sourceCode());
        assertEquals("2026-08-22", s.examStartDate().toString());
        assertEquals("2026-06-29T10:00", s.regStartAt().toString());
        assertEquals("2026-07-10T18:00", s.regEndAt().toString());
        assertEquals("2026-09-14", s.resultDate().toString());
    }

    /**
     * <b>지난해 문구가 주석으로 남아 있다.</b> {@code <!-- 방문 접수기간 2024.09.23 ~ 2024.10.11 -->}
     * 를 걷어내지 않으면 <b>2024년 날짜가 올해 접수기간</b>으로 들어간다.
     * 사람 눈에는 안 보이는 값이라 화면을 봐도 못 잡는다.
     */
    @Test
    @DisplayName("대한검정회: 주석에 남은 지난해 날짜에 속지 않는다")
    void daehan_ignores_commented_out_dates() throws IOException {
        String html = fixture("daehan_hanja_schedule.html");
        assertTrue(html.contains("2024.09.23"), "픽스처에 주석 처리된 옛 날짜가 없다 — 이 테스트가 아무것도 검증하지 않는다");

        List<CollectedSchedule> out = new DaehanHanjaScheduleSource().parse(html);

        assertTrue(out.stream().allMatch(s -> s.regStartAt().getYear() >= 2026),
                "주석 속 2024년 날짜가 접수기간으로 들어왔다");
    }

    /**
     * 같은 블록에 <b>한자·한문전문지도사(제92회)</b>가 같이 있다. 우리 마스터에 없는 시험이라
     * 그 회차를 잘못 집으면 한자급수 회차가 92회로 들어간다.
     */
    @Test
    @DisplayName("대한검정회: 같이 실린 다른 시험의 회차를 집지 않는다")
    void daehan_picks_the_right_exam_round() throws IOException {
        List<CollectedSchedule> out = new DaehanHanjaScheduleSource().parse(fixture("daehan_hanja_schedule.html"));

        assertTrue(out.stream().noneMatch(s -> s.round() == 92),
                "한자·한문전문지도사(제92회) 회차를 집었다");
    }

    // ===== 한국세무사회 =====

    /**
     * 이 표에는 <b>연도가 없다.</b> 시행처가 그 해 계획만 싣기 때문이다 — 올해로 읽는다.
     * 회차 번호도 표에 없어서, 우리는 시험일을 멱등 키로 쓴다(화면엔 회차로 안 보인다).
     */
    @Test
    @DisplayName("세무사회: 연도 없는 표에 올해를 붙여 읽는다")
    void kacta_fills_in_the_missing_year() throws IOException {
        int thisYear = TimeUtil.today().getYear();
        List<CollectedSchedule> out = new KactaScheduleSource().parse(fixture("kacta_schedule.html"));

        assertFalse(out.isEmpty(), "한 건도 못 뽑았다");
        assertTrue(out.stream().allMatch(s -> s.year() == thisYear),
                "연도가 올해가 아니다");
        CollectedSchedule s = out.stream()
                .filter(x -> x.certificateName().equals("전산세무 1급"))
                .filter(x -> x.examStartDate().toString().endsWith("-10-03"))
                .findFirst().orElse(null);
        assertNotNull(s, "10.03 회차가 없다");
        assertEquals(thisYear + "-08-27T10:00", s.regStartAt().toString());
        assertEquals(thisYear + "-09-02T18:00", s.regEndAt().toString());
        assertEquals(thisYear + "-10-29", s.resultDate().toString());
    }

    /** 표기가 제각각이다 — {@code 07.02∼ 07.08}, {@code 11. 30 ∼12.05}. 여섯 회차를 다 읽어야 한다. */
    @Test
    @DisplayName("세무사회: 띄어쓰기가 제각각이어도 여섯 회차를 다 읽는다")
    void kacta_survives_inconsistent_spacing() throws IOException {
        List<CollectedSchedule> out = new KactaScheduleSource().parse(fixture("kacta_schedule.html"));

        assertEquals(6, out.stream().map(CollectedSchedule::examStartDate).distinct().count(),
                "여섯 회차가 아니다");
        assertEquals(24, out.size(), "6회차 × 4종목이어야 한다");
    }

    /** 한 회차가 전산세무 1·2급, 전산회계 1·2급 넷에 같이 붙는다 — 같은 날 같이 치른다. */
    @Test
    @DisplayName("세무사회: 한 회차가 네 종목에 붙는다")
    void kacta_round_applies_to_four_exams() throws IOException {
        List<CollectedSchedule> out = new KactaScheduleSource().parse(fixture("kacta_schedule.html"));

        for (String name : new String[]{"전산세무 1급", "전산세무 2급", "전산회계 1급", "전산회계 2급"}) {
            assertTrue(out.stream().anyMatch(s -> s.certificateName().equals(name)), name + " 이 없다");
        }
    }

    /** 회차를 지어내지 않는다 — 시행처가 표에 안 적었으므로 화면에도 안 보여야 한다. */
    @Test
    @DisplayName("세무사회: 없는 회차 번호를 지어내지 않는다")
    void kacta_does_not_invent_a_round_number() throws IOException {
        List<CollectedSchedule> out = new KactaScheduleSource().parse(fixture("kacta_schedule.html"));

        assertTrue(out.stream().allMatch(s -> s.round() >= 1_000_000),
                "회차 자리에 시행처가 안 준 작은 숫자가 들어갔다 — 화면에 '1회'로 뜬다");
    }

    // ===== 공통 계약 =====

    @Test
    @DisplayName("셋 다 접수가 시험보다 늦지 않다")
    void registration_precedes_exam() throws IOException {
        List<CollectedSchedule> all = new java.util.ArrayList<>();
        all.addAll(new TesatScheduleSource().parse(fixture("tesat_schedule.html")));
        all.addAll(new DaehanHanjaScheduleSource().parse(fixture("daehan_hanja_schedule.html")));
        all.addAll(new KactaScheduleSource().parse(fixture("kacta_schedule.html")));

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
            assertTrue(new TesatScheduleSource().parse(html).isEmpty(), "TESAT: " + html);
            assertTrue(new DaehanHanjaScheduleSource().parse(html).isEmpty(), "대한검정회: " + html);
            assertTrue(new KactaScheduleSource().parse(html).isEmpty(), "세무사회: " + html);
        }
    }
}

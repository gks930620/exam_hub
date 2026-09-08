package com.test.test.exam.collect;

import com.test.test.exam.domain.ExamType;
import org.junit.jupiter.api.BeforeAll;
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
 * 한국생산성본부(KPC) 원서접수 표 파싱.
 *
 * <p>이 시행처는 <b>"수기 필수"로 분류돼 있었다.</b> 2026-09-08 전수조사에서 그 판단이 틀렸음이
 * 드러났다 — 예전 조사가 없는 주소(404)를 열어 보고 "JS 셸"이라고 적었을 뿐, 실제 페이지에는
 * 회차·시험일·접수기간이 한 줄에 다 있다. 12종이 여기서 자동으로 들어온다.
 *
 * <p>한 줄이 곧 한 회차다:
 * {@code 2026년 제10회 ITQ정기시험 D-2 시험일 2026-10-17 인터넷접수 2026-09-10 오전 10:00 ~ 2026-09-16 접수 D-2}
 */
class KpcParserTest {

    private static List<CollectedSchedule> parsed;

    @BeforeAll
    static void parseFixture() throws IOException {
        String html = new String(
                new ClassPathResource("fixtures/kpc_schedule.html").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        parsed = new KpcScheduleSource().parse(html);
    }

    private static CollectedSchedule find(String name, int round) {
        return parsed.stream()
                .filter(s -> s.certificateName().equals(name) && s.round() == round)
                .findFirst().orElse(null);
    }

    @Test
    @DisplayName("일정을 뽑는다")
    void extracts_schedules() {
        assertFalse(parsed.isEmpty(), "한 건도 못 뽑았다 — 표 구조가 바뀌었나?");
        assertTrue(parsed.stream().allMatch(s -> s.year() == 2026), "연도가 뒤섞였다");
        assertTrue(parsed.stream().allMatch(s -> s.examType() == ExamType.WRITTEN),
                "이 시행처는 필기·실기 구분이 없다 — 전부 WRITTEN 이어야 한다");
    }

    /**
     * ITQ 는 <b>한 시험명에 여러 종목</b>이 걸린다(한글·엑셀·파워포인트·액세스·인터넷).
     * 표에는 "ITQ정기시험" 한 줄뿐이라, 그 한 줄을 5종에 똑같이 붙여야 한다.
     */
    @Test
    @DisplayName("ITQ 한 줄이 5개 종목에 붙는다")
    void itq_row_applies_to_all_five_subjects() {
        for (String name : new String[]{
                "ITQ 정보기술자격(한글)", "ITQ 정보기술자격(엑셀)", "ITQ 정보기술자격(파워포인트)",
                "ITQ 정보기술자격(액세스)", "ITQ 정보기술자격(인터넷)"}) {
            assertNotNull(find(name, 10), name + " 제10회가 없다");
        }
    }

    @Test
    @DisplayName("회차·접수·시험일을 정확히 읽는다")
    void reads_round_and_dates() {
        CollectedSchedule s = find("ITQ 정보기술자격(한글)", 10);

        assertNotNull(s, "ITQ 제10회가 없다");
        assertEquals("M0375", s.sourceCode(), "마스터와 다른 코드를 주면 같은 시험이 하나 더 생긴다");
        assertEquals("2026-09-10T10:00", s.regStartAt().toString(), "접수 시작이 틀렸다");
        assertEquals("2026-09-16T18:00", s.regEndAt().toString(), "접수 마감이 틀렸다");
        assertEquals("2026-10-17", s.examStartDate().toString(), "시험일이 틀렸다");
    }

    /** 같은 종목의 다음 회차도 함께 온다 — 접수 중인 것만 보면 다음 회차를 놓친다. */
    @Test
    @DisplayName("다음 회차도 같이 읽는다")
    void reads_upcoming_rounds_too() {
        CollectedSchedule eleventh = find("ITQ 정보기술자격(한글)", 11);

        assertNotNull(eleventh, "제11회가 없다 — 접수 중인 것만 읽고 있나?");
        assertEquals("2026-11-14", eleventh.examStartDate().toString());
        assertEquals("2026-10-08T10:00", eleventh.regStartAt().toString());
    }

    /** GTQ 는 1급·2급, GTQi 는 별도 종목이다. 한 줄("GTQ/GTQi 정기시험")이 셋에 붙는다. */
    @Test
    @DisplayName("GTQ/GTQi 한 줄이 세 종목에 붙는다")
    void gtq_row_applies_to_three() {
        for (String name : new String[]{
                "GTQ 그래픽기술자격 1급", "GTQ 그래픽기술자격 2급", "GTQi 일러스트 1급"}) {
            assertNotNull(find(name, 10), name + " 제10회가 없다");
        }
    }

    /** 표에 없는 시험명은 만들어 내지 않는다 — 모르는 이름은 버린다. */
    @Test
    @DisplayName("모르는 시험명은 넣지 않는다")
    void unknown_names_are_dropped() {
        assertTrue(parsed.stream().allMatch(s -> s.sourceCode().startsWith("M0")),
                "마스터에 없는 코드가 들어왔다");
    }

    @Test
    @DisplayName("접수가 시험보다 늦지 않다")
    void registration_precedes_exam() {
        parsed.stream()
                .filter(s -> s.regStartAt() != null && s.examStartDate() != null)
                .forEach(s -> assertFalse(s.examStartDate().isBefore(s.regStartAt().toLocalDate()),
                        s.certificateName() + " " + s.round() + "회 시험일이 접수 시작보다 앞이다"));
    }

    @Test
    @DisplayName("표가 없으면 예외 없이 0건")
    void no_table_yields_nothing() {
        assertTrue(new KpcScheduleSource().parse("<html>점검 중</html>").isEmpty());
    }
}

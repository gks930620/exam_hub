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
 * 국민체육진흥공단 체육지도자 — 생활2급·전문2급·유소년·노인 <b>4종</b>.
 *
 * <h3>이 시험이 어려운 이유</h3>
 * <b>급수마다 접수 창구가 다르다.</b> 2026년 실측:
 * <pre>
 * 2급 전문      2026.03.19 ~ 03.23
 * 2급 생활·유소년·노인   2026.04.02 (하루)
 * </pre>
 * 시험일은 넷 다 같지만(04.18) <b>접수는 다르다.</b> 하나를 넷에 붙이면 셋이 틀린 날짜가 되고,
 * 그 셋은 접수를 놓친다. 그래서 <b>급수별 페이지를 급수마다 따로</b> 읽는다
 * ({@code schedPlan.kspo?QF_GRADE_CD=<급수코드>}).
 *
 * <p>처음엔 "급수를 못 가르니 안 붙인다"고 판단했었다. 첫 화면 표가 지난해 것이었고
 * 연간일정계획이 기본으로 1급만 보여 줘서 창구가 하나뿐인 줄 알았다. 실제로는
 * 라디오 버튼이 {@code QF_GRADE_CD} 를 바꿔 급수별 표를 부른다(2026-09-08 재조사).
 */
class KspoParserTest {

    private static String fixture(String name) throws IOException {
        return new String(new ClassPathResource("fixtures/" + name).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private static List<CollectedSchedule> parse(String fixture, String gradeCode) throws IOException {
        return new KspoScheduleSource().parse(fixture(fixture), KspoScheduleSource.grade(gradeCode));
    }

    @Test
    @DisplayName("생활2급: 접수·시험일·발표일을 읽는다")
    void reads_the_life_sports_grade() throws IOException {
        List<CollectedSchedule> out = parse("kspo_lsc2.html", "LSC2");

        assertEquals(1, out.size(), "이 급수 페이지는 한 종목만 만든다");
        CollectedSchedule s = out.get(0);
        assertEquals("생활스포츠지도사 2급", s.certificateName());
        assertEquals("M0473", s.sourceCode());
        assertEquals(2026, s.year(), "연도는 표 안에 없다 — '2026년도 연간일정 계획'에서 가져와야 한다");
        assertEquals(1, s.round(), "연 1회다");
        assertEquals("2026-04-02T10:00", s.regStartAt().toString());
        assertEquals("2026-04-02T18:00", s.regEndAt().toString(), "접수가 하루짜리다 — 그대로 읽어야 한다");
        assertEquals("2026-04-18", s.examStartDate().toString());
        assertEquals("2026-05-08", s.resultDate().toString());
        assertEquals(ExamType.WRITTEN, s.examType());
    }

    /**
     * <b>급수가 다르면 접수도 다르다.</b> 이걸 못 가르면 셋에게 틀린 날짜가 나간다 —
     * 이 스크래퍼가 존재하는 이유다.
     */
    @Test
    @DisplayName("전문2급은 접수 창구가 다르다 — 생활2급과 같으면 안 된다")
    void the_professional_grade_has_a_different_window() throws IOException {
        CollectedSchedule pro = parse("kspo_psc2.html", "PSC2").get(0);
        CollectedSchedule life = parse("kspo_lsc2.html", "LSC2").get(0);

        assertEquals("전문스포츠지도사 2급", pro.certificateName());
        assertEquals("M0474", pro.sourceCode());
        assertEquals("2026-03-19T10:00", pro.regStartAt().toString());
        assertEquals("2026-03-23T18:00", pro.regEndAt().toString());

        assertFalse(pro.regStartAt().equals(life.regStartAt()),
                "두 급수의 접수 시작이 같다 — 급수별 페이지를 안 읽고 하나를 복사한 것이다");
        assertEquals(pro.examStartDate(), life.examStartDate(), "시험일은 넷 다 같은 날이다");
    }

    /**
     * 표에는 일반과정 말고 <b>추가취득·특별과정</b> 행이 더 있는데, 열리지 않은 해에는
     * {@code " ~ "} 로 비어 온다. 그걸 회차로 만들면 날짜 없는 유령 회차가 생긴다.
     */
    @Test
    @DisplayName("비어 있는 과정 행은 회차로 만들지 않는다")
    void empty_course_rows_are_not_turned_into_rounds() throws IOException {
        String html = fixture("kspo_lsc2.html");
        assertTrue(html.contains("추가취득"), "픽스처에 빈 과정 행이 없다 — 이 테스트가 아무것도 검증하지 않는다");

        List<CollectedSchedule> out = new KspoScheduleSource().parse(html, KspoScheduleSource.grade("LSC2"));

        assertEquals(1, out.size(), "빈 과정 행까지 회차로 만들었다");
        assertNotNull(out.get(0).regStartAt());
    }

    /** 급수 넷을 다 맡는다 — 하나라도 빠지면 그 급수는 수기로 남는다. */
    @Test
    @DisplayName("급수 넷을 맡는다")
    void covers_four_grades() {
        assertEquals(4, KspoScheduleSource.grades().size());
        for (String code : new String[]{"LSC2", "PSC2", "YUSC", "OLSC"}) {
            assertNotNull(KspoScheduleSource.grade(code), code + " 가 빠졌다");
        }
        assertEquals(4, KspoScheduleSource.grades().stream()
                .map(KspoScheduleSource.Grade::sourceCode).distinct().count(), "종목코드가 겹친다");
    }

    @Test
    @DisplayName("접수가 시험보다 늦지 않다")
    void registration_precedes_exam() throws IOException {
        for (String[] pair : new String[][]{{"kspo_lsc2.html", "LSC2"}, {"kspo_psc2.html", "PSC2"}}) {
            parse(pair[0], pair[1]).stream()
                    .filter(s -> s.regStartAt() != null && s.examStartDate() != null)
                    .forEach(s -> assertFalse(s.examStartDate().isBefore(s.regStartAt().toLocalDate()),
                            s.certificateName() + " 시험일이 접수 시작보다 앞이다"));
        }
    }

    /** 잘못된 급수코드를 주면 시행처가 625B 짜리 알림 페이지를 준다 — 거기서 날짜를 지어내면 안 된다. */
    @Test
    @DisplayName("일정이 없는 화면이면 예외 없이 0건")
    void no_schedule_page_yields_nothing() {
        assertTrue(new KspoScheduleSource()
                .parse("<html><body>해당 데이터가 없습니다.</body></html>", KspoScheduleSource.grade("LSC2")).isEmpty());
        assertTrue(new KspoScheduleSource().parse("", KspoScheduleSource.grade("LSC2")).isEmpty());
        assertTrue(new KspoScheduleSource().parse("<html></html>", null).isEmpty());
    }
}

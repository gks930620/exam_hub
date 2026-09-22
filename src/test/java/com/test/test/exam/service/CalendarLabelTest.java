package com.test.test.exam.service;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.domain.Series;
import com.test.test.exam.web.dto.MeDtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>캘린더가 카드·메일과 같은 말을 해야 한다.</b>
 *
 * <p>2026-09-22 에 "실기를 치르지도 않는 시험에 필기가 붙는다"를 고쳤는데(206종),
 * 카드({@code DdayService})와 메일({@code NotificationContentFactory})만 고치고
 * <b>캘린더를 빠뜨렸다.</b> 그래서 같은 회차를 카드는 "37회 접수 마감", 캘린더는
 * "37회 필기 접수 마감" 이라고 부른다.
 *
 * <p>한 사실을 화면마다 다르게 부르면 사용자는 둘 중 어느 쪽도 못 믿는다 —
 * 이 프로젝트가 화면끼리 셈법을 통일해 온 이유와 같다.
 *
 * <p>순수 계산이라 컨벤션 §6 의 예외로 둔다.
 */
class CalendarLabelTest {

    private final FavoriteService service = new FavoriteService(null, null, null, null);

    private Certificate cert(String name) {
        return Certificate.builder().name(name).slug(name)
                .series(Series.ETC).agency("한국산업인력공단").build();
    }

    private ExamSchedule sitting(Certificate c, int round, ExamType type, int daysAhead) {
        LocalDate exam = TimeUtil.today().plusDays(daysAhead);
        return ExamSchedule.builder()
                .certificate(c).year(exam.getYear()).round(round).examType(type)
                .regStartAt(TimeUtil.now().minusDays(3))
                .regEndAt(TimeUtil.now().plusDays(daysAhead - 5L))
                .examStartDate(exam).examEndDate(exam)
                .provenance(ScheduleProvenance.API).status(ScheduleStatus.ACTIVE)
                .build();
    }

    /** 부르는 쪽이 준비한 것을 그대로 쓰도록, 화면이 보는 것과 같은 경로로 부른다. */
    @SuppressWarnings("unchecked")
    private List<MeDtos.CalendarEvent> events(List<ExamSchedule> schedules, Map<Long, String> names) {
        return (List<MeDtos.CalendarEvent>) ReflectionTestUtils.invokeMethod(
                service, "eventsBetween", schedules, names,
                TimeUtil.today().minusDays(1), TimeUtil.today().plusYears(1));
    }

    @Test
    @DisplayName("한 종류만 치르는 시험은 캘린더에도 구분을 안 붙인다")
    void single_type_has_no_label_in_the_calendar() {
        Certificate c = cert("공인중개사");
        ReflectionTestUtils.setField(c, "id", 1L);
        List<ExamSchedule> rounds = List.of(sitting(c, 37, ExamType.WRITTEN, 30));

        List<MeDtos.CalendarEvent> out = events(rounds, Map.of(1L, "공인중개사"));

        assertThat(out).isNotEmpty();
        assertThat(out).allSatisfy(e ->
                assertThat(e.getLabel()).doesNotContain("필기").startsWith("37회"));
    }

    @Test
    @DisplayName("필기·실기를 둘 다 치르면 캘린더도 구분해 말한다 — 접수일이 서로 다르다")
    void split_exam_keeps_the_label_in_the_calendar() {
        Certificate c = cert("정보처리기사");
        ReflectionTestUtils.setField(c, "id", 2L);
        List<ExamSchedule> rounds = List.of(
                sitting(c, 3, ExamType.WRITTEN, 30),
                sitting(c, 3, ExamType.PRACTICAL, 60));

        List<MeDtos.CalendarEvent> out = events(rounds, Map.of(2L, "정보처리기사"));

        assertThat(out).anySatisfy(e -> assertThat(e.getLabel()).contains("필기"));
        assertThat(out).anySatisfy(e -> assertThat(e.getLabel()).contains("실기"));
    }

    /** 시험이 여럿이면 각자 자기 기준으로 판단해야 한다 — 한 시험 때문에 다른 시험이 물들면 안 된다. */
    @Test
    @DisplayName("시험마다 따로 판단한다")
    void each_exam_is_judged_on_its_own() {
        Certificate single = cert("공인중개사");
        ReflectionTestUtils.setField(single, "id", 1L);
        Certificate split = cert("정보처리기사");
        ReflectionTestUtils.setField(split, "id", 2L);

        List<ExamSchedule> rounds = List.of(
                sitting(single, 37, ExamType.WRITTEN, 30),
                sitting(split, 3, ExamType.WRITTEN, 40),
                sitting(split, 3, ExamType.PRACTICAL, 70));

        List<MeDtos.CalendarEvent> out =
                events(rounds, Map.of(1L, "공인중개사", 2L, "정보처리기사"));

        assertThat(out).filteredOn(e -> "공인중개사".equals(e.getName()))
                .allSatisfy(e -> assertThat(e.getLabel()).doesNotContain("필기"));
        assertThat(out).filteredOn(e -> "정보처리기사".equals(e.getName()))
                .anySatisfy(e -> assertThat(e.getLabel()).contains("필기"));
    }
}

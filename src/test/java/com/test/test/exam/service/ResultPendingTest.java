package com.test.test.exam.service;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.domain.Series;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>시험은 끝났고 합격자 발표만 남은 상태</b>를 화면이 말해 주는가.
 *
 * <p>그전에는 못 했다. 시험일이 지나면 {@code PAST_ONLY} 가 되어 카드가 <b>"다음 회차 미정 ·
 * 마지막 시험 2026-08-21"</b> 만 보여줬고, 정작 <b>9월 16일 합격자 발표</b>는 화면 어디에도 없었다
 * (감정사·검수사 등 24종, 2026-09-10 실측). 시험을 막 친 사람이 이 서비스를 여는 이유가 그 날짜인데도.
 *
 * <p>접수보다 뒤에 두는 이유: 발표는 놓쳐도 되돌릴 수 있지만 접수는 못 되돌린다.
 */
class ResultPendingTest {

    private final DdayService dday = new DdayService();

    private static ExamSchedule schedule(int round, ExamType type,
                                         LocalDateTime regStart, LocalDateTime regEnd,
                                         LocalDate examStart, LocalDate examEnd, LocalDate result) {
        return ExamSchedule.builder()
                .year(TimeUtil.today().getYear()).round(round).examType(type)
                .regStartAt(regStart).regEndAt(regEnd)
                .examStartDate(examStart).examEndDate(examEnd).resultDate(result)
                .status(ScheduleStatus.ACTIVE)
                .build();
    }

    private static Certificate cert() {
        return Certificate.builder().name("감정사").slug("감정사")
                .series(Series.ETC).agency("한국산업인력공단").build();
    }

    @Test
    @DisplayName("시험은 지났고 발표일이 남았으면 '합격 발표 예정' 과 D-day 를 준다")
    void examOver_resultAhead_showsResultPending() {
        LocalDate today = TimeUtil.today();
        ExamSchedule written = schedule(26, ExamType.WRITTEN,
                null, null, today.minusDays(20), today.minusDays(20), today.plusDays(6));

        NextEvent next = dday.computeNextEvent(List.of(written));

        assertEquals(CardBadge.RESULT_PENDING, next.badge());
        assertEquals(6, next.dday());
        assertTrue(next.label().contains("합격자 발표"), "라벨: " + next.label());
        assertEquals(today.plusDays(6).atStartOfDay(), next.at());
    }

    @Test
    @DisplayName("발표를 기다리는 시험은 '다음 회차 미정'(PAST_ONLY)이 아니다")
    void resultPending_isNotPastOnly() {
        LocalDate today = TimeUtil.today();
        ExamSchedule written = schedule(26, ExamType.WRITTEN,
                null, null, today.minusDays(20), today.minusDays(20), today.plusDays(6));

        DdayService.ScheduleSummary summary = dday.summarize(cert(), List.of(written));

        assertEquals(DdayService.ScheduleState.UPCOMING, summary.state());
        assertEquals(CardBadge.RESULT_PENDING, summary.next().badge());
    }

    @Test
    @DisplayName("발표일이 지났으면 다시 '다음 회차 미정'이다")
    void resultPassed_backToPastOnly() {
        LocalDate today = TimeUtil.today();
        ExamSchedule written = schedule(26, ExamType.WRITTEN,
                null, null, today.minusDays(40), today.minusDays(40), today.minusDays(1));

        DdayService.ScheduleSummary summary = dday.summarize(cert(), List.of(written));

        assertEquals(DdayService.ScheduleState.PAST_ONLY, summary.state());
    }

    @Test
    @DisplayName("발표 당일도 아직 '발표 예정' 이다 — D-0")
    void resultToday_isStillPending() {
        LocalDate today = TimeUtil.today();
        ExamSchedule written = schedule(26, ExamType.WRITTEN,
                null, null, today.minusDays(20), today.minusDays(20), today);

        NextEvent next = dday.computeNextEvent(List.of(written));

        assertEquals(CardBadge.RESULT_PENDING, next.badge());
        assertEquals(0, next.dday());
    }

    @Test
    @DisplayName("접수 중인 회차가 있으면 발표보다 접수가 먼저다 — 접수는 놓치면 못 되돌린다")
    void openRegistration_beatsResult() {
        LocalDate today = TimeUtil.today();
        ExamSchedule done = schedule(26, ExamType.WRITTEN,
                null, null, today.minusDays(20), today.minusDays(20), today.plusDays(6));
        ExamSchedule next27 = schedule(27, ExamType.WRITTEN,
                today.minusDays(2).atStartOfDay(), today.plusDays(9).atTime(18, 0),
                today.plusDays(40), today.plusDays(40), today.plusDays(70));

        NextEvent next = dday.computeNextEvent(List.of(done, next27));

        assertEquals(CardBadge.REG_OPEN, next.badge());
    }

    @Test
    @DisplayName("시험일을 모르는 회차는 발표일만으로 '발표 예정'이 되지 않는다")
    void noExamDate_doesNotBecomeResultPending() {
        LocalDate today = TimeUtil.today();
        ExamSchedule onlyResult = schedule(26, ExamType.WRITTEN,
                null, null, null, null, today.plusDays(6));

        NextEvent next = dday.computeNextEvent(List.of(onlyResult));

        assertEquals(CardBadge.NONE, next.badge(), "시험을 쳤는지도 모르는데 발표를 안내할 수는 없다");
    }

    @Test
    @DisplayName("여러 회차가 발표를 기다리면 가장 이른 발표를 고른다")
    void earliestResultWins() {
        LocalDate today = TimeUtil.today();
        ExamSchedule practical = schedule(26, ExamType.PRACTICAL,
                null, null, today.minusDays(10), today.minusDays(10), today.plusDays(20));
        ExamSchedule written = schedule(26, ExamType.WRITTEN,
                null, null, today.minusDays(30), today.minusDays(30), today.plusDays(3));

        NextEvent next = dday.computeNextEvent(List.of(practical, written));

        assertEquals(CardBadge.RESULT_PENDING, next.badge());
        assertEquals(3, next.dday());
    }
}

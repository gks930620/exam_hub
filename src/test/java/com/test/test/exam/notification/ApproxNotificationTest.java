package com.test.test.exam.notification;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.NotificationSchedule;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.domain.Series;
import com.test.test.exam.repository.NotificationLogRepository;
import com.test.test.exam.repository.NotificationScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>지어낸 날짜로 알림을 보내지 않는다.</b>
 *
 * <p>비큐넷 시드는 시행처 일정을 못 구한 시험에 <b>추정 날짜</b>를 넣는다(APPROX).
 * 그 값이 알림 예약까지 흘러가면 사용자는 <b>"내일 접수 시작입니다"</b> 라는 메일을 받고,
 * 그 날 시행처에 가면 아무 일도 없다. 그러고는 진짜 마감을 놓친다 —
 * 일정을 안 알려 주는 것보다 나쁘다.
 *
 * <p>2026-09-21 실측: 추정 회차 22건이 미래 일정으로 서 있었고, 그중 19종이 사용자 카드의
 * D-day 였으며, 알림 예약은 <b>출처를 보지 않고</b> 전부 만들고 있었다.
 *
 * <p>화면에서는 계속 보여 준다(추정이라고 표시해서) — 대략의 시기라도 아는 편이 낫다.
 * 다만 <b>"이 날입니다"라고 먼저 연락하는 것</b>은 확인된 날짜에만 한다.
 */
class ApproxNotificationTest {

    private final NotificationScheduleRepository repo = Mockito.mock(NotificationScheduleRepository.class);
    private final NotificationLogRepository logs = Mockito.mock(NotificationLogRepository.class);
    private final NotificationScheduleService service = new NotificationScheduleService(repo, logs);

    private ExamSchedule schedule(ScheduleProvenance provenance) {
        Certificate c = Certificate.builder()
                .name("국가직 9급 공개경쟁채용").slug("국가직9급공개경쟁채용")
                .series(Series.ETC).agency("인사혁신처").build();
        LocalDate soon = TimeUtil.today().plusDays(40);
        return ExamSchedule.builder()
                .certificate(c).year(soon.getYear()).round(1).examType(ExamType.WRITTEN)
                .regStartAt(soon.minusDays(20).atTime(10, 0))
                .regEndAt(soon.minusDays(14).atTime(18, 0))
                .examStartDate(soon).examEndDate(soon)
                .provenance(provenance).status(ScheduleStatus.ACTIVE)
                .build();
    }

    private void stubEmpty() {
        Mockito.when(repo.findByExamScheduleAndEventType(Mockito.any(), Mockito.any()))
                .thenReturn(Optional.empty());
        Mockito.when(repo.findByExamSchedule(Mockito.any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("추정 날짜(APPROX)로는 알림을 예약하지 않는다")
    void approx_schedules_no_notifications() {
        stubEmpty();

        service.recalc(schedule(ScheduleProvenance.APPROX), false);

        Mockito.verify(repo, Mockito.never()).save(Mockito.any());
    }

    @Test
    @DisplayName("시행처에서 확인된 날짜는 평소대로 예약한다 — 추정치만 막는다")
    void confirmed_schedules_still_notify() {
        for (ScheduleProvenance p : List.of(ScheduleProvenance.API, ScheduleProvenance.SCRAPED, ScheduleProvenance.MANUAL)) {
            Mockito.reset(repo);
            stubEmpty();

            service.recalc(schedule(p), false);

            ArgumentCaptor<NotificationSchedule> saved = ArgumentCaptor.forClass(NotificationSchedule.class);
            Mockito.verify(repo, Mockito.atLeastOnce()).save(saved.capture());
            assertTrue(saved.getAllValues().size() >= 3,
                    p + " 는 확인된 값인데 알림이 " + saved.getAllValues().size() + "건뿐이다");
        }
    }

    /**
     * 추정치였다가 시행처 값이 들어오면 <b>그때부터</b> 알림이 걸려야 한다.
     * 안 그러면 "한 번 추정치였던 시험"은 영영 알림이 안 간다.
     */
    @Test
    @DisplayName("추정치가 확인된 값으로 바뀌면 그때 예약된다")
    void upgrading_to_confirmed_starts_notifying() {
        stubEmpty();
        ExamSchedule s = schedule(ScheduleProvenance.APPROX);

        service.recalc(s, false);
        Mockito.verify(repo, Mockito.never()).save(Mockito.any());

        s.changeProvenance(ScheduleProvenance.SCRAPED);
        service.recalc(s, false);

        Mockito.verify(repo, Mockito.atLeastOnce()).save(Mockito.any());
    }

    /**
     * 이미 걸려 있던 예약은 <b>치워야</b> 한다. 확정이던 회차가 추정치로 내려앉는 일은 드물지만,
     * 남겨 두면 그 예약이 그대로 발송된다.
     */
    @Test
    @DisplayName("확정이던 회차가 추정치가 되면 대기 중인 예약을 취소한다")
    void downgrading_to_approx_cancels_pending() {
        ExamSchedule s = schedule(ScheduleProvenance.APPROX);
        NotificationSchedule pending = NotificationSchedule.builder()
                .examSchedule(s)
                .eventType(com.test.test.exam.domain.NotificationEventType.REG_OPEN_DAY)
                .sendAt(TimeUtil.now().plusDays(1))
                .status(com.test.test.exam.domain.NotificationScheduleStatus.PENDING)
                .build();
        Mockito.when(repo.findByExamSchedule(s)).thenReturn(List.of(pending));
        Mockito.when(repo.findByExamScheduleAndEventType(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());

        service.recalc(s, false);

        assertEquals(com.test.test.exam.domain.NotificationScheduleStatus.CANCELED, pending.getStatus(),
                "추정 회차인데 대기 중인 알림이 그대로 남았다 — 그날 지어낸 날짜로 메일이 나간다");
    }
}

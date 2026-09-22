package com.test.test.exam.notification;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.NotificationChannel;
import com.test.test.exam.domain.NotificationEventType;
import com.test.test.exam.domain.NotificationLog;
import com.test.test.exam.domain.NotificationResult;
import com.test.test.exam.domain.NotificationSchedule;
import com.test.test.exam.domain.NotificationScheduleStatus;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.domain.Series;
import com.test.test.exam.repository.ExamScheduleRepository;
import com.test.test.exam.repository.NotificationLogRepository;
import com.test.test.exam.repository.NotificationScheduleRepository;
import com.test.test.exam.repository.UserFavoriteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>한 번 실패하면 그 사람은 영영 못 받는다 — 그걸 고친다.</b>
 *
 * <p>2026-09-22 실측한 동작: 발송이 실패해도 {@code notification_log} 에 실패 행을 쓰고,
 * 예약은 그대로 {@code markSent()} 로 넘어갔다. 멱등키가 (회원, 예약)이라 그 실패 행이
 * <b>다음 배치의 재시도까지 막는다.</b> 결과적으로 재시도가 하나도 없었다.
 *
 * <p>SMTP 가 잠깐 끊기거나 메일 서버가 몇 분 느린 건 흔한 일이다. 그 몇 분 때문에
 * "접수 마감 하루 전" 메일이 통째로 사라지면, 그게 바로 이 서비스가 존재하는 이유를 놓치는 것이다.
 *
 * <p>그래서: <b>잠깐 실패</b>는 기록을 남기지 않고 예약을 대기로 두어 다음 배치가 다시 시도한다.
 * <b>영영 못 보내는 경우</b>(받을 주소가 없음)는 기록을 남기고 다시 시도하지 않는다 —
 * 주소가 없는 사람에게 5분마다 다시 거는 건 아무 의미가 없다.
 * 무한 재시도도 막는다: 발송 시각에서 너무 멀어지면 이미 늦은 알림이라 포기한다.
 */
class NotificationRetryTest {

    private final NotificationScheduleRepository scheduleRepo =
            Mockito.mock(NotificationScheduleRepository.class);
    private final NotificationLogRepository logRepo = Mockito.mock(NotificationLogRepository.class);
    private final UserFavoriteRepository favoriteRepo = Mockito.mock(UserFavoriteRepository.class);
    private final ExamScheduleRepository examRepo = Mockito.mock(ExamScheduleRepository.class);
    private final StubSender sender = new StubSender();

    private final NotificationDispatchWorker worker = new NotificationDispatchWorker(
            examRepo, scheduleRepo, logRepo, favoriteRepo, new NotificationContentFactory(), sender);

    /** 결과를 정해 두고 돌려주는 발송기. */
    private static class StubSender implements NotificationSender {
        NotificationResult next = NotificationResult.SUCCESS;
        int calls = 0;

        @Override public NotificationChannel channel() { return NotificationChannel.EMAIL; }

        @Override public NotificationResult send(Member user, NotificationMessage message) {
            calls++;
            return next;
        }
    }

    private NotificationSchedule armed(LocalDateTime sendAt) {
        LocalDate exam = TimeUtil.today().plusDays(10);
        Certificate cert = Certificate.builder()
                .name("정보처리기사").slug("정보처리기사").series(Series.ETC).agency("한국산업인력공단").build();
        ExamSchedule schedule = ExamSchedule.builder()
                .certificate(cert).year(exam.getYear()).round(3).examType(ExamType.WRITTEN)
                .regStartAt(TimeUtil.now().minusDays(5)).regEndAt(TimeUtil.now().plusDays(2))
                .examStartDate(exam).examEndDate(exam)
                .provenance(ScheduleProvenance.API).status(ScheduleStatus.ACTIVE)
                .build();

        NotificationSchedule ns = NotificationSchedule.builder()
                .examSchedule(schedule).eventType(NotificationEventType.REG_CLOSE_EVE)
                .sendAt(sendAt).status(NotificationScheduleStatus.PENDING)
                .build();

        Mockito.when(scheduleRepo.findById(Mockito.any())).thenReturn(Optional.of(ns));
        Mockito.when(examRepo.findDistinctExamTypes(Mockito.any())).thenReturn(List.of(ExamType.WRITTEN));
        Mockito.when(favoriteRepo.findUsersByCertificateId(Mockito.any()))
                .thenReturn(List.of(member()));
        Mockito.when(logRepo.existsByMemberIdAndNotificationScheduleId(Mockito.any(), Mockito.any()))
                .thenReturn(false);
        return ns;
    }

    private Member member() {
        Member m = Member.builder().nickname("테스터").email("a@b.com").build();
        org.springframework.test.util.ReflectionTestUtils.setField(m, "id", 1L);
        return m;
    }

    @Test
    @DisplayName("보냈으면 예약을 닫고 기록을 남긴다")
    void success_closes_the_reservation() {
        NotificationSchedule ns = armed(TimeUtil.now().minusMinutes(1));
        sender.next = NotificationResult.SUCCESS;

        int sent = worker.dispatchOne(1L);

        assertThat(sent).isEqualTo(1);
        assertThat(ns.getStatus()).isEqualTo(NotificationScheduleStatus.SENT);
        Mockito.verify(logRepo).save(Mockito.any(NotificationLog.class));
    }

    /**
     * 핵심. SMTP 가 잠깐 끊긴 것뿐인데 그 사람이 영영 못 받으면 안 된다.
     * 기록을 남기면 멱등키가 재시도까지 막으므로 <b>기록도 남기지 않는다.</b>
     */
    @Test
    @DisplayName("잠깐 실패하면 대기로 두어 다음 배치가 다시 시도한다")
    void transient_failure_stays_pending_for_retry() {
        NotificationSchedule ns = armed(TimeUtil.now().minusMinutes(1));
        sender.next = NotificationResult.FAILED;

        int sent = worker.dispatchOne(1L);

        assertThat(sent).isZero();
        assertThat(ns.getStatus())
                .as("실패했는데 예약을 닫으면 그 사람은 영영 못 받는다")
                .isEqualTo(NotificationScheduleStatus.PENDING);
        Mockito.verify(logRepo, Mockito.never()).save(Mockito.any(NotificationLog.class));
    }

    /** 받을 주소가 없는 사람에게 5분마다 다시 거는 건 아무 의미가 없다. */
    @Test
    @DisplayName("받을 주소가 없으면 기록을 남기고 다시 시도하지 않는다")
    void permanent_failure_is_not_retried() {
        NotificationSchedule ns = armed(TimeUtil.now().minusMinutes(1));
        sender.next = NotificationResult.SUBSCRIPTION_EXPIRED;

        worker.dispatchOne(1L);

        assertThat(ns.getStatus()).isEqualTo(NotificationScheduleStatus.SENT);
        Mockito.verify(logRepo).save(Mockito.any(NotificationLog.class));
    }

    /**
     * 무한 재시도는 막는다. 발송 시각에서 너무 멀어지면 이미 늦은 알림이고,
     * 그때까지 안 됐으면 다음 5분에도 안 된다.
     */
    @Test
    @DisplayName("너무 오래 밀린 알림은 포기한다 — 이미 늦었다")
    void gives_up_after_the_retry_window() {
        NotificationSchedule ns = armed(
                TimeUtil.now().minusHours(NotificationDispatchWorker.RETRY_WINDOW_HOURS + 1));
        sender.next = NotificationResult.FAILED;

        worker.dispatchOne(1L);

        assertThat(ns.getStatus())
                .as("영영 대기로 두면 밀린 예약이 계속 쌓여 진짜 고장을 덮는다")
                .isEqualTo(NotificationScheduleStatus.SENT);
    }

    /** 이미 받은 사람에게 두 번 가면 안 된다 — 재시도는 못 받은 사람에게만. */
    @Test
    @DisplayName("재시도는 이미 받은 사람을 건너뛴다")
    void retry_skips_those_already_reached() {
        armed(TimeUtil.now().minusMinutes(1));
        Mockito.when(logRepo.existsByMemberIdAndNotificationScheduleId(Mockito.any(), Mockito.any()))
                .thenReturn(true);
        sender.next = NotificationResult.SUCCESS;

        worker.dispatchOne(1L);

        assertThat(sender.calls).as("이미 받은 사람에게 또 보냈다").isZero();
    }
}

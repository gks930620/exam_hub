package com.test.test.integration;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.AuthProvider;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.NotificationEventType;
import com.test.test.exam.domain.NotificationSchedule;
import com.test.test.exam.domain.NotificationScheduleStatus;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.domain.Series;
import com.test.test.exam.domain.UserFavorite;
import com.test.test.exam.notification.NotificationDispatchService;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import com.test.test.exam.repository.MemberRepository;
import com.test.test.exam.repository.NotificationLogRepository;
import com.test.test.exam.repository.NotificationScheduleRepository;
import com.test.test.exam.repository.UserFavoriteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 발송 배치 — 건별 독립 트랜잭션.
 *
 * <p>배치 전체가 한 트랜잭션이면 한 건의 실패가 앞서 보낸 발송 로그까지 되돌려 <b>중복 발송</b>의 문이 열린다.
 * 그래서 건별로 새 트랜잭션을 연다. 새 트랜잭션은 테스트 트랜잭션 안의 미커밋 데이터를 못 보므로
 * 이 테스트는 데이터를 실제로 커밋하고 끝나면 지운다.
 *
 * <p>취소된 회차의 <b>취소 안내</b>(SCHEDULE_CHANGED)는 나가야 하고, 같은 회차의 시각 알림(D-7 등)은 떨어져야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationDispatchIntegrationTest {

    @Autowired private NotificationDispatchService dispatchService;
    @Autowired private TransactionTemplate tx;
    @Autowired private MemberRepository memberRepository;
    @Autowired private CertificateRepository certificateRepository;
    @Autowired private ExamScheduleRepository examScheduleRepository;
    @Autowired private UserFavoriteRepository userFavoriteRepository;
    @Autowired private NotificationScheduleRepository notificationScheduleRepository;
    @Autowired private NotificationLogRepository notificationLogRepository;

    private Long memberId;
    private Long certId;
    private Long scheduleId;
    private Long cancelNoticeId;
    private Long timedAlertId;

    @AfterEach
    void cleanup() {
        tx.executeWithoutResult(s -> {
            if (scheduleId != null) {
                ExamSchedule schedule = examScheduleRepository.findById(scheduleId).orElse(null);
                if (schedule != null) {
                    notificationScheduleRepository.findByExamSchedule(schedule).forEach(ns -> {
                        notificationLogRepository.findAll().stream()
                                .filter(l -> l.getNotificationSchedule().getId().equals(ns.getId()))
                                .forEach(notificationLogRepository::delete);
                        notificationScheduleRepository.delete(ns);
                    });
                    examScheduleRepository.delete(schedule);
                }
            }
            if (memberId != null && certId != null) {
                userFavoriteRepository.findByMemberIdAndCertificateId(memberId, certId)
                        .ifPresent(userFavoriteRepository::delete);
            }
            if (certId != null) certificateRepository.deleteById(certId);
            if (memberId != null) memberRepository.deleteById(memberId);
        });
    }

    private void givenCanceledRoundWithFavoriteAndDueAlerts() {
        tx.executeWithoutResult(s -> {
            String unique = UUID.randomUUID().toString().substring(0, 8);
            Member member = memberRepository.save(Member.builder()
                    .provider(AuthProvider.GOOGLE).providerId("dispatch-" + unique)
                    .nickname("발송" + unique).build());
            Certificate cert = certificateRepository.save(Certificate.builder()
                    .name("발송시험 " + unique).slug("발송시험-" + unique)
                    .series(Series.ETC).agency("테스트시행처").build());
            userFavoriteRepository.save(UserFavorite.builder().member(member).certificate(cert).build());
            LocalDate today = TimeUtil.today();
            ExamSchedule schedule = examScheduleRepository.save(ExamSchedule.builder()
                    .certificate(cert).year(today.getYear()).round(1).examType(ExamType.WRITTEN)
                    .examStartDate(today.plusDays(30)).status(ScheduleStatus.CANCELED)
                    .provenance(ScheduleProvenance.MANUAL).build());
            NotificationSchedule notice = notificationScheduleRepository.save(NotificationSchedule.builder()
                    .examSchedule(schedule).eventType(NotificationEventType.SCHEDULE_CHANGED)
                    .sendAt(TimeUtil.now().minusMinutes(1)).status(NotificationScheduleStatus.PENDING).build());
            NotificationSchedule timed = notificationScheduleRepository.save(NotificationSchedule.builder()
                    .examSchedule(schedule).eventType(NotificationEventType.EXAM_D7)
                    .sendAt(TimeUtil.now().minusMinutes(1)).status(NotificationScheduleStatus.PENDING).build());
            memberId = member.getId();
            certId = cert.getId();
            scheduleId = schedule.getId();
            cancelNoticeId = notice.getId();
            timedAlertId = timed.getId();
        });
    }

    @Test
    @DisplayName("취소된 회차: 취소 안내는 나가고, 시각 알림은 떨어진다 — 건별 트랜잭션으로")
    void cancel_notice_is_sent_and_timed_alert_is_dropped() {
        givenCanceledRoundWithFavoriteAndDueAlerts();

        int sent = dispatchService.dispatchDue();

        assertTrue(sent >= 1, "취소 안내가 한 건도 안 나갔다");
        assertEquals(NotificationScheduleStatus.SENT,
                notificationScheduleRepository.findById(cancelNoticeId).orElseThrow().getStatus(),
                "취소된 회차라고 취소 안내까지 버렸다");
        assertEquals(NotificationScheduleStatus.CANCELED,
                notificationScheduleRepository.findById(timedAlertId).orElseThrow().getStatus(),
                "취소된 회차의 D-7 알림이 살아 있다");
        assertTrue(notificationLogRepository.existsByMemberIdAndNotificationScheduleId(memberId, cancelNoticeId),
                "발송 로그가 없다 — 멱등 처리가 안 된다");
    }
}

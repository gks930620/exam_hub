package com.test.test.exam.notification;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.*;
import com.test.test.exam.repository.NotificationLogRepository;
import com.test.test.exam.repository.NotificationScheduleRepository;
import com.test.test.exam.repository.UserFavoriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 발송 배치 (설계 04 §3-4). 도래한 PENDING 예약 → 관심 사용자 × 토글 필터 → 발송 →
 * notification_log UNIQUE 제약으로 멱등 처리 → 예약 SENT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private final NotificationScheduleRepository notificationScheduleRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final NotificationContentFactory contentFactory;
    private final NotificationSender sender; // 활성 구현 1개(현재 Log, 채널 확정 시 웹 푸시/이메일)

    /** 도래한 예약을 모두 처리. 반환: 실제 발송(신규 로그) 건수. */
    @Transactional
    public int dispatchDue() {
        LocalDateTime now = TimeUtil.now();
        List<NotificationSchedule> due = notificationScheduleRepository
                .findByStatusAndSendAtLessThanEqualOrderBySendAtAsc(
                        NotificationScheduleStatus.PENDING, now);

        int sentCount = 0;
        for (NotificationSchedule ns : due) {
            sentCount += dispatchOne(ns);
        }
        if (sentCount > 0 || !due.isEmpty()) {
            log.info("[Dispatch] 도래 예약 {}건 처리, 신규 발송 {}건", due.size(), sentCount);
        }
        return sentCount;
    }

    private int dispatchOne(NotificationSchedule ns) {
        ExamSchedule schedule = ns.getExamSchedule();

        // 안전장치: 일정이 더 이상 ACTIVE 가 아니면 취소 처리
        if (schedule.getStatus() != ScheduleStatus.ACTIVE) {
            ns.cancel();
            return 0;
        }

        NotificationEventType.ToggleTarget target = ns.getEventType().getToggleTarget();
        NotificationMessage message = contentFactory.build(
                new NotificationContentFactory.NotificationSchedule_Ref(schedule, ns.getEventType()));

        List<Member> favoritedUsers = userFavoriteRepository
                .findUsersByCertificateId(schedule.getCertificate().getId());

        int sent = 0;
        for (Member user : favoritedUsers) {
            if (!user.acceptsEvent(target)) {
                continue; // 유형별 토글 off
            }
            // 멱등: 이미 발송 로그가 있으면 스킵 (FR-27)
            if (notificationLogRepository.existsByMemberIdAndNotificationScheduleId(user.getId(), ns.getId())) {
                continue;
            }
            if (deliver(user, ns, message)) {
                sent++;
            }
        }
        ns.markSent();
        return sent;
    }

    private boolean deliver(Member user, NotificationSchedule ns, NotificationMessage message) {
        NotificationResult result = sender.send(user, message);
        try {
            notificationLogRepository.save(NotificationLog.builder()
                    .member(user)
                    .notificationSchedule(ns)
                    .channel(sender.channel())   // 체인이면 실제로 나간 채널이 담긴다
                    .sentAt(TimeUtil.now())
                    .result(result)
                    .errorMessage(result == NotificationResult.SUCCESS ? null : result.name())
                    .build());
        } catch (DataIntegrityViolationException dup) {
            // 동시 배치 실행 경합 — UNIQUE 제약이 중복 발송을 최종 차단 (NFR-03)
            log.debug("[Dispatch] 멱등 충돌 무시 user={} ns={}", user.getId(), ns.getId());
            return false;
        }

        // 구독 만료(웹 푸시 410 등) → 로그로 남긴다. 만료 구독 삭제는 채널 확정 후 구독 모델과 함께 붙인다.
        if (result == NotificationResult.SUBSCRIPTION_EXPIRED) {
            log.warn("[Dispatch] 구독 만료 user={} — 채널 구현 시 만료 구독 정리 필요", user.getId());
        }
        return result == NotificationResult.SUCCESS;
    }
}

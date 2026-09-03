package com.test.test.exam.notification;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.NotificationEventType;
import com.test.test.exam.domain.NotificationLog;
import com.test.test.exam.domain.NotificationResult;
import com.test.test.exam.domain.NotificationSchedule;
import com.test.test.exam.domain.NotificationScheduleStatus;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.repository.NotificationLogRepository;
import com.test.test.exam.repository.NotificationScheduleRepository;
import com.test.test.exam.repository.UserFavoriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 발송 예약 <b>한 건</b>의 처리 — 항상 새 트랜잭션({@code REQUIRES_NEW}).
 *
 * <p>{@link NotificationDispatchService} 와 빈을 나눈 이유: 같은 빈 안에서 {@code this.dispatchOne()} 을
 * 부르면 프록시를 거치지 않아 트랜잭션 속성이 먹지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatchWorker {

    private final NotificationScheduleRepository notificationScheduleRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final NotificationContentFactory contentFactory;
    private final NotificationSender sender; // 체인(@Primary): 알림톡 → 이메일 → 로그

    /** 반환: 실제 발송 건수. 예약이 그새 사라졌거나 PENDING 이 아니면 0. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int dispatchOne(Long notificationScheduleId) {
        NotificationSchedule ns = notificationScheduleRepository.findById(notificationScheduleId).orElse(null);
        if (ns == null || ns.getStatus() != NotificationScheduleStatus.PENDING) {
            return 0;   // 조회와 처리 사이에 다른 배치가 먼저 처리했다
        }
        ExamSchedule schedule = ns.getExamSchedule();

        // 안전장치: 일정이 더 이상 ACTIVE 가 아니면 시각 알림은 떨어뜨린다.
        // 단 취소된 회차의 "취소·연기 안내"(SCHEDULE_CHANGED)는 바로 그 상황을 알리는 것이라 나가야 한다.
        boolean cancelNotice = schedule.getStatus() == ScheduleStatus.CANCELED
                && ns.getEventType() == NotificationEventType.SCHEDULE_CHANGED;
        if (!schedule.isActive() && !cancelNotice) {
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

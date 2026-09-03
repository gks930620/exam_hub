package com.test.test.exam.notification;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.NotificationEventType;
import com.test.test.exam.domain.NotificationSchedule;
import com.test.test.exam.domain.NotificationScheduleStatus;
import com.test.test.exam.repository.NotificationScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * exam_schedule → notification_schedule 파생/재계산 (설계 04 §3-2, §3-3).
 * 수집 INSERT/UPDATE 시 이벤트별 발송 시각을 재생성한다. 발송 배치는 이 결과만 단순 조회.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScheduleService {

    private final NotificationScheduleRepository repository;

    /**
     * 회차 일정 1건의 알림 예약 재계산.
     *
     * @param scheduleChanged 접수/시험일이 실제 변경되었는지 → SCHEDULE_CHANGED 발송 트리거.
     *                        ACTIVE 가 아닌 회차(취소·보류)에 true 를 주면 "취소/연기" 안내 한 건이 즉시 예약된다 —
     *                        대기 알림만 조용히 지우면 접수해 둔 사용자는 취소를 모른다.
     */
    @Transactional
    public void recalc(ExamSchedule s, boolean scheduleChanged) {
        // ACTIVE 가 아니면(취소/보류/완료) 대기 예약을 취소
        if (!s.isActive()) {
            repository.findByExamSchedule(s).forEach(ns -> {
                if (ns.getStatus() == NotificationScheduleStatus.PENDING) {
                    ns.cancel();
                }
            });
            if (scheduleChanged) {
                upsertChangeAlert(s, TimeUtil.now());
            }
            return;
        }

        LocalDateTime now = TimeUtil.now();

        upsertTimed(s, NotificationEventType.REG_OPEN_EVE, regOpenEve(s), now);
        upsertTimed(s, NotificationEventType.REG_OPEN_DAY, regOpenDay(s), now);
        upsertTimed(s, NotificationEventType.REG_CLOSE_EVE, regCloseEve(s), now);
        upsertTimed(s, NotificationEventType.EXAM_D7, examD7(s), now);
        upsertTimed(s, NotificationEventType.EXAM_D1, examD1(s), now);

        if (scheduleChanged) {
            upsertChangeAlert(s, now);
        }
    }

    private void upsertTimed(ExamSchedule s, NotificationEventType type, LocalDateTime sendAt, LocalDateTime now) {
        if (sendAt == null) {
            return; // 근거 필드 없음 → 이 이벤트는 생성 안 함
        }
        NotificationScheduleStatus status = sendAt.isAfter(now)
                ? NotificationScheduleStatus.PENDING
                : NotificationScheduleStatus.SKIPPED; // 과거 시점

        repository.findByExamScheduleAndEventType(s, type).ifPresentOrElse(existing -> {
            // 이미 발송된 건은 다시 손대지 않음(중복 발송 방지)
            if (existing.getStatus() == NotificationScheduleStatus.SENT) {
                return;
            }
            existing.reschedule(sendAt, status);
        }, () -> repository.save(NotificationSchedule.builder()
                .examSchedule(s).eventType(type).sendAt(sendAt).status(status).build()));
    }

    /** 변경 알림: 다음 배치에서 즉시 발송 (send_at=now, PENDING). 재변경 시 다시 PENDING 으로 리셋. */
    private void upsertChangeAlert(ExamSchedule s, LocalDateTime now) {
        repository.findByExamScheduleAndEventType(s, NotificationEventType.SCHEDULE_CHANGED)
                .ifPresentOrElse(
                        existing -> existing.reschedule(now, NotificationScheduleStatus.PENDING),
                        () -> repository.save(NotificationSchedule.builder()
                                .examSchedule(s)
                                .eventType(NotificationEventType.SCHEDULE_CHANGED)
                                .sendAt(now)
                                .status(NotificationScheduleStatus.PENDING)
                                .build()));
    }

    // ===== 발송 시각 계산 (설계 04 §2-5 이벤트 유형 규칙) =====

    private LocalDateTime regOpenEve(ExamSchedule s) {
        return s.getRegStartAt() == null ? null
                : s.getRegStartAt().toLocalDate().minusDays(1).atTime(20, 0);
    }

    private LocalDateTime regOpenDay(ExamSchedule s) {
        return s.getRegStartAt() == null ? null
                : s.getRegStartAt().toLocalDate().atTime(9, 0);
    }

    private LocalDateTime regCloseEve(ExamSchedule s) {
        return s.getRegEndAt() == null ? null
                : s.getRegEndAt().toLocalDate().minusDays(1).atTime(20, 0);
    }

    private LocalDateTime examD7(ExamSchedule s) {
        return s.getExamStartDate() == null ? null
                : s.getExamStartDate().minusDays(7).atTime(9, 0);
    }

    private LocalDateTime examD1(ExamSchedule s) {
        return s.getExamStartDate() == null ? null
                : s.getExamStartDate().minusDays(1).atTime(20, 0);
    }
}

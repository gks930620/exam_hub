package com.test.test.exam.repository;

import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.NotificationEventType;
import com.test.test.exam.domain.NotificationSchedule;
import com.test.test.exam.domain.NotificationScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationScheduleRepository extends JpaRepository<NotificationSchedule, Long> {

    Optional<NotificationSchedule> findByExamScheduleAndEventType(
            ExamSchedule examSchedule, NotificationEventType eventType);

    List<NotificationSchedule> findByExamSchedule(ExamSchedule examSchedule);

    /** 발송 배치: 도래한 대기 예약 */
    List<NotificationSchedule> findByStatusAndSendAtLessThanEqualOrderBySendAtAsc(
            NotificationScheduleStatus status, LocalDateTime now);
}

package com.test.test.exam.repository;

import com.test.test.exam.domain.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    /** 멱등 발송 확인 (FR-27) */
    boolean existsByMemberIdAndNotificationScheduleId(Long memberId, Long notificationScheduleId);

    /** 보존 정책: 180일 경과분 삭제 */
    @Modifying
    @Query("DELETE FROM NotificationLog l WHERE l.sentAt < :threshold")
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold);
}

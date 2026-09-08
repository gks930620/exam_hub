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

    /**
     * 그 예약의 발송 이력을 지운다 — <b>변경·취소 알림을 다시 무장할 때</b> 쓴다.
     *
     * <p>멱등키가 (회원, 예약)이라 예약 행을 재사용하면 <b>이미 한 번 받은 사람은 두 번째 사건을
     * 영영 못 받는다.</b> 시험일이 또 옮겨지거나 회차가 취소되는 건 흔한 일이라, 새 사건을 무장할 때
     * 그 예약의 이력을 비워 같은 사람에게 다시 갈 수 있게 한다(2026-09-04).
     */
    void deleteByNotificationScheduleId(Long notificationScheduleId);

    /** 보존 정책: 180일 경과분 삭제 */
    @Modifying
    @Query("DELETE FROM NotificationLog l WHERE l.sentAt < :threshold")
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold);
}

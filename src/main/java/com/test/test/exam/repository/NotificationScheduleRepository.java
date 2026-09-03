package com.test.test.exam.repository;

import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.NotificationEventType;
import com.test.test.exam.domain.NotificationSchedule;
import com.test.test.exam.domain.NotificationScheduleStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationScheduleRepository extends JpaRepository<NotificationSchedule, Long> {

    Optional<NotificationSchedule> findByExamScheduleAndEventType(
            ExamSchedule examSchedule, NotificationEventType eventType);

    List<NotificationSchedule> findByExamSchedule(ExamSchedule examSchedule);

    /**
     * 발송 배치: 도래한 대기 예약의 id — 상한(Pageable)을 둔다.
     * 엔티티가 아니라 id 를 주는 이유: 건별 처리가 각자 새 트랜잭션에서 다시 읽기 때문이다.
     */
    @Query("""
            SELECT n.id FROM NotificationSchedule n
             WHERE n.status = :status AND n.sendAt <= :now
             ORDER BY n.sendAt ASC
            """)
    List<Long> findDueIds(@Param("status") NotificationScheduleStatus status,
                          @Param("now") LocalDateTime now,
                          Pageable pageable);
}

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

    /**
     * 발송 시각이 <b>이미 지났는데 아직 대기</b>인 예약 수 — 발송이 막혔다는 신호다.
     *
     * <p>배치는 5분마다 도는데 그 사이 쌓일 수 있으니 소수는 정상이다. 그런데 이 수가 줄지 않고
     * 남아 있으면 발송이 계속 실패하며 재시도만 하고 있다는 뜻이다(SMTP 자격증명 만료가 이렇게 보인다).
     */
    long countByStatusAndSendAtLessThanEqual(NotificationScheduleStatus status, LocalDateTime now);

    /** 아직 발송 시각이 안 된 대기 예약 수 — 앞으로 나갈 것. */
    long countByStatusAndSendAtGreaterThan(NotificationScheduleStatus status, LocalDateTime now);
}

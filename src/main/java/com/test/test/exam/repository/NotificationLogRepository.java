package com.test.test.exam.repository;

import com.test.test.exam.domain.NotificationLog;
import com.test.test.exam.domain.NotificationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 최근 발송을 <b>채널별로</b> 센다 — 성공/실패로는 안 보이는 것을 보기 위해서다.
     *
     * <p>발송 체인의 마지막 {@code LogNotificationSender} 는 서버 로그에 한 줄 찍고
     * <b>언제나 SUCCESS 를 돌려준다.</b> 그래서 채널을 안 가르면 아무도 못 받은 날도 성공률 100% 로
     * 보인다. LOG 로 몇 건이 흘러갔는지가 이 서비스에서 가장 중요한 숫자 중 하나다.
     *
     * @return {채널, 건수} 배열. 건수가 0 인 채널은 아예 빠진다.
     */
    @Query("""
            SELECT l.channel, COUNT(l) FROM NotificationLog l
             WHERE l.sentAt >= :since AND l.result = :result
             GROUP BY l.channel
            """)
    List<Object[]> countByChannelSince(@Param("since") LocalDateTime since,
                                       @Param("result") NotificationResult result);

    /** 최근 기간에 실패로 기록된 건수(구독 만료 포함 — 어느 쪽이든 사람에게는 안 갔다). */
    @Query("""
            SELECT COUNT(l) FROM NotificationLog l
             WHERE l.sentAt >= :since AND l.result <> com.test.test.exam.domain.NotificationResult.SUCCESS
            """)
    long countFailedSince(@Param("since") LocalDateTime since);
}

package com.test.test.exam.repository;

import com.test.test.exam.domain.CrawlLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CrawlLogRepository extends JpaRepository<CrawlLog, Long> {

    /** 장애 대응: 최근 실행 N건 조회 (2회 연속 실패 판정) */
    List<CrawlLog> findBySourceOrderByStartedAtDesc(String source, Pageable pageable);

    /** 보존 정책: 90일 경과분 삭제 */
    @Modifying
    @Query("DELETE FROM CrawlLog c WHERE c.startedAt < :threshold")
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold);
}

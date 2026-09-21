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

    /**
     * 수집 건강 화면용 — 최근 기록을 통째로 읽어 소스별로 묶는다.
     *
     * <p>소스마다 따로 물으면 24번 왕복한다. 기록은 90일치뿐이고 하루 한 번 도는 배치라
     * 양이 작다 — 한 번에 읽어 메모리에서 가른다.
     */
    List<CrawlLog> findByStartedAtAfterOrderByStartedAtDesc(LocalDateTime threshold);

    /** 보존 정책: 90일 경과분 삭제 */
    @Modifying
    @Query("DELETE FROM CrawlLog c WHERE c.startedAt < :threshold")
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold);
}

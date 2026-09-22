package com.test.test.exam.repository;

import com.test.test.exam.domain.CertificateDetail;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateDetailRepository extends JpaRepository<CertificateDetail, Long> {

    Optional<CertificateDetail> findByCertificateId(Long certificateId);

    List<CertificateDetail> findByCertificateIdIn(List<Long> certificateIds);

    /**
     * <b>아직 안 받았거나 오래된</b> 시험의 종목코드 — 다음에 받아 올 것들.
     *
     * <p>한 번에 613콜을 지르지 않고 조금씩 채운다. 큐넷 하루 한도가 1,000회인데 일정 수집이
     * 이미 매일 613콜을 쓰고 있어서(문서마다 한도가 계정당인지 오퍼레이션당인지 다르게 적혀 있다),
     * 남은 예산을 모르는 채로 한 번에 다 부르면 그날 일정 수집까지 같이 죽는다.
     *
     * <p>이 정보는 일정과 달리 <b>거의 안 변한다</b> — 매달 한 번씩 조금씩 채우면 충분하다.
     * 안 받은 것이 먼저 오고, 그 다음이 오래된 것이다.
     */
    @Query("""
            SELECT c.sourceCode FROM Certificate c
             LEFT JOIN CertificateDetail d ON d.certificate = c
             WHERE c.sourceCode IS NOT NULL AND c.sourceCode <> ''
               AND c.agency = :agency
               AND c.lifecycle IN (com.test.test.exam.domain.CertificateLifecycle.ACTIVE, com.test.test.exam.domain.CertificateLifecycle.UNVERIFIED)
               AND (d.id IS NULL OR d.collectedAt < :staleBefore)
             ORDER BY CASE WHEN d.id IS NULL THEN 0 ELSE 1 END, d.collectedAt ASC, c.sourceCode ASC
            """)
    List<String> findCodesNeedingDetail(@Param("agency") String agency,
                                        @Param("staleBefore") LocalDateTime staleBefore,
                                        Pageable pageable);

    /** 얼마나 채웠나 — 매니저가 진행을 볼 수 있게. */
    @Query("""
            SELECT COUNT(d) FROM CertificateDetail d
             WHERE d.certificate.agency = :agency
            """)
    long countByAgency(@Param("agency") String agency);
}

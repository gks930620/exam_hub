package com.test.test.exam.repository;

import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.Series;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    Optional<Certificate> findBySlug(String slug);

    Optional<Certificate> findBySourceCode(String sourceCode);

    /**
     * 이름으로 찾기(공백 무시). 수집이 <b>같은 시험을 하나 더 만드는 것</b>을 막는 마지막 방어선이다.
     * 시드마다 종목코드가 달라(M0002 vs KCA-SEC) 코드로는 못 찾는 경우가 실제로 반복됐다.
     */
    @Query("SELECT c FROM Certificate c WHERE REPLACE(c.name, ' ', '') = REPLACE(:name, ' ', '')")
    List<Certificate> findByNameIgnoringSpaces(@Param("name") String name);

    boolean existsBySlug(String slug);

    /**
     * 자격증명 부분 일치 검색 (대소문자 무시).
     * <b>폐지·개칭된 시험은 뺀다</b> — 오지 않을 접수를 기다리게 두면 안 된다.
     */
    @Query("SELECT c FROM Certificate c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) AND c.lifecycle IN (com.test.test.exam.domain.CertificateLifecycle.ACTIVE, com.test.test.exam.domain.CertificateLifecycle.UNVERIFIED) ORDER BY c.favoriteCount DESC, c.name ASC")
    List<Certificate> searchByName(@Param("q") String q, Pageable pageable);

    /** 인기순 TOP N */
    List<Certificate> findAllByOrderByFavoriteCountDescNameAsc(Pageable pageable);

    /** 매니저용 — 폐지·개칭 기록(시험 변천사). 사용자 화면에서 사라진 것들이 여기 남는다. */
    @Query("""
            SELECT c FROM Certificate c
             WHERE c.lifecycle <> com.test.test.exam.domain.CertificateLifecycle.ACTIVE
             ORDER BY c.lifecycle ASC, c.name ASC
            """)
    List<Certificate> findLifecycleHistory();

    List<Certificate> findAllByOrderBySeriesAscNameAsc();

    List<Certificate> findBySeriesOrderByNameAsc(Series series);

    /**
     * 전체 둘러보기 — 검색어·분류는 둘 다 선택. 일정 유무와 무관하게 마스터 전체를 대상으로 한다.
     * (검색어가 없으면 전체, 분류가 없으면 모든 분류)
     */
    @Query("""
            SELECT c FROM Certificate c
             WHERE (:q = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')))
               AND (:category = '' OR c.category = :category)
               AND c.lifecycle IN (com.test.test.exam.domain.CertificateLifecycle.ACTIVE, com.test.test.exam.domain.CertificateLifecycle.UNVERIFIED)
            """)
    Page<Certificate> browse(@Param("q") String q, @Param("category") String category, Pageable pageable);

    /** 분류별 종목 수 — 필터 칩에 개수를 같이 보여주기 위한 것. */
    @Query("""
            SELECT c.category, COUNT(c) FROM Certificate c
             WHERE c.category IS NOT NULL AND c.category <> ''
               AND c.lifecycle IN (com.test.test.exam.domain.CertificateLifecycle.ACTIVE, com.test.test.exam.domain.CertificateLifecycle.UNVERIFIED)
             GROUP BY c.category
             ORDER BY COUNT(c) DESC, c.category ASC
            """)
    List<Object[]> countByCategory();
}

package com.test.test.exam.repository;

import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.Series;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    Optional<Certificate> findBySlug(String slug);

    Optional<Certificate> findBySourceCode(String sourceCode);

    /** 재수집 대상의 시행기관을 알아내려고 쓴다 — 그 기관을 담당하는 스크래퍼만 부르기 위해서다. */
    List<Certificate> findBySourceCodeIn(Collection<String> sourceCodes);

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

    /** 인기순 TOP N — 폐지·개칭은 뺀다(검색·둘러보기와 같은 모집단). */
    @Query("""
            SELECT c FROM Certificate c
             WHERE c.lifecycle IN (com.test.test.exam.domain.CertificateLifecycle.ACTIVE, com.test.test.exam.domain.CertificateLifecycle.UNVERIFIED)
             ORDER BY c.favoriteCount DESC, c.name ASC
            """)
    List<Certificate> findPopularVisible(Pageable pageable);

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

    /**
     * <b>화면에 보이는</b> 시험 수. 폐지·개칭은 검색에서 빠지므로 여기서도 뺀다.
     * 전체 수(count())를 쓰면 매니저 화면의 "등록된 시험"만 24 크게 나와 숫자가 안 맞는다.
     */
    @Query("SELECT COUNT(c) FROM Certificate c WHERE c.lifecycle IN (com.test.test.exam.domain.CertificateLifecycle.ACTIVE, com.test.test.exam.domain.CertificateLifecycle.UNVERIFIED)")
    long countVisible();

    /**
     * 그중 <b>날짜 있는</b> 살아 있는 일정이 하나라도 있는 시험 수. 위와 같은 모집단이어야 뺄셈이 맞는다.
     * 상시도 뺀다 — 예전 시드가 상시 시험에 넣어 둔 일정이 남아 있어, 안 빼면 분자만 부풀어
     * 현황 화면의 '일정 없음' 수와 6 어긋났다(실측).
     * 연도·회차만 있고 날짜가 없는 행은 세지 않는다 — 매니저 현황·사용자 카드({@code ExamSchedule.hasAnyDate})와 같은 기준.
     */
    @Query("""
            SELECT COUNT(DISTINCT c.id) FROM Certificate c
             WHERE c.lifecycle IN (com.test.test.exam.domain.CertificateLifecycle.ACTIVE, com.test.test.exam.domain.CertificateLifecycle.UNVERIFIED)
               AND c.rollingAdmission = false
               AND EXISTS (SELECT 1 FROM ExamSchedule s
                            WHERE s.certificate = c AND s.status = com.test.test.exam.domain.ScheduleStatus.ACTIVE
                              AND (s.examEndDate IS NOT NULL OR s.examStartDate IS NOT NULL
                                   OR s.regEndAt IS NOT NULL OR s.regStartAt IS NOT NULL))
            """)
    long countVisibleWithSchedule();

    /** 상시·예약제 — "일정"이 없는 시험. 채울 대상에서 빼고 따로 센다. */
    @Query("SELECT COUNT(c) FROM Certificate c WHERE c.rollingAdmission = true AND c.lifecycle IN (com.test.test.exam.domain.CertificateLifecycle.ACTIVE, com.test.test.exam.domain.CertificateLifecycle.UNVERIFIED)")
    long countVisibleRolling();

    /**
     * <b>지금 접수 중인</b> 시험 — 마감이 급한 것부터.
     *
     * <p>조건은 {@code ExamScheduleRepository.countCertificatesWithOpenRegistration} 과 <b>같아야 한다.</b>
     * 칩에 "접수 중 232"라고 써 놓고 눌렀을 때 다른 수가 나오면 둘 중 무엇을 믿어야 할지 알 수 없다.
     *
     * <p>정렬은 그 시험에서 <b>가장 먼저 닫히는 접수의 마감</b> 기준이다. 이 목록의 쓸모가
     * 순서에 있어서다 — 이번 주에 마감되는 것을 찾으러 온 사람이 인기순 232개를 넘기면 안 된다.
     */
    @Query("""
            SELECT c FROM Certificate c
             WHERE (:q = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')))
               AND (:category = '' OR c.category = :category)
               AND c.rollingAdmission = false
               AND c.lifecycle IN (com.test.test.exam.domain.CertificateLifecycle.ACTIVE, com.test.test.exam.domain.CertificateLifecycle.UNVERIFIED)
               AND EXISTS (SELECT 1 FROM ExamSchedule s
                            WHERE s.certificate = c
                              AND s.status = com.test.test.exam.domain.ScheduleStatus.ACTIVE
                              AND s.regStartAt <= :now AND s.regEndAt >= :now)
             ORDER BY (SELECT MIN(s2.regEndAt) FROM ExamSchedule s2
                        WHERE s2.certificate = c
                          AND s2.status = com.test.test.exam.domain.ScheduleStatus.ACTIVE
                          AND s2.regStartAt <= :now AND s2.regEndAt >= :now) ASC, c.name ASC
            """)
    Page<Certificate> browseOpen(@Param("q") String q, @Param("category") String category,
                                 @Param("now") LocalDateTime now, Pageable pageable);

    /**
     * <b>접수가 곧 시작되는</b> 시험 — 시작이 가까운 것부터.
     *
     * <p>조건은 {@code ExamScheduleRepository.countCertificatesWithRegistrationOpening} 과 같다.
     *
     * <p><b>이미 접수 중인 시험은 뺀다.</b> 한 회차는 열려 있고 다른 회차가 곧 열리는 시험이 있는데
     * (HSK 등), 카드는 가장 가까운 사건을 보여주므로 목록이 D-1 · D-15 · D-2 처럼 뒤죽박죽으로
     * 보인다(2026-09-22 실측). 이미 열렸으면 "지금 접수 중"이지 "곧 시작"이 아니다.
     */
    @Query("""
            SELECT c FROM Certificate c
             WHERE (:q = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')))
               AND (:category = '' OR c.category = :category)
               AND c.rollingAdmission = false
               AND c.lifecycle IN (com.test.test.exam.domain.CertificateLifecycle.ACTIVE, com.test.test.exam.domain.CertificateLifecycle.UNVERIFIED)
               AND EXISTS (SELECT 1 FROM ExamSchedule s
                            WHERE s.certificate = c
                              AND s.status = com.test.test.exam.domain.ScheduleStatus.ACTIVE
                              AND s.regStartAt > :from AND s.regStartAt <= :to)
               AND NOT EXISTS (SELECT 1 FROM ExamSchedule o
                                WHERE o.certificate = c
                                  AND o.status = com.test.test.exam.domain.ScheduleStatus.ACTIVE
                                  AND o.regStartAt <= :from AND o.regEndAt >= :from)
             ORDER BY (SELECT MIN(s2.regStartAt) FROM ExamSchedule s2
                        WHERE s2.certificate = c
                          AND s2.status = com.test.test.exam.domain.ScheduleStatus.ACTIVE
                          AND s2.regStartAt > :from AND s2.regStartAt <= :to) ASC, c.name ASC
            """)
    Page<Certificate> browseSoon(@Param("q") String q, @Param("category") String category,
                                 @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                 Pageable pageable);
}

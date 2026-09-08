package com.test.test.exam.repository;

import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExamScheduleRepository extends JpaRepository<ExamSchedule, Long> {

    /** 수집 upsert 자연 키 조회 */
    Optional<ExamSchedule> findByCertificateAndYearAndRoundAndExamType(
            Certificate certificate, Integer year, Integer round, ExamType examType);

    List<ExamSchedule> findByCertificateIdAndStatusOrderByExamStartDateAsc(
            Long certificateId, ScheduleStatus status);

    /** 여러 상태를 한 번에 — 상세(ACTIVE+CANCELED), 매니저 목록(ACTIVE+PENDING_REVIEW) */
    List<ExamSchedule> findByCertificateIdAndStatusInOrderByExamStartDateAsc(
            Long certificateId, Collection<ScheduleStatus> statuses);

    List<ExamSchedule> findByCertificateIdAndYearAndStatusOrderByRoundAscExamTypeAsc(
            Long certificateId, Integer year, ScheduleStatus status);

    /** 여러 자격증의 특정 상태 일정 일괄 조회 (홈 D-day 카드/캘린더용) */
    List<ExamSchedule> findByCertificateIdInAndStatus(List<Long> certificateIds, ScheduleStatus status);

    /** 여러 자격증의 여러 상태 일정 일괄 조회 (매니저 현황 — 보류까지 판정에 넣는다) */
    List<ExamSchedule> findByCertificateIdInAndStatusIn(List<Long> certificateIds, Collection<ScheduleStatus> statuses);

    /**
     * 주어진 자격증들 중 <b>일정이 하나라도 있는</b> 것의 id.
     * 목록에서 "일정 미정" 배지를 붙이기 위한 것 — 항목마다 조회하면 N+1 이 되므로 한 번에 가져온다.
     */
    @Query("SELECT DISTINCT s.certificate.id FROM ExamSchedule s WHERE s.certificate.id IN :ids")
    List<Long> findCertificateIdsHavingSchedule(@Param("ids") List<Long> ids);

    /** 접수 시작 7일 이내 임박 종목의 자격증 id (임박 재확인 배치용) */
    @Query("""
            SELECT DISTINCT s.certificate.id FROM ExamSchedule s
            WHERE s.status = com.test.test.exam.domain.ScheduleStatus.ACTIVE
              AND s.regStartAt IS NOT NULL
              AND s.regStartAt BETWEEN :from AND :to
            """)
    List<Long> findCertificateIdsWithImminentRegistration(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    List<ExamSchedule> findByExamStartDateBetween(LocalDate from, LocalDate to);

    /** 어떤 출처로 가장 최근에 받은 시각 — 스냅샷이 "DB 가 파일보다 새로운가"를 묻는다. 없으면 null. */
    @Query("SELECT MAX(s.collectedAt) FROM ExamSchedule s WHERE s.provenance = :provenance")
    LocalDateTime findLatestCollectedAt(@Param("provenance") ScheduleProvenance provenance);

    /** 지금 접수 중인 시험 수 — 첫 화면 지표. 폐지·개칭과 상시는 뺀다(같은 줄의 다른 숫자와 모집단을 맞춘다). */
    @Query("""
            SELECT COUNT(DISTINCT s.certificate.id) FROM ExamSchedule s
            WHERE s.status = com.test.test.exam.domain.ScheduleStatus.ACTIVE
              AND s.regStartAt <= :now AND s.regEndAt >= :now
              AND s.certificate.rollingAdmission = false
              AND s.certificate.lifecycle IN (com.test.test.exam.domain.CertificateLifecycle.ACTIVE, com.test.test.exam.domain.CertificateLifecycle.UNVERIFIED)
            """)
    long countCertificatesWithOpenRegistration(@Param("now") LocalDateTime now);

    /** 접수가 곧 시작되는 시험 수 — 첫 화면 지표. */
    @Query("""
            SELECT COUNT(DISTINCT s.certificate.id) FROM ExamSchedule s
            WHERE s.status = com.test.test.exam.domain.ScheduleStatus.ACTIVE
              AND s.regStartAt > :from AND s.regStartAt <= :to
              AND s.certificate.rollingAdmission = false
              AND s.certificate.lifecycle IN (com.test.test.exam.domain.CertificateLifecycle.ACTIVE, com.test.test.exam.domain.CertificateLifecycle.UNVERIFIED)
            """)
    long countCertificatesWithRegistrationOpening(@Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to);

    /** 일정이 하나라도 살아 있는 시험의 수 — 매니저 화면의 "얼마나 채워졌나". */
    @Query("SELECT COUNT(DISTINCT s.certificate.id) FROM ExamSchedule s WHERE s.status = 'ACTIVE'")
    long countDistinctCertificateWithActiveSchedule();
}

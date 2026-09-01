package com.test.test.exam.collect;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * 수집 diff·검증 (DR-03, DR-04). 자연 키 매칭 upsert + source_hash 비교 + 날짜 무결성/이상치 판정.
 * 조회 서비스와 분리해 수집 로직만 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiffService {

    /** DR-04-b: 기존 대비 이 일수 이상 이동 시 자동 반영 보류 */
    private static final long PENDING_REVIEW_MOVE_DAYS = 30;

    private final CertificateRepository certificateRepository;
    private final ExamScheduleRepository examScheduleRepository;

    public enum DiffType {NEW, UPDATED, UNCHANGED, SKIPPED, PENDING_REVIEW}

    /** upsert 결과. schedule 은 SKIPPED 시 null. changed=알림 재계산이 필요한 실질 변경(접수/시험일). */
    public record Outcome(DiffType type, ExamSchedule schedule, boolean changed) {
        static Outcome skipped() {
            return new Outcome(DiffType.SKIPPED, null, false);
        }
    }

    /**
     * 한 건 upsert. 자연 키 (cert, year, round, examType).
     */
    @Transactional
    public Outcome upsert(CollectedSchedule rec) {
        // (c) 필수 필드 누락 → 스킵
        if (!rec.hasRequiredKeys()) {
            log.warn("[Diff] 필수 필드 누락 → 스킵: {}", rec);
            return Outcome.skipped();
        }
        // (a) 수집 원본 자체의 날짜 무결성 위반 → 스킵
        if (!isDateOrderValid(rec.regStartAt(), rec.regEndAt(),
                rec.examStartDate(), rec.examEndDate(), rec.resultDate())) {
            log.warn("[Diff] 날짜 순서 무결성 위반 → 스킵: {} {}회 {}",
                    rec.certificateName(), rec.round(), rec.examType());
            return Outcome.skipped();
        }

        Certificate cert = ensureCertificate(rec);
        LocalDateTime now = TimeUtil.now();
        String newHash = hash(rec);

        ExamSchedule existing = examScheduleRepository
                .findByCertificateAndYearAndRoundAndExamType(cert, rec.year(), rec.round(), rec.examType())
                .orElse(null);

        if (existing == null) {
            ExamSchedule saved = examScheduleRepository.save(ExamSchedule.builder()
                    .certificate(cert)
                    .year(rec.year()).round(rec.round()).examType(rec.examType())
                    .regStartAt(rec.regStartAt()).regEndAt(rec.regEndAt())
                    .examStartDate(rec.examStartDate()).examEndDate(rec.examEndDate())
                    .resultDate(rec.resultDate())
                    .status(ScheduleStatus.ACTIVE)
                    .sourceUrl(rec.sourceUrl()).sourceHash(newHash)
                    .provenance(rec.provenance())
                    .collectedAt(now)
                    .build());
            return new Outcome(DiffType.NEW, saved, true);
        }

        // 해시 동일 → 변경 없음 (수집 확인 시각만 갱신)
        if (newHash.equals(existing.getSourceHash())) {
            existing.touchCollectedAt(now);
            existing.changeProvenance(rec.provenance());
            return new Outcome(DiffType.UNCHANGED, existing, false);
        }

        // (b) 급격한 이동 → PENDING_REVIEW (수집 데이터는 반영하되 노출/알림 보류)
        boolean bigMove = isBigMove(existing, rec);
        boolean scheduleChanged = isScheduleChanged(existing, rec);

        existing.changeProvenance(rec.provenance());
        existing.applyFrom(rec.regStartAt(), rec.regEndAt(),
                rec.examStartDate(), rec.examEndDate(), rec.resultDate(),
                rec.sourceUrl(), newHash,
                bigMove ? ScheduleStatus.PENDING_REVIEW : ScheduleStatus.ACTIVE,
                now);
        examScheduleRepository.save(existing);

        if (bigMove) {
            log.warn("[Diff] 30일 이상 일정 이동 감지 → PENDING_REVIEW: {} {}회 {} (관리자 확인 필요)",
                    rec.certificateName(), rec.round(), rec.examType());
            return new Outcome(DiffType.PENDING_REVIEW, existing, false);
        }
        return new Outcome(DiffType.UPDATED, existing, scheduleChanged);
    }

    private Certificate ensureCertificate(CollectedSchedule rec) {
        return certificateRepository.findBySourceCode(rec.sourceCode())
                .map(c -> {
                    // 마스터 메타가 바뀌었으면 반영
                    if (!c.getName().equals(rec.certificateName())
                            || c.getSeries() != rec.series()
                            || !c.getAgency().equals(rec.agency())
                            || !java.util.Objects.equals(c.getCategory(), rec.category())) {
                        c.updateMeta(rec.certificateName(), rec.series(), rec.agency(), rec.category());
                        certificateRepository.save(c);
                    }
                    return c;
                })
                .or(() -> byName(rec))
                .orElseGet(() -> certificateRepository.save(Certificate.builder()
                        .name(rec.certificateName())
                        .slug(uniqueSlug(rec.certificateName()))
                        .series(rec.series())
                        .agency(rec.agency())
                        .category(rec.category())
                        .sourceCode(rec.sourceCode())
                        .build()));
    }

    /**
     * 종목코드로 못 찾았을 때 <b>이름으로 한 번 더</b> 찾는다.
     *
     * <p>같은 시험이 시드마다 다른 코드를 갖고 있는 일이 잦다(정보보안기사는 마스터가 M0002,
     * 비큐넷 시드가 KCA-SEC). 코드만 보고 없으면 새로 만들어서 <b>같은 이름의 시험이 둘</b>이 됐다 —
     * 토익·유통관리사·정보보안기사에서 실제로 겪었다.
     *
     * <p>찾으면 그 시험에 이번 코드를 <b>연결</b>해 다음부터는 코드로 바로 찾게 한다.
     * 이미 다른 코드가 붙어 있으면 건드리지 않는다(먼저 붙은 쪽이 기준).
     */
    private Optional<Certificate> byName(CollectedSchedule rec) {
        List<Certificate> found = certificateRepository.findByNameIgnoringSpaces(rec.certificateName());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Certificate cert = found.get(0);
        log.info("[Diff] 종목코드로는 못 찾았지만 이름이 같은 시험이 있다 — 새로 만들지 않는다. "
                + "name={} 기존코드={} 수집코드={}", cert.getName(), cert.getSourceCode(), rec.sourceCode());
        if (cert.getSourceCode() == null && rec.sourceCode() != null) {
            cert.linkSourceCode(rec.sourceCode());
            certificateRepository.save(cert);
        }
        return Optional.of(cert);
    }

    private String uniqueSlug(String name) {
        String base = name.trim();
        if (!certificateRepository.existsBySlug(base)) {
            return base;
        }
        int i = 2;
        while (certificateRepository.existsBySlug(base + "-" + i)) {
            i++;
        }
        return base + "-" + i;
    }

    /** 접수시작 ≤ 접수마감 < 시험시작 ≤ 시험종료 < 발표 (null 필드는 검사 생략) */
    boolean isDateOrderValid(LocalDateTime regStart, LocalDateTime regEnd,
                             LocalDate examStart, LocalDate examEnd, LocalDate result) {
        // 접수시작 ≤ 접수마감
        if (regStart != null && regEnd != null && regStart.isAfter(regEnd)) return false;
        // 접수마감 < 시험시작
        if (regEnd != null && examStart != null && !regEnd.toLocalDate().isBefore(examStart)) return false;
        // 시험시작 ≤ 시험종료
        if (examStart != null && examEnd != null && examStart.isAfter(examEnd)) return false;
        // 시험종료 < 발표
        if (examEnd != null && result != null && !examEnd.isBefore(result)) return false;
        return true;
    }

    private boolean isBigMove(ExamSchedule existing, CollectedSchedule rec) {
        if (existing.getRegStartAt() != null && rec.regStartAt() != null) {
            if (Math.abs(ChronoUnit.DAYS.between(
                    existing.getRegStartAt().toLocalDate(), rec.regStartAt().toLocalDate())) >= PENDING_REVIEW_MOVE_DAYS) {
                return true;
            }
        }
        if (existing.getExamStartDate() != null && rec.examStartDate() != null) {
            return Math.abs(ChronoUnit.DAYS.between(
                    existing.getExamStartDate(), rec.examStartDate())) >= PENDING_REVIEW_MOVE_DAYS;
        }
        return false;
    }

    /** 접수기간/시험일 변경 여부 (SCHEDULE_CHANGED 알림 트리거 판정) */
    private boolean isScheduleChanged(ExamSchedule existing, CollectedSchedule rec) {
        return !java.util.Objects.equals(existing.getRegStartAt(), rec.regStartAt())
                || !java.util.Objects.equals(existing.getRegEndAt(), rec.regEndAt())
                || !java.util.Objects.equals(existing.getExamStartDate(), rec.examStartDate());
    }

    private String hash(CollectedSchedule rec) {
        String raw = String.join("|",
                String.valueOf(rec.year()), String.valueOf(rec.round()), String.valueOf(rec.examType()),
                String.valueOf(rec.regStartAt()), String.valueOf(rec.regEndAt()),
                String.valueOf(rec.examStartDate()), String.valueOf(rec.examEndDate()),
                String.valueOf(rec.resultDate()), String.valueOf(rec.sourceUrl()));
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            // SHA-256 은 모든 JVM 필수 알고리즘 — 사실상 도달 불가
            return Integer.toHexString(raw.hashCode());
        }
    }
}

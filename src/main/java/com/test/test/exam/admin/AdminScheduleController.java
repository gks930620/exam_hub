package com.test.test.exam.admin;

import com.test.test.common.exception.BusinessRuleException;
import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.notification.NotificationScheduleService;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 운영자 수기 일정 입력 (설계 07 §4-2 2단계).
 *
 * <p><b>왜 필요한가</b>: 공무원·JLPT·DELE 처럼 <b>연 1~2회</b> 시행하는 시험은
 * 스크래퍼를 만들고 유지하는 비용이 사람이 1년에 두 번 넣는 비용보다 훨씬 크다.
 * 게다가 이런 시험일수록 접수 창이 좁아 알림 가치가 최고다.
 *
 * <p>모든 경로가 {@code /api/admin/**} 이라 SecurityConfig 가 ADMIN 권한을 요구한다.
 * 사용법은 <b>운영/03_일정_직접입력.md</b>.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/schedules")
@RequiredArgsConstructor
public class AdminScheduleController {

    /** 매니저 목록에 보이는 상태 — 보류(PENDING_REVIEW)는 매니저가 확인해야 풀리므로 반드시 보여야 한다. */
    /**
     * 매니저가 보는 회차 — <b>취소한 것도 보인다.</b>
     *
     * <p>안 보이면 취소한 회차를 매니저가 잊고 같은 번호로 다시 넣어 되살린다. 사용자 상세에는
     * "취소됨"으로 남아 있는데 매니저 화면에서만 사라져 서로 다른 사실을 보게 된다(2026-09-04).
     */
    private static final List<ScheduleStatus> MANAGER_VISIBLE =
            List.of(ScheduleStatus.ACTIVE, ScheduleStatus.PENDING_REVIEW, ScheduleStatus.CANCELED);

    private final CertificateRepository certificateRepository;
    private final ExamScheduleRepository examScheduleRepository;
    private final NotificationScheduleService notificationScheduleService;

    /**
     * 특정 시험의 등록된 일정 — 입력 전에 이미 있는지 확인하는 용도. ACTIVE 와 PENDING_REVIEW 를 함께 준다(status 로 구분).
     *
     * <p>⚠️ {@code @Transactional} 이 필요하다. {@code ExamSchedule.certificate} 는 지연 로딩인데
     * {@code open-in-view=false} 라 트랜잭션이 끝나면 접근하는 순간 {@code LazyInitializationException}
     * 이 난다 — 매니저 화면의 "등록된 일정"이 통째로 500 이었다(2026-08-28).
     * 시험 이름은 이미 알고 있으니 넘겨주고, 지연 로딩을 건드리지 않는다.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<AdminDtos.ScheduleRow>> list(@RequestParam Long certificateId) {
        Certificate cert = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new EntityNotFoundException("시험을 찾을 수 없습니다."));
        List<AdminDtos.ScheduleRow> rows = examScheduleRepository
                .findByCertificateIdAndStatusInOrderByExamStartDateAsc(certificateId, MANAGER_VISIBLE)
                .stream().map(s -> AdminDtos.ScheduleRow.of(s, cert)).toList();
        return ResponseEntity.ok(rows);
    }

    /**
     * 일정 등록. 같은 (시험, 연도, 회차, 구분)이 이미 있으면 <b>덮어쓴다</b> —
     * 시행처가 일정을 바꾸는 일이 흔해서, 다시 넣는 게 자연스러운 수정 방법이다.
     * 보류·취소됐던 행도 저장하면 ACTIVE 로 돌아온다(매니저가 확인했다는 뜻).
     *
     * <p>변경 알림(SCHEDULE_CHANGED)은 <b>기존 행의 날짜가 실제로 바뀌었을 때</b>, 또는 사용자에게
     * 알리지 못한 상태(보류·취소)에서 확정으로 돌아올 때만 나간다. 새 회차를 넣는 것은 변경이 아니다.
     */
    @PostMapping
    @Transactional
    public ResponseEntity<AdminDtos.ScheduleRow> upsert(@Valid @RequestBody AdminDtos.UpsertRequest req) {
        Certificate cert = certificateRepository.findById(req.certificateId())
                .orElseThrow(() -> new EntityNotFoundException("시험을 찾을 수 없습니다."));

        validate(req);

        ExamType type = parseType(req.examType());
        Optional<ExamSchedule> found = examScheduleRepository
                .findByCertificateAndYearAndRoundAndExamType(cert, req.year(), req.round(), type);
        boolean changed = found.isPresent()
                && (!found.get().isActive()
                || found.get().hasDifferentDates(req.regStartAt(), req.regEndAt(),
                req.examStartDate(), req.examEndDate(), req.resultDate()));

        ExamSchedule schedule = found.orElseGet(() -> ExamSchedule.builder()
                .certificate(cert).year(req.year()).round(req.round()).examType(type)
                .status(ScheduleStatus.ACTIVE)
                .build());

        // 매니저가 공고를 보고 넣은 값이다 — 추정치 경고가 붙으면 안 되고, 수집이 덮어쓰지도 않는다
        schedule.changeProvenance(ScheduleProvenance.MANUAL);
        schedule.applyFrom(
                req.regStartAt(), req.regEndAt(),
                req.examStartDate(), req.examEndDate(), req.resultDate(),
                req.sourceUrl(), "MANUAL", ScheduleStatus.ACTIVE, TimeUtil.now());

        ExamSchedule saved = examScheduleRepository.save(schedule);

        // 일정이 생겼으니 알림 예약도 다시 만든다 — 이게 빠지면 넣어도 알림이 안 간다
        notificationScheduleService.recalc(saved, changed);

        log.info("[Admin] 수기 일정 저장 cert={} {}년 {}회 {} ({})",
                cert.getName(), req.year(), req.round(), type, found.isPresent() ? (changed ? "변경" : "동일") : "신규");
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminDtos.ScheduleRow.of(saved, cert));
    }

    /**
     * 일정 취소(연기·취소 공지). 물리 삭제하지 않는다 — 이미 나간 알림의 근거가 사라지면 안 된다.
     * 대기 중이던 알림은 취소되고, 관심 등록자에게 취소 안내가 한 건 나간다.
     */
    @DeleteMapping("/{scheduleId}")
    @Transactional
    public ResponseEntity<Void> cancel(@PathVariable Long scheduleId) {
        ExamSchedule schedule = examScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new EntityNotFoundException("일정을 찾을 수 없습니다."));
        schedule.changeStatus(ScheduleStatus.CANCELED);
        notificationScheduleService.recalc(schedule, true);
        log.info("[Admin] 일정 취소 scheduleId={} {}년 {}회 {}",
                scheduleId, schedule.getYear(), schedule.getRound(), schedule.getExamType());
        return ResponseEntity.noContent().build();
    }

    /** 날짜가 하나는 있어야 하고, 순서 규칙은 수집과 같은 것(ExamSchedule)을 쓴다. */
    private void validate(AdminDtos.UpsertRequest r) {
        if (r.regStartAt() == null && r.regEndAt() == null
                && r.examStartDate() == null && r.examEndDate() == null && r.resultDate() == null) {
            throw new BusinessRuleException("날짜를 하나 이상 입력하세요. 연도·회차만 있는 행은 일정이 아닙니다.");
        }
        String violation = ExamSchedule.dateOrderViolation(r.regStartAt(), r.regEndAt(),
                r.examStartDate(), r.examEndDate(), r.resultDate());
        if (violation != null) {
            throw new BusinessRuleException(violation);
        }
    }

    private ExamType parseType(String raw) {
        try {
            return ExamType.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessRuleException("구분은 WRITTEN 또는 PRACTICAL 이어야 합니다.");
        }
    }

    /** 이 컨트롤러 전용 DTO. */
    public static final class AdminDtos {

        private AdminDtos() {
        }

        public record UpsertRequest(
                @NotNull(message = "시험을 선택하세요.") Long certificateId,
                @NotNull(message = "연도를 입력하세요.")
                @Min(value = 2000, message = "연도는 2000~2100 사이여야 합니다.")
                @Max(value = 2100, message = "연도는 2000~2100 사이여야 합니다.")
                Integer year,
                @NotNull(message = "회차를 입력하세요.")
                @Min(value = 1, message = "회차는 1 이상이어야 합니다.")
                Integer round,
                @NotBlank(message = "구분(WRITTEN/PRACTICAL)을 입력하세요.") String examType,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime regStartAt,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime regEndAt,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate examStartDate,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate examEndDate,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate resultDate,
                String sourceUrl
        ) {
        }

        public record ScheduleRow(
                Long id, Long certificateId, String certificateName,
                int year, int round, String examType,
                String regStartAt, String regEndAt,
                String examStartDate, String examEndDate, String resultDate,
                String status, String sourceUrl
        ) {
            /** 시험은 호출부가 이미 갖고 있다 — 지연 로딩을 건드리지 않으려고 받아 쓴다. */
            public static ScheduleRow of(ExamSchedule s, Certificate cert) {
                return new ScheduleRow(
                        s.getId(), cert.getId(), cert.getName(),
                        s.getYear(), s.getRound(), s.getExamType().name(),
                        TimeUtil.format(s.getRegStartAt()), TimeUtil.format(s.getRegEndAt()),
                        TimeUtil.format(s.getExamStartDate()), TimeUtil.format(s.getExamEndDate()),
                        TimeUtil.format(s.getResultDate()),
                        s.getStatus().name(), s.getSourceUrl());
            }
        }
    }
}

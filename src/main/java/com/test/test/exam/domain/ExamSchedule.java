package com.test.test.exam.domain;

import com.test.test.exam.common.TimeUtil;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 회차별 시험 일정 (설계 04 §2-2, 핵심 테이블).
 * 자연 키: (certificate_id, year, round, exam_type) — 수집 upsert 기준.
 */
@Entity
@Table(name = "exam_schedule",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_schedule_natural",
                columnNames = {"certificate_id", "exam_year", "round", "exam_type"}),
        indexes = {
                @Index(name = "idx_schedule_reg_start", columnList = "reg_start_at"),
                @Index(name = "idx_schedule_exam", columnList = "exam_start_date")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ExamSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "certificate_id", nullable = false)
    private Certificate certificate;

    @Column(name = "exam_year", nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer round;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "exam_type", nullable = false, length = 20)
    private ExamType examType;

    @Column(name = "reg_start_at")
    private LocalDateTime regStartAt;

    @Column(name = "reg_end_at")
    private LocalDateTime regEndAt;

    @Column(name = "exam_start_date")
    private LocalDate examStartDate;

    @Column(name = "exam_end_date")
    private LocalDate examEndDate;

    @Column(name = "result_date")
    private LocalDate resultDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ScheduleStatus status = ScheduleStatus.ACTIVE;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    /** 이 일정을 어디서 얻었나. APPROX 면 화면이 "시행처 확인 필요"를 띄운다 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "provenance", length = 20)
    @Builder.Default
    private ScheduleProvenance provenance = ScheduleProvenance.SCRAPED;

    /** 원본 레코드 해시 — diff 비교용 */
    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    /**
     * 매니저 입력 행에 대해 <b>수집이 다른 날짜를 봤다</b>는 기록(사람이 읽을 문장).
     * 덮지는 않는다 — 매니저가 공고와 대조해 판단할 근거만 남긴다.
     */
    @Column(name = "source_conflict", length = 300)
    private String sourceConflict;

    @Column(name = "source_conflict_at")
    private LocalDateTime sourceConflictAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = TimeUtil.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.collectedAt == null) {
            this.collectedAt = now;
        }
        if (this.status == null) {
            this.status = ScheduleStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = TimeUtil.now();
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 이 회차에서 알려진 가장 늦은 날짜 — 시험 종료·시작, 없으면 접수 마감·시작. 아무 날짜도 없으면 null.
     *
     * <p>연도·회차만 있고 날짜가 하나도 없는 행은 사용자에게 아무것도 알려 주지 못한다.
     * 그래서 현황 판정(매니저 할 일·사용자 카드)은 이런 행을 "일정 없음"으로 친다 —
     * 회차만 넣어 두고 잊은 것이 "일정 있음"으로 둔갑하지 않게.
     */
    public LocalDate latestKnownDate() {
        if (examEndDate != null) return examEndDate;
        if (examStartDate != null) return examStartDate;
        if (regEndAt != null) return regEndAt.toLocalDate();
        if (regStartAt != null) return regStartAt.toLocalDate();
        return null;
    }

    /** 날짜가 하나라도 있는가 — 없으면 일정으로 세지 않는다 */
    public boolean hasAnyDate() {
        return latestKnownDate() != null;
    }

    /** 이보다 큰 숫자는 회차가 아니라 우리가 만든 멱등 키다 — 실제 회차는 네 자리를 안 넘는다. */
    private static final int MAX_REAL_ROUND = 10_000;

    /**
     * 시행처가 <b>실제로 회차를 매기는</b> 시험인가.
     *
     * <p>토익스피킹·TOEIC Bridge·토플처럼 회차 개념이 없는 시험이 있다. 그래도 우리는 같은 회차를
     * 두 번 넣지 않으려고 회차 자리에 <b>날짜</b>를 멱등 키로 넣는다. 그 숫자는 시행처가 부르는
     * 이름이 아니라 우리 내부 키다 — 화면에 "20261011회"로 새어 나가면 사용자는 우리가 뭔가
     * 잘못 읽었다고 생각한다(실제로 그렇게 보였다, 2026-09-08).
     *
     * <p>기준은 <b>만 미만</b>이다. 처음엔 백만으로 잡았는데 시드가 쓰는 {@code YYYYMM}
     * (예: 202609)이 그 밑이라 "202609회"가 그대로 화면에 떴다. 실제 회차는 가장 큰 것이
     * DIAT 의 2612회라 네 자리를 안 넘는다.
     */
    public boolean hasPublishedRound() {
        return round != null && round < MAX_REAL_ROUND;
    }

    /**
     * 화면에 쓰는 회차 표기 — {@code "576회 필기"}, 회차가 없는 시험이면 {@code "필기"}.
     * D-day·캘린더가 <b>같은 문구</b>를 쓰도록 여기 한 곳에 둔다.
     */
    public String roundLabel() {
        String type = examType == null ? "" : examType.getLabel();
        return hasPublishedRound() ? round + "회 " + type : type;
    }

    public boolean isActive() {
        return status == ScheduleStatus.ACTIVE;
    }

    /**
     * 날짜 순서 규칙 — 수집(DiffService)과 수기 입력(AdminScheduleController)이 <b>같은 규칙</b>을 쓴다.
     * 위반이 없으면 null, 있으면 사람이 읽을 사유.
     *
     * <p>접수 시작 ≤ 접수 마감 ≤ 시험 시작(같은 날 허용 — 당일 접수가 있다) ≤ 시험 종료 ≤ 발표.
     * 발표는 시험 종료일(없으면 시작일) 이후여야 한다. null 인 칸은 검사하지 않는다.
     */
    public static String dateOrderViolation(LocalDateTime regStart, LocalDateTime regEnd,
                                            LocalDate examStart, LocalDate examEnd, LocalDate result) {
        if (regStart != null && regEnd != null && regStart.isAfter(regEnd)) {
            return "접수 시작이 마감보다 늦습니다.";
        }
        if (regEnd != null && examStart != null && regEnd.toLocalDate().isAfter(examStart)) {
            return "접수 마감이 시험일보다 늦습니다.";
        }
        if (examStart != null && examEnd != null && examStart.isAfter(examEnd)) {
            return "시험 시작일이 종료일보다 늦습니다.";
        }
        LocalDate examLast = examEnd != null ? examEnd : examStart;
        if (examLast != null && result != null && result.isBefore(examLast)) {
            return "발표일이 시험 종료일보다 앞섭니다.";
        }
        return null;
    }

    public static boolean isDateOrderValid(LocalDateTime regStart, LocalDateTime regEnd,
                                           LocalDate examStart, LocalDate examEnd, LocalDate result) {
        return dateOrderViolation(regStart, regEnd, examStart, examEnd, result) == null;
    }

    /**
     * 접수·시험·발표 날짜가 지금 값과 <b>실제로</b> 다른가 — SCHEDULE_CHANGED 알림의 근거.
     * 수집과 수기 저장이 같은 판정을 쓴다. 원문 URL·해시 같은 메타는 변경이 아니다.
     */
    public boolean hasDifferentDates(LocalDateTime regStart, LocalDateTime regEnd,
                                     LocalDate examStart, LocalDate examEnd, LocalDate result) {
        return !java.util.Objects.equals(this.regStartAt, regStart)
                || !java.util.Objects.equals(this.regEndAt, regEnd)
                || !java.util.Objects.equals(this.examStartDate, examStart)
                || !java.util.Objects.equals(this.examEndDate, examEnd)
                || !java.util.Objects.equals(this.resultDate, result);
    }

    /**
     * 매니저가 넣은 값과 <b>수집값이 다르다</b>는 표시. 수집은 이 행을 덮지 않되(사람이 넣은 값이 이긴다)
     * 시행처가 뭐라고 하는지는 남겨 둔다 — 없으면 매니저가 바뀐 사실을 영영 모른다(2026-09-08).
     */
    public void markSourceConflict(String sourceSays, LocalDateTime at) {
        this.sourceConflict = sourceSays;
        this.sourceConflictAt = at;
    }

    /** 시행처가 매니저 값과 같아졌거나, 매니저가 다시 저장했다 — 표시를 뗀다. */
    public void clearSourceConflict() {
        this.sourceConflict = null;
        this.sourceConflictAt = null;
    }

    /** 수집 diff 로 변경된 필드를 반영한다. */
    public void applyFrom(LocalDateTime regStartAt, LocalDateTime regEndAt,
                          LocalDate examStartDate, LocalDate examEndDate, LocalDate resultDate,
                          String sourceUrl, String sourceHash, ScheduleStatus status,
                          LocalDateTime collectedAt) {
        this.regStartAt = regStartAt;
        this.regEndAt = regEndAt;
        this.examStartDate = examStartDate;
        this.examEndDate = examEndDate;
        this.resultDate = resultDate;
        this.sourceUrl = sourceUrl;
        this.sourceHash = sourceHash;
        this.status = status;
        this.collectedAt = collectedAt;
    }

    /** 출처를 바꾼다 — 추정치가 실데이터로 교체되면 경고가 사라져야 한다. */
    public void changeProvenance(ScheduleProvenance provenance) {
        this.provenance = provenance;
    }

    public void touchCollectedAt(LocalDateTime at) {
        this.collectedAt = at;
    }

    public void changeStatus(ScheduleStatus status) {
        this.status = status;
    }
}

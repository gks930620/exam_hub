package com.test.test.exam.domain;

import com.test.test.exam.common.TimeUtil;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 발송 예약 — 이벤트 단위 (설계 04 §2-5).
 * exam_schedule 1건에서 파생되는 "보낼 시점"들. 수집/변경 시 재계산.
 * UNIQUE(exam_schedule_id, event_type).
 */
@Entity
@Table(name = "notification_schedule",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notif_schedule", columnNames = {"exam_schedule_id", "event_type"}),
        indexes = @Index(name = "idx_notif_pending", columnList = "status, send_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NotificationSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_schedule_id", nullable = false)
    private ExamSchedule examSchedule;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 30)
    private NotificationEventType eventType;

    @Column(name = "send_at", nullable = false)
    private LocalDateTime sendAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NotificationScheduleStatus status = NotificationScheduleStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = TimeUtil.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = NotificationScheduleStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = TimeUtil.now();
    }

    // ===== 비즈니스 메서드 =====

    public void reschedule(LocalDateTime sendAt, NotificationScheduleStatus status) {
        this.sendAt = sendAt;
        this.status = status;
    }

    public void markSent() {
        this.status = NotificationScheduleStatus.SENT;
    }

    public void cancel() {
        this.status = NotificationScheduleStatus.CANCELED;
    }
}

package com.test.test.exam.domain;

import com.test.test.exam.common.TimeUtil;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 발송 이력 — 사용자 단위 (설계 04 §2-6).
 * UNIQUE(member_id, notification_schedule_id) = 중복 발송 방지 멱등 키 (FR-27).
 */
@Entity
@Table(name = "notification_log",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notif_log_idem",
                columnNames = {"member_id", "notification_schedule_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_schedule_id", nullable = false)
    private NotificationSchedule notificationSchedule;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private NotificationChannel channel;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private NotificationResult result;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (this.sentAt == null) {
            this.sentAt = TimeUtil.now();
        }
    }
}

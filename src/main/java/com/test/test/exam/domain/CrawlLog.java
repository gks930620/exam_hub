package com.test.test.exam.domain;

import com.test.test.exam.common.TimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 수집 감사 로그 (설계 04 §2-7). 독립 테이블.
 */
@Entity
@Table(name = "crawl_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CrawlLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ScheduleSource 식별자 (예: QNET_API, MOCK) */
    @Column(nullable = false, length = 30)
    private String source;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "fetched_count")
    private Integer fetchedCount;

    @Column(name = "new_count")
    private Integer newCount;

    @Column(name = "updated_count")
    private Integer updatedCount;

    @Column(name = "skipped_count")
    private Integer skippedCount;

    @Column(name = "pending_review_count")
    private Integer pendingReviewCount;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    public static CrawlLog start(String source) {
        return CrawlLog.builder()
                .source(source)
                .startedAt(TimeUtil.now())
                .success(false)
                .build();
    }

    public void finishSuccess(int fetched, int neu, int updated, int skipped, int pendingReview) {
        this.finishedAt = TimeUtil.now();
        this.fetchedCount = fetched;
        this.newCount = neu;
        this.updatedCount = updated;
        this.skippedCount = skipped;
        this.pendingReviewCount = pendingReview;
        this.success = true;
    }

    public void finishFailure(String errorMessage) {
        this.finishedAt = TimeUtil.now();
        this.success = false;
        this.errorMessage = errorMessage != null && errorMessage.length() > 1000
                ? errorMessage.substring(0, 1000) : errorMessage;
    }
}

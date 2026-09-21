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

    /**
     * 종목을 지정해 돌린 <b>부분 수집</b>인가(매니저 재수집·임박 재확인). 전체 배치면 false.
     *
     * <p>이게 없으면 수집 건강 판정이 거짓 경보를 낸다. 부분 수집은 그 소스가 맡는 종목이
     * 대상에 없으면 <b>0건이 정상</b>이다 — 큐넷 API 는 4자리 종목코드가 없으면 아예 호출하지
     * 않는다(호출 낭비를 막으려고). 그 0건을 전체 배치의 0건과 같게 보면 "고장"이라고 뜬다.
     * 거짓 경보는 경보가 없는 것보다 나쁘다 — 매니저가 경고를 무시하게 된다(2026-09-21 실측).
     */
    @Column(name = "partial")
    private boolean partial;

    public static CrawlLog start(String source) {
        return start(source, false);
    }

    public static CrawlLog start(String source, boolean partial) {
        return CrawlLog.builder()
                .source(source)
                .startedAt(TimeUtil.now())
                .success(false)
                .partial(partial)
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

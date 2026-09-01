package com.test.test.exam.community;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 댓글 (설계 08 §2-3). 대댓글(계층)은 이번 범위 밖 — 필요해지면 {@code parentId} 를 더한다.
 * 글과 마찬가지로 soft delete 다.
 */
@Entity
@Table(name = "comment", indexes = {
        @Index(name = "idx_comment_post", columnList = "post_id, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Comment {

    public static final int CONTENT_MAX = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = CONTENT_MAX)
    private String content;

    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = TimeUtil.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = TimeUtil.now();
    }

    public void softDelete() {
        this.deleted = true;
    }

    public boolean isWrittenBy(Member m) {
        return m != null && this.member.getId().equals(m.getId());
    }
}

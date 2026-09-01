package com.test.test.exam.community;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 커뮤니티 글 (설계 08 §2-3).
 *
 * <p><b>삭제는 soft delete</b> — 댓글이 달린 글을 물리 삭제하면 대화가 통째로 사라진다.
 * <p>{@code commentCount} 는 비정규화다. 목록에서 글마다 댓글 수를 세면 N+1 이 된다.
 */
@Entity
@Table(name = "post", indexes = {
        @Index(name = "idx_post_board_created", columnList = "board, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Post {

    public static final int TITLE_MAX = 200;
    public static final int CONTENT_MAX = 10_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Board board;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = TITLE_MAX)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private int viewCount = 0;

    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    private int commentCount = 0;

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

    // ===== 비즈니스 메서드 =====

    public void edit(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public void softDelete() {
        this.deleted = true;
    }

    public void increaseView() {
        this.viewCount++;
    }

    public void increaseComment() {
        this.commentCount++;
    }

    public void decreaseComment() {
        this.commentCount = Math.max(0, this.commentCount - 1);
    }

    public boolean isWrittenBy(Member m) {
        return m != null && this.member.getId().equals(m.getId());
    }
}

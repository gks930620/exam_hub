package com.test.test.exam.community;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Member;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 커뮤니티 API DTO (설계 08 §4-3).
 * <p>탈퇴한 회원의 글도 남으므로 작성자 표기는 {@link #authorName} 에서 흡수한다.
 */
public final class CommunityDtos {

    private CommunityDtos() {
    }

    /** 탈퇴 회원은 이름을 노출하지 않는다 — 글은 남기되 사람은 지운다. */
    private static String displayName(Member m) {
        return m.isActive() ? m.getNickname() : "탈퇴한 사용자";
    }

    private static String displayImage(Member m) {
        return m.isActive() ? m.getProfileImage() : null;
    }

    public record BoardItem(String code, String name, String description) {
        public static BoardItem of(Board b) {
            return new BoardItem(b.name(), b.getBoardName(), b.getDescription());
        }
    }

    public record BoardListResponse(List<BoardItem> items) {
    }

    /** 목록용 — 본문은 안 보낸다(목록에서 필요 없고 응답만 커진다). */
    public record PostSummary(
            Long id,
            String boardCode,
            String boardName,
            String title,
            String authorName,
            Long authorId,
            int viewCount,
            int commentCount,
            String createdAt
    ) {
        public static PostSummary of(Post p) {
            return new PostSummary(
                    p.getId(), p.getBoard().name(), p.getBoard().getBoardName(),
                    p.getTitle(), displayName(p.getMember()), p.getMember().getId(),
                    p.getViewCount(), p.getCommentCount(), TimeUtil.format(p.getCreatedAt()));
        }
    }

    public record PostListResponse(
            List<PostSummary> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record PostDetail(
            Long id,
            String boardCode,
            String boardName,
            String title,
            String content,
            String authorName,
            String authorImage,
            Long authorId,
            int viewCount,
            int commentCount,
            String createdAt,
            String updatedAt,
            /** 지금 로그인한 사람이 이 글의 주인인지 — 프런트가 수정/삭제 버튼을 띄울 근거 */
            boolean mine
    ) {
        public static PostDetail of(Post p, Member viewer) {
            return new PostDetail(
                    p.getId(), p.getBoard().name(), p.getBoard().getBoardName(),
                    p.getTitle(), p.getContent(),
                    displayName(p.getMember()), displayImage(p.getMember()), p.getMember().getId(),
                    p.getViewCount(), p.getCommentCount(),
                    TimeUtil.format(p.getCreatedAt()), TimeUtil.format(p.getUpdatedAt()),
                    p.isWrittenBy(viewer));
        }
    }

    public record CommentItem(
            Long id,
            String content,
            String authorName,
            String authorImage,
            Long authorId,
            String createdAt,
            boolean mine
    ) {
        public static CommentItem of(Comment c, Member viewer) {
            return new CommentItem(
                    c.getId(), c.getContent(),
                    displayName(c.getMember()), displayImage(c.getMember()), c.getMember().getId(),
                    TimeUtil.format(c.getCreatedAt()), c.isWrittenBy(viewer));
        }
    }

    public record CommentListResponse(List<CommentItem> items) {
    }

    // ===== 요청 =====

    public record CreatePostRequest(
            @NotBlank(message = "게시판을 선택하세요.")
            String boardCode,
            @NotBlank(message = "제목을 입력하세요.")
            @Size(max = Post.TITLE_MAX, message = "제목은 200자 이하여야 합니다.")
            String title,
            @NotBlank(message = "내용을 입력하세요.")
            @Size(max = Post.CONTENT_MAX, message = "내용은 10,000자 이하여야 합니다.")
            String content
    ) {
    }

    public record UpdatePostRequest(
            @NotBlank(message = "제목을 입력하세요.")
            @Size(max = Post.TITLE_MAX, message = "제목은 200자 이하여야 합니다.")
            String title,
            @NotBlank(message = "내용을 입력하세요.")
            @Size(max = Post.CONTENT_MAX, message = "내용은 10,000자 이하여야 합니다.")
            String content
    ) {
    }

    public record CreateCommentRequest(
            @NotBlank(message = "댓글을 입력하세요.")
            @Size(max = Comment.CONTENT_MAX, message = "댓글은 1,000자 이하여야 합니다.")
            String content
    ) {
    }
}

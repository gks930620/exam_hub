package com.test.test.exam.community;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Member;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 커뮤니티 API DTO (설계 08 §4-3).
 * <p>탈퇴한 회원의 글도 남으므로 작성자 표기는 {@code authorName} 에서 흡수한다.
 *
 * <p><b>DTO 는 record 가 아니라 class 다</b>(코드 컨벤션 §0). 요청 DTO 에는 {@code @Setter} 도 붙인다 —
 * Jackson 이 JSON 을 채워 넣을 손잡이가 없으면 필드가 전부 null 로 들어온다.
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

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoardItem {
        private String code;
        private String name;
        private String description;

        public static BoardItem of(Board b) {
            return new BoardItem(b.name(), b.getBoardName(), b.getDescription());
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoardListResponse {
        private List<BoardItem> items;
    }

    /** 목록용 — 본문은 안 보낸다(목록에서 필요 없고 응답만 커진다). */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostSummary {
        private Long id;
        private String boardCode;
        private String boardName;
        private String title;
        private String authorName;
        private Long authorId;
        private int viewCount;
        private int commentCount;
        private String createdAt;

        public static PostSummary of(Post p) {
            return new PostSummary(
                    p.getId(), p.getBoard().name(), p.getBoard().getBoardName(),
                    p.getTitle(), displayName(p.getMember()), p.getMember().getId(),
                    p.getViewCount(), p.getCommentCount(), TimeUtil.format(p.getCreatedAt()));
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostListResponse {
        private List<PostSummary> items;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostDetail {
        private Long id;
        private String boardCode;
        private String boardName;
        private String title;
        private String content;
        private String authorName;
        private String authorImage;
        private Long authorId;
        private int viewCount;
        private int commentCount;
        private String createdAt;
        private String updatedAt;
        /** 지금 로그인한 사람이 이 글의 주인인지 — 프런트가 수정/삭제 버튼을 띄울 근거 */
        private boolean mine;

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

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentItem {
        private Long id;
        private String content;
        private String authorName;
        private String authorImage;
        private Long authorId;
        private String createdAt;
        private boolean mine;

        public static CommentItem of(Comment c, Member viewer) {
            return new CommentItem(
                    c.getId(), c.getContent(),
                    displayName(c.getMember()), displayImage(c.getMember()), c.getMember().getId(),
                    TimeUtil.format(c.getCreatedAt()), c.isWrittenBy(viewer));
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentListResponse {
        private List<CommentItem> items;
    }

    // ===== 요청 =====

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePostRequest {
        @NotBlank(message = "게시판을 선택하세요.")
        private String boardCode;
        @NotBlank(message = "제목을 입력하세요.")
        @Size(max = Post.TITLE_MAX, message = "제목은 200자 이하여야 합니다.")
        private String title;
        @NotBlank(message = "내용을 입력하세요.")
        @Size(max = Post.CONTENT_MAX, message = "내용은 10,000자 이하여야 합니다.")
        private String content;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePostRequest {
        @NotBlank(message = "제목을 입력하세요.")
        @Size(max = Post.TITLE_MAX, message = "제목은 200자 이하여야 합니다.")
        private String title;
        @NotBlank(message = "내용을 입력하세요.")
        @Size(max = Post.CONTENT_MAX, message = "내용은 10,000자 이하여야 합니다.")
        private String content;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateCommentRequest {
        @NotBlank(message = "댓글을 입력하세요.")
        @Size(max = Comment.CONTENT_MAX, message = "댓글은 1,000자 이하여야 합니다.")
        private String content;
    }
}

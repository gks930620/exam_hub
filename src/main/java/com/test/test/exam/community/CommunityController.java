package com.test.test.exam.community;

import com.test.test.exam.auth.CurrentMember;
import com.test.test.exam.domain.Member;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 커뮤니티 API (설계 08 §4-3).
 *
 * <p>읽기(GET)는 비로그인 허용 — {@code @CurrentMember(required = false)} 라 로그인했으면
 * {@code mine} 플래그가 채워져 프런트가 수정/삭제 버튼을 띄울 수 있다.
 * 쓰기는 로그인 필수(SecurityConfig 가 401 로 끊는다).
 */
@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @GetMapping("/boards")
    public ResponseEntity<CommunityDtos.BoardListResponse> boards() {
        return ResponseEntity.ok(communityService.boards());
    }

    @GetMapping("/posts")
    public ResponseEntity<CommunityDtos.PostListResponse> list(
            @RequestParam(required = false) String board,
            /* 제목·본문에서 찾을 말. 비면 전체 */
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(communityService.list(board, query, page, size));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<CommunityDtos.PostDetail> detail(
            @PathVariable Long id,
            @CurrentMember(required = false) Member viewer) {
        return ResponseEntity.ok(communityService.detail(id, viewer));
    }

    @PostMapping("/posts")
    public ResponseEntity<CommunityDtos.PostDetail> create(
            @CurrentMember Member author,
            @Valid @RequestBody CommunityDtos.CreatePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(communityService.create(author, request));
    }

    @PutMapping("/posts/{id}")
    public ResponseEntity<CommunityDtos.PostDetail> update(
            @PathVariable Long id,
            @CurrentMember Member editor,
            @Valid @RequestBody CommunityDtos.UpdatePostRequest request) {
        return ResponseEntity.ok(communityService.update(id, editor, request));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @CurrentMember Member requester) {
        communityService.delete(id, requester);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/posts/{id}/comments")
    public ResponseEntity<CommunityDtos.CommentListResponse> comments(
            @PathVariable Long id,
            @CurrentMember(required = false) Member viewer) {
        return ResponseEntity.ok(communityService.comments(id, viewer));
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<CommunityDtos.CommentItem> addComment(
            @PathVariable Long id,
            @CurrentMember Member author,
            @Valid @RequestBody CommunityDtos.CreateCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(communityService.addComment(id, author, request));
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id, @CurrentMember Member requester) {
        communityService.deleteComment(id, requester);
        return ResponseEntity.noContent().build();
    }
}

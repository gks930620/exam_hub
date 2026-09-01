package com.test.test.exam.community;

import com.test.test.common.exception.AccessDeniedException;
import com.test.test.common.exception.BusinessRuleException;
import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.exam.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 커뮤니티 — 게시판/글/댓글 (설계 08 §4-3).
 *
 * <p>규칙 두 가지가 전부다:
 * <ol>
 *   <li><b>읽기는 누구나, 쓰기는 로그인.</b> (인가는 SecurityConfig 가 막고, 여기선 소유권만 본다)</li>
 *   <li><b>수정·삭제는 본인만.</b> 남의 글이면 403.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CommunityService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    // ===== 게시판 =====

    public CommunityDtos.BoardListResponse boards() {
        return new CommunityDtos.BoardListResponse(
                Arrays.stream(Board.values()).map(CommunityDtos.BoardItem::of).toList());
    }

    // ===== 글 =====

    @Transactional(readOnly = true)
    public CommunityDtos.PostListResponse list(String boardCode, int page, int size) {
        if (page < 0) {
            throw new BusinessRuleException("page 는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessRuleException("size 는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
        PageRequest pageable = PageRequest.of(page, size);

        Page<Post> found = (boardCode == null || boardCode.isBlank())
                ? postRepository.findByDeletedFalseOrderByCreatedAtDesc(pageable)
                : postRepository.findByBoardAndDeletedFalseOrderByCreatedAtDesc(Board.from(boardCode), pageable);

        return new CommunityDtos.PostListResponse(
                found.getContent().stream().map(CommunityDtos.PostSummary::of).collect(Collectors.toList()),
                found.getNumber(), found.getSize(), found.getTotalElements(), found.getTotalPages());
    }

    /** 상세 — 조회수를 1 올린다(본인 글이어도 올린다, 단순함이 낫다). */
    @Transactional
    public CommunityDtos.PostDetail detail(Long postId, Member viewer) {
        Post post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new EntityNotFoundException("글을 찾을 수 없습니다."));
        post.increaseView();
        return CommunityDtos.PostDetail.of(post, viewer);
    }

    @Transactional
    public CommunityDtos.PostDetail create(Member author, CommunityDtos.CreatePostRequest req) {
        Post post = postRepository.save(Post.builder()
                .board(Board.from(req.boardCode()))
                .member(author)
                .title(req.title().trim())
                .content(req.content())
                .build());
        return CommunityDtos.PostDetail.of(post, author);
    }

    @Transactional
    public CommunityDtos.PostDetail update(Long postId, Member editor, CommunityDtos.UpdatePostRequest req) {
        Post post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new EntityNotFoundException("글을 찾을 수 없습니다."));
        requireOwner(post.isWrittenBy(editor), editor);
        post.edit(req.title().trim(), req.content());
        return CommunityDtos.PostDetail.of(post, editor);
    }

    @Transactional
    public void delete(Long postId, Member requester) {
        Post post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new EntityNotFoundException("글을 찾을 수 없습니다."));
        requireOwner(post.isWrittenBy(requester), requester);
        post.softDelete();
    }

    // ===== 댓글 =====

    @Transactional(readOnly = true)
    public CommunityDtos.CommentListResponse comments(Long postId, Member viewer) {
        // 삭제된 글의 댓글은 보여주지 않는다 — 글이 사라졌는데 대화만 남으면 맥락이 없다
        postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new EntityNotFoundException("글을 찾을 수 없습니다."));

        List<CommunityDtos.CommentItem> items = commentRepository
                .findByPostIdAndDeletedFalseOrderByCreatedAtAsc(postId).stream()
                .map(c -> CommunityDtos.CommentItem.of(c, viewer))
                .collect(Collectors.toList());
        return new CommunityDtos.CommentListResponse(items);
    }

    @Transactional
    public CommunityDtos.CommentItem addComment(Long postId, Member author,
                                                CommunityDtos.CreateCommentRequest req) {
        Post post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new EntityNotFoundException("글을 찾을 수 없습니다."));

        Comment comment = commentRepository.save(Comment.builder()
                .post(post).member(author).content(req.content().trim()).build());
        post.increaseComment();   // 목록에서 매번 세지 않으려고 들고 있는 값
        return CommunityDtos.CommentItem.of(comment, author);
    }

    @Transactional
    public void deleteComment(Long commentId, Member requester) {
        Comment comment = commentRepository.findByIdAndDeletedFalse(commentId)
                .orElseThrow(() -> new EntityNotFoundException("댓글을 찾을 수 없습니다."));
        requireOwner(comment.isWrittenBy(requester), requester);
        comment.softDelete();
        comment.getPost().decreaseComment();
    }

    /** 본인이거나 관리자면 통과. */
    private void requireOwner(boolean isOwner, Member requester) {
        if (!isOwner && !requester.isAdmin()) {
            throw new AccessDeniedException("본인이 작성한 글만 수정·삭제할 수 있습니다.");
        }
    }
}

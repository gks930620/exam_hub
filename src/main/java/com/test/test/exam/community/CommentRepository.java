package com.test.test.exam.community;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** 글의 댓글 — 삭제된 것은 빼고 오래된 순(대화 흐름). */
    @EntityGraph(attributePaths = "member")
    List<Comment> findByPostIdAndDeletedFalseOrderByCreatedAtAsc(Long postId);

    @EntityGraph(attributePaths = {"member", "post"})
    Optional<Comment> findByIdAndDeletedFalse(Long id);
}

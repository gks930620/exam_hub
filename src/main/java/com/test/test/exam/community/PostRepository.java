package com.test.test.exam.community;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    /** 목록 — 삭제된 글은 빼고 최신순. 작성자를 같이 가져와 N+1 을 막는다. */
    @EntityGraph(attributePaths = "member")
    Page<Post> findByBoardAndDeletedFalseOrderByCreatedAtDesc(Board board, Pageable pageable);

    @EntityGraph(attributePaths = "member")
    Page<Post> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "member")
    Optional<Post> findByIdAndDeletedFalse(Long id);
}

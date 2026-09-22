package com.test.test.exam.community;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
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

    /**
     * 제목·본문에서 찾는다(게시판 전체).
     *
     * <p>글이 쌓이면 "그때 그 글"을 다시 못 찾는다 — 더 보기를 스무 번 누르는 것 말고는 길이 없었다.
     * 본문까지 보는 이유: 사람들은 제목이 아니라 <b>안에 있던 말</b>을 기억한다.
     *
     * <p>본문은 {@code @Lob} 이라 그대로 {@code LOWER()} 를 걸면 하이버네이트가 거부하고
     * <b>기동이 통째로 깨진다</b>(CLOB 은 문자열 함수의 인자가 아니다). 그래서 캐스팅해서 넘긴다.
     *
     * <p><b>게시판 유무로 메서드를 나눈 이유</b>: {@code :board IS NULL} 관용구는 하이버네이트가
     * 파라미터 타입을 못 잡아 <b>조건 전체가 어긋난다</b>. 이 저장소에서 분류 필터가 빈 결과를
     * 냈던 것과 같은 함정이고, 여기서도 검색이 통째로 0건이 됐다(2026-09-22 실측).
     */
    @EntityGraph(attributePaths = "member")
    @Query("""
            SELECT p FROM Post p
             WHERE p.deleted = false
               AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
                    OR LOWER(CAST(p.content AS string)) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\')
             ORDER BY p.createdAt DESC
            """)
    Page<Post> search(@Param("q") String q, Pageable pageable);

    /** 같은 검색을 한 게시판 안에서. 조건을 하나로 합치지 않는 이유는 위에 적었다. */
    @EntityGraph(attributePaths = "member")
    @Query("""
            SELECT p FROM Post p
             WHERE p.deleted = false AND p.board = :board
               AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
                    OR LOWER(CAST(p.content AS string)) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\')
             ORDER BY p.createdAt DESC
            """)
    Page<Post> searchInBoard(@Param("q") String q, @Param("board") Board board, Pageable pageable);
}

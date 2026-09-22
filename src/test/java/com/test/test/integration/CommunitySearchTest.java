package com.test.test.integration;

import com.test.test.exam.community.Board;
import com.test.test.exam.community.Post;
import com.test.test.exam.community.PostRepository;
import com.test.test.exam.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>"그때 그 글"을 다시 찾을 수 있어야 한다.</b>
 *
 * <p>글이 쌓이면 더 보기를 스무 번 누르는 것 말고는 길이 없었다. 게시판 필터뿐이라
 * 같은 게시판 안에서 찾는 건 불가능했다.
 *
 * <p><b>본문까지 본다.</b> 사람들은 제목이 아니라 <b>안에 있던 말</b>을 기억한다 —
 * "정보처리기사 실기 준비하신 분" 같은 제목보다 "수제비 3회독" 같은 본문 조각이 먼저 떠오른다.
 */
class CommunitySearchTest extends ApiIntegrationTestSupport {

    @Autowired PostRepository postRepository;

    private Post post(Member author, Board board, String title, String content) {
        return postRepository.save(Post.builder()
                .member(author).board(board).title(title).content(content).build());
    }

    /**
     * 검색어는 {@code param()} 으로 넘긴다.
     *
     * <p>주소 문자열에 직접 인코딩해 넣으면 MockMvc 가 <b>한 번 더 인코딩</b>해서
     * {@code %EC%...} 가 {@code %25EC%25...} 가 된다 — 검색이 통째로 0건이 됐다(2026-09-22).
     * 저장소는 멀쩡했고 테스트만 틀린 것이라 한참 엉뚱한 데를 봤다.
     */
    private MockHttpServletRequestBuilder search(String query) {
        return get("/api/community/posts").param("query", query);
    }

    @Test
    @DisplayName("제목으로 찾는다")
    void finds_by_title() throws Exception {
        Member me = newMember();
        post(me, Board.FREE, "정보처리기사 실기 후기", "잘 봤습니다");
        post(me, Board.FREE, "토익 점수 고민", "700점대입니다");

        mockMvc.perform(search("정보처리기사"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("정보처리기사 실기 후기"));
    }

    /** 사람들은 제목이 아니라 안에 있던 말을 기억한다. */
    @Test
    @DisplayName("본문으로도 찾는다 — 제목만 보면 대부분 못 찾는다")
    void finds_by_content() throws Exception {
        Member me = newMember();
        post(me, Board.FREE, "아무 제목", "수제비 3회독 하고 붙었습니다");

        mockMvc.perform(search("수제비"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("게시판과 같이 좁힐 수 있다")
    void combines_with_the_board_filter() throws Exception {
        Member me = newMember();
        post(me, Board.FREE, "정보처리기사 잡담", "자유게시판 글");
        post(me, Board.QNA, "정보처리기사 질문", "질문게시판 글");

        mockMvc.perform(search("정보처리기사").param("board", "QNA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("정보처리기사 질문"));
    }

    @Test
    @DisplayName("없으면 빈 목록이다 — 오류가 아니다")
    void nothing_found_is_not_an_error() throws Exception {
        mockMvc.perform(search("존재하지않는말zzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    /** 검색어가 비면 예전처럼 전체가 나와야 한다 — 기존 화면이 깨지지 않는다. */
    @Test
    @DisplayName("검색어가 비면 전체가 나온다")
    void blank_query_lists_everything() throws Exception {
        post(newMember(), Board.FREE, "아무 글", "아무 내용");

        mockMvc.perform(search(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isNotEmpty());
    }

    /** 지운 글이 검색으로 되살아나면 안 된다. */
    @Test
    @DisplayName("지운 글은 검색에도 안 나온다")
    void deleted_posts_stay_hidden() throws Exception {
        Post p = post(newMember(), Board.FREE, "지울 글 유니크토큰", "곧 지웁니다");
        p.softDelete();
        postRepository.save(p);

        mockMvc.perform(search("유니크토큰"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }
}

package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.exam.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 커뮤니티 API 통합테스트 (설계 08 §4-3).
 *
 * <p>계약 두 줄:
 * <ul>
 *   <li><b>읽기는 누구나, 쓰기는 로그인</b> — 비로그인 쓰기는 401</li>
 *   <li><b>수정·삭제는 본인만</b> — 남의 글은 403</li>
 * </ul>
 */
class CommunityApiIntegrationTest extends ApiIntegrationTestSupport {

    private long createPost(Member author, String title) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/community/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"boardCode":"FREE","title":"%s","content":"본문입니다."}
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("id").asLong();
    }

    // ===== 게시판 =====

    @Test
    void boards_are_public() throws Exception {
        mockMvc.perform(get("/api/community/boards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").exists())
                .andExpect(jsonPath("$.items[0].name").exists());
    }

    // ===== 읽기는 누구나 =====

    @Test
    @DisplayName("비로그인도 글 목록·상세를 본다")
    void list_and_detail_are_public() throws Exception {
        long id = createPost(newMember(), "공개 글");

        mockMvc.perform(get("/api/community/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());

        mockMvc.perform(get("/api/community/posts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("공개 글"))
                .andExpect(jsonPath("$.mine").value(false));   // 비로그인이면 내 글일 수 없다
    }

    @Test
    void detail_marks_mine_for_author() throws Exception {
        Member author = newMember();
        long id = createPost(author, "내 글");

        mockMvc.perform(get("/api/community/posts/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mine").value(true));
    }

    // ===== 쓰기는 로그인 =====

    @Test
    @DisplayName("비로그인 글쓰기 → 401")
    void write_without_login_returns_401() throws Exception {
        mockMvc.perform(post("/api/community/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardCode\":\"FREE\",\"title\":\"제목\",\"content\":\"본문\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blank_title_returns_400() throws Exception {
        mockMvc.perform(post("/api/community/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardCode\":\"FREE\",\"title\":\"\",\"content\":\"본문\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknown_board_returns_400() throws Exception {
        mockMvc.perform(post("/api/community/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardCode\":\"NOPE\",\"title\":\"제목\",\"content\":\"본문\"}"))
                .andExpect(status().isBadRequest());
    }

    // ===== 수정·삭제는 본인만 =====

    @Test
    @DisplayName("남의 글 수정 → 403")
    void update_others_post_returns_403() throws Exception {
        long id = createPost(newMember(), "원글");

        mockMvc.perform(put("/api/community/posts/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember()))   // 다른 사람
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"바꿈\",\"content\":\"바꿈\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("남의 글 삭제 → 403")
    void delete_others_post_returns_403() throws Exception {
        long id = createPost(newMember(), "원글");

        mockMvc.perform(delete("/api/community/posts/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember())))
                .andExpect(status().isForbidden());
    }

    @Test
    void author_can_update_and_delete() throws Exception {
        Member author = newMember();
        long id = createPost(author, "원글");

        mockMvc.perform(put("/api/community/posts/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"고친 제목\",\"content\":\"고친 본문\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("고친 제목"));

        mockMvc.perform(delete("/api/community/posts/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isNoContent());

        // soft delete — 목록·상세에서 사라진다
        mockMvc.perform(get("/api/community/posts/{id}", id))
                .andExpect(status().isNotFound());
    }

    /** 관리자는 남의 글도 지울 수 있어야 한다(운영). */
    @Test
    void admin_can_delete_others_post() throws Exception {
        long id = createPost(newMember(), "신고된 글");

        mockMvc.perform(delete("/api/community/posts/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isNoContent());
    }

    // ===== 댓글 =====

    @Test
    void comment_flow() throws Exception {
        Member author = newMember();
        long postId = createPost(author, "댓글 달릴 글");

        mockMvc.perform(post("/api/community/posts/{id}/comments", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"첫 댓글\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("첫 댓글"));

        // 댓글 수가 글에 반영된다(목록에서 매번 세지 않으려고 들고 있는 값)
        mockMvc.perform(get("/api/community/posts/{id}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentCount").value(1));

        mockMvc.perform(get("/api/community/posts/{id}/comments", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void comment_without_login_returns_401() throws Exception {
        long postId = createPost(newMember(), "글");

        mockMvc.perform(post("/api/community/posts/{id}/comments", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_others_comment_returns_403() throws Exception {
        Member author = newMember();
        long postId = createPost(author, "글");

        MvcResult res = mockMvc.perform(post("/api/community/posts/{id}/comments", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"내 댓글\"}"))
                .andExpect(status().isCreated()).andReturn();
        long commentId = objectMapper.readTree(
                res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("id").asLong();

        mockMvc.perform(delete("/api/community/comments/{id}", commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember())))
                .andExpect(status().isForbidden());
    }

    // ===== 목록 필터 =====

    @Test
    void list_filters_by_board() throws Exception {
        createPost(newMember(), "자유글");

        MvcResult res = mockMvc.perform(get("/api/community/posts").param("board", "QNA"))
                .andExpect(status().isOk()).andReturn();
        JsonNode items = objectMapper.readTree(
                res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");

        for (JsonNode it : items) {
            if (!"QNA".equals(it.path("boardCode").asText())) {
                throw new AssertionError("다른 게시판 글이 섞였다: " + it.path("boardCode").asText());
            }
        }
    }
/**
     * <b>내가 쓴 글 모아보기.</b> 글이 쌓이면 자기 글을 다시 찾을 길이 목록을 훑는 것뿐이었다 —
     * 검색은 말을 기억해야 쓰는 것이고, 자기가 쓴 글은 "내가 썼다"는 사실만 기억한다.
     *
     * <p>게시판으로 거르지 않는다(어느 게시판에 썼는지는 보통 기억하지 못한다).
     */
    @Test
    @DisplayName("내 글만 모아 준다 — 남의 글은 섞이지 않는다")
    void mine_returns_only_my_posts() throws Exception {
        Member me = newMember();
        Member other = newMember();
        createPost(me, "내가 쓴 글 A");
        createPost(other, "남이 쓴 글");
        createPost(me, "내가 쓴 글 B");

        mockMvc.perform(get("/api/community/posts/mine")
                        .header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                // 최신순 — 나중에 쓴 글이 위다
                .andExpect(jsonPath("$.items[0].title").value("내가 쓴 글 B"))
                .andExpect(jsonPath("$.items[1].title").value("내가 쓴 글 A"));
    }

    /** 누구 글인지는 토큰이 정한다 — 비로그인에게 "내 글"은 뜻이 없다. */
    @Test
    @DisplayName("비로그인은 내 글을 못 본다 → 401")
    void mine_requires_login() throws Exception {
        mockMvc.perform(get("/api/community/posts/mine"))
                .andExpect(status().isUnauthorized());
    }

    /** 지운 글은 내 목록에도 안 보인다 — 지웠는데 남아 있으면 지운 게 아니다. */
    @Test
    @DisplayName("지운 글은 내 목록에서도 빠진다")
    void mine_excludes_deleted() throws Exception {
        Member me = newMember();
        long id = createPost(me, "지울 글");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/community/posts/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/community/posts/mine")
                        .header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}

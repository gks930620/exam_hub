package com.test.test.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * React 화면 경로와 백엔드 경로의 보안 경계.
 *
 * <p>화면 경로를 하나씩 화이트리스트에 적는 방식은 라우트를 추가할 때마다 빠뜨렸다 —
 * {@code /my} 가 빠져 새로고침하면 401 이 났다(실측 2026-09-03). 그래서 규칙을 뒤집는다:
 * <b>백엔드 접두어({@code /api}·{@code /actuator}…)가 아닌 GET 은 전부 화면</b>이고,
 * 백엔드 경로는 지금처럼 인증·인가를 탄다. 이 테스트가 그 두 방향을 못 박는다.
 */
class SpaRoutingSecurityIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    @DisplayName("/my 를 새로고침해도 index.html 이 온다 (401 아님)")
    void my_page_refresh_serves_index() throws Exception {
        mockMvc.perform(get("/my"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    @DisplayName("아직 없는 화면 경로도 열린다 — 라우트마다 화이트리스트를 고칠 필요가 없다")
    void unknown_page_route_serves_index() throws Exception {
        mockMvc.perform(get("/some-future-page/with/depth"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    @DisplayName("백엔드 경로는 여전히 인증을 탄다 — 화면을 열어 준 규칙이 API 를 열면 안 된다")
    void backend_paths_still_require_authentication() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/overview")).andExpect(status().isUnauthorized());
        // 공개로 열어 둔 health·info 이외의 actuator 는 막힌다
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("화면 경로라도 GET 이 아니면 막힌다")
    void non_get_on_page_route_is_denied() throws Exception {
        mockMvc.perform(post("/my")).andExpect(status().isUnauthorized());
    }
}

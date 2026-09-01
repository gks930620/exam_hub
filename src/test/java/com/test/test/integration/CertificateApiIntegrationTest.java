package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 자격증 조회 API 통합테스트 (설계 05 §2-1, §2-2 — CertificateController).
 * 공개 API(비로그인). 로그인 상태면 favorited 플래그가 채워진다.
 * 정상 응답 + 에러 경로(400 검색어 짧음/파라미터 누락, 404 없음)를 관통 검증한다.
 */
class CertificateApiIntegrationTest extends ApiIntegrationTestSupport {

    /** 시드된 자격증 하나의 id 를 인기 목록으로 조회해 얻는다(하드코딩 회피). */
    private long anyCertificateId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/certificates/popular"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).path("items");
        return items.get(0).path("id").asLong();
    }

    @Test
    void search_returns_matching_certificates() throws Exception {
        mockMvc.perform(get("/api/certificates").param("query", "정보처리"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].id").isNumber())
                .andExpect(jsonPath("$.items[0].name").exists())
                .andExpect(jsonPath("$.items[0].favorited").value(false));
    }

    @Test
    void search_with_too_short_query_returns_400() throws Exception {
        mockMvc.perform(get("/api/certificates").param("query", "정"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_without_query_param_returns_400() throws Exception {
        mockMvc.perform(get("/api/certificates"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void popular_returns_top_certificates() throws Exception {
        mockMvc.perform(get("/api/certificates/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].id").isNumber());
    }

    @Test
    void detail_returns_certificate_with_schedules() throws Exception {
        long id = anyCertificateId();

        mockMvc.perform(get("/api/certificates/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.favorited").value(false))
                .andExpect(jsonPath("$.schedules").isArray());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("로그인 상태면 관심 등록 여부(favorited)가 채워진다")
    void detail_reflects_favorited_flag_for_logged_in_member() throws Exception {
        long id = anyCertificateId();
        com.test.test.exam.domain.Member m = newMember();

        // 등록 전에는 false
        mockMvc.perform(get("/api/certificates/{id}", id)
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, bearer(m)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(false));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/me/favorites")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"certificateId\": " + id + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/certificates/{id}", id)
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, bearer(m)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(true));
    }

    @Test
    void detail_of_unknown_certificate_returns_404() throws Exception {
        mockMvc.perform(get("/api/certificates/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void detail_by_slug_returns_certificate() throws Exception {
        // 데모 시드의 정보처리기사(slug=정보처리기사) — 구 pSEO /cert/{slug} 대체 API
        mockMvc.perform(get("/api/certificates/by-slug/{slug}", "정보처리기사"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("정보처리기사"))
                .andExpect(jsonPath("$.category").exists())
                .andExpect(jsonPath("$.schedules").isArray());
    }

    @Test
    void detail_by_unknown_slug_returns_404() throws Exception {
        mockMvc.perform(get("/api/certificates/by-slug/{slug}", "없는자격증-xyz"))
                .andExpect(status().isNotFound());
    }
}

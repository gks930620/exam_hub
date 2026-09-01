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
 * 로그인 사용자 API 통합테스트 (설계 08 §4).
 *
 * <p>핵심 계약: <b>비로그인은 401</b>(400 아님). 프런트가 "요청이 잘못됨"과 "로그인이 필요함"을
 * 구분해야 로그인 화면으로 보낼 수 있다.
 */
class MeApiIntegrationTest extends ApiIntegrationTestSupport {

    private long anyCertificateId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/certificates/popular"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
        return items.get(0).path("id").asLong();
    }

    // ===== 인증 경계 =====

    @Test
    @DisplayName("비로그인으로 내 시험 조회 → 401")
    void favorites_without_login_returns_401() throws Exception {
        mockMvc.perform(get("/api/me/favorites"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("잘못된 토큰 → 401")
    void favorites_with_broken_token_returns_401() throws Exception {
        mockMvc.perform(get("/api/me/favorites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인하면 내 정보를 준다")
    void me_returns_profile() throws Exception {
        Member m = newMember();
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(m)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(m.getId()))
                .andExpect(jsonPath("$.nickname").value(m.getNickname()))
                .andExpect(jsonPath("$.provider").value("GOOGLE"));
    }

    // ===== 관심 등록 =====

    @Test
    void add_favorite_returns_201() throws Exception {
        long certId = anyCertificateId();
        Member m = newMember();

        mockMvc.perform(post("/api/me/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"certificateId\": " + certId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.certificateId").value((int) certId));
    }

    @Test
    void add_favorite_duplicate_returns_409() throws Exception {
        long certId = anyCertificateId();
        Member m = newMember();
        String body = "{\"certificateId\": " + certId + "}";

        mockMvc.perform(post("/api/me/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/me/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    /** 유료화를 안 하기로 해서 관심 개수 제한(구 무료 3개)을 없앴다. */
    @Test
    @DisplayName("관심 시험은 3개를 넘겨도 등록된다 (유료화 제한 폐지)")
    void favorites_have_no_count_limit() throws Exception {
        Member m = newMember();
        MvcResult result = mockMvc.perform(get("/api/certificates/browse").param("size", "5"))
                .andExpect(status().isOk()).andReturn();
        JsonNode items = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/me/favorites")
                            .header(HttpHeaders.AUTHORIZATION, bearer(m))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"certificateId\": " + items.get(i).path("id").asLong() + "}"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/me/favorites").header(HttpHeaders.AUTHORIZATION, bearer(m)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5));
    }

    @Test
    void remove_favorite_returns_204() throws Exception {
        long certId = anyCertificateId();
        Member m = newMember();

        mockMvc.perform(post("/api/me/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"certificateId\": " + certId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/me/favorites/{id}", certId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(m)))
                .andExpect(status().isNoContent());
    }

    // ===== 알림 설정 =====

    @Test
    void get_notify_settings_returns_defaults() throws Exception {
        mockMvc.perform(get("/api/me/notify-settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifyReg").value(true))
                .andExpect(jsonPath("$.notifyExam").value(true))
                .andExpect(jsonPath("$.notifyChange").value(true));
    }

    /** 저장한 값이 이후 조회에 반영돼야 한다 — 조회 API 가 없어 항상 전체 ON 으로 보이던 오표시의 회귀 방지. */
    @Test
    void get_notify_settings_returns_saved_values() throws Exception {
        Member m = newMember();

        mockMvc.perform(put("/api/me/notify-settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notifyReg\": false, \"notifyExam\": true, \"notifyChange\": false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/notify-settings").header(HttpHeaders.AUTHORIZATION, bearer(m)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifyReg").value(false))
                .andExpect(jsonPath("$.notifyChange").value(false));
    }

    // ===== 프로필 =====

    @Test
    void change_nickname() throws Exception {
        mockMvc.perform(patch("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \"새닉네임\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새닉네임"));
    }

    @Test
    void blank_nickname_returns_400() throws Exception {
        mockMvc.perform(patch("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ===== 알림 받을 이메일 =====
    //
    // 카카오는 비즈 앱이 아니면 이메일 동의항목을 켤 수 없다(요청하면 KOE205 로 로그인 자체가 막힌다).
    // 그래서 소셜에서 이메일이 안 온다. 알림 채널이 이메일뿐이라 직접 입력이 유일한 수신 경로다.

    @Test
    @DisplayName("이메일을 직접 입력해 저장한다")
    void change_email() throws Exception {
        Member m = newMember();

        mockMvc.perform(put("/api/me/email")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"me@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"));

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(m)))
                .andExpect(jsonPath("$.email").value("me@example.com"));
    }

    @Test
    @DisplayName("형식이 아닌 이메일 → 400 (저장 전에 막는다)")
    void invalid_email_returns_400() throws Exception {
        mockMvc.perform(put("/api/me/email")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"골뱅이없음\"}"))
                .andExpect(status().isBadRequest());
    }

    /** 빈 값은 "알림 안 받겠다"는 뜻이다 — 400 이 아니라 null 로 지운다. */
    @Test
    @DisplayName("빈 이메일은 수신 해제")
    void blank_email_clears_it() throws Exception {
        Member m = newMember();

        mockMvc.perform(put("/api/me/email")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"me@example.com\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/me/email")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void change_email_without_login_returns_401() throws Exception {
        mockMvc.perform(put("/api/me/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"me@example.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ===== 캘린더 =====

    @Test
    void calendar_returns_events_array() throws Exception {
        mockMvc.perform(get("/api/me/calendar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember()))
                        .param("year", "2026").param("month", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events").isArray());
    }

    @Test
    void calendar_without_params_returns_400() throws Exception {
        mockMvc.perform(get("/api/me/calendar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember())))
                .andExpect(status().isBadRequest());
    }
}

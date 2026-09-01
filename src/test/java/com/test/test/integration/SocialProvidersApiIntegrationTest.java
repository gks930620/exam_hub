package com.test.test.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 쓸 수 있는 소셜 로그인 제공자 조회.
 *
 * <p><b>왜 필요한가</b> — 로그인 화면이 카카오·구글 버튼을 무조건 그리고 있었다. 그런데 구글은
 * Client Secret 이 없으면 등록 자체가 안 만들어져서, 버튼을 누르면
 * {@code Invalid Client Registration with Id: google} 로 <b>흰 화면 500</b> 이 떴다.
 * 키를 아직 안 넣은 환경(지금 로컬, 그리고 키를 하나만 넣은 배포)에서 반드시 겪는다.
 *
 * <p>그래서 서버가 <b>실제로 등록된 것만</b> 알려주고 화면은 그것만 그린다.
 * 매니저 로그인이 {@code /api/manager/available} 로 같은 일을 하고 있어 그 방식을 따랐다.
 */
class SocialProvidersApiIntegrationTest extends ApiIntegrationTestSupport {

    /** 로그인 화면은 비로그인 상태에서 열린다 — 여기에 인증을 걸면 화면이 아무것도 못 그린다. */
    @Test
    @DisplayName("비로그인도 조회할 수 있다")
    void is_public() throws Exception {
        mockMvc.perform(get("/api/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers").isArray());
    }

    /**
     * 키를 하나도 안 넣은 환경(테스트 프로파일이 그렇다)에서는 빈 배열이어야 한다.
     * 여기서 이름이 하나라도 나오면 화면이 누르면 500 나는 버튼을 그린다.
     */
    @Test
    @DisplayName("등록이 없으면 빈 배열 — 화면에 버튼이 안 그려진다")
    void empty_when_nothing_configured() throws Exception {
        mockMvc.perform(get("/api/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers.length()").value(0));
    }

    /**
     * 한쪽만 설정한 상태가 이 서비스의 <b>정상</b> 상태다(구글은 Client Secret 대기 중).
     * 설정한 것만 나오고 안 한 것은 안 나와야 한다.
     */
    @Nested
    @SpringBootTest(properties = {
            "spring.security.oauth2.client.registration.kakao.client-id=test-key",
            "spring.security.oauth2.client.registration.kakao.client-secret=test-secret",
            "spring.security.oauth2.client.registration.kakao.client-authentication-method=client_secret_post",
            "spring.security.oauth2.client.registration.kakao.authorization-grant-type=authorization_code",
            "spring.security.oauth2.client.registration.kakao.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
            "spring.security.oauth2.client.registration.kakao.scope=profile_nickname",
            "spring.security.oauth2.client.provider.kakao.authorization-uri=https://kauth.kakao.com/oauth/authorize",
            "spring.security.oauth2.client.provider.kakao.token-uri=https://kauth.kakao.com/oauth/token",
            "spring.security.oauth2.client.provider.kakao.user-info-uri=https://kapi.kakao.com/v2/user/me",
            "spring.security.oauth2.client.provider.kakao.user-name-attribute=id"})
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class KakaoOnly {

        @Autowired
        MockMvc mvc;

        @Test
        @DisplayName("카카오만 설정하면 카카오만 나온다")
        void lists_only_configured_ones() throws Exception {
            mvc.perform(get("/api/auth/providers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.providers.length()").value(1))
                    .andExpect(jsonPath("$.providers[0]").value("kakao"));
        }
    }
}

package com.test.test.integration;

import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.Series;
import com.test.test.exam.repository.CertificateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>클라이언트 잘못을 서버 잘못으로 말하지 않는다.</b>
 *
 * <p>500 은 두 가지를 망친다 — 보낸 쪽이 무엇을 고쳐야 하는지 모르고, 모니터링에 500 이 쌓여
 * <b>진짜 고장이 묻힌다.</b> 2026-11-14 테스트에서 둘이 걸렸다:
 * <ul>
 *   <li>별 아이콘을 두 번 빠르게 누르면 500 — 그런데 <b>등록은 성공해 있었다</b>(유니크 제약 위반).
 *       사용자는 실패한 줄 알고 또 누르고, 그때마다 또 빨간 줄을 본다.</li>
 *   <li>{@code Content-Type: text/plain} 으로 POST 하면 500.</li>
 * </ul>
 */
class ClientErrorStatusTest extends ApiIntegrationTestSupport {

    @Autowired
    private CertificateRepository certificateRepository;

    private Certificate newCert() {
        return certificateRepository.save(Certificate.builder()
                .name("상태코드시험-" + System.nanoTime())
                .slug("status-" + System.nanoTime())
                .series(Series.ETC).agency("테스트")
                .build());
    }

    @Test
    @DisplayName("JSON 아닌 Content-Type 으로 보내면 415 다 — 500 이 아니다")
    void wrong_content_type_is_415() throws Exception {
        mockMvc.perform(post("/api/community/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember()))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("이미 등록된 시험을 또 등록하면 409 다 — 500 이 아니다")
    void duplicate_favorite_is_409() throws Exception {
        String token = bearer(newMember());
        Long certId = newCert().getId();
        String body = "{\"certificateId\":" + certId + "}";

        mockMvc.perform(post("/api/me/favorites")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        // 두 번째 — 더블클릭이 만드는 상황이다. 사용자에게 "서버 내부 오류"라고 하지 않는다.
        mockMvc.perform(post("/api/me/favorites")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    /**
     * 이름에 {@code /} 가 든 시험이 있다("관광통역안내사(말레이/인도네시아어)"). slug 에 그대로 쓰면
     * 톰캣이 인코딩된 슬래시를 거부해 <b>그 시험만 주소로 못 열린다</b>(2026-11-14 실측).
     * 서버 설정을 느슨하게 하는 대신 slug 에 못 쓰는 글자를 안 넣는 쪽으로 막는다.
     */
    @Test
    @DisplayName("이름에 슬래시가 있어도 slug 으로 열린다")
    void slug_with_slash_in_name_is_reachable() throws Exception {
        Certificate saved = certificateRepository.save(Certificate.builder()
                .name("관광통역안내사(말레이/인도네시아어)")
                .slug(com.test.test.exam.common.Slugs.of("관광통역안내사(말레이/인도네시아어)"))
                .series(Series.ETC).agency("한국산업인력공단")
                .build());

        mockMvc.perform(get("/api/certificates/by-slug/{slug}", saved.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("관광통역안내사(말레이/인도네시아어)"));
    }

    @Test
    @DisplayName("검색어의 LIKE 와일드카드는 글자로 취급한다 — browse 와 같은 답을 준다")
    void search_escapes_like_wildcards() throws Exception {
        // '%%' 는 "모든 것"이 아니라 글자 두 개다. 그런 이름의 시험은 없다.
        mockMvc.perform(get("/api/certificates").param("query", "%%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("이메일은 도메인에 점이 있어야 받는다 — 못 받는 주소를 저장하면 알림이 조용히 사라진다")
    void email_requires_a_dotted_domain() throws Exception {
        String token = bearer(newMember());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/me/email")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/me/email")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"name@example.com\"}"))
                .andExpect(status().isOk());
    }
}

package com.test.test.integration;

import com.test.test.exam.domain.AuthProvider;
import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>내 주소로 진짜 오는지 지금 확인할 수 있어야 한다.</b>
 *
 * <p>이 서비스가 하는 일은 하나다: 접수 마감 전에 메일을 보낸다. 그런데 주소에 오타가 하나
 * 있으면({@code gmail} 을 {@code gmial} 로) 형식 검증은 통과하고, 발송도 "성공"으로 기록되고,
 * <b>메일만 조용히 사라진다.</b> 사용자는 시험 접수를 놓친 뒤에야 안다.
 *
 * <p>그래서 스스로 한 통 보내 볼 수 있게 한다. 지금 안 오면 지금 고칠 수 있다.
 *
 * <p><b>정직하게 답한다.</b> 발송 체인의 끝 {@code LogNotificationSender} 는 서버 로그에 한 줄
 * 찍고 언제나 성공을 돌려준다. 그 길로 빠졌으면 "보냈습니다"가 아니라 <b>"보낼 수단이 없습니다"</b>
 * 라고 말해야 한다 — 안 그러면 사용자는 오지도 않을 메일을 기다린다.
 */
class TestNotificationTest extends ApiIntegrationTestSupport {

    private static final String URL = "/api/me/notify-settings/test";

    /** 주소가 비어 있는 회원 — 카카오는 이메일을 안 줘서 실제로 흔한 상태다. */
    private Member memberWithoutEmail() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return memberRepository.save(Member.builder()
                .provider(AuthProvider.KAKAO)
                .providerId("noemail-" + unique)
                .nickname("주소없음" + unique)
                .role(MemberRole.USER)
                .build());
    }

    @Test
    @DisplayName("비로그인은 보낼 수 없다")
    void anonymous_cannot_send() throws Exception {
        mockMvc.perform(post(URL))
                .andExpect(status().isUnauthorized());
    }

    /** 주소가 없으면 보낼 데가 없다 — "성공"이라고 하면 안 된다. */
    @Test
    @DisplayName("받을 주소가 없으면 400 으로 막고 무엇을 해야 하는지 말한다")
    void without_an_address_it_says_what_to_do() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", bearer(memberWithoutEmail())))
                .andExpect(status().isBadRequest());
    }

    /**
     * 로컬·테스트에는 메일이 꺼져 있어 발송이 LOG 로 빠진다.
     * 그때 "보냈습니다"라고 하면 사용자는 오지도 않은 메일을 기다린다.
     */
    @Test
    @DisplayName("실제로 나간 채널과 도달 여부를 그대로 알려 준다")
    void answers_with_the_channel_actually_used() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", bearer(newMember())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("LOG"))
                .andExpect(jsonPath("$.delivered").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    /** 확인 메일은 진짜 알림과 섞이면 안 된다 — 발송 이력(멱등키)은 회차 알림의 것이다. */
    @Test
    @DisplayName("여러 번 눌러도 막히지 않는다 — 확인용이라 회차 알림의 멱등키와 무관하다")
    void can_be_sent_more_than_once() throws Exception {
        String token = bearer(newMember());

        mockMvc.perform(post(URL).header("Authorization", token)).andExpect(status().isOk());
        mockMvc.perform(post(URL).header("Authorization", token)).andExpect(status().isOk());
    }
}

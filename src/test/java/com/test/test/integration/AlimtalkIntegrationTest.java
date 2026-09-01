package com.test.test.integration;

import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.NotificationChannel;
import com.test.test.exam.notification.NotificationSenderChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 알림톡 수신 준비 상태와 발송 채널 체인.
 *
 * <p><b>알림톡은 번호로 보낸다</b>는 게 핵심 제약이다. 이메일이 있어도 번호가 없으면 카톡은 못 간다.
 * 여기서 번호 등록·정규화·검증과, 번호가 없을 때 이메일로 폴백되는 흐름을 못 박는다.
 */
class AlimtalkIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    private NotificationSenderChain senderChain;

    // ===== 번호 등록 =====

    @Test
    @DisplayName("번호를 등록하면 알림톡을 받을 수 있는 상태가 된다")
    void register_phone_number() throws Exception {
        Member m = newMember();
        assertFalse(m.canReceiveAlimtalk(), "등록 전에는 알림톡 수신 불가여야 한다");

        mockMvc.perform(put("/api/me/phone")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\": \"01012345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("01012345678"));

        Member reloaded = memberRepository.findById(m.getId()).orElseThrow();
        assertTrue(reloaded.canReceiveAlimtalk());
    }

    @Test
    @DisplayName("하이픈을 넣어도 숫자만 저장된다 — 같은 번호가 둘이 되면 안 된다")
    void phone_number_is_normalized() throws Exception {
        Member m = newMember();
        m.changePhoneNumber("010-1234-5678");
        memberRepository.save(m);

        assertEquals("01012345678", memberRepository.findById(m.getId()).orElseThrow().getPhoneNumber());
    }

    @Test
    void invalid_phone_number_returns_400() throws Exception {
        mockMvc.perform(put("/api/me/phone")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\": \"12345\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("빈 값으로 보내면 수신 해제된다 (그러면 이메일로만 간다)")
    void empty_phone_number_clears_it() throws Exception {
        Member m = newMember();
        m.changePhoneNumber("01012345678");
        memberRepository.save(m);

        mockMvc.perform(put("/api/me/phone")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\": \"\"}"))
                .andExpect(status().isOk());

        assertFalse(memberRepository.findById(m.getId()).orElseThrow().canReceiveAlimtalk());
    }

    @Test
    void phone_update_without_login_returns_401() throws Exception {
        mockMvc.perform(put("/api/me/phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\": \"01012345678\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_exposes_phone_number() throws Exception {
        Member m = newMember();
        m.changePhoneNumber("01098765432");
        memberRepository.save(m);

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(m)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("01098765432"));
    }

    // ===== 발송 체인 =====

    /**
     * 알림톡 키가 없는 로컬/테스트에서는 알림톡 발송기가 아예 뜨지 않는다.
     * 그래도 발송이 실패하면 안 된다 — 남은 채널(로그/이메일)로 나가야 한다.
     */
    @Test
    @DisplayName("알림톡 키가 없어도 발송은 성공한다 — 남은 채널로 폴백")
    void chain_falls_back_when_alimtalk_absent() {
        Member m = newMember();

        var result = senderChain.send(m,
                new com.test.test.exam.notification.NotificationMessage(
                        "접수 마감 D-1", "내일 18시에 마감됩니다.",
                        java.util.Map.of("certificateName", "정보처리기사")));

        assertEquals(com.test.test.exam.domain.NotificationResult.SUCCESS, result);
        assertNotNull(senderChain.lastUsedChannel(), "실제로 나간 채널이 기록돼야 한다");
    }

    @Test
    @DisplayName("실제로 나간 채널이 기록된다 — 로그에 남길 값")
    void chain_records_used_channel() {
        senderChain.send(newMember(),
                new com.test.test.exam.notification.NotificationMessage("제목", "본문", java.util.Map.of()));

        NotificationChannel used = senderChain.lastUsedChannel();
        assertTrue(used == NotificationChannel.LOG
                        || used == NotificationChannel.EMAIL
                        || used == NotificationChannel.ALIMTALK,
                "알 수 없는 채널: " + used);
    }
}

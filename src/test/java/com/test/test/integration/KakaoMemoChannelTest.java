package com.test.test.integration;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.AuthProvider;
import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 카카오 "나에게 보내기" 수신 가능 조건.
 *
 * <p><b>왜 이 채널이 있나</b>: 알림톡은 발신프로필에 사업자등록번호가 필수라 <b>테스트 발송조차 불가능</b>하다.
 * 이 채널은 카카오 로그인만으로 되지만 <b>앱 멤버(본인·팀원)에게만</b> 간다 — 그래서 운영용이 아니라 테스트용이다.
 *
 * <p>여기서는 "누구에게 보낼 수 있는가"의 경계를 못 박는다. 실제 카카오 API 호출은 하지 않는다.
 */
class KakaoMemoChannelTest extends ApiIntegrationTestSupport {

    private Member kakaoMember(String accessToken) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Member m = memberRepository.save(Member.builder()
                .provider(AuthProvider.KAKAO)
                .providerId("kakao-" + unique)
                .nickname("카톡유저" + unique)
                .role(MemberRole.USER)
                .build());
        if (accessToken != null) {
            m.updateKakaoToken(accessToken, "refresh-token", TimeUtil.now().plusHours(6));
            memberRepository.save(m);
        }
        return m;
    }

    @Test
    @DisplayName("카카오 로그인 + 토큰이 있으면 나에게 보내기 가능")
    void kakao_member_with_token_can_receive() {
        assertTrue(kakaoMember("access-token").canReceiveKakaoMemo());
    }

    @Test
    @DisplayName("토큰이 없으면 못 보낸다 — talk_message 동의를 안 받은 경우")
    void kakao_member_without_token_cannot_receive() {
        assertFalse(kakaoMember(null).canReceiveKakaoMemo());
    }

    @Test
    @DisplayName("구글 로그인 사용자에게는 카톡을 보낼 수 없다")
    void google_member_cannot_receive_kakao_memo() {
        assertFalse(newMember().canReceiveKakaoMemo(), "구글 계정은 카톡 채널 대상이 아니다");
    }

    @Test
    @DisplayName("만료 판정 — 만료 시각이 지나면 갱신 대상")
    void expired_token_is_detected() {
        Member m = kakaoMember("access-token");
        assertFalse(m.isKakaoTokenExpired());

        m.updateKakaoToken("old", "refresh", TimeUtil.now().minusMinutes(1));
        assertTrue(m.isKakaoTokenExpired(), "지난 시각이면 만료로 봐야 한다");
    }

    @Test
    @DisplayName("갱신 응답에 refresh token 이 없어도 기존 값을 지킨다")
    void refresh_token_is_kept_when_absent() {
        Member m = kakaoMember("access-token");
        m.updateKakaoToken("new-access", null, TimeUtil.now().plusHours(6));

        assertEquals("refresh-token", m.getKakaoRefreshToken(),
                "카카오는 갱신 응답에 refresh_token 을 안 줄 때가 있다 — 지우면 재로그인해야 한다");
        assertEquals("new-access", m.getKakaoAccessToken());
    }

    @Test
    @DisplayName("탈퇴하면 카카오 토큰도 지운다")
    void withdraw_clears_kakao_token() {
        Member m = kakaoMember("access-token");
        m.withdraw();

        assertNull(m.getKakaoAccessToken());
        assertNull(m.getKakaoRefreshToken());
        assertFalse(m.canReceiveKakaoMemo());
    }
}

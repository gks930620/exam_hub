package com.test.test.exam.auth;

import com.test.test.exam.domain.AuthProvider;
import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Member;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 소셜 로그인 성공 → Member upsert → JWT 발급 → 프런트 콜백으로 리다이렉트 (설계 08 §3-1).
 *
 * <p><b>토큰을 쿼리스트링이 아니라 프래그먼트(#)로 넘긴다.</b>
 * 쿼리는 서버 접근로그·Referer 헤더에 남아 토큰이 새어 나간다. 프래그먼트는 서버로 전송되지 않는다.
 *
 * <p>공급자마다 사용자 정보 모양이 달라 여기서 흡수한다:
 * <ul>
 *   <li>구글: {@code sub}, {@code email}, {@code name}, {@code picture} 가 최상위</li>
 *   <li>카카오: {@code id} 최상위 + {@code kakao_account.email},
 *       {@code kakao_account.profile.nickname/profile_image_url} 로 <b>중첩</b></li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final MemberService memberService;
    private final JwtProvider jwtProvider;
    /**
     * ⚠️ 선택 의존성이다. 이 빈은 <b>OAuth2 키가 설정됐을 때만</b> 생긴다.
     * 필수로 받으면 키 없는 환경(로컬·테스트)에서 컨텍스트가 통째로 안 뜬다 —
     * "키가 없어도 앱은 뜬다"는 원칙을 지키려면 반드시 ObjectProvider 여야 한다.
     */
    private final ObjectProvider<OAuth2AuthorizedClientService> authorizedClientService;

    /** 프런트 콜백 주소. 단일 서버라 기본은 같은 출처. */
    @Value("${app.oauth.redirect-uri:/oauth/callback}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String registrationId = resolveRegistrationId(authentication);

        AuthProvider provider = AuthProvider.from(registrationId);
        Profile profile = extract(provider, oAuth2User.getAttributes());

        Member member = memberService.upsertFromOAuth(
                provider, profile.providerId(), profile.email(), profile.nickname(), profile.profileImage());

        // 카카오 토큰을 보관해 둔다 — 사업자등록 전에는 "나에게 보내기"가 카톡을 확인할 유일한 길이다
        if (provider == AuthProvider.KAKAO) {
            saveKakaoToken(member, authentication, registrationId);
        }

        String token = jwtProvider.issue(member.getId(), member.getNickname(), member.getRole().name());

        // 프래그먼트로 전달 — 쿼리스트링은 로그에 남는다
        String target = redirectUri + "#token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private String resolveRegistrationId(Authentication authentication) {
        if (authentication instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken t) {
            return t.getAuthorizedClientRegistrationId();
        }
        throw new IllegalStateException("OAuth2 인증이 아닙니다: " + authentication.getClass());
    }

    @SuppressWarnings("unchecked")
    private Profile extract(AuthProvider provider, Map<String, Object> attrs) {
        if (provider == AuthProvider.GOOGLE) {
            return new Profile(
                    String.valueOf(attrs.get("sub")),
                    (String) attrs.get("email"),
                    (String) attrs.get("name"),
                    (String) attrs.get("picture"));
        }

        // 카카오 — 동의 항목에 따라 없을 수 있으므로 전부 null 방어
        String providerId = String.valueOf(attrs.get("id"));
        Map<String, Object> account = (Map<String, Object>) attrs.get("kakao_account");
        String email = account == null ? null : (String) account.get("email");
        Map<String, Object> kakaoProfile = account == null ? null : (Map<String, Object>) account.get("profile");
        String nickname = kakaoProfile == null ? null : (String) kakaoProfile.get("nickname");
        String image = kakaoProfile == null ? null : (String) kakaoProfile.get("profile_image_url");
        return new Profile(providerId, email, nickname, image);
    }

    /** 스프링 시큐리티가 보관한 OAuth2 토큰을 꺼내 Member 에 옮긴다. */
    private void saveKakaoToken(Member member, Authentication authentication, String registrationId) {
        try {
            OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
            OAuth2AuthorizedClientService service = authorizedClientService.getIfAvailable();
            if (service == null) {
                return;
            }
            OAuth2AuthorizedClient client = service.loadAuthorizedClient(registrationId, token.getName());
            if (client == null || client.getAccessToken() == null) {
                return;
            }
            var access = client.getAccessToken();
            var refresh = client.getRefreshToken();
            memberService.updateKakaoToken(member,
                    access.getTokenValue(),
                    refresh == null ? null : refresh.getTokenValue(),
                    access.getExpiresAt() == null
                            ? TimeUtil.now().plusHours(6)
                            : java.time.LocalDateTime.ofInstant(access.getExpiresAt(), java.time.ZoneId.of("Asia/Seoul")));
        } catch (Exception e) {
            // 토큰 보관 실패가 로그인을 막으면 안 된다 — 로그인은 성공시키고 넘어간다
            log.warn("[OAuth2] 카카오 토큰 보관 실패: {}", e.toString());
        }
    }

    private record Profile(String providerId, String email, String nickname, String profileImage) {
    }
}

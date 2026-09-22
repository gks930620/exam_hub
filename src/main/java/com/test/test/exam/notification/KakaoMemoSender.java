package com.test.test.exam.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.NotificationChannel;
import com.test.test.exam.domain.NotificationResult;
import com.test.test.exam.repository.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 카카오톡 "나에게 보내기" — <b>사업자등록 없이 카톡 알림을 확인하는 유일한 방법</b>.
 *
 * <p><b>왜 이게 필요한가</b>: 알림톡은 발신프로필 등록에 사업자등록번호가 필수라
 * <b>테스트 발송조차 불가능</b>하다. 반면 이 API 는 카카오 로그인만 있으면 되고,
 * 카카오 문서 기준 <b>권한 없는 앱은 "앱 멤버"에게만</b> 보낼 수 있다.
 * 즉 개발자 본인·팀원에게는 진짜 카톡이 온다 — 알림 문구와 흐름을 실제로 눈으로 확인할 수 있다.
 *
 * <p><b>한계</b> (운영 채널로 쓸 수 없는 이유):
 * <ul>
 *   <li>앱 멤버가 아닌 일반 사용자에게는 안 간다(비즈앱 전환 = 결국 사업자 필요).</li>
 *   <li>구글로 로그인한 사용자에게는 당연히 못 보낸다.</li>
 *   <li>액세스 토큰 6시간 / 리프레시 토큰 약 2개월. 2개월 미접속자는 끊긴다.</li>
 * </ul>
 * 그래서 이건 <b>개발·테스트 채널</b>이고, 운영은 알림톡이다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "notification.kakao-memo.enabled", havingValue = "true")
public class KakaoMemoSender implements NotificationSender {

    private static final String SEND_URL = "https://kapi.kakao.com/v2/api/talk/memo/default/send";
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";

    private final RestClient http = RestClient.builder().build();
    private final ObjectMapper objectMapper;
    private final MemberRepository memberRepository;
    private final String clientId;
    private final String clientSecret;
    private final String linkBase;

    public KakaoMemoSender(ObjectMapper objectMapper,
                           MemberRepository memberRepository,
                           @Value("${spring.security.oauth2.client.registration.kakao.client-id:}") String clientId,
                           @Value("${spring.security.oauth2.client.registration.kakao.client-secret:}") String clientSecret,
                           @Value("${app.link-base:http://localhost:8101}") String linkBase) {
        this.objectMapper = objectMapper;
        this.memberRepository = memberRepository;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.linkBase = linkBase;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.KAKAO_MEMO;
    }

    @Override
    @Transactional
    public NotificationResult send(Member member, NotificationMessage message) {
        if (!member.canReceiveKakaoMemo()) {
            // 구글 로그인이거나 토큰이 없는 경우 — 실패가 아니라 "이 채널로는 못 보냄"
            return NotificationResult.SUBSCRIPTION_EXPIRED;
        }

        String token = member.getKakaoAccessToken();
        if (member.isKakaoTokenExpired()) {
            token = refresh(member);
            if (token == null) {
                log.warn("[카톡메모] 토큰 갱신 실패 — 재로그인 필요 member={}", member.getId());
                return NotificationResult.SUBSCRIPTION_EXPIRED;
            }
        }

        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("template_object", objectMapper.writeValueAsString(templateObject(message)));

            http.post()
                    .uri(SEND_URL)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[카톡메모] 발송 member={} title=\"{}\"", member.getId(), message.getTitle());
            return NotificationResult.SUCCESS;
        } catch (Exception e) {
            log.warn("[카톡메모] 발송 실패 member={}: {}", member.getId(), e.toString());
            return NotificationResult.FAILED;
        }
    }

    /** 카카오 기본 템플릿(text). 알림톡과 달리 사전 심사가 없어 문구를 자유롭게 쓴다. */
    private Map<String, Object> templateObject(NotificationMessage message) {
        Map<String, String> data = message.getData() == null ? Map.of() : message.getData();
        String link = linkBase + "/cert/" + data.getOrDefault("certificateId", "");

        return Map.of(
                "object_type", "text",
                "text", message.getTitle() + "\n\n" + message.getBody(),
                "link", Map.of("web_url", link, "mobile_web_url", link),
                "button_title", "일정 확인하기");
    }

    /** 액세스 토큰이 만료됐으면 리프레시 토큰으로 새로 받는다. */
    private String refresh(Member member) {
        if (member.getKakaoRefreshToken() == null || clientId.isBlank()) {
            return null;
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "refresh_token");
            form.add("client_id", clientId);
            form.add("refresh_token", member.getKakaoRefreshToken());
            if (!clientSecret.isBlank()) {
                form.add("client_secret", clientSecret);
            }

            String body = http.post().uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(String.class);

            var json = objectMapper.readTree(body);
            String accessToken = json.path("access_token").asText(null);
            if (accessToken == null) {
                return null;
            }
            member.updateKakaoToken(accessToken,
                    json.path("refresh_token").asText(null),
                    TimeUtil.now().plusSeconds(json.path("expires_in").asLong(21_600)));
            memberRepository.save(member);
            return accessToken;
        } catch (Exception e) {
            log.warn("[카톡메모] 토큰 갱신 실패 member={}: {}", member.getId(), e.toString());
            return null;
        }
    }
}

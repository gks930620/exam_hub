package com.test.test.exam.notification;

import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.NotificationChannel;
import com.test.test.exam.domain.NotificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 카카오 알림톡 발송 (대행사: SOLAPI).
 *
 * <p><b>왜 알림톡인가</b>: 한국에서 알림은 결국 카톡이다. 이메일은 안 읽히고,
 * 텔레그램·디스코드는 일반 사용자에게 "깔아라" 할 수 없다.
 *
 * <p><b>알림톡의 제약</b> — 코드를 읽을 때 알고 있어야 하는 것들:
 * <ul>
 *   <li>본문을 자유롭게 못 쓴다. <b>사전 승인된 템플릿</b> + {@code #{변수}} 치환뿐이다({@link AlimtalkTemplate}).</li>
 *   <li>이메일이 아니라 <b>휴대폰번호</b>로 보낸다. 번호가 없으면 발송 자체가 불가능하다.</li>
 *   <li>수신자가 카톡을 안 쓰거나 채널을 차단하면 실패한다 → 이메일 폴백({@link NotificationSenderChain}).</li>
 *   <li><b>성공 건만 과금</b>되지만 어쨌든 건당 과금이다. 대량 루프를 돌리기 전에 한 번 더 생각할 것.</li>
 * </ul>
 *
 * <p>키가 없으면 이 빈은 뜨지 않는다({@code notification.alimtalk.enabled}). 그래도 앱은 정상 동작하고
 * 이메일/로그로 알림이 나간다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "notification.alimtalk.enabled", havingValue = "true")
public class KakaoAlimtalkSender implements NotificationSender {

    private static final String SEND_URL = "https://api.solapi.com/messages/v4/send";

    private final RestClient http = RestClient.builder().build();
    private final Environment environment;

    private final String apiKey;
    private final String apiSecret;
    /** 발신프로필 키 — 어느 카카오톡 채널로 보낼지 */
    private final String pfId;
    /** 발신번호(사전 등록된 번호). 알림톡 실패 시 SMS 대체발송에 쓰인다 */
    private final String from;
    /** true 면 알림톡 실패 시 SMS 로 자동 대체발송(추가 과금). 기본은 끔 — 이메일로 폴백한다 */
    private final boolean smsFallback;

    public KakaoAlimtalkSender(
            Environment environment,
            @Value("${notification.alimtalk.api-key}") String apiKey,
            @Value("${notification.alimtalk.api-secret}") String apiSecret,
            @Value("${notification.alimtalk.pf-id}") String pfId,
            @Value("${notification.alimtalk.from:}") String from,
            @Value("${notification.alimtalk.sms-fallback:false}") boolean smsFallback) {
        this.environment = environment;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.pfId = pfId;
        this.from = from;
        this.smsFallback = smsFallback;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.ALIMTALK;
    }

    @Override
    public NotificationResult send(Member member, NotificationMessage message) {
        if (!member.canReceiveAlimtalk()) {
            // 번호가 없으면 실패가 아니라 "이 채널로는 못 보냄" — 체인이 이메일로 넘긴다
            log.debug("[알림톡] 번호 없음 member={}", member.getId());
            return NotificationResult.SUBSCRIPTION_EXPIRED;
        }

        String templateId = resolveTemplateId(message);
        if (templateId == null || templateId.isBlank()) {
            log.warn("[알림톡] 템플릿 코드 미설정 — 승인 후 설정에 넣어야 한다. message=\"{}\"", message.title());
            return NotificationResult.FAILED;
        }

        try {
            Map<String, Object> body = Map.of("message", Map.of(
                    "to", member.getPhoneNumber(),
                    "from", from,
                    "kakaoOptions", kakaoOptions(templateId, member, message)));

            http.post()
                    .uri(SEND_URL)
                    .header("Authorization", authorization())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[알림톡] 발송 member={} template={}", member.getId(), templateId);
            return NotificationResult.SUCCESS;
        } catch (Exception e) {
            // 카톡 미사용·채널 차단·번호 오류 등 — 체인이 이메일로 넘긴다
            log.warn("[알림톡] 발송 실패 member={}: {}", member.getId(), e.toString());
            return NotificationResult.FAILED;
        }
    }

    private Map<String, Object> kakaoOptions(String templateId, Member member, NotificationMessage message) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("pfId", pfId);
        options.put("templateId", templateId);
        options.put("variables", variables(member, message));
        // 기본은 SMS 대체발송을 끈다 — 알림톡이 실패하면 이메일로 보내는 게 우리 정책이고,
        // SMS 는 추가 과금이라 모르는 사이에 돈이 나가면 안 된다
        options.put("disableSms", !smsFallback);
        return options;
    }

    /**
     * 템플릿 변수 치환. 승인 문구의 {@code #{변수}} 이름과 <b>정확히</b> 맞아야 한다 —
     * 하나라도 어긋나면 발송이 거부된다.
     */
    private Map<String, String> variables(Member member, NotificationMessage message) {
        Map<String, String> data = message.data() == null ? Map.of() : message.data();
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("#{닉네임}", member.getNickname());
        vars.put("#{시험명}", data.getOrDefault("certificateName", message.title()));
        vars.put("#{일정}", data.getOrDefault("eventAt", ""));
        vars.put("#{안내}", message.body());
        return vars;
    }

    /** 이벤트 유형에 맞는 승인 템플릿 코드를 설정에서 찾는다. */
    private String resolveTemplateId(NotificationMessage message) {
        String eventType = message.data() == null ? null : message.data().get("eventType");
        AlimtalkTemplate template = AlimtalkTemplate.REG_CLOSING;
        if (eventType != null) {
            try {
                template = AlimtalkTemplate.from(
                        com.test.test.exam.domain.NotificationEventType.valueOf(eventType));
            } catch (IllegalArgumentException ignored) {
                // 알 수 없는 유형이면 기본값 유지
            }
        }
        return environment.getProperty(template.getPropertyKey(), template.getDefaultCode());
    }

    /**
     * SOLAPI 인증 헤더.
     * {@code HMAC-SHA256 apiKey=..., date=..., salt=..., signature=HMAC(date+salt, secret)}
     */
    private String authorization() {
        String date = Instant.now().toString();
        String salt = UUID.randomUUID().toString().replace("-", "");
        String signature = hmacSha256(date + salt, apiSecret);
        return "HMAC-SHA256 apiKey=" + apiKey + ", date=" + date
                + ", salt=" + salt + ", signature=" + signature;
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("알림톡 서명 생성 실패", e);
        }
    }
}

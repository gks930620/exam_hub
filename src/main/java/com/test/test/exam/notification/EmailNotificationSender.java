package com.test.test.exam.notification;

import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.NotificationChannel;
import com.test.test.exam.domain.NotificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * 이메일 알림 발송 (사용자 결정 2026-08-07 — 웹 푸시가 아니라 이메일).
 *
 * <p><b>왜 이메일인가</b>: 접수 마감은 놓치면 끝이라 도달이 중요하다.
 * 웹 푸시는 브라우저를 닫으면 못 보고, 알림 권한을 거부하면 아예 못 보낸다.
 *
 * <p><b>주소는 사용자가 직접 넣는다</b> — 카카오의 {@code account_email} 동의항목은 비즈 앱
 * 전환 후에만 켤 수 있어서(요청하면 KOE205 로 로그인이 막힌다) 소셜에서 이메일이 오지 않는다.
 * 내 정보 화면의 {@code PUT /api/me/email} 로 받는다.
 *
 * <p>{@code notification.email.enabled=true} 이고 SMTP 설정이 있을 때만 이 빈이 뜬다.
 * 없으면 {@link LogNotificationSender} 가 남아 로컬 개발이 그대로 돌아간다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final String from;
    private final String fromName;
    private final String linkBase;

    public EmailNotificationSender(JavaMailSender mailSender,
                                   @Value("${notification.email.from}") String from,
                                   @Value("${notification.email.from-name:모든시험한번에보기}") String fromName,
                                   @Value("${app.link-base}") String linkBase) {
        this.mailSender = mailSender;
        this.from = from;
        this.fromName = fromName;
        this.linkBase = linkBase;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public NotificationResult send(Member member, NotificationMessage message) {
        if (!member.canReceiveEmail()) {
            // 카카오 로그인에서 이메일 동의를 안 했으면 주소가 없다 — 실패가 아니라 "보낼 수 없음"이다
            log.debug("[Email] 수신 주소 없음 member={}", member.getId());
            return NotificationResult.SUBSCRIPTION_EXPIRED;
        }

        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom(from, fromName);
            helper.setTo(member.getEmail());
            helper.setSubject(message.title());
            helper.setText(body(member, message), true);
            mailSender.send(mime);

            log.info("[Email] 발송 member={} subject=\"{}\"", member.getId(), message.title());
            return NotificationResult.SUCCESS;
        } catch (MailException | jakarta.mail.MessagingException | UnsupportedEncodingException e) {
            log.error("[Email] 발송 실패 member={}: {}", member.getId(), e.toString());
            return NotificationResult.FAILED;
        }
    }

    /**
     * 본문. 템플릿 엔진을 쓸 만큼 복잡하지 않아 문자열로 만든다 —
     * 알림 메일은 한 문단 + 링크가 전부다.
     */
    private String body(Member member, NotificationMessage message) {
        Map<String, String> data = message.data() == null ? Map.of() : message.data();
        String certId = data.getOrDefault("certificateId", "");
        // app.link-base 를 써야 로컬(8101)·운영이 각자 자기 주소로 간다.
        // 여기를 하드코딩하면 메일 속 버튼이 아무 데도 닿지 않는다.
        String link = linkBase + "/cert/" + certId;

        return """
                <div style="font-family:system-ui,-apple-system,'Malgun Gothic',sans-serif;
                            max-width:520px;margin:0 auto;padding:24px;color:#2b2745">
                  <p style="font-size:13px;color:#9491b4;margin:0 0 18px">모든시험한번에보기</p>
                  <h1 style="font-size:20px;font-weight:700;letter-spacing:-.02em;margin:0 0 12px">%s</h1>
                  <p style="font-size:15px;line-height:1.7;margin:0 0 22px">%s</p>
                  <a href="%s" style="display:inline-block;background:#6d5efc;color:#fff;
                     text-decoration:none;padding:11px 22px;border-radius:999px;font-weight:600">
                     일정 확인하기</a>
                  <hr style="border:0;border-top:1px solid #eeedfa;margin:28px 0 14px" />
                  <p style="font-size:12px;color:#9491b4;line-height:1.7;margin:0">
                    %s 님께 보내는 알림입니다. 알림 설정은 사이트의 <b>알림 설정</b>에서 끌 수 있습니다.<br />
                    본 알림은 참고용입니다 — 최종 일정은 시행처에서 확인하세요.
                  </p>
                </div>
                """.formatted(message.title(), message.body(), link, member.getNickname());
    }
}

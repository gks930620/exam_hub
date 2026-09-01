package com.test.test.exam.notification;

import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.NotificationChannel;
import com.test.test.exam.domain.NotificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 현재 유일한 발송 구현 — 실제 푸시 대신 로그로 남긴다. 발송 로직/멱등/배치를 이 구현으로 완전 검증 가능.
 * (웹 전용 실발송은 추후 웹 푸시/이메일 Sender 로 추가 예정 — 모바일 FCM 제거함)
 */
@Slf4j
@Component
public class LogNotificationSender implements NotificationSender {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.LOG;
    }

    @Override
    public NotificationResult send(Member user, NotificationMessage message) {
        log.info("[PUSH:LOG] member={} title=\"{}\" body=\"{}\" data={}",
                user.getNickname(), message.title(), message.body(), message.data());
        return NotificationResult.SUCCESS;
    }
}

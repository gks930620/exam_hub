package com.test.test.exam.notification;

import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.NotificationChannel;
import com.test.test.exam.domain.NotificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 발송 체인({@link NotificationSenderChain})의 <b>마지막 폴백</b> — 알림톡·카카오 메모·이메일이 모두 못 보낼 때
 * 로그로만 남긴다. 실발송 채널이 하나도 안 켜진 로컬·테스트에서는 결과적으로 이것만 돌므로,
 * 발송 로직/멱등/배치를 실제 발송 없이 검증할 수 있다. 운영은 {@code MAIL_ENABLED=true} 로 이메일이 실발송이다.
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
                user.getNickname(), message.getTitle(), message.getBody(), message.getData());
        return NotificationResult.SUCCESS;
    }
}

package com.test.test.exam.notification;

import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.NotificationChannel;
import com.test.test.exam.domain.NotificationResult;

/**
 * 알림 발송 채널 추상화 (채널 확정 전까지 로그/스텁으로).
 * 채널 추가(웹 푸시·이메일·알림톡)는 이 인터페이스 구현으로 확장.
 */
public interface NotificationSender {

    NotificationChannel channel();

    /** 발송 시도 후 결과 반환. 수신 주소 만료는 SUBSCRIPTION_EXPIRED 로 구분(구독 정리 트리거). */
    NotificationResult send(Member user, NotificationMessage message);
}

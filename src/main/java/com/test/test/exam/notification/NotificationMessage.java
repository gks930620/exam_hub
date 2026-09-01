package com.test.test.exam.notification;

import java.util.Map;

/**
 * 발송 페이로드 (설계 05 페이로드 예시).
 * data 에는 딥링크용 route/식별자 포함.
 */
public record NotificationMessage(
        String title,
        String body,
        Map<String, String> data
) {
}

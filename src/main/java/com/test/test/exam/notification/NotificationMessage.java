package com.test.test.exam.notification;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 발송 페이로드 (설계 05 페이로드 예시).
 * data 에는 딥링크용 route/식별자 포함.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    private String title;
    private String body;
    private Map<String, String> data;
}

package com.test.test.exam.domain;

/**
 * 개별 발송 결과.
 * SUBSCRIPTION_EXPIRED 는 수신 주소가 더 이상 유효하지 않은 경우(웹 푸시 구독 만료 410 등) — 구독 정리 대상.
 */
public enum NotificationResult {
    SUCCESS,
    FAILED,
    SUBSCRIPTION_EXPIRED
}

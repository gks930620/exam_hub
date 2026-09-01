package com.test.test.exam.domain;

/**
 * notification_schedule 발송 예약 상태.
 * - PENDING: 발송 대기
 * - SENT: 발송 완료
 * - CANCELED: 일정 취소로 예약 취소
 * - SKIPPED: 파생 시점에 이미 과거라 발송 생략
 */
public enum NotificationScheduleStatus {
    PENDING,
    SENT,
    CANCELED,
    SKIPPED
}

package com.test.test.exam.domain;

/**
 * exam_schedule 상태.
 * - ACTIVE: 정상 노출
 * - PENDING_REVIEW: 검증 보류(30일 이상 이동 등) — 조회/알림 대상에서 제외, 관리자 확인 대기 (DR-04-b)
 * - CANCELED: 취소된 회차
 * - DONE: 완료(과거) 회차
 */
public enum ScheduleStatus {
    ACTIVE,
    PENDING_REVIEW,
    CANCELED,
    DONE
}

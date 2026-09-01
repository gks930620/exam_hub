package com.test.test.exam.domain;

/**
 * 알림 이벤트 유형 및 대상 토글 (설계 04 §2-5).
 */
public enum NotificationEventType {
    REG_OPEN_EVE(ToggleTarget.REG),   // 접수 시작 전날 20:00
    REG_OPEN_DAY(ToggleTarget.REG),   // 접수 시작 당일 09:00
    REG_CLOSE_EVE(ToggleTarget.REG),  // 접수 마감 전날 20:00
    EXAM_D7(ToggleTarget.EXAM),       // 시험일 7일 전 09:00
    EXAM_D1(ToggleTarget.EXAM),       // 시험일 전날 20:00
    SCHEDULE_CHANGED(ToggleTarget.CHANGE); // 변경 감지 즉시(다음 배치)

    public enum ToggleTarget { REG, EXAM, CHANGE }

    private final ToggleTarget toggleTarget;

    NotificationEventType(ToggleTarget toggleTarget) {
        this.toggleTarget = toggleTarget;
    }

    public ToggleTarget getToggleTarget() {
        return toggleTarget;
    }
}

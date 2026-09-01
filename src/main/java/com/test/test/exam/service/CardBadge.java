package com.test.test.exam.service;

/**
 * 홈 D-day 카드/상세 히어로의 상태 배지.
 * priority: 카드 정렬 순서 (설계 03 S-01) — ① 접수중 ② 접수 예정 ③ 시험 예정.
 */
public enum CardBadge {
    REG_OPEN("접수중", 0),
    REG_UPCOMING("접수 예정", 1),
    EXAM_UPCOMING("시험 예정", 2),
    NONE("일정 없음", 3);

    private final String label;
    private final int priority;

    CardBadge(String label, int priority) {
        this.label = label;
        this.priority = priority;
    }

    public String getLabel() {
        return label;
    }

    public int getPriority() {
        return priority;
    }
}

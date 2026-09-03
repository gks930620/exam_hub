package com.test.test.exam.service;

/**
 * 홈 D-day 카드/상세 히어로의 상태 배지.
 * priority: 카드 정렬 순서 (설계 03 S-01) — ① 접수 중 ② 시험 진행 중 ③ 접수 예정 ④ 시험 예정.
 *
 * <p>접수 중이 진행 중보다 앞인 이유: 이 서비스의 약속은 "접수 마감을 놓치지 않게"다.
 * 실기처럼 몇 주짜리 시험 기간 동안 다음 회차 접수가 열리면, 그 접수가 먼저 보여야 한다.
 */
public enum CardBadge {
    REG_OPEN("접수 중", 0),
    EXAM_ONGOING("시험 진행 중", 1),
    REG_UPCOMING("접수 예정", 2),
    EXAM_UPCOMING("시험 예정", 3),
    NONE("일정 없음", 4);

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

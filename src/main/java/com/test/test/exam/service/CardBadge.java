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
    /**
     * 시험은 끝났고 합격자 발표만 남았다. 다른 넷보다 뒤인 이유는, 다음 회차 접수가 열려 있으면
     * 그게 먼저 급하기 때문이다 — 발표는 놓쳐도 되돌릴 수 있지만 접수는 못 되돌린다.
     *
     * <p>이게 없을 땐 시험을 친 사람에게 <b>"다음 회차 미정"</b>만 보였다. 감정사는 8/21 에 시험을
     * 치고 9/16 에 발표인데 화면 어디에도 9/16 이 없었다(2026-09-10 실측, 24종).
     * 시험을 막 친 사람이 가장 궁금한 날짜다.
     */
    RESULT_PENDING("합격 발표 예정", 4),
    NONE("일정 없음", 5);

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

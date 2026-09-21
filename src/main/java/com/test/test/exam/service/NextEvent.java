package com.test.test.exam.service;

import java.time.LocalDateTime;

/**
 * 자격증의 "다음(또는 진행 중) 대표 이벤트" 계산 결과.
 *
 * @param badge           상태 배지
 * @param type            상세용 이벤트 타입 문자열(REG_CLOSING/REG_OPEN/EXAM)
 * @param label           표기 라벨 (예: "2회 필기 접수 마감")
 * @param at              이벤트 기준 시각
 * @param dday            남은 일수 (오늘=0=D-day, 접수중은 마감까지 남은 일수)
 * @param examScheduleId  근거가 된 회차 일정 id
 * @param confirmed       <b>시행처에서 확인된 날짜인가.</b> 추정치면 false —
 *                        카드가 D-day 옆에 "추정"을 붙여야 사용자가 그 날짜를 곧이곧대로 믿지 않는다.
 *                        상세 표에만 표시하던 때는 카드만 보는 사람에게 닿지 않았다(2026-09-21).
 */
public record NextEvent(
        CardBadge badge,
        String type,
        String label,
        LocalDateTime at,
        long dday,
        Long examScheduleId,
        boolean confirmed
) {
    public static NextEvent none() {
        return new NextEvent(CardBadge.NONE, "NONE", "예정된 일정 없음", null, 0, null, true);
    }

    public boolean isPresent() {
        return badge != CardBadge.NONE;
    }
}

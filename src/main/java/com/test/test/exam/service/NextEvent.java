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
 */
public record NextEvent(
        CardBadge badge,
        String type,
        String label,
        LocalDateTime at,
        long dday,
        Long examScheduleId
) {
    public static NextEvent none() {
        return new NextEvent(CardBadge.NONE, "NONE", "예정된 일정 없음", null, 0, null);
    }

    public boolean isPresent() {
        return badge != CardBadge.NONE;
    }
}

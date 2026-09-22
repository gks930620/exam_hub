package com.test.test.exam.service;

import com.test.test.common.exception.BusinessRuleException;

/**
 * 시험 찾기에서 <b>무엇만 볼지</b>.
 *
 * <p>이 서비스의 약속은 "접수 마감을 놓치지 않게"인데, 그전에는 841종을 인기순으로 훑는 길밖에
 * 없었다. 2026-09-22 실측으로 232종이 접수 중이고 그중 6종이 그 주에 마감이었는데 그 6종을
 * 찾을 방법이 없었다.
 *
 * <p><b>정렬을 따로 고르게 하지 않는다.</b> 상태가 이미 "무엇이 급한가"를 담고 있다 —
 * 접수 중이면 마감이 급한 것부터, 곧 접수면 시작이 가까운 것부터다.
 */
public enum BrowseState {

    /** 지금 원서를 넣을 수 있다 — 마감 임박순 */
    OPEN,
    /** 일주일 안에 접수가 열린다 — 시작 임박순 */
    SOON;

    /** 접수 시작 예고를 며칠 앞까지 볼 것인가. 첫 화면 지표와 같은 값이어야 숫자가 맞는다. */
    public static final int SOON_DAYS = 7;

    /**
     * 비어 있으면 {@code null}(= 전체). 모르는 값은 <b>조용히 전체를 주지 않고</b> 막는다 —
     * 오타 하나에 전체 목록을 주면 사용자는 걸러진 목록을 보고 있다고 믿는다.
     */
    public static BrowseState from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("state 는 OPEN 또는 SOON 이어야 합니다. 받은 값=" + raw);
        }
    }
}

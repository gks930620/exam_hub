package com.test.test.exam.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 관심/홈 D-day 카드 관련 DTO (설계 05 §2-3, §2-4).
 */
public final class FavoriteDtos {

    private FavoriteDtos() {
    }

    /** GET /api/me/favorites 응답 */
    public record ListResponse(List<Card> items) {
    }

    /**
     * 홈 D-day 카드 1건.
     *
     * @param badge         REG_OPEN | EXAM_ONGOING | REG_UPCOMING | EXAM_UPCOMING | NONE
     * @param dday          이벤트까지 남은 일수. <b>이벤트가 없으면 null</b> — 0 은 "오늘"이지 "없음"이 아니다
     * @param scheduleState ROLLING | UPCOMING | PAST_ONLY | NONE — 시험 찾기 카드와 같은 판정
     *                      (날짜 없는 회차 제외, 상시 우선)
     * @param lastExamDate  PAST_ONLY 일 때 마지막 시험일(yyyy-MM-dd), 없으면 null
     * @param hiddenReason  폐지·개칭으로 사용자 화면에서 숨긴 시험이면 그 이유(새 이름 안내). 보이는 시험이면 null
     */
    public record Card(
            Long certificateId,
            String name,
            String badge,
            String badgeLabel,
            String eventLabel,
            String eventAt,
            Long dday,
            String scheduleState,
            String lastExamDate,
            String hiddenReason
    ) {
    }

    /** POST /api/me/favorites 요청 */
    public record CreateRequest(
            @NotNull(message = "certificateId는 필수입니다.")
            Long certificateId
    ) {
    }

    /** POST /api/me/favorites 응답 */
    public record CreateResponse(
            Long certificateId,
            int favoriteCount
    ) {
    }
}

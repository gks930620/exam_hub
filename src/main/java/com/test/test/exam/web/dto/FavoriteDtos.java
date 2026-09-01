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

    /** 홈 D-day 카드 1건 */
    public record Card(
            Long certificateId,
            String name,
            String badge,
            String badgeLabel,
            String eventLabel,
            String eventAt,
            long dday
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

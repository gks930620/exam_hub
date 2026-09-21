package com.test.test.exam.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 관심/홈 D-day 카드 관련 DTO (설계 05 §2-3, §2-4).
 *
 * <p><b>DTO 는 record 가 아니라 class 다</b>(코드 컨벤션 §0). 응답은 {@code @Getter} 만,
 * <b>요청은 {@code @Setter} 도 붙인다</b> — Jackson 이 JSON 을 채워 넣을 손잡이가 필요하다.
 * 안 붙이면 필드가 전부 null 로 들어오고, 그건 컴파일에 안 걸리고 <b>런타임에 조용히</b> 틀린다.
 */
public final class FavoriteDtos {

    private FavoriteDtos() {
    }

    /** GET /api/me/favorites 응답 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListResponse {
        private List<Card> items;
    }

    /**
     * 홈 D-day 카드 1건.
     *
     * <p>{@code badge} 는 REG_OPEN | EXAM_ONGOING | REG_UPCOMING | EXAM_UPCOMING | RESULT_PENDING | NONE.
     * {@code dday} 는 이벤트까지 남은 일수 — <b>이벤트가 없으면 null</b> 이다. 0 은 "오늘"이지 "없음"이 아니다.
     * {@code scheduleState} 는 ROLLING | UPCOMING | PAST_ONLY | NONE 로 시험 찾기 카드와 같은 판정이다
     * (날짜 없는 회차 제외, 상시 우선). {@code lastExamDate} 는 PAST_ONLY 일 때 마지막 시험일이고,
     * {@code hiddenReason} 은 폐지·개칭으로 숨긴 시험의 사유다(보이는 시험이면 null).
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Card {
        private Long certificateId;
        private String name;
        private String badge;
        private String badgeLabel;
        private String eventLabel;
        private String eventAt;
        private Long dday;
        private String scheduleState;
        private String lastExamDate;
        private String hiddenReason;
        /** 다음 일정이 시행처에서 확인된 날짜인가. 추정치면 false — 카드가 "추정"을 붙인다 */
        private boolean nextConfirmed;
    }

    /** POST /api/me/favorites 요청 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull(message = "certificateId는 필수입니다.")
        private Long certificateId;
    }

    /** POST /api/me/favorites 응답 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateResponse {
        private Long certificateId;
        private int favoriteCount;
    }
}

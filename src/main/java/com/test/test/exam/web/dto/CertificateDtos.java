package com.test.test.exam.web.dto;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.service.NextEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 자격증 관련 REST 응답 DTO 묶음 (설계 05 §2-1, §2-2).
 *
 * <p><b>DTO 는 record 가 아니라 class 다</b>(코드 컨벤션 §0). 여기는 전부 응답이라
 * {@code @Setter} 가 없다 — 서버가 만들어 내보내기만 하고 받아 채울 일이 없다.
 */
public final class CertificateDtos {

    private CertificateDtos() {
    }

    /**
     * GET /api/certificates, /popular, /browse 결과 아이템.
     * {@code hasSchedule=false} 는 "시험은 있는데 일정이 아직 없음" — 화면에서 '일정 미정'으로 표기한다.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long id;
        private String name;
        private String slug;
        private String series;
        private String seriesLabel;
        private String category;
        private String agency;
        private boolean favorited;
        private boolean hasSchedule;
        /** 상시·예약제 — "일정"이 없는 시험. 화면은 '일정 미정' 대신 '상시시험'을 보여준다 */
        private boolean rolling;
        /**
         * 카드가 보여줄 상태 — UPCOMING(앞으로 일정 있음) | PAST_ONLY(남은 일정이 전부 지남 = 다음 회차 미정)
         * | NONE(일정 없음) | ROLLING(상시). "일정 있음"만으로는 지난 일정만 남은 시험이
         * 멀쩡해 보여서 사용자가 지난 날짜를 믿게 된다.
         */
        private String scheduleState;
        /** UPCOMING 일 때 대표 이벤트(사용자 화면과 매니저 화면이 같은 계산을 쓴다) */
        private String nextLabel;
        private String nextAt;
        private Integer nextDday;
        private String nextBadge;
        /** PAST_ONLY 일 때 마지막 시험일 */
        private String lastExamDate;

        public static Item of(Certificate c, boolean favorited) {
            return of(c, favorited, true);
        }

        public static Item of(Certificate c, boolean favorited, boolean hasSchedule) {
            return of(c, favorited, hasSchedule, null, null, null);
        }

        public static Item of(Certificate c, boolean favorited, boolean hasSchedule,
                              String scheduleState, NextEvent next, String lastExamDate) {
            boolean upcoming = next != null && next.isPresent();
            return new Item(
                    c.getId(), c.getName(), c.getSlug(),
                    c.getSeries().name(), c.getSeries().getLabel(),
                    c.getCategory(),
                    c.getAgency(), favorited, hasSchedule, c.isRollingAdmission(),
                    scheduleState,
                    upcoming ? next.label() : null,
                    upcoming ? TimeUtil.format(next.at()) : null,
                    upcoming ? (int) next.dday() : null,
                    upcoming ? next.badge().name() : null,
                    lastExamDate);
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResponse {
        private List<Item> items;
    }

    /** GET /api/certificates/browse — 전체 둘러보기(페이징) */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrowseResponse {
        private List<Item> items;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    /** GET /api/certificates/stats — 첫 화면 지표 타일. 비로그인도 본다 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatsResponse {
        private long totalExams;
        private long withSchedule;
        /** 지금 접수 중 */
        private long registrationOpen;
        /** 7일 안에 접수 시작 */
        private long openingWithin7Days;
        /** 상시·예약제 — "못 얻은 데이터" 안에 포함돼 있다. 화면이 "상시 N 포함"으로 푼다 */
        private long rolling;
    }

    /** GET /api/certificates/categories — 필터용 분류 목록 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryItem {
        private String name;
        private long count;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryResponse {
        private List<CategoryItem> items;
    }

    /** GET /api/certificates/{id} 상세 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailResponse {
        private Long id;
        private String name;
        private String category;
        private String agency;
        private String sourceUrl;
        private String collectedAt;
        private boolean favorited;
        /** 상시·예약제 — 일정 표 대신 "원하는 날짜에 신청" 안내를 보여준다 */
        private boolean rolling;
        private EventDto nextEvent;
        private List<ScheduleDto> schedules;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventDto {
        private String type;
        private String label;
        private long dday;
        private String at;

        public static EventDto of(NextEvent e) {
            if (e == null || !e.isPresent()) {
                return null;
            }
            return new EventDto(e.type(), e.label(), e.dday(), TimeUtil.format(e.at()));
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleDto {
        private Long id;
        private int year;
        private int round;
        private String examType;
        private String regStartAt;
        private String regEndAt;
        private String examStartDate;
        private String examEndDate;
        private String resultDate;
        private String status;
        /** 이 날짜를 어디서 얻었나 — 추정치면 화면이 경고를 띄운다 */
        private String provenance;
        private String provenanceLabel;
        /** 시행처에서 확인된 값인가. false 면 "시행처 확인 필요" */
        private boolean confirmed;

        public static ScheduleDto of(ExamSchedule s) {
            return new ScheduleDto(
                    s.getId(),
                    s.getYear(),
                    s.getRound(),
                    s.getExamType().name(),
                    TimeUtil.format(s.getRegStartAt()),
                    TimeUtil.format(s.getRegEndAt()),
                    TimeUtil.format(s.getExamStartDate()),
                    TimeUtil.format(s.getExamEndDate()),
                    TimeUtil.format(s.getResultDate()),
                    s.getStatus().name(),
                    s.getProvenance() == null ? null : s.getProvenance().name(),
                    s.getProvenance() == null ? null : s.getProvenance().getLabel(),
                    s.getProvenance() == null || s.getProvenance().isConfirmed());
        }
    }
}

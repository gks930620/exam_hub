package com.test.test.exam.web.dto;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.service.NextEvent;

import java.util.List;

/**
 * 자격증 관련 REST 응답 DTO 묶음 (설계 05 §2-1, §2-2).
 */
public final class CertificateDtos {

    private CertificateDtos() {
    }

    /**
     * GET /api/certificates, /popular, /browse 결과 아이템.
     * {@code hasSchedule=false} 는 "시험은 있는데 일정이 아직 없음" — 화면에서 '일정 미정'으로 표기한다.
     */
    public record Item(
            Long id,
            String name,
            String slug,
            String series,
            String seriesLabel,
            String category,
            String agency,
            boolean favorited,
            boolean hasSchedule,
            /** 상시·예약제 — "일정"이 없는 시험. 화면은 '일정 미정' 대신 '상시시험'을 보여준다 */
            boolean rolling
    ) {
        public static Item of(Certificate c, boolean favorited) {
            return of(c, favorited, true);
        }

        public static Item of(Certificate c, boolean favorited, boolean hasSchedule) {
            return new Item(
                    c.getId(), c.getName(), c.getSlug(),
                    c.getSeries().name(), c.getSeries().getLabel(),
                    c.getCategory(),
                    c.getAgency(), favorited, hasSchedule, c.isRollingAdmission());
        }
    }

    public record SearchResponse(List<Item> items) {
    }

    /** GET /api/certificates/browse — 전체 둘러보기(페이징) */
    public record BrowseResponse(
            List<Item> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    /** GET /api/certificates/stats — 첫 화면 지표 타일. 비로그인도 본다 */
    public record StatsResponse(
            long totalExams,
            long withSchedule,
            /** 지금 접수 중 */
            long registrationOpen,
            /** 7일 안에 접수 시작 */
            long openingWithin7Days,
            /** 상시·예약제 — "못 얻은 데이터" 안에 포함돼 있다. 화면이 "상시 N 포함"으로 푼다 */
            long rolling
    ) {
    }

    /** GET /api/certificates/categories — 필터용 분류 목록 */
    public record CategoryItem(String name, long count) {
    }

    public record CategoryResponse(List<CategoryItem> items) {
    }

    /** GET /api/certificates/{id} 상세 */
    public record DetailResponse(
            Long id,
            String name,
            String category,
            String agency,
            String sourceUrl,
            String collectedAt,
            boolean favorited,
            /** 상시·예약제 — 일정 표 대신 "원하는 날짜에 신청" 안내를 보여준다 */
            boolean rolling,
            EventDto nextEvent,
            List<ScheduleDto> schedules
    ) {
    }

    public record EventDto(
            String type,
            String label,
            long dday,
            String at
    ) {
        public static EventDto of(NextEvent e) {
            if (e == null || !e.isPresent()) {
                return null;
            }
            return new EventDto(e.type(), e.label(), e.dday(), TimeUtil.format(e.at()));
        }
    }

    public record ScheduleDto(
            Long id,
            int year,
            int round,
            String examType,
            String regStartAt,
            String regEndAt,
            String examStartDate,
            String examEndDate,
            String resultDate,
            String status,
            /** 이 날짜를 어디서 얻었나 — 추정치면 화면이 경고를 띄운다 */
            String provenance,
            String provenanceLabel,
            /** 시행처에서 확인된 값인가. false 면 "시행처 확인 필요" */
            boolean confirmed
    ) {
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

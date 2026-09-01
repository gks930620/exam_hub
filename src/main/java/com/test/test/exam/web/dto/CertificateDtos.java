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
            boolean hasSchedule
    ) {
        public static Item of(Certificate c, boolean favorited) {
            return of(c, favorited, true);
        }

        public static Item of(Certificate c, boolean favorited, boolean hasSchedule) {
            return new Item(
                    c.getId(), c.getName(), c.getSlug(),
                    c.getSeries().name(), c.getSeries().getLabel(),
                    c.getCategory(),
                    c.getAgency(), favorited, hasSchedule);
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

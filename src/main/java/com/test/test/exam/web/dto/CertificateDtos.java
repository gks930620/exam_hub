package com.test.test.exam.web.dto;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.CertificateDetail;
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
        /**
         * 다음 일정이 <b>시행처에서 확인된 날짜인가.</b> 추정치면 false — 카드가 "추정"을 붙인다.
         * 상세 표에만 표시하던 때는 카드만 보는 사람에게 닿지 않았다(2026-09-21).
         */
        private boolean nextConfirmed;

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
                    lastExamDate,
                    !upcoming || next.confirmed());
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
        /**
         * 이 시험이 필기·실기를 <b>따로</b> 치르는가 — 화면이 구분을 보여줄지 정하는 값.
         *
         * <p>화면이 회차 목록만 보고 정하면 연도 필터에 흔들린다(그 해에 실기가 없으면 사라진다).
         * 판단은 연도로 거르기 전 전체로 해야 해서 서버가 정해 내려준다.
         */
        private boolean splitsByExamType;
        /**
         * 날짜가 아닌 정보 — 응시료·시험과목·검정방법·합격기준. <b>없으면 null</b>.
         *
         * <p>큐넷 시험만 채워진다. 비큐넷은 이 정보를 어디서 얻을지 아직 조사된 바가 없어서
         * 비어 있는 것이 정상이다 — 화면이 빈 칸을 만들지 않도록 통째로 null 을 준다.
         */
        private ExamInfoDto examInfo;
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
                    // 판정은 도메인 한 곳에서 — 출처가 비면 확정으로 보지 않는다(ExamSchedule.isConfirmed)
                    s.isConfirmed());
        }
    }

    /**
     * 시험의 날짜가 아닌 정보.
     *
     * <p>큐넷이 이 정보를 구조화해서 주지 않아 원문을 갈라 담는다. <b>못 가른 칸은 null 이고
     * 원문({@code acquisitionRaw})은 남는다</b> — 화면이 조각이 없으면 원문을 보여주면 된다.
     * 억지로 채운 값은 틀린 값이고, 틀린 응시료는 없는 응시료보다 나쁘다.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExamInfoDto {
        /** 필기 응시료(원). 모르면 null — 0 이 아니다 */
        private Integer feeWritten;
        private Integer feePractical;
        /** 응시료 원문 — 숫자로 못 가른 시험이 있다 */
        private String feeRaw;
        /** 관련학과. <b>응시자격이 아니다</b> — 큐넷 API 에 응시자격은 없다 */
        private String relatedMajor;
        private String subjects;
        private String examMethod;
        private String passStandard;
        /** 취득방법 원문. 조각을 못 가른 시험은 이것만 있다 */
        private String acquisitionRaw;
        /** 언제 받아온 정보인지 — 오래된 값을 최신처럼 보여주지 않는다 */
        private String collectedAt;

        public static ExamInfoDto from(CertificateDetail d) {
            if (d == null || !d.hasAnything()) {
                return null;   // 보여줄 것이 없으면 화면이 칸을 만들지 않게 통째로 비운다
            }
            return new ExamInfoDto(
                    d.getFeeWritten(), d.getFeePractical(), d.getFeeRaw(),
                    d.getRelatedMajor(), d.getSubjects(), d.getExamMethod(),
                    d.getPassStandard(), d.getAcquisitionRaw(),
                    TimeUtil.format(d.getCollectedAt()));
        }
    }
}

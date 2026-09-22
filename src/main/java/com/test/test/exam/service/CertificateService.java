package com.test.test.exam.service;

import com.test.test.common.exception.BusinessRuleException;
import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.CertificateDetailRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import com.test.test.exam.repository.UserFavoriteRepository;
import com.test.test.exam.web.dto.CertificateDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 시험 검색/인기/상세 조회 (설계 05 §2-1, §2-2).
 * 조회는 항상 DB만 바라본다 (외부 API 실시간 호출 없음 — NFR-02/아키텍처 1-1).
 */
@Service
@RequiredArgsConstructor
public class CertificateService {

    private static final String NOT_FOUND = "시험을 찾을 수 없습니다.";
    private static final int SEARCH_LIMIT = 30;
    private static final int POPULAR_LIMIT = 10;
    /** 한 번에 가져갈 수 있는 최대 개수 — 과대 요청(DoS) 방어. */
    private static final int MAX_BROWSE_SIZE = 100;
    /**
     * 상세에 보여줄 회차 상태. 취소 회차는 <b>남긴다</b>(status 로 구분) — 접수해 둔 사람이 알아야 한다.
     * 보류(PENDING_REVIEW)는 매니저가 확인하기 전이라 사용자에게 보내지 않는다.
     */
    private static final List<ScheduleStatus> USER_VISIBLE = List.of(ScheduleStatus.ACTIVE, ScheduleStatus.CANCELED);

    private final CertificateRepository certificateRepository;
    private final ExamScheduleRepository examScheduleRepository;
    private final CertificateDetailRepository certificateDetailRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final DdayService ddayService;

    /** 첫 화면 지표 — 킷의 지표 타일에 들어간다. 상시·예약제는 일정이 없으니 자연히 안 세진다. */
    @Transactional(readOnly = true)
    public CertificateDtos.StatsResponse stats() {
        LocalDateTime now = TimeUtil.now();
        return new CertificateDtos.StatsResponse(
                certificateRepository.countVisible(),
                certificateRepository.countVisibleWithSchedule(),
                examScheduleRepository.countCertificatesWithOpenRegistration(now),
                examScheduleRepository.countCertificatesWithRegistrationOpening(now, now.plusDays(7)),
                certificateRepository.countVisibleRolling());
    }

    /** 시험명 부분 일치 검색. query 2자 미만이면 400. */
    @Transactional(readOnly = true)
    public CertificateDtos.SearchResponse search(String query, Long memberId) {
        if (query == null || query.trim().length() < 2) {
            throw new BusinessRuleException("검색어는 2자 이상 입력하세요.");
        }
        List<Certificate> results = certificateRepository.searchByName(
                query.trim(), PageRequest.of(0, SEARCH_LIMIT));
        return new CertificateDtos.SearchResponse(toItems(results, memberId));
    }

    /**
     * 전체 둘러보기 — 검색어·분류 선택, 페이징.
     * <p>검색({@link #search})과 달리 <b>검색어 없이도</b> 볼 수 있고, <b>일정이 없는 시험도 포함</b>한다.
     * 시험이 존재한다는 사실 자체가 사용자에게 정보이고, 관심 등록해 두면 일정이 붙는 순간 알림이 간다.
     */
    @Transactional(readOnly = true)
    public CertificateDtos.BrowseResponse browse(String query, String category,
                                                 int page, int size, Long memberId) {
        return browse(query, category, null, page, size, memberId);
    }

    /**
     * @param state 무엇만 볼지. {@code null} 이면 전체(인기순) — 예전과 같다.
     *              {@code OPEN}·{@code SOON} 이면 그 상태만, 그리고 <b>급한 것부터</b> 나온다.
     *              정렬을 따로 고르게 하지 않는 이유는 상태가 이미 무엇이 급한지를 담고 있어서다.
     */
    @Transactional(readOnly = true)
    public CertificateDtos.BrowseResponse browse(String query, String category, BrowseState state,
                                                 int page, int size, Long memberId) {
        if (page < 0) {
            throw new BusinessRuleException("page 는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_BROWSE_SIZE) {
            throw new BusinessRuleException("size 는 1~" + MAX_BROWSE_SIZE + " 사이여야 합니다.");
        }

        // 조건 없음을 빈 문자열로 표현한다. (":param IS NULL" 관용구는 Hibernate 가
        //  파라미터 타입을 못 잡아 조건 전체가 어긋난다 — 실제로 분류 필터가 빈 결과를 냈다)
        String q = blankToEmpty(query);
        String cat = blankToEmpty(category);
        LocalDateTime now = TimeUtil.now();

        // 상태를 고르면 정렬도 따라간다. 상태별 질의는 ORDER BY 를 자기가 들고 있어서
        // Pageable 에 Sort 를 얹지 않는다(얹으면 두 정렬이 겹쳐 순서가 어긋난다).
        Page<Certificate> found;
        if (state == BrowseState.OPEN) {
            found = certificateRepository.browseOpen(q, cat, now, PageRequest.of(page, size));
        } else if (state == BrowseState.SOON) {
            found = certificateRepository.browseSoon(q, cat, now,
                    now.plusDays(BrowseState.SOON_DAYS), PageRequest.of(page, size));
        } else {
            found = certificateRepository.browse(q, cat,
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "favoriteCount")
                            .and(Sort.by(Sort.Direction.ASC, "name"))));
        }

        return new CertificateDtos.BrowseResponse(
                toItems(found.getContent(), memberId),
                found.getNumber(), found.getSize(),
                found.getTotalElements(), found.getTotalPages());
    }

    /** 분류 목록 + 각 분류의 종목 수 (필터 칩). */
    @Transactional(readOnly = true)
    public CertificateDtos.CategoryResponse categories() {
        List<CertificateDtos.CategoryItem> items = certificateRepository.countByCategory().stream()
                .map(row -> new CertificateDtos.CategoryItem((String) row[0], ((Number) row[1]).longValue()))
                .collect(Collectors.toList());
        return new CertificateDtos.CategoryResponse(items);
    }

    private String blankToEmpty(String s) {
        return (s == null || s.isBlank()) ? "" : s.trim();
    }

    /** 인기 시험 TOP 10 (관심 등록 수 기준). 폐지·개칭은 뺀다 — 검색과 같은 모집단. */
    @Transactional(readOnly = true)
    public CertificateDtos.SearchResponse popular(Long memberId) {
        List<Certificate> results = certificateRepository
                .findPopularVisible(PageRequest.of(0, POPULAR_LIMIT));
        return new CertificateDtos.SearchResponse(toItems(results, memberId));
    }

    /** 시험 상세 + 연간 회차 일정 + 다음 이벤트. */
    @Transactional(readOnly = true)
    public CertificateDtos.DetailResponse detail(Long id, Integer year, Long memberId) {
        Certificate cert = certificateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
        return buildDetail(cert, year, memberId);
    }

    /** slug 로 상세 (공유 링크). */
    @Transactional(readOnly = true)
    public Certificate getBySlugOrThrow(String slug) {
        return certificateRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public CertificateDtos.DetailResponse buildDetail(Certificate cert, Integer year, Long memberId) {
        // 날짜가 하나도 없는 회차(연도·회차만)는 사용자에게 보여줄 것이 없다 — 카드·매니저와 같은 기준
        List<ExamSchedule> schedules = examScheduleRepository
                .findByCertificateIdAndStatusInOrderByExamStartDateAsc(cert.getId(), USER_VISIBLE)
                .stream().filter(ExamSchedule::hasAnyDate).toList();

        // 연도 필터 (생략 시 전체 노출 — 상세는 연간 아코디언)
        List<ExamSchedule> filtered = year == null
                ? schedules
                : schedules.stream().filter(s -> year.equals(s.getYear())).collect(Collectors.toList());

        // 다음 이벤트는 ACTIVE 회차만으로 계산한다(DdayService 가 걸러 준다)
        NextEvent next = ddayService.computeNextEvent(schedules);

        // 구분(필기/실기)을 보여줄지는 연도로 거르기 전 전체로 본다 —
        // 그 해에 실기가 없다고 구분이 사라지면 화면이 해마다 달라 보인다.
        boolean splitsByExamType = ExamSchedule.splitsByExamType(schedules);

        // 날짜가 아닌 정보(응시료·과목·합격기준). 아직 안 받은 시험은 null 이고 그게 정상이다.
        CertificateDtos.ExamInfoDto examInfo = CertificateDtos.ExamInfoDto.from(
                certificateDetailRepository.findByCertificateId(cert.getId()).orElse(null));
        boolean favorited = memberId != null
                && userFavoriteRepository.existsByMemberIdAndCertificateId(memberId, cert.getId());

        String sourceUrl = filtered.stream()
                .map(ExamSchedule::getSourceUrl)
                .filter(u -> u != null && !u.isBlank())
                .findFirst().orElse(null);
        String collectedAt = filtered.stream()
                .map(ExamSchedule::getCollectedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(TimeUtil::format)
                .orElse(null);

        List<CertificateDtos.ScheduleDto> scheduleDtos = filtered.stream()
                .sorted(Comparator
                        .comparing(ExamSchedule::getYear)
                        .thenComparing(ExamSchedule::getRound)
                        .thenComparing(s -> s.getExamType().name()))
                .map(CertificateDtos.ScheduleDto::of)
                .collect(Collectors.toList());

        return new CertificateDtos.DetailResponse(
                cert.getId(), cert.getName(), cert.getCategory(), cert.getAgency(), sourceUrl, collectedAt,
                favorited, cert.isRollingAdmission(), CertificateDtos.EventDto.of(next),
                splitsByExamType, examInfo, scheduleDtos);
    }

    private List<CertificateDtos.Item> toItems(List<Certificate> certs, Long memberId) {
        final Set<Long> favoriteIds = memberId == null
                ? Collections.emptySet()
                : Set.copyOf(userFavoriteRepository.findCertificateIdsByMemberId(memberId));
        // 카드마다 "앞으로 일정이 있나 / 다 지났나 / 없나"를 계산한다 — 내 시험·상세·매니저와 같은 DdayService.summarize
        final Map<Long, List<ExamSchedule>> byCert = certs.isEmpty()
                ? Map.of()
                : examScheduleRepository.findByCertificateIdInAndStatus(
                        certs.stream().map(Certificate::getId).toList(), ScheduleStatus.ACTIVE)
                .stream().collect(Collectors.groupingBy(s -> s.getCertificate().getId()));
        return certs.stream()
                .map(c -> {
                    DdayService.ScheduleSummary summary =
                            ddayService.summarize(c, byCert.getOrDefault(c.getId(), List.of()));
                    return CertificateDtos.Item.of(
                            c, favoriteIds.contains(c.getId()), summary.hasSchedule(),
                            summary.state().name(), summary.next(), TimeUtil.format(summary.lastExamDate()));
                })
                .collect(Collectors.toList());
    }
}

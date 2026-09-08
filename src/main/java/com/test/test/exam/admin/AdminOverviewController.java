package com.test.test.exam.admin;

import com.test.test.common.exception.BusinessRuleException;
import com.test.test.exam.collect.ScheduleSource;
import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import com.test.test.exam.service.DdayService;
import com.test.test.exam.service.NextEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 매니저용 <b>시험 일정 현황</b> — 무엇을 해야 하는지가 곧바로 보이는 화면의 뒷단.
 *
 * <p><b>"일정 있음/없음"으로는 부족했다.</b> 수기로 넣는 시험은 회차가 지나면 다음 회차를 넣어야
 * 하는데 "일정 있음"으로 분류되어 매니저가 알 길이 없었다(사용자 지적 2026-09-02). 그래서 시험마다
 * <b>행동</b>을 판정한다 — 첫 일정 입력 / 일정 이동 확인 / 다음 회차 입력 / 시행처 확인 / 수집 점검.
 * 행동이 없는 시험은 기다리면 되는 것(대기), 앞으로 일정이 있는 것(정상), 상시(대상 아님)로 나뉜다.
 *
 * <p>판정에 쓰는 사실: 이 시험이 <b>어디서 오나</b>(source — 살아 있는 소스가 그 기관을 맡는가, 사람이 넣는가),
 * <b>일정이 얼마나 신선한가</b>(freshness — 앞으로 남은 게 있나, 다 지났나, 없나),
 * <b>확인이 필요한가</b>(APPROX 추정치·PENDING_REVIEW 보류가 걸려 있나). 다음 이벤트 계산은 사용자 화면과 같은
 * {@link DdayService} 를 쓴다 — 매니저와 사용자가 다른 셈법을 보면 안 된다.
 *
 * <p><b>"자동"의 근거는 살아 있는 소스다</b>(2026-09-03). 행의 출처(provenance)가 scraped 라는 것은 "언젠가 누가
 * 긁었다"일 뿐이라, 정적 시드의 scraped 행을 근거로 삼으면 아무도 안 긁는 시험이 자동으로 보인다. 지금 떠 있는
 * {@link ScheduleSource} 들이 {@link ScheduleSource#coveredAgencies()} 로 밝힌 기관, 또는 큐넷 4자리 종목코드만 자동이다.
 */
@RestController
@RequestMapping("/api/admin/overview")
@RequiredArgsConstructor
public class AdminOverviewController {

    /**
     * 자동 소스인데 이보다 오래 새 회차가 없으면 "회차 끊김"으로 본다 — 폐지·개칭됐거나 수집이 빠진 것.
     *
     * <p>60일로 잡았더니 114종이 걸렸다(2026-09-02 실측). 큐넷 연 1~2회 시험이 올해 회차를 마친
     * 정상 상태를 고장으로 본 것이다. 다음 해 계획은 12월에 나오니 1년을 넘겨야 진짜 이상하다.
     */
    static final int STALE_AUTO_DAYS = 365;

    /** 한 쪽의 상한 — 과대 요청 방어. 전체가 필요하면 쪽을 넘긴다(개수는 bucketCounts 가 준다). */
    static final int MAX_PAGE_SIZE = 100;

    /** 판정에 넣는 회차 상태 — 보류는 "확인할 일"이 되어야 하므로 같이 읽는다. */
    private static final List<ScheduleStatus> JUDGED = List.of(ScheduleStatus.ACTIVE, ScheduleStatus.PENDING_REVIEW);

    private final CertificateRepository certificateRepository;
    private final ExamScheduleRepository examScheduleRepository;
    private final DdayService ddayService;
    private final List<ScheduleSource> sources;

    /**
     * @param bucket   {@code TODO|WAITING|OK|ROLLING} — 비우면 전체
     * @param action   TODO 안에서 행동으로 좁힌다 — {@code FIRST_INPUT|REVIEW_MOVE|NEXT_ROUND|VERIFY|CHECK_SOURCE}
     * @param query    시험명 부분일치
     * @param category 분류
     */
    @GetMapping
    public ResponseEntity<OverviewResponse> overview(
            @RequestParam(required = false) String bucket,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        if (page < 0) {
            throw new BusinessRuleException("page 는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessRuleException("size 는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }

        LocalDate today = TimeUtil.today();
        Set<String> covered = liveCoverage();
        Set<String> coveredCodes = liveExamCodes();

        // 보이는 시험 전부를 한 번에 읽고, 필터는 메모리에서 건다 —
        // 탭·행동의 개수는 검색과 무관하게 늘 같은 값이어야 한다(남은 일의 크기).
        List<Certificate> all = certificateRepository.browse("", "", PageRequest.of(0, 5000)).getContent();
        Map<Long, List<ExamSchedule>> byCert = examScheduleRepository
                .findByCertificateIdInAndStatusIn(
                        all.stream().map(Certificate::getId).toList(), JUDGED)
                .stream()
                .collect(Collectors.groupingBy(s -> s.getCertificate().getId()));

        List<Row> allRows = all.stream()
                .map(c -> judge(c, byCert.getOrDefault(c.getId(), List.of()), today, covered, coveredCodes))
                .toList();

        String q = query == null ? "" : query.trim().toLowerCase();
        String cat = category == null ? "" : category.trim();

        List<Row> rows = allRows.stream()
                .filter(r -> q.isEmpty() || r.certificateName().toLowerCase().contains(q))
                .filter(r -> cat.isEmpty() || cat.equals(r.category()))
                .filter(r -> bucket == null || bucket.isBlank() || bucket.equals(r.bucket()))
                .filter(r -> action == null || action.isBlank() || action.equals(r.action()))
                .sorted(Comparator.comparingInt(Row::sortKey).thenComparing(Row::certificateName))
                .toList();

        int from = Math.min(page * size, rows.size());
        int to = Math.min(from + size, rows.size());

        return ResponseEntity.ok(new OverviewResponse(
                rows.subList(from, to), rows.size(), page,
                count(allRows, Row::bucket),
                count(allRows.stream().filter(r -> r.action() != null).toList(), Row::action),
                count(allRows.stream().filter(r -> r.waitingReason() != null).toList(), Row::waitingReason)));
    }

    /** 지금 떠 있는 소스들이 맡는 기관의 합집합. */
    private Set<String> liveCoverage() {
        Set<String> covered = new LinkedHashSet<>();
        for (ScheduleSource s : sources) {
            covered.addAll(s.coveredAgencies());
        }
        return covered;
    }

    /** 지금 떠 있는 소스들이 <b>이름을 대고</b> 맡는 종목코드의 합집합. 기관보다 정확하다. */
    private Set<String> liveExamCodes() {
        Set<String> codes = new LinkedHashSet<>();
        for (ScheduleSource s : sources) {
            codes.addAll(s.coveredExamCodes());
        }
        return codes;
    }

    private static Map<String, Long> count(List<Row> rows, java.util.function.Function<Row, String> key) {
        return rows.stream().collect(Collectors.groupingBy(key, Collectors.counting()));
    }

    // ── 판정 ──────────────────────────────────────────────────────────────

    /** 이 시험의 일정이 어디서 오나 */
    enum Source {
        AUTO("자동"), CRAWL_PLANNED("크롤링 예정"), MANUAL("수기"), ROLLING("상시");
        final String label;
        Source(String label) { this.label = label; }
    }

    /** 매니저가 지금 해야 하는 일 */
    enum Action {
        FIRST_INPUT("첫 일정 입력", 0),
        /** 수집이 30일 넘게 이동한 회차를 보류했다 — 매니저가 확인해 저장하면 풀린다 */
        REVIEW_MOVE("일정 이동 확인", 1),
        SOURCE_MISMATCH("수집값과 다름", 2),
        NEXT_ROUND("다음 회차 입력", 3),
        VERIFY("시행처 확인", 4),
        CHECK_SOURCE("회차 끊김 확인", 5);
        final String label;
        final int order;
        Action(String label, int order) { this.label = label; this.order = order; }
    }

    /** 할 일은 아니지만 왜 비어 있는지 — 기다리면 되는 이유 */
    enum Waiting {
        ANNOUNCEMENT_PENDING("공고 전 — 나오면 자동"),
        AUTO_NEXT_PENDING("다음 회차 수집 대기 — 자동"),
        CRAWL_PLANNED("크롤링 예정 — 자동");
        final String label;
        Waiting(String label) { this.label = label; }
    }

    private Row judge(Certificate c, List<ExamSchedule> schedules, LocalDate today,
                      Set<String> covered, Set<String> coveredCodes) {
        boolean rolling = c.isRollingAdmission();
        boolean qnet = c.getSourceCode() != null && c.getSourceCode().matches("[0-9]{4}");
        // "자동"의 근거 = 살아 있는 소스가 그 기관을 담당하고, 실제로 그 소스가 넣은 행(API·SCRAPED)이 있다.
        // 기관만 보면 느슨하다 — JLPT 스크래퍼의 JEES 가 같은 기관의 BJT 까지 자동으로 만들고, 상의 스크래퍼가
        // 안 긁는 무역영어가 "공고 전"이 됐다(실측 2026-09-03, 31종). 행만 보면 정적 시드의 scraped 에 속는다.
        boolean hasMachineRows = schedules.stream().anyMatch(s ->
                s.getProvenance() == ScheduleProvenance.API || s.getProvenance() == ScheduleProvenance.SCRAPED);
        // 소스가 종목코드를 대 놓고 아는 경우는 행이 없어도 자동이다 — 시행처가 올해 회차를 안 연 것뿐이고,
        // 열면 그대로 들어온다. 이게 없으면 "수기로 넣으세요"가 떠서 매니저가 헛일을 한다
        // (파생상품투자권유대행인, 2026-09-08).
        boolean namedByLiveSource = c.getSourceCode() != null && coveredCodes.contains(c.getSourceCode());
        boolean fedByMachine = qnet || namedByLiveSource
                || (AgencyMatcher.matches(c.getAgency(), covered) && hasMachineRows);

        Source source;
        if (rolling) {
            source = Source.ROLLING;
        } else if (fedByMachine) {
            source = Source.AUTO;
        } else {
            source = switch (NoScheduleReason.of(c)) {
                case ANNOUNCEMENT_PENDING -> Source.AUTO;
                case CRAWL_PLANNED -> Source.CRAWL_PLANNED;
                case MANUAL -> Source.MANUAL;
            };
        }

        // 보류 회차 중 아직 안 지난 것만 사람의 확인이 필요하다 — 지난 회차의 보류(TOPIK 104·105회)는 아무도 안 본다
        // 아직 안 지난 회차의 충돌만 본다 — 지난 회차의 불일치는 아무도 안 본다
        boolean hasSourceConflict = schedules.stream()
                .filter(s -> s.getSourceConflict() != null && s.hasAnyDate())
                .anyMatch(s -> !s.latestKnownDate().isBefore(today));
        boolean hasPendingReview = schedules.stream().anyMatch(s -> s.getStatus() == ScheduleStatus.PENDING_REVIEW
                && s.hasAnyDate() && !s.latestKnownDate().isBefore(today));
        // 날짜가 하나도 없는 회차(연도·회차만 넣고 잊은 것)는 일정으로 치지 않는다 — 사용자에게 아무것도 못 알려 준다.
        // 보류 회차도 사용자에게 안 보이므로 신선도에는 안 넣는다.
        List<ExamSchedule> dated = schedules.stream()
                .filter(ExamSchedule::isActive)
                .filter(ExamSchedule::hasAnyDate)
                .toList();
        NextEvent next = ddayService.computeNextEvent(dated);
        // 추정치 경고는 아직 안 지난 회차에만 — 지난 추정치는 아무도 안 본다
        boolean needsReview = dated.stream().anyMatch(s -> s.getProvenance() == ScheduleProvenance.APPROX
                && !s.latestKnownDate().isBefore(today));
        // 마지막 회차 = 연도·회차가 가장 뒤인 것. 같은 회차면 늦은 날짜(실기)가 뒤
        ExamSchedule last = dated.stream()
                .max(Comparator.comparing((ExamSchedule s) -> s.getYear() == null ? 0 : s.getYear())
                        .thenComparing(s -> s.getRound() == null ? 0 : s.getRound())
                        .thenComparing(ExamSchedule::latestKnownDate))
                .orElse(null);
        LocalDate lastDate = last == null ? null : last.latestKnownDate();
        String freshness = dated.isEmpty() ? "NONE" : next.isPresent() ? "UPCOMING" : "PAST_ONLY";
        // 마지막 회차가 미래일 수 있다(접수 마감만 있고 시작이 없는 행처럼 D-day 후보가 안 되는 경우).
        // 그때 "-45일 지남" 같은 문구가 나오고 정렬 밴드까지 넘어가므로 0 아래로는 내리지 않는다.
        Integer daysSince = lastDate == null ? null
                : Math.max(0, (int) ChronoUnit.DAYS.between(lastDate, today));

        Action act = null;
        Waiting waiting = null;
        if (source == Source.ROLLING) {
            // 상시는 판정 대상이 아니다 — 추정치가 걸려 있어도 "시행처 확인"을 시키지 않는다(실측: 6종이 새어 들어왔다)
        } else if (hasPendingReview) {
            // 보류는 사용자에게도 안 보이고 알림도 안 간다 — 매니저가 풀어 주기 전엔 영원히 그대로다
            act = Action.REVIEW_MOVE;
        } else if (hasSourceConflict) {
            // 매니저가 넣은 값과 시행처가 말하는 값이 다르다. 수집은 사람 값을 안 덮으므로
            // 여기서 알려 주지 않으면 옛 날짜로 D-day 와 알림이 계속 나간다.
            act = Action.SOURCE_MISMATCH;
        } else if (source == Source.MANUAL && freshness.equals("NONE")) {
            act = Action.FIRST_INPUT;
        } else if (source == Source.MANUAL && freshness.equals("PAST_ONLY")) {
            act = Action.NEXT_ROUND;
        } else if (needsReview && freshness.equals("UPCOMING")) {
            act = Action.VERIFY;
        } else if (source == Source.AUTO && freshness.equals("PAST_ONLY")
                && daysSince != null && daysSince > STALE_AUTO_DAYS) {
            act = Action.CHECK_SOURCE;
        } else if (source == Source.AUTO && freshness.equals("NONE")) {
            waiting = Waiting.ANNOUNCEMENT_PENDING;
        } else if (source == Source.AUTO && freshness.equals("PAST_ONLY")) {
            waiting = Waiting.AUTO_NEXT_PENDING;
        } else if (source == Source.CRAWL_PLANNED && !freshness.equals("UPCOMING")) {
            waiting = Waiting.CRAWL_PLANNED;
        }

        String bucket = source == Source.ROLLING ? "ROLLING"
                : act != null ? "TODO"
                : waiting != null ? "WAITING"
                : "OK";

        // 정렬: 할 일은 행동 순. 다음 회차는 오래 지난 것부터, 시행처 확인은 임박한 것부터(급하다). 대기·정상은 이름순.
        int sortKey = switch (bucket) {
            case "TODO" -> act.order * 100_000 + switch (act) {
                case NEXT_ROUND -> daysSince == null ? 99_999 : Math.max(0, 99_999 - daysSince);
                case VERIFY -> next.isPresent() ? (int) Math.min(99_999, Math.max(0, next.dday())) : 99_999;
                default -> 0;
            };
            case "WAITING" -> 400_000;
            case "OK" -> 500_000;
            default -> 600_000;
        };

        String lastLabel = last == null || freshness.equals("UPCOMING") ? null
                : "%d년 %s".formatted(last.getYear(), last.roundLabel());

        return new Row(
                c.getId(), c.getName(), c.getCategory(), c.getAgency(), schedules.size(),
                source.name(), source.label, freshness, needsReview,
                act == null ? null : act.name(), act == null ? null : act.label,
                waiting == null ? null : waiting.name(), waiting == null ? null : waiting.label,
                bucket,
                lastDate == null ? null : lastDate.toString(), lastLabel, daysSince,
                next.isPresent() ? next.label() : null,
                next.isPresent() ? TimeUtil.format(next.at()) : null,
                next.isPresent() ? (int) next.dday() : null,
                next.isPresent() ? next.badge().name() : null,
                sortKey);
    }

    public record Row(
            Long certificateId,
            String certificateName,
            String category,
            String agency,
            /** 판정에 넣은 회차 수(ACTIVE + PENDING_REVIEW) */
            int scheduleCount,
            /** AUTO | CRAWL_PLANNED | MANUAL | ROLLING */
            String source,
            String sourceLabel,
            /** NONE | PAST_ONLY | UPCOMING */
            String freshness,
            boolean needsReview,
            /** 할 일. 없으면 null — FIRST_INPUT | REVIEW_MOVE | NEXT_ROUND | VERIFY | CHECK_SOURCE */
            String action,
            String actionLabel,
            /** 할 일은 아니고 기다리면 되는 이유. 없으면 null */
            String waitingReason,
            String waitingLabel,
            /** TODO | WAITING | OK | ROLLING */
            String bucket,
            /** 마지막 시험일과 그 회차 — 다음 회차를 넣을 때 "어디까지 넣었더라"의 답 */
            String lastExamDate,
            String lastLabel,
            Integer daysSinceLast,
            /** 앞으로의 대표 이벤트(사용자 화면과 같은 계산) */
            String nextLabel,
            String nextAt,
            Integer nextDday,
            String nextBadge,
            int sortKey
    ) {
    }

    public record OverviewResponse(List<Row> items, int totalElements, int page,
                                   /** TODO/WAITING/OK/ROLLING 별 개수 — 필터와 무관 */
                                   Map<String, Long> bucketCounts,
                                   /** 할 일 안의 행동별 개수 */
                                   Map<String, Long> actionCounts,
                                   /** 대기 안의 이유별 개수 */
                                   Map<String, Long> waitingCounts) {
    }
}

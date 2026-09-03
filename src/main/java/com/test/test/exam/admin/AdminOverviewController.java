package com.test.test.exam.admin;

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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 매니저용 <b>시험 일정 현황</b> — 무엇을 해야 하는지가 곧바로 보이는 화면의 뒷단.
 *
 * <p><b>"일정 있음/없음"으로는 부족했다.</b> 수기로 넣는 시험은 회차가 지나면 다음 회차를 넣어야
 * 하는데 "일정 있음"으로 분류되어 매니저가 알 길이 없었다(사용자 지적 2026-09-02). 그래서 시험마다
 * <b>행동</b>을 판정한다 — 첫 일정 입력 / 다음 회차 입력 / 시행처 확인 / 수집 점검. 행동이 없는
 * 시험은 기다리면 되는 것(대기), 앞으로 일정이 있는 것(정상), 상시(대상 아님)로 나뉜다.
 *
 * <p>판정에 쓰는 사실: 이 시험이 <b>어디서 오나</b>(source — 큐넷 API·스크래퍼가 덮는가, 사람이 넣는가),
 * <b>일정이 얼마나 신선한가</b>(freshness — 앞으로 남은 게 있나, 다 지났나, 없나),
 * <b>확인이 필요한가</b>(APPROX 추정치가 걸려 있나). 다음 이벤트 계산은 사용자 화면과 같은
 * {@link DdayService} 를 쓴다 — 매니저와 사용자가 다른 셈법을 보면 안 된다.
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

    private final CertificateRepository certificateRepository;
    private final ExamScheduleRepository examScheduleRepository;
    private final DdayService ddayService;

    /**
     * @param bucket   {@code TODO|WAITING|OK|ROLLING} — 비우면 전체
     * @param action   TODO 안에서 행동으로 좁힌다 — {@code FIRST_INPUT|NEXT_ROUND|VERIFY|CHECK_SOURCE}
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

        LocalDate today = TimeUtil.today();

        // 보이는 시험 전부를 한 번에 읽고, 필터는 메모리에서 건다 —
        // 탭·행동의 개수는 검색과 무관하게 늘 같은 값이어야 한다(남은 일의 크기).
        List<Certificate> all = certificateRepository.browse("", "", PageRequest.of(0, 5000)).getContent();
        Map<Long, List<ExamSchedule>> byCert = examScheduleRepository
                .findByCertificateIdInAndStatus(
                        all.stream().map(Certificate::getId).toList(), ScheduleStatus.ACTIVE)
                .stream()
                .collect(Collectors.groupingBy(s -> s.getCertificate().getId()));

        List<Row> allRows = all.stream()
                .map(c -> judge(c, byCert.getOrDefault(c.getId(), List.of()), today))
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
        NEXT_ROUND("다음 회차 입력", 1),
        VERIFY("시행처 확인", 2),
        CHECK_SOURCE("회차 끊김 확인", 3);
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

    private Row judge(Certificate c, List<ExamSchedule> schedules, LocalDate today) {
        boolean rolling = c.isRollingAdmission();
        boolean qnet = c.getSourceCode() != null && c.getSourceCode().matches("[0-9]{4}");
        boolean fedByMachine = schedules.stream()
                .anyMatch(s -> s.getProvenance() == ScheduleProvenance.API || s.getProvenance() == ScheduleProvenance.SCRAPED);

        Source source;
        if (rolling) {
            source = Source.ROLLING;
        } else if (qnet || fedByMachine) {
            source = Source.AUTO;
        } else {
            source = switch (NoScheduleReason.of(c)) {
                case ANNOUNCEMENT_PENDING -> Source.AUTO;
                case CRAWL_PLANNED -> Source.CRAWL_PLANNED;
                case MANUAL -> Source.MANUAL;
            };
        }

        // 날짜가 하나도 없는 회차(연도·회차만 넣고 잊은 것)는 일정으로 치지 않는다 — 사용자에게 아무것도 못 알려 준다
        List<ExamSchedule> dated = schedules.stream().filter(ExamSchedule::hasAnyDate).toList();
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
        Integer daysSince = lastDate == null ? null : (int) ChronoUnit.DAYS.between(lastDate, today);

        Action act = null;
        Waiting waiting = null;
        if (source == Source.ROLLING) {
            // 상시는 판정 대상이 아니다 — 추정치가 걸려 있어도 "시행처 확인"을 시키지 않는다(실측: 6종이 새어 들어왔다)
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
                : "%d년 %d회 %s".formatted(last.getYear(), last.getRound(),
                        last.getExamType() == null ? "" : last.getExamType().getLabel());

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
            int scheduleCount,
            /** AUTO | CRAWL_PLANNED | MANUAL | ROLLING */
            String source,
            String sourceLabel,
            /** NONE | PAST_ONLY | UPCOMING */
            String freshness,
            boolean needsReview,
            /** 할 일. 없으면 null — FIRST_INPUT | NEXT_ROUND | VERIFY | CHECK_SOURCE */
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

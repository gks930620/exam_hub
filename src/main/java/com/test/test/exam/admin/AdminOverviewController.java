package com.test.test.exam.admin;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 매니저용 <b>시험 일정 현황</b> — 여러 시험의 상태를 한 화면에서 본다.
 *
 * <p><b>왜 필요한가</b>: 수기 입력 화면은 "시험 하나를 검색해 고른 뒤"에야 그 시험의 일정을
 * 보여준다. 그래서 매니저는 <b>무엇이 비어 있는지 알 방법이 없었다</b> — 480종을 하나씩
 * 검색해 볼 수는 없으니, 사실상 기억나는 시험만 채우게 된다.
 *
 * <p>여기서는 반대로 <b>급한 것부터</b> 보여준다: 일정이 아예 없는 시험 → 있는 일정이 전부
 * 지나간 시험 → 접수 중 → 예정. 매니저는 위에서부터 처리하면 된다.
 */
@RestController
@RequestMapping("/api/admin/overview")
@RequiredArgsConstructor
public class AdminOverviewController {

    private final CertificateRepository certificateRepository;
    private final ExamScheduleRepository examScheduleRepository;

    /**
     * 시험별 일정 현황.
     *
     * @param status   비우면 전체. {@code NONE|PAST|OPEN|UPCOMING}
     * @param query    시험명 부분일치
     * @param category 분류
     */
    @GetMapping
    public ResponseEntity<OverviewResponse> overview(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        LocalDate today = TimeUtil.today();

        // 필터에 걸린 시험을 먼저 좁히고(이름·분류), 그 시험들의 일정만 한 번에 가져온다.
        // 시험마다 일정을 조회하면 480번 쿼리가 나간다.
        List<Certificate> candidates = certificateRepository.browse(
                query == null ? "" : query.trim(),
                category == null ? "" : category.trim(),
                PageRequest.of(0, 2000)).getContent();

        Map<Long, List<ExamSchedule>> byCert = examScheduleRepository
                .findByCertificateIdInAndStatus(
                        candidates.stream().map(Certificate::getId).toList(), ScheduleStatus.ACTIVE)
                .stream()
                .collect(Collectors.groupingBy(s -> s.getCertificate().getId()));

        List<Row> rows = candidates.stream()
                .map(c -> Row.of(c, byCert.getOrDefault(c.getId(), List.of()), today))
                .filter(r -> status == null || status.isBlank() || r.status().equals(status))
                // 급한 것 위로. 같은 상태면 이름순이라 매번 같은 순서로 보인다.
                .sorted(Comparator.comparingInt((Row r) -> Status.valueOf(r.status()).urgency)
                        .thenComparing(Row::certificateName))
                .toList();

        int from = Math.min(page * size, rows.size());
        int to = Math.min(from + size, rows.size());

        return ResponseEntity.ok(new OverviewResponse(
                rows.subList(from, to),
                rows.size(),
                page,
                summarize(candidates, byCert, today)));
    }

    /** 상태별 개수 — 매니저가 "얼마나 남았나"를 먼저 본다. 필터를 걸어도 이 숫자는 안 변한다. */
    private Map<String, Long> summarize(List<Certificate> all,
                                        Map<Long, List<ExamSchedule>> byCert,
                                        LocalDate today) {
        return all.stream()
                .map(c -> Row.of(c, byCert.getOrDefault(c.getId(), List.of()), today).status())
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
    }

    /**
     * 매니저가 손대야 하는 순서. {@code urgency} 가 작을수록 위로 온다.
     *
     * <p>{@link #PAST} 를 {@link #NONE} 만큼 급하게 보는 이유: 지난 일정만 남은 시험은
     * 화면에 <b>끝난 날짜가 그대로 걸려 있다</b>. 비어 있는 것보다 오히려 나쁘다.
     */
    private enum Status {
        NONE(0),      // 일정이 하나도 없다
        PAST(1),      // 있는 일정이 전부 지나갔다
        OPEN(2),      // 지금 접수 중
        UPCOMING(3);  // 앞으로 있을 일정이 있다 — 할 일 없음

        final int urgency;

        Status(int urgency) {
            this.urgency = urgency;
        }
    }

    public record Row(
            Long certificateId,
            String certificateName,
            String category,
            String agency,
            int scheduleCount,
            String status,
            /** 다음(또는 마지막) 일정 요약 — 연도·회차·구분 */
            String nextLabel,
            String nextRegStartAt,
            String nextRegEndAt,
            String nextExamDate,
            /** 접수 마감까지 남은 날. 접수 중이 아니면 null */
            Integer regDDay
    ) {
        static Row of(Certificate c, List<ExamSchedule> schedules, LocalDate today) {
            if (schedules.isEmpty()) {
                return new Row(c.getId(), c.getName(), c.getCategory(), c.getAgency(),
                        0, Status.NONE.name(), null, null, null, null, null);
            }

            // 앞으로 남은 것 중 가장 이른 것. 없으면 가장 최근에 지난 것을 보여준다
            // (매니저가 "어디까지 넣었더라"를 알아야 다음 회차를 넣을 수 있다).
            ExamSchedule next = schedules.stream()
                    .filter(s -> s.getExamStartDate() != null && !s.getExamStartDate().isBefore(today))
                    .min(Comparator.comparing(ExamSchedule::getExamStartDate))
                    .orElseGet(() -> schedules.stream()
                            .max(Comparator.comparing(ExamSchedule::getExamStartDate,
                                    Comparator.nullsFirst(Comparator.naturalOrder())))
                            .orElse(schedules.get(0)));

            boolean hasFuture = next.getExamStartDate() != null
                    && !next.getExamStartDate().isBefore(today);
            boolean regOpen = next.getRegStartAt() != null && next.getRegEndAt() != null
                    && !today.isBefore(next.getRegStartAt().toLocalDate())
                    && !today.isAfter(next.getRegEndAt().toLocalDate());

            Status status = !hasFuture ? Status.PAST : regOpen ? Status.OPEN : Status.UPCOMING;
            Integer dDay = regOpen
                    ? (int) java.time.temporal.ChronoUnit.DAYS.between(today, next.getRegEndAt().toLocalDate())
                    : null;

            return new Row(
                    c.getId(), c.getName(), c.getCategory(), c.getAgency(),
                    schedules.size(), status.name(),
                    "%d년 %d회 %s".formatted(next.getYear(), next.getRound(),
                            next.getExamType() == null ? "" : next.getExamType().getLabel()),
                    TimeUtil.format(next.getRegStartAt()),
                    TimeUtil.format(next.getRegEndAt()),
                    TimeUtil.format(next.getExamStartDate()),
                    dDay);
        }
    }

    public record OverviewResponse(List<Row> items, int totalElements, int page,
                                   Map<String, Long> counts) {
    }
}

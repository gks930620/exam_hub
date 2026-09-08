package com.test.test.exam.service;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * D-day / 다음 이벤트 판정 (FR-10, FR-11). 순수 계산 — 외부 의존 없음.
 * 시험 찾기 카드, 내 시험 카드, 상세 히어로, 매니저 현황이 모두 이 로직을 공유한다 —
 * 화면마다 셈법이 다르면 어느 한쪽은 거짓말이 된다.
 */
@Service
public class DdayService {

    /**
     * 자격증의 회차 일정들로부터 "진행 중 or 다음 대표 이벤트"를 고른다.
     * 규칙:
     *  1) 접수 중(now ∈ [regStart, regEnd])인 회차가 있으면 → 그 중 마감이 가장 임박한 것 (접수 중)
     *  2) 없으면 시험 진행 중(today ∈ [examStart, examEnd])인 회차 → 종료가 가장 가까운 것 (D-0)
     *  3) 없으면 → 아직 오지 않은 접수 시작 / 시험일 중 가장 가까운 시각의 이벤트
     *  4) 아무 것도 없으면 NONE
     *
     * <p>접수 중이 진행 중보다 앞인 이유: 실기처럼 몇 주짜리 시험 기간에 다음 회차 접수가 열리면
     * 그 접수를 놓치는 것이 더 큰 손해다 — 이 서비스의 약속은 "접수 마감을 놓치지 않게"다.
     */
    public NextEvent computeNextEvent(List<ExamSchedule> schedules) {
        LocalDateTime now = TimeUtil.now();
        LocalDate today = now.toLocalDate();

        List<NextEvent> candidates = new ArrayList<>();

        for (ExamSchedule s : schedules) {
            if (!s.isActive()) {
                continue;
            }
            String typeLabel = s.roundLabel();

            LocalDateTime regStart = s.getRegStartAt();
            LocalDateTime regEnd = s.getRegEndAt();

            if (regStart != null) {
                if (regEnd != null && !now.isBefore(regStart) && !now.isAfter(regEnd)) {
                    // 접수 중 → 마감까지
                    long dday = ChronoUnit.DAYS.between(today, regEnd.toLocalDate());
                    candidates.add(new NextEvent(CardBadge.REG_OPEN, "REG_CLOSING",
                            typeLabel + " 접수 마감", regEnd, dday, s.getId()));
                    continue; // 이 회차는 접수 중 이벤트로 대표 (다음 시험일은 굳이 후보에 안 넣음)
                } else if (now.isBefore(regStart)) {
                    // 마감을 아직 몰라도 시작일이 앞에 있으면 접수 예정이다 — 알림(REG_OPEN_EVE)은 이미 그렇게 간다
                    long dday = ChronoUnit.DAYS.between(today, regStart.toLocalDate());
                    candidates.add(new NextEvent(CardBadge.REG_UPCOMING, "REG_OPEN",
                            typeLabel + " 접수 시작", regStart, dday, s.getId()));
                }
            }

            // 시험 종료일까지가 시험이다 — 실기는 몇 주에 걸치고, 종료일 전에 "지난 시험"이 되면 안 된다
            // (ExamSchedule.latestKnownDate 와 같은 기준: 종료일이 없으면 시작일)
            LocalDate examStart = s.getExamStartDate();
            LocalDate examEnd = s.getExamEndDate() != null ? s.getExamEndDate() : examStart;
            if (examStart != null) {
                if (today.isBefore(examStart)) {
                    long dday = ChronoUnit.DAYS.between(today, examStart);
                    candidates.add(new NextEvent(CardBadge.EXAM_UPCOMING, "EXAM",
                            typeLabel + " 시험", examStart.atStartOfDay(), dday, s.getId()));
                } else if (!today.isAfter(examEnd)) {
                    candidates.add(new NextEvent(CardBadge.EXAM_ONGOING, "EXAM_ONGOING",
                            typeLabel + " 시험 진행 중", examEnd.atStartOfDay(), 0, s.getId()));
                }
            }
        }

        if (candidates.isEmpty()) {
            return NextEvent.none();
        }

        // 접수 중 → 진행 중 → 그 외 순. 같은 급 안에서는 시각(at)이 가장 가까운 것
        for (CardBadge first : List.of(CardBadge.REG_OPEN, CardBadge.EXAM_ONGOING)) {
            NextEvent picked = candidates.stream()
                    .filter(e -> e.badge() == first)
                    .min(Comparator.comparing(NextEvent::at))
                    .orElse(null);
            if (picked != null) {
                return picked;
            }
        }
        return candidates.stream()
                .min(Comparator.comparing(NextEvent::at))
                .orElse(NextEvent.none());
    }

    /**
     * 카드가 보여줄 일정 상태 한 묶음 — 시험 찾기 카드·내 시험 카드·매니저 현황이 같은 판정을 쓴다.
     *
     * <p>날짜가 하나도 없는 회차(연도·회차만)는 일정으로 치지 않고, ACTIVE 가 아닌 회차도 뺀다.
     * 상시·예약제는 회차와 무관하게 ROLLING 이다.
     */
    public ScheduleSummary summarize(Certificate cert, List<ExamSchedule> schedules) {
        List<ExamSchedule> dated = schedules.stream()
                .filter(ExamSchedule::isActive)
                .filter(ExamSchedule::hasAnyDate)
                .toList();
        NextEvent next = dated.isEmpty() ? NextEvent.none() : computeNextEvent(dated);
        ScheduleState state = cert.isRollingAdmission() ? ScheduleState.ROLLING
                : dated.isEmpty() ? ScheduleState.NONE
                : next.isPresent() ? ScheduleState.UPCOMING
                : ScheduleState.PAST_ONLY;
        LocalDate lastExamDate = dated.stream()
                .map(ExamSchedule::getExamStartDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new ScheduleSummary(state, next, !dated.isEmpty(), lastExamDate);
    }

    /** 카드 상태 — 프런트 계약 문자열과 이름이 같다. */
    public enum ScheduleState {
        /** 상시·예약제 — 일정이라는 것이 없다 */
        ROLLING,
        /** 앞으로의 일정이 있다 */
        UPCOMING,
        /** 일정은 있는데 전부 지났다 = 다음 회차 미정 */
        PAST_ONLY,
        /** 일정 없음 */
        NONE
    }

    /**
     * @param state        카드 상태
     * @param next         대표 이벤트 (없으면 {@link NextEvent#none()})
     * @param hasSchedule  날짜 있는 살아 있는 회차가 하나라도 있는가
     * @param lastExamDate 마지막 시험 시작일 — PAST_ONLY 일 때 "다음 회차 미정(마지막 시험일)" 안내용
     */
    public record ScheduleSummary(ScheduleState state, NextEvent next, boolean hasSchedule, LocalDate lastExamDate) {
    }
}

package com.test.test.exam.service;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ScheduleStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * D-day / 다음 이벤트 판정 (FR-10, FR-11). 순수 계산 — 외부 의존 없음.
 * 홈 카드, 상세 히어로, pSEO 상단 요약이 모두 이 로직을 공유한다.
 */
@Service
public class DdayService {

    /**
     * 자격증의 회차 일정들로부터 "진행 중 or 다음 대표 이벤트"를 고른다.
     * 규칙:
     *  1) 접수중(now ∈ [regStart, regEnd])인 회차가 있으면 → 그 중 마감이 가장 임박한 것 (접수중)
     *  2) 없으면 → 아직 오지 않은 접수 시작 / 시험일 중 가장 가까운 시각의 이벤트
     *  3) 아무 것도 없으면 NONE
     */
    public NextEvent computeNextEvent(List<ExamSchedule> schedules) {
        LocalDateTime now = TimeUtil.now();
        LocalDate today = now.toLocalDate();

        List<NextEvent> candidates = new ArrayList<>();

        for (ExamSchedule s : schedules) {
            if (s.getStatus() != ScheduleStatus.ACTIVE) {
                continue;
            }
            String typeLabel = s.getRound() + "회 " + s.getExamType().getLabel();

            LocalDateTime regStart = s.getRegStartAt();
            LocalDateTime regEnd = s.getRegEndAt();

            if (regStart != null && regEnd != null) {
                if (!now.isBefore(regStart) && !now.isAfter(regEnd)) {
                    // 접수중 → 마감까지
                    long dday = ChronoUnit.DAYS.between(today, regEnd.toLocalDate());
                    candidates.add(new NextEvent(CardBadge.REG_OPEN, "REG_CLOSING",
                            typeLabel + " 접수 마감", regEnd, dday, s.getId()));
                    continue; // 이 회차는 접수중 이벤트로 대표 (다음 시험일은 굳이 후보에 안 넣음)
                } else if (now.isBefore(regStart)) {
                    long dday = ChronoUnit.DAYS.between(today, regStart.toLocalDate());
                    candidates.add(new NextEvent(CardBadge.REG_UPCOMING, "REG_OPEN",
                            typeLabel + " 접수 시작", regStart, dday, s.getId()));
                }
            }

            LocalDate examDate = s.getExamStartDate();
            if (examDate != null && !examDate.isBefore(today)) {
                long dday = ChronoUnit.DAYS.between(today, examDate);
                candidates.add(new NextEvent(CardBadge.EXAM_UPCOMING, "EXAM",
                        typeLabel + " 시험", examDate.atStartOfDay(), dday, s.getId()));
            }
        }

        if (candidates.isEmpty()) {
            return NextEvent.none();
        }

        // 접수중이 있으면 그 중 마감 임박(at 최소) 우선, 없으면 시각(at) 가장 가까운 이벤트
        boolean hasOpen = candidates.stream().anyMatch(e -> e.badge() == CardBadge.REG_OPEN);
        if (hasOpen) {
            return candidates.stream()
                    .filter(e -> e.badge() == CardBadge.REG_OPEN)
                    .min(Comparator.comparing(NextEvent::at))
                    .orElse(NextEvent.none());
        }
        return candidates.stream()
                .min(Comparator.comparing(NextEvent::at))
                .orElse(NextEvent.none());
    }
}

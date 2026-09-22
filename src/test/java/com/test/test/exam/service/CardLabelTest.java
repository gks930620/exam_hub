package com.test.test.exam.service;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.ScheduleStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>카드에 뜨는 한 줄이 말이 되는가.</b>
 *
 * <p>이 문구는 사용자가 목록에서 가장 먼저 읽는 것이고, 같은 계산이 내 시험·상세·매니저 화면에도
 * 쓰인다. 여기가 어긋나면 화면마다 다른 말을 하게 된다.
 *
 * <p>두 가지를 잡는다.
 * <ul>
 *   <li><b>앞 공백</b> — 회차도 구분도 없는 시험(TOEIC Bridge·토익스피킹 등)은 앞에 붙일 말이 없다.
 *       그대로 이어 붙이면 카드에 {@code " 접수 마감"} 이 뜬다(2026-09-22 실측).</li>
 *   <li><b>없는 구분</b> — 실기를 치르지 않는 시험에 "필기"를 붙이면 틀린 말이다.
 *       전수 실측으로 206종이 그랬다.</li>
 * </ul>
 *
 * <p>순수 계산이라 컨벤션 §6 의 예외로 둔다.
 */
class CardLabelTest {

    private final DdayService dday = new DdayService();

    private ExamSchedule sitting(Integer round, ExamType type, int daysUntilRegEnd) {
        LocalDate exam = TimeUtil.today().plusDays(daysUntilRegEnd + 20);
        return ExamSchedule.builder()
                .year(exam.getYear()).round(round).examType(type)
                .regStartAt(TimeUtil.now().minusDays(3))
                .regEndAt(TimeUtil.now().plusDays(daysUntilRegEnd))
                .examStartDate(exam).examEndDate(exam)
                .provenance(ScheduleProvenance.API).status(ScheduleStatus.ACTIVE)
                .build();
    }

    /** TOEIC Bridge 처럼 회차를 안 매기는 시험 — 회차 자리에 날짜가 들어 있다. */
    @Test
    @DisplayName("붙일 말이 없는 시험의 문구가 공백으로 시작하지 않는다")
    void label_never_starts_with_a_space() {
        List<ExamSchedule> rounds = List.of(sitting(20261018, ExamType.WRITTEN, 5));

        NextEvent e = dday.computeNextEvent(rounds);

        assertThat(e.label()).isEqualTo("접수 마감");
    }

    @Test
    @DisplayName("한 종류만 치르는 시험에는 구분을 안 붙인다 — 회차는 남는다")
    void single_type_keeps_the_round_only() {
        List<ExamSchedule> rounds = List.of(sitting(37, ExamType.WRITTEN, 5));

        assertThat(dday.computeNextEvent(rounds).label()).isEqualTo("37회 접수 마감");
    }

    @Test
    @DisplayName("필기·실기를 둘 다 치르면 구분해 말한다 — 접수일이 서로 다르다")
    void split_exam_says_which_one() {
        // 실기 접수가 먼저 마감되므로 실기가 대표 이벤트다
        List<ExamSchedule> rounds = List.of(
                sitting(3, ExamType.PRACTICAL, 5),
                sitting(3, ExamType.WRITTEN, 40));

        assertThat(dday.computeNextEvent(rounds).label()).isEqualTo("3회 실기 접수 마감");
    }

    /** 구분이 하나뿐인 시험이라도 회차가 진짜면 회차는 보여 준다. */
    @Test
    @DisplayName("어떤 경우에도 문구가 공백으로 시작하거나 끝나지 않는다")
    void labels_are_trimmed_in_every_shape() {
        for (Integer round : new Integer[]{null, 0, 3, 202609, 20261018}) {
            for (ExamType type : new ExamType[]{null, ExamType.WRITTEN, ExamType.PRACTICAL}) {
                String label = dday.computeNextEvent(List.of(sitting(round, type, 5))).label();
                assertThat(label)
                        .as("round=%s type=%s 의 문구", round, type)
                        .isEqualTo(label.trim())
                        .isNotBlank();
            }
        }
    }
}

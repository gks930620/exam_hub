package com.test.test.exam.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>우리가 만든 내부 키가 화면에 회차로 새어 나가면 안 된다.</b>
 *
 * <p>회차를 안 매기는 시험(토익스피킹·HSK·전산세무회계)은 같은 회차를 두 번 넣지 않으려고
 * 날짜를 회차 자리에 넣는다. 그 숫자가 그대로 보이면 사용자는 우리가 뭔가 잘못 읽었다고 생각한다.
 *
 * <p>기준을 백만으로 잡았다가 <b>시드가 쓰는 연·월(202609)이 그 밑</b>이라
 * "2026년 202609회"가 화면에 떴다(2026-09-08 실측). 실제 회차는 네 자리를 안 넘는다.
 */
class ExamScheduleRoundLabelTest {

    private static ExamSchedule withRound(int round) {
        return ExamSchedule.builder()
                .year(2026).round(round).examType(ExamType.WRITTEN)
                .examStartDate(LocalDate.of(2026, 9, 12))
                .build();
    }

    @Test
    @DisplayName("시행처가 매긴 회차는 그대로 보여준다")
    void real_rounds_are_shown() {
        assertTrue(withRound(576).hasPublishedRound());
        assertEquals("576회 필기", withRound(576).roundLabel());
        assertEquals("1회 필기", withRound(1).roundLabel());
    }

    /** DIAT 가 2612회까지 간다 — 네 자리는 진짜 회차다. */
    @Test
    @DisplayName("네 자리 회차도 진짜 회차다")
    void four_digit_rounds_are_real() {
        assertTrue(withRound(2612).hasPublishedRound());
        assertEquals("2612회 필기", withRound(2612).roundLabel());
    }

    @Test
    @DisplayName("날짜를 키로 쓴 회차는 감춘다 — YYYYMMDD 도 YYYYMM 도")
    void synthetic_keys_are_hidden() {
        for (int key : new int[]{20261011, 202609, 20260912}) {
            assertFalse(withRound(key).hasPublishedRound(), key + " 를 진짜 회차로 봤다");
            assertEquals("필기", withRound(key).roundLabel(), key + " 가 화면에 새어 나간다");
        }
    }

    /**
     * 큐넷이 실기 회차를 <b>0</b> 으로 주는 행이 92건 있었다(2026-09-09 실측).
     * 그대로 두면 화면에 "2026년 0회 실기"로 뜬다.
     */
    @Test
    @DisplayName("0 이하는 회차가 아니다")
    void zero_or_negative_is_not_a_round() {
        for (int bad : new int[]{0, -3}) {
            assertFalse(withRound(bad).hasPublishedRound(), bad + " 를 진짜 회차로 봤다");
            assertEquals("필기", withRound(bad).roundLabel(), bad + " 가 화면에 새어 나간다");
        }
    }

    @Test
    @DisplayName("회차가 없어도 터지지 않는다")
    void missing_round_is_safe() {
        ExamSchedule s = ExamSchedule.builder().year(2026).examType(ExamType.WRITTEN).build();

        assertFalse(s.hasPublishedRound());
        assertEquals("필기", s.roundLabel());
    }

    private static ExamSchedule withType(int round, ExamType type) {
        return ExamSchedule.builder()
                .year(2026).round(round).examType(type)
                .examStartDate(LocalDate.of(2026, 9, 12))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // 구분(필기/실기)을 언제 보여주나
    //
    // 2026-09-22 전수 실측: 공개 시험 841종 중 206종이 실기가 하나도 없는데 "필기"를 달고 있었다.
    // 어학 47종·금융 19종·보건의료 17종·국가전문자격 99종 등인데, 이 시험들에는 필기/실기 구분이
    // 아예 없다. 토익스피킹에 "필기 접수 마감"이라고 쓰면 그냥 틀린 말이다.
    // 반대로 국가기술자격은 거의 전부가 둘 다 갖는다(건설 98종 중 95종 등) — 거기서는 필기 접수와
    // 실기 접수가 완전히 다른 날이라 구분이 빠지면 안 된다.
    //
    // 그래서 카테고리 목록을 손으로 들고 있는 대신 데이터가 정하게 한다:
    // 그 시험이 실제로 두 종류를 갖고 있을 때만 구분을 보여준다. 회차 번호를 다루는 방식과 같다.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("필기·실기를 둘 다 치르는 시험이면 구분한다")
    void both_types_means_the_split_is_real() {
        List<ExamSchedule> rounds = List.of(
                withType(3, ExamType.WRITTEN), withType(3, ExamType.PRACTICAL));

        assertTrue(ExamSchedule.splitsByExamType(rounds));
        assertEquals("3회 필기", rounds.get(0).roundLabel(true));
        assertEquals("3회 실기", rounds.get(1).roundLabel(true));
    }

    @Test
    @DisplayName("한 종류만 치르는 시험에는 구분을 붙이지 않는다 — 없는 구분을 지어내는 것이다")
    void single_type_needs_no_label() {
        List<ExamSchedule> rounds = List.of(
                withType(3, ExamType.WRITTEN), withType(4, ExamType.WRITTEN));

        assertFalse(ExamSchedule.splitsByExamType(rounds));
        assertEquals("3회", rounds.get(0).roundLabel(false));
    }

    /** 회차도 안 매기고 구분도 없는 시험(토플 등) — 남길 말이 없으면 빈 문자열이다. */
    @Test
    @DisplayName("회차도 구분도 없으면 빈 문자열이다 — 앞에 공백을 남기지 않는다")
    void nothing_to_say_is_empty() {
        assertEquals("", withType(20261011, ExamType.WRITTEN).roundLabel(false));
    }

    /** 구분이 비어 있는 행이 섞여 있다고 "두 종류"로 보면 안 된다. */
    @Test
    @DisplayName("구분이 비어 있는 행은 종류로 세지 않는다")
    void null_type_is_not_a_kind() {
        assertFalse(ExamSchedule.splitsByExamType(List.of(
                withType(3, ExamType.WRITTEN), withType(4, null))));
    }

    @Test
    @DisplayName("회차가 없으면 목록도 비었다고 본다")
    void empty_list_is_not_split() {
        assertFalse(ExamSchedule.splitsByExamType(List.of()));
        assertFalse(ExamSchedule.splitsByExamType(null));
    }

    /** 취소된 실기 회차도 "이 시험은 실기가 있다"는 사실이다 — 상태로 거르지 않는다. */
    @Test
    @DisplayName("지난 회차·취소 회차도 종류를 세는 데는 쓴다")
    void past_and_canceled_rounds_still_count() {
        ExamSchedule canceled = withType(2, ExamType.PRACTICAL);
        canceled.changeStatus(ScheduleStatus.CANCELED);

        assertTrue(ExamSchedule.splitsByExamType(List.of(withType(3, ExamType.WRITTEN), canceled)),
                "실기가 한 번이라도 있었으면 그 시험은 실기가 있는 시험이다");
    }
}

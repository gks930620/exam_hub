package com.test.test.exam.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

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
}

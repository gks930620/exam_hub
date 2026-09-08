package com.test.test.exam.collect;

import com.test.test.exam.domain.ExamType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 금융감독원 공인회계사시험 일정 파싱.
 *
 * <p>시험 전용 사이트({@code cpa.fss.or.kr})가 <b>그 회차 하나</b>를 첫 화면에 크게 싣는다.
 * <pre>
 * 61회 공인회계사 시험 일정 안내
 *   제1차 시험  시험일 2026년 03월 02일 (월)  응시원서접수 2026년 01월 08일 (목) ~ 01월 20일 (화) 18:00
 *   제2차 시험  시험일 2026년 06월 27일 (토) ~ 06월 28일 (일)  …
 * </pre>
 *
 * <p>까다로운 점 셋.
 * <ul>
 *   <li>접수 <b>끝 날짜에 연도가 없다</b>({@code ~ 01월 20일}). 시작 연도를 붙이되 해를 넘길 수 있다.</li>
 *   <li>시험이 <b>이틀에 걸친다</b>(2차: 6/27~6/28). 종료일을 따로 담아야 "지난 시험"으로 일찍 넘어가지 않는다.</li>
 *   <li>1차·2차를 우리 구분(필기·실기)에 맞춰야 <b>같은 회차 두 줄</b>이 겹치지 않는다.</li>
 * </ul>
 */
class FssCpaParserTest {

    private static List<CollectedSchedule> parsed() throws IOException {
        String html = new String(
                new ClassPathResource("fixtures/fss_cpa_schedule.html").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        return new FssCpaScheduleSource().parse(html);
    }

    private static CollectedSchedule of(List<CollectedSchedule> list, ExamType type) {
        return list.stream().filter(s -> s.examType() == type).findFirst().orElse(null);
    }

    @Test
    @DisplayName("1차 시험을 필기로 읽는다")
    void reads_the_first_stage() throws IOException {
        List<CollectedSchedule> out = parsed();

        assertFalse(out.isEmpty(), "한 건도 못 뽑았다");
        CollectedSchedule s = of(out, ExamType.WRITTEN);
        assertNotNull(s, "1차 시험이 없다");
        assertEquals("M0287", s.sourceCode());
        assertEquals("공인회계사", s.certificateName());
        assertEquals(2026, s.year());
        assertEquals(61, s.round(), "회차를 제목에서 못 읽었다");
        assertEquals("2026-03-02", s.examStartDate().toString());
        assertEquals("2026-01-08T09:00", s.regStartAt().toString());
        assertEquals("2026-01-20T18:00", s.regEndAt().toString(), "접수 마감의 연도를 못 채웠다");
        assertEquals("2026-03-31", s.resultDate().toString());
    }

    /** 2차는 이틀에 걸친다 — 종료일을 안 담으면 첫날이 지나자마자 "지난 시험"이 된다. */
    @Test
    @DisplayName("2차 시험은 실기로, 이틀에 걸친 기간까지 읽는다")
    void reads_the_second_stage_spanning_two_days() throws IOException {
        CollectedSchedule s = of(parsed(), ExamType.PRACTICAL);

        assertNotNull(s, "2차 시험이 없다");
        assertEquals(61, s.round());
        assertEquals("2026-06-27", s.examStartDate().toString());
        assertEquals("2026-06-28", s.examEndDate().toString(), "이틀째를 안 담았다");
        assertEquals("2026-05-07T09:00", s.regStartAt().toString());
        assertEquals("2026-05-19T18:00", s.regEndAt().toString());
        assertEquals("2026-09-01", s.resultDate().toString());
    }

    @Test
    @DisplayName("1차·2차 두 줄만 만든다")
    void makes_exactly_two_rows() throws IOException {
        assertEquals(2, parsed().size(), "회차가 두 줄(1차·2차)이 아니다");
    }

    @Test
    @DisplayName("접수가 시험보다 늦지 않다")
    void registration_precedes_exam() throws IOException {
        parsed().forEach(s -> assertFalse(s.examStartDate().isBefore(s.regStartAt().toLocalDate()),
                s.examType() + " 시험일이 접수 시작보다 앞이다"));
    }

    @Test
    @DisplayName("빈 화면이면 예외 없이 0건")
    void empty_page_yields_nothing() {
        assertTrue(new FssCpaScheduleSource().parse("<html>점검 중</html>").isEmpty());
        assertTrue(new FssCpaScheduleSource().parse("").isEmpty());
    }
}

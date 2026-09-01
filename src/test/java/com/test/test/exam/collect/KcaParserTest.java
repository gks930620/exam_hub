package com.test.test.exam.collect;

import com.test.test.exam.domain.ExamType;
import org.junit.jupiter.api.BeforeAll;
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
 * KCA 국가기술자격검정 일정표 파싱.
 *
 * <p>이 표의 핵심은 <b>rowspan</b> 이다 — 한 회차의 필기 일정을 여러 분야가 공유하고 실기만
 * 갈라진다. 이어받기를 못 하면 <b>엉뚱한 회차의 접수일</b>이 붙는데, 그건 조용히 틀리는 부류다.
 */
class KcaParserTest {

    private static List<CollectedSchedule> parsed;

    @BeforeAll
    static void parseFixture() throws IOException {
        String html = new String(
                new ClassPathResource("fixtures/kca_schedule.html").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        parsed = new KcaScheduleSource().parse(html);
    }

    private static CollectedSchedule find(String name, int round, ExamType type) {
        return parsed.stream()
                .filter(s -> s.certificateName().equals(name) && s.round() == round && s.examType() == type)
                .findFirst().orElse(null);
    }

    @Test
    @DisplayName("일정을 뽑는다")
    void extracts_schedules() {
        assertFalse(parsed.isEmpty(), "일정을 하나도 못 뽑았다 — 표 구조가 바뀌었나?");
        assertTrue(parsed.stream().allMatch(s -> s.year() == 2026), "연도가 뒤섞였다");
    }

    /**
     * 정보보안기사는 <b>이어지는 행</b>(비고에 "정보보안 분야")에 실기가 있고,
     * 필기는 제2회 본 행에서 rowspan 으로 이어받아야 한다.
     * 이어받기가 깨지면 필기 접수일이 비거나 다른 회차 값이 붙는다.
     */
    @Test
    @DisplayName("정보보안기사가 제2회 필기 일정을 이어받는다")
    void security_engineer_inherits_written_schedule() {
        CollectedSchedule written = find("정보보안기사", 2, ExamType.WRITTEN);

        assertNotNull(written, "정보보안기사 제2회 필기가 없다");
        assertEquals("KCA-SEC", written.sourceCode(), "이미 붙어 있는 코드와 다르면 같은 시험이 하나 더 생긴다");
        assertEquals("2026-05-11T10:00", written.regStartAt().toString(), "필기 접수 시작이 제2회 값이 아니다");
        assertEquals("2026-05-14T18:00", written.regEndAt().toString());
        assertEquals("2026-05-22", written.examStartDate().toString());
    }

    /** 같은 회차에서 실기는 분야마다 다르다 — 정보보안은 두 번째 묶음(7.25~8.9)이다. */
    @Test
    @DisplayName("실기는 분야별로 갈라진 값을 쓴다")
    void practical_uses_field_specific_dates() {
        CollectedSchedule practical = find("정보보안기사", 2, ExamType.PRACTICAL);

        assertNotNull(practical, "정보보안기사 제2회 실기가 없다");
        assertEquals("2026-07-25", practical.examStartDate().toString(),
                "전파전자통신 쪽 실기일(7.4)이 잘못 붙었다");
    }

    /** 전파전자통신은 첫 번째 묶음이다. 둘이 뒤바뀌면 양쪽 다 틀린다. */
    @Test
    @DisplayName("전파전자통신은 자기 실기일을 쓴다")
    void radio_field_keeps_its_own_practical() {
        CollectedSchedule practical = find("전파전자통신기사", 1, ExamType.PRACTICAL);

        assertNotNull(practical, "전파전자통신기사 제1회 실기가 없다");
        assertEquals("2026-03-28", practical.examStartDate().toString());
    }

    /**
     * 비고가 빈 이어지는 행은 <b>버린다</b>. 정보보안일 가능성이 높지만 표가 그렇게 말하지 않는다 —
     * 추측으로 붙이면 접수일이 틀린 채 서비스된다.
     */
    @Test
    @DisplayName("분야를 안 밝힌 행은 넣지 않는다")
    void unlabeled_rows_are_skipped() {
        // 제1회의 두 번째 실기 묶음(4.11~4.26)은 비고가 비어 있다
        assertTrue(parsed.stream()
                        .noneMatch(s -> s.examStartDate() != null
                                && s.examStartDate().toString().equals("2026-04-11")),
                "분야를 모르는 실기 일정이 들어왔다");
    }

    @Test
    @DisplayName("접수가 시험보다 늦지 않다")
    void registration_precedes_exam() {
        parsed.stream()
                .filter(s -> s.regStartAt() != null && s.examStartDate() != null)
                .forEach(s -> assertFalse(s.examStartDate().isBefore(s.regStartAt().toLocalDate()),
                        s.certificateName() + " " + s.round() + "회 " + s.examType()
                                + " 시험일이 접수 시작보다 앞이다"));
    }

    @Test
    @DisplayName("표가 없으면 예외 없이 0건")
    void no_table_yields_nothing() {
        assertTrue(new KcaScheduleSource().parse("<html>2026년 점검 중</html>").isEmpty());
    }
}

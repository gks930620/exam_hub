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
     * 비고가 <b>정말로</b> 빈 행은 버린다. 정보보안일 가능성이 높아도 표가 그렇게 말하지 않으면
     * 추측으로 붙이지 않는다 — 접수일이 틀린 채 서비스되는 게 없느니만 못하다.
     *
     * <p>예전엔 이 테스트가 제1회의 4.11 실기를 "비고가 비었다"고 봤는데, 사실은 비고가 있고
     * {@code </td>} 가 없어 파서가 못 읽은 것이었다(2026-09-04). 그래서 여기서는 진짜로 비고가
     * 빈 표를 만들어 확인한다.
     */
    @Test
    @DisplayName("분야를 안 밝힌 행은 넣지 않는다")
    void unlabeled_rows_are_skipped() {
        String html = """
                <html>2026년도 국가기술자격 검정시행일정
                <table><tr><th>회별</th><th>필기시험 원서접수</th><th>필기 시험</th><th>필기합격</th>
                <th>응시자격</th><th>실기시험 원서접수</th><th>실기 시험</th><th>합격자 발표</th><th>비 고</th></tr>
                <tr><td rowspan="2">제1회</td><td rowspan="2">1.26(월)~1.29(목)</td><td rowspan="2">2.9(월)~3.6(금)</td>
                <td rowspan="2">3.13(금)</td><td rowspan="2">2.9(월)~3.17(화)</td><td rowspan="2">3.16(월)~3.19(목)</td>
                <td>3.28(토)~3.30(월)</td><td>4.10(금)</td><td>전파전자통신 분야</td></tr>
                <tr><td>4.11(토)~4.26(일)</td><td>5.8(금)</td><td></td></tr>
                </table></html>
                """;

        assertTrue(new KcaScheduleSource().parse(html).stream()
                        .noneMatch(s -> s.examStartDate() != null
                                && s.examStartDate().toString().equals("2026-04-11")),
                "분야를 모르는 실기 일정이 들어왔다");
    }

    /** 반대로, 비고가 있으면(닫는 태그가 없더라도) 그 분야에 제대로 붙어야 한다. */
    @Test
    @DisplayName("제1회 4.11 실기는 정보보안 분야 것이다")
    void round_one_practical_belongs_to_the_declared_fields() {
        CollectedSchedule practical = find("정보보안기사", 1, ExamType.PRACTICAL);

        assertNotNull(practical, "정보보안기사 제1회 실기가 없다");
        assertEquals("2026-04-11", practical.examStartDate().toString());
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

    /**
     * <b>정보보안 분야는 제1·2·4회에 다 있다.</b> 제2회만 뽑히면 나머지 자리를 추정치가 메운다 —
     * 실제로 시드가 만든 "제3회"(존재하지 않는 회차)가 사용자 화면에 떠 있었다(2026-09-04 실측).
     *
     * <p>제1·4회의 정보보안은 <b>회차 칸이 없는 이어지는 행</b>에 있다. rowspan 이어받기가
     * 회차까지 물려주지 못하면 이 행들이 통째로 사라진다.
     */
    @Test
    @DisplayName("정보보안기사는 제1·2·4회가 다 나온다 — 이어지는 행의 회차를 물려받는다")
    void security_engineer_has_all_three_rounds() {
        for (int round : new int[]{1, 2, 4}) {
            assertNotNull(find("정보보안기사", round, ExamType.WRITTEN), "정보보안기사 제" + round + "회 필기가 없다");
            assertNotNull(find("정보보안기사", round, ExamType.PRACTICAL), "정보보안기사 제" + round + "회 실기가 없다");
        }
    }

    /** 제4회는 앞으로 올 회차라 특히 중요하다 — 필기는 회차 행에서, 실기는 이어지는 행에서 온다. */
    @Test
    @DisplayName("제4회: 필기는 회차 행 값, 실기는 이어지는 행 값")
    void round_four_merges_both_rows() {
        CollectedSchedule written = find("정보보안기사", 4, ExamType.WRITTEN);
        assertNotNull(written, "정보보안기사 제4회 필기가 없다");
        assertEquals("2026-08-31T10:00", written.regStartAt().toString());
        assertEquals("2026-09-14", written.examStartDate().toString());

        CollectedSchedule practical = find("정보보안기사", 4, ExamType.PRACTICAL);
        assertNotNull(practical, "정보보안기사 제4회 실기가 없다");
        assertEquals("2026-10-19T10:00", practical.regStartAt().toString(), "실기 접수는 회차 행에서 이어받는다");
        assertEquals("2026-11-14", practical.examStartDate().toString(), "실기 시험은 이어지는 행(11.14~11.29)이다");
    }

    /** 방송통신 분야도 이어지는 행에만 있다 — 한 건도 안 나오면 그 종목은 영원히 빈다. */
    @Test
    @DisplayName("방송통신기사도 나온다")
    void broadcasting_engineer_is_collected() {
        assertNotNull(find("방송통신기사", 1, ExamType.PRACTICAL), "방송통신기사 제1회 실기가 없다");
    }

    /**
     * <b>제3회는 정보보안 분야에 없다.</b> 특성화고 기능사 전용 회차다.
     * 이걸 만들어 내면 사용자가 없는 접수일을 기다린다.
     */
    @Test
    @DisplayName("있지도 않은 제3회를 만들지 않는다")
    void does_not_invent_round_three() {
        assertTrue(parsed.stream().noneMatch(s -> s.round() == 3),
                "제3회는 기능사 전종목 회차라 우리 종목에는 없다");
    }
}

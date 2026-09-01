package com.test.test.exam.collect;

import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 데이터산업진흥원 연간 일정표 파싱.
 *
 * <p>저장해 둔 실제 응답(2026-08-10)으로만 검증한다 — <b>테스트가 네트워크를 타지 않는다.</b>
 * 사이트가 개편되면 이 테스트는 그대로 통과하고 수집 로그에 "0건" 경고가 뜬다(설계 의도).
 *
 * <p>이 표가 중요한 이유: <b>빅데이터분석기사</b>가 여기 있다. 국가기술자격인데 시행처가
 * 큐넷이 아니라서 큐넷 API 로는 영원히 안 들어온다.
 */
class DataqParserTest {

    private static List<CollectedSchedule> parsed;

    @BeforeAll
    static void parseFixture() throws IOException {
        String html = new String(
                new ClassPathResource("fixtures/dataq_schedule.html").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        parsed = new DataqScheduleSource().parse(html);
    }

    @Test
    @DisplayName("표 하나에서 여러 종목을 뽑는다")
    void extracts_multiple_exams() {
        assertFalse(parsed.isEmpty(), "일정을 하나도 못 뽑았다 — 표 구조가 바뀌었나?");

        Map<String, Long> byName = parsed.stream()
                .collect(Collectors.groupingBy(CollectedSchedule::certificateName, Collectors.counting()));
        assertTrue(byName.size() >= 3, "종목이 너무 적다: " + byName.keySet());
    }

    /** 큐넷에 없는 국가기술자격 — 이게 안 들어오면 이 스크래퍼를 만든 이유가 없다. */
    @Test
    @DisplayName("빅데이터분석기사가 들어온다")
    void includes_bigdata_engineer() {
        CollectedSchedule s = parsed.stream()
                .filter(x -> "빅데이터분석기사".equals(x.certificateName()))
                .findFirst().orElse(null);

        assertNotNull(s, "빅데이터분석기사가 없다");
        assertEquals("M0003", s.sourceCode(), "종목코드가 마스터와 다르면 같은 시험이 하나 더 생긴다");
        assertEquals(2026, s.year());
    }

    /**
     * 표는 rowspan 으로 종목·회차를 묶어 놔서 행마다 셀 수가 다르다.
     * 이걸 잘못 읽으면 실기 일정이 <b>엉뚱한 종목</b>에 붙는다 — 조용히 틀리는 부류다.
     */
    @Test
    @DisplayName("rowspan 으로 생략된 종목·회차를 이어 받는다")
    void carries_over_rowspan_values() {
        List<CollectedSchedule> bigdata = parsed.stream()
                .filter(x -> "빅데이터분석기사".equals(x.certificateName()))
                .toList();

        assertTrue(bigdata.stream().anyMatch(x -> x.examType() == ExamType.WRITTEN), "필기가 없다");
        assertTrue(bigdata.stream().anyMatch(x -> x.examType() == ExamType.PRACTICAL),
                "실기가 없다 — rowspan 이어받기가 안 된다");
        assertTrue(bigdata.stream().allMatch(x -> x.round() > 0), "회차를 못 읽은 행이 있다");
    }

    /** 접수는 "3.3~9" 처럼 연도가 없다. 표 제목의 연도를 붙여야 한다. */
    @Test
    @DisplayName("연도 없는 날짜에 표 제목의 연도를 붙인다")
    void fills_year_from_caption() {
        assertTrue(parsed.stream().allMatch(s -> s.year() == 2026), "연도가 뒤섞였다");
        assertTrue(parsed.stream().filter(s -> s.regStartAt() != null)
                        .allMatch(s -> s.regStartAt().getYear() == 2026),
                "접수 시작 연도가 틀렸다");
    }

    /** "9.28~10.13" 처럼 접수 도중 달이 바뀌는 칸이 있다. */
    @Test
    @DisplayName("접수 시작이 마감보다 늦지 않다")
    void registration_range_is_ordered() {
        parsed.stream()
                .filter(s -> s.regStartAt() != null && s.regEndAt() != null)
                .forEach(s -> assertFalse(s.regEndAt().isBefore(s.regStartAt()),
                        s.certificateName() + " " + s.round() + "회 접수 기간이 거꾸로다: "
                                + s.regStartAt() + " ~ " + s.regEndAt()));
    }

    /** 시행처에서 읽어온 값이라 "확인 필요" 경고가 붙으면 안 된다. */
    @Test
    @DisplayName("출처는 SCRAPED — 추정치가 아니다")
    void marks_scraped_provenance() {
        assertTrue(parsed.stream().allMatch(s -> s.provenance() == ScheduleProvenance.SCRAPED));
    }

    /** 표가 사라지거나 형식이 바뀌면 예외가 아니라 0건이어야 한다 — 수집 배치가 죽으면 안 된다. */
    @Test
    @DisplayName("빈 HTML 이면 예외 없이 0건")
    void empty_html_yields_nothing() {
        assertTrue(new DataqScheduleSource().parse("<html></html>").isEmpty());
    }
}

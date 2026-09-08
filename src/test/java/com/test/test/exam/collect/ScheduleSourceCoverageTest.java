package com.test.test.exam.collect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 수집기가 밝힌 종목코드({@link ScheduleSource#coveredExamCodes()})가 <b>실제 마스터에 있는 코드</b>인지.
 *
 * <p>스크래퍼는 시행처의 종목명을 우리 코드(M0407…)로 손으로 옮겨 적는다. <b>한 글자만 틀려도
 * 조용히 같은 이름의 시험이 하나 더 생긴다</b> — 토익·유통관리사·정보보안기사에서 실제로 겪었다.
 * 컴파일도 통과하고 테스트도 통과하고, 화면에서 중복을 보고서야 안다. 그래서 여기서 잡는다.
 *
 * <p>매니저 화면의 "자동/수기" 판정도 이 코드를 근거로 쓰므로, 틀린 코드는 <b>매니저에게
 * 헛일을 시키는 것</b>이기도 하다.
 *
 * <p>기본 테스트 설정에는 스크래퍼 빈이 없어(네트워크 차단) 여기서만 켠다. 빈이 뜨는 것과
 * 네트워크를 타는 것은 별개다 — 기동 시 수집은 {@code collect.on-startup} 이 기본 false 라 안 돈다.
 */
@SpringBootTest(properties = {"scrape.enabled=true"})
@ActiveProfiles("test")
class ScheduleSourceCoverageTest {

    @Autowired
    private List<ScheduleSource> sources;

    private Set<String> masterCodes() throws IOException {
        JsonNode root = new ObjectMapper()
                .readTree(new ClassPathResource("seed/exam_master.json").getInputStream());
        JsonNode list = root.isArray() ? root : root.path("exams");
        Set<String> codes = new HashSet<>();
        for (JsonNode e : list) {
            codes.add(e.path("sourceCode").asText());
        }
        assertFalse(codes.isEmpty(), "마스터를 못 읽었다 — 이 테스트가 아무것도 검증하지 않는다");
        return codes;
    }

    @Test
    @DisplayName("밝힌 종목코드는 전부 마스터에 있다 — 한 글자만 틀려도 같은 시험이 하나 더 생긴다")
    void declared_codes_exist_in_the_master() throws IOException {
        Set<String> master = masterCodes();
        for (ScheduleSource s : sources) {
            for (String code : s.coveredExamCodes()) {
                assertTrue(master.contains(code),
                        s.sourceId() + " 가 마스터에 없는 코드를 맡는다고 한다: " + code);
            }
        }
    }

    @Test
    @DisplayName("한 종목을 두 소스가 맡지 않는다 — 서로 덮어쓰며 매일 싸운다")
    void no_exam_is_claimed_twice() {
        Map<String, String> owner = new HashMap<>();
        for (ScheduleSource s : sources) {
            for (String code : s.coveredExamCodes()) {
                String before = owner.put(code, s.sourceId());
                assertTrue(before == null,
                        code + " 를 " + before + " 와 " + s.sourceId() + " 가 같이 맡는다");
            }
        }
    }

    /** 코드를 밝히는 소스가 하나도 없으면 위 두 테스트가 아무것도 검증하지 않는다. */
    @Test
    @DisplayName("코드를 밝히는 소스가 실제로 있다")
    void something_declares_codes() {
        long declared = sources.stream().mapToLong(s -> s.coveredExamCodes().size()).sum();
        assertTrue(declared >= 30, "코드를 밝힌 소스가 너무 적다 — scrape.enabled 가 안 먹었나? " + declared);
    }
}

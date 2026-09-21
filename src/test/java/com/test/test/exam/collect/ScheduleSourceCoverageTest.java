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

    private JsonNode read(String path) throws IOException {
        return new ObjectMapper().readTree(new ClassPathResource(path).getInputStream());
    }

    /**
     * 시드가 만드는 <b>모든</b> 종목코드. 마스터와 비큐넷 시드를 둘 다 본다 —
     * 코드는 2026-09-21 에 통일했지만, 새 시드가 어긋난 코드를 들고 오면 여기서 잡힌다.
     */
    private Set<String> masterCodes() throws IOException {
        Set<String> codes = new HashSet<>();
        for (String f : new String[]{"seed/exam_master.json", "seed/non_qnet_exams.json"}) {
            for (JsonNode e : list(read(f))) {
                codes.add(e.path("sourceCode").asText());
            }
        }
        assertFalse(codes.isEmpty(), "시드를 못 읽었다 — 이 테스트가 아무것도 검증하지 않는다");
        return codes;
    }

    @Test
    @DisplayName("밝힌 종목코드는 전부 시드에 있는 코드다 — 한 글자만 틀려도 같은 시험이 하나 더 생긴다")
    void declared_codes_exist_in_the_master() throws IOException {
        Set<String> master = masterCodes();
        for (ScheduleSource s : sources) {
            for (String code : s.coveredExamCodes()) {
                assertTrue(master.contains(code),
                        s.sourceId() + " 가 어느 시드에도 없는 코드를 맡는다고 한다: " + code);
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

    /**
     * <b>같은 시험에 시드마다 다른 코드가 붙어 있다.</b> 비큐넷 시드가 마스터보다 먼저 종목을 만들어서,
     * 로컬에서는 시드 코드가(예: {@code TESAT}) 운영에서는 마스터 코드가({@code M0440}) 붙는다
     * — 비큐넷 시드는 {@code @Profile("!prod")} 라 운영엔 안 돈다.
     *
     * <p>수집 자체는 {@link DiffService} 가 이름으로도 찾아 주므로 어느 쪽이든 붙는다. 그런데
     * {@link ScheduleSource#coveredExamCodes()} 는 <b>코드로만</b> 맞춰 보기 때문에, 마스터 코드만
     * 밝히면 로컬에서 그 판정이 조용히 안 먹는다(2026-09-08 TESAT 에서 실측).
     *
     * <p>그래서 <b>두 코드를 다 밝히게</b> 한다. 이 테스트가 빠진 쪽을 짚어 준다.
     */
    @Test
    @DisplayName("시드마다 코드가 다른 시험은 두 코드를 다 밝힌다")
    void sources_declare_both_codes_when_the_seeds_disagree() throws IOException {
        Map<String, String> seedCodeByMasterCode = seedAliases();
        for (ScheduleSource s : sources) {
            Set<String> declared = s.coveredExamCodes();
            for (String code : declared) {
                String alias = seedCodeByMasterCode.get(code);
                assertTrue(alias == null || declared.contains(alias),
                        s.sourceId() + " 가 " + code + " 만 밝혔다 — 비큐넷 시드는 같은 시험을 "
                                + alias + " 로 만든다. 둘 다 밝혀야 로컬에서도 판정이 먹는다");
            }
        }
    }

    /** 이름이 같은데 코드가 다른 종목: 마스터 코드 → 비큐넷 시드 코드. */
    private Map<String, String> seedAliases() throws IOException {
        Map<String, String> masterByName = new HashMap<>();
        for (JsonNode e : list(read("seed/exam_master.json"))) {
            masterByName.put(e.path("name").asText(), e.path("sourceCode").asText());
        }
        Map<String, String> aliases = new HashMap<>();
        for (JsonNode e : list(read("seed/non_qnet_exams.json"))) {
            String master = masterByName.get(e.path("name").asText());
            String seed = e.path("sourceCode").asText();
            if (master != null && !master.equals(seed)) {
                aliases.put(master, seed);
            }
        }
        return aliases;
    }

    private JsonNode list(JsonNode root) {
        return root.isArray() ? root : root.has("exams") ? root.path("exams") : root.path("items");
    }

    /** 코드를 밝히는 소스가 하나도 없으면 위 두 테스트가 아무것도 검증하지 않는다. */
    @Test
    @DisplayName("코드를 밝히는 소스가 실제로 있다")
    void something_declares_codes() {
        long declared = sources.stream().mapToLong(s -> s.coveredExamCodes().size()).sum();
        assertTrue(declared >= 30, "코드를 밝힌 소스가 너무 적다 — scrape.enabled 가 안 먹었나? " + declared);
    }
}

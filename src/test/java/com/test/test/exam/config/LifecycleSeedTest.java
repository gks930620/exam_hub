package com.test.test.exam.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.test.exam.domain.CertificateLifecycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>목록에서 뺀 시험의 기록</b>({@code seed/certificate_lifecycle.json})이 실제 마스터와 맞는지.
 *
 * <p>이 파일은 이름으로 시험을 찾는다. <b>한 글자만 달라도 조용히 아무 일도 안 일어난다</b> —
 * 뺐다고 생각한 시험이 그대로 목록에 남아 있고, 아무도 모른다. 그래서 여기서 잡는다.
 *
 * <p>2026-09-08 에 셋을 뺐다: 보험계리사·손해사정사·보험중개사. 시험은 멀쩡한데
 * <b>시행처의 시험 사이트가 아예 없다</b>({@code in.fss.or.kr}·{@code exam.fss.or.kr} 모두 없는 도메인,
 * 금감원 본 사이트의 자격시험 목록 주소는 오류). 일정을 구할 길이 없는 시험을 목록에 두면
 * 사용자는 "일정 없음"만 보고, 매니저는 오지 않을 자동 수집을 기다린다.
 */
class LifecycleSeedTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonNode read(String path) throws IOException {
        return JSON.readTree(new ClassPathResource(path).getInputStream());
    }

    private JsonNode lifecycleItems() throws IOException {
        return read("seed/certificate_lifecycle.json").path("items");
    }

    /**
     * 변천사는 <b>DB 에 있는 시험 이름</b>으로 찾는다. 그 시험은 세 시드 중 어디서 와도 된다 —
     * 큐넷 613종, 비큐넷 마스터, 일정까지 담은 비큐넷 시드.
     */
    private Set<String> knownNames() throws IOException {
        Set<String> names = new HashSet<>();
        for (String f : new String[]{"seed/exam_master.json", "seed/qnet_master.json", "seed/non_qnet_exams.json"}) {
            JsonNode root = read(f);
            JsonNode list = root.isArray() ? root : root.has("exams") ? root.path("exams") : root.path("items");
            for (JsonNode e : list) {
                String name = e.path("name").asText();
                if (!name.isBlank()) {
                    names.add(name);
                }
            }
        }
        assertFalse(names.isEmpty(), "시드를 못 읽었다 — 이 테스트가 아무것도 검증하지 않는다");
        return names;
    }

    /** 이름이 틀리면 아무 일도 안 일어난다 — 뺐다고 믿은 시험이 그대로 남는다. */
    @Test
    @DisplayName("기록의 시험 이름은 전부 실제 시험이다")
    void every_recorded_name_exists() throws IOException {
        Set<String> master = knownNames();
        for (JsonNode item : lifecycleItems()) {
            String name = item.path("name").asText();
            assertTrue(master.contains(name),
                    "그런 이름의 시험이 없어 이 기록은 아무 일도 안 한다: " + name);
        }
    }

    @Test
    @DisplayName("상태 값은 전부 아는 값이다")
    void every_lifecycle_value_is_known() throws IOException {
        for (JsonNode item : lifecycleItems()) {
            String raw = item.path("lifecycle").asText();
            assertTrue(java.util.Arrays.stream(CertificateLifecycle.values()).anyMatch(v -> v.name().equals(raw)),
                    "모르는 상태 값이라 건너뛴다: " + raw + " (" + item.path("name").asText() + ")");
        }
    }

    /**
     * 금감원 보험 3종은 <b>일부러</b> 뺐다. 근거가 없으면 나중에 누가 되살려 놓고
     * 왜 뺐는지 다시 조사하게 된다.
     */
    @Test
    @DisplayName("금감원 보험 3종은 EXCLUDED 로, 이유를 적어 남긴다")
    void the_three_insurance_exams_are_excluded_with_a_reason() throws IOException {
        int found = 0;
        for (JsonNode item : lifecycleItems()) {
            String name = item.path("name").asText();
            if (!name.equals("보험계리사") && !name.equals("손해사정사") && !name.equals("보험중개사")) {
                continue;
            }
            found++;
            assertEquals("EXCLUDED", item.path("lifecycle").asText(),
                    name + " 은 폐지된 게 아니다 — 우리가 못 다루는 것뿐이다");
            assertTrue(item.path("note").asText().length() >= 20,
                    name + " 을 왜 뺐는지 근거가 없다");
        }
        assertEquals(3, found, "보험 3종이 기록에 다 없다");
    }
}

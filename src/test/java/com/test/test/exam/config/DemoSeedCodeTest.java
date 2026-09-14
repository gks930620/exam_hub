package com.test.test.exam.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.test.exam.collect.DemoDataProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>데모 시드의 큐넷 종목코드가 진짜 그 시험의 코드인가.</b>
 *
 * <p>큐넷 수집은 이름이 아니라 <b>종목코드(jmCd)로 행을 찾는다</b>. 그래서 데모가 A 시험에 B 시험의
 * 코드를 달아 두면, 수집이 그 행을 B 로 알고 <b>이름을 B 로 바꿔 버린다</b>. slug 은 안 바뀌므로
 * 그 행은 "이름은 B, slug 은 A" 가 된다.
 *
 * <p>실제로 그랬다(2026-09-10 실측). 산업안전기사에 2290(정보처리산업기사)을 달아 둔 탓에
 * {@code /api/certificates/by-slug/산업안전기사} 가 <b>정보처리산업기사</b>를 돌려줬고,
 * 진짜 산업안전기사는 slug 을 뺏겨 {@code 산업안전기사-2} 를 달고 있었다.
 * 소방설비기사(전기분야)↔에너지관리기능사, 정보처리산업기사↔조경산업기사도 같은 사고였다.
 *
 * <p>없는 코드(1230·1450·5836)도 셋 있었다. 이건 이름이 안 바뀌니 눈에 안 띄지만,
 * <b>코드 매칭이 통째로 실패해</b> 이름 매칭이라는 약한 고리에만 기대게 된다 — 표기가 한 글자만
 * 달라지면 그날로 일정이 안 붙는다.
 *
 * <p>사람 눈으로는 절대 못 잡는 종류의 오류라 여기서 자동으로 대조한다.
 */
class DemoSeedCodeTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 큐넷 마스터: 이름 → 종목코드 */
    private Map<String, String> qnetCodeByName() throws IOException {
        JsonNode root = JSON.readTree(new ClassPathResource("seed/qnet_master.json").getInputStream());
        Map<String, String> byName = new HashMap<>();
        for (JsonNode e : root.path("exams")) {
            String name = e.path("name").asText();
            String code = e.path("sourceCode").asText();
            if (!name.isBlank() && !code.isBlank()) {
                byName.put(name, code);
            }
        }
        assertFalse(byName.isEmpty(), "큐넷 마스터를 못 읽었다 — 이 테스트가 아무것도 검증하지 않는다");
        return byName;
    }

    @Test
    @DisplayName("데모 시드의 큐넷 종목은 큐넷 마스터와 같은 종목코드를 쓴다")
    void demoQnetCodesMatchMaster() throws IOException {
        Map<String, String> master = qnetCodeByName();
        List<String> wrong = new ArrayList<>();

        for (DemoDataProvider.DemoCert cert : new DemoDataProvider().demoCertificates()) {
            String expected = master.get(cert.name());
            if (expected == null) {
                continue;   // 큐넷 종목이 아니다(SQLD·컴활) — 대조할 기준이 없다
            }
            if (!expected.equals(cert.sourceCode())) {
                String owner = master.entrySet().stream()
                        .filter(en -> en.getValue().equals(cert.sourceCode()))
                        .map(Map.Entry::getKey).findFirst().orElse("(없는 코드)");
                wrong.add("%s 에 %s 를 달아 놨다 — 그건 %s 의 코드다(맞는 코드 %s)"
                        .formatted(cert.name(), cert.sourceCode(), owner, expected));
            }
        }

        assertTrue(wrong.isEmpty(),
                "데모 종목코드가 큐넷 마스터와 어긋난다. 수집이 이 행의 이름을 남의 이름으로 바꾼다:\n  "
                        + String.join("\n  ", wrong));
    }

    @Test
    @DisplayName("데모 시드 안에서 종목코드가 겹치지 않는다")
    void demoCodesAreUnique() {
        Map<String, String> seen = new HashMap<>();
        List<String> clash = new ArrayList<>();
        for (DemoDataProvider.DemoCert cert : new DemoDataProvider().demoCertificates()) {
            String prev = seen.put(cert.sourceCode(), cert.name());
            if (prev != null) {
                clash.add(cert.sourceCode() + " → " + prev + " / " + cert.name());
            }
        }
        assertTrue(clash.isEmpty(), "같은 종목코드를 둘이 나눠 쓰면 수집이 한 행에 몰아넣는다: " + clash);
    }

    @Test
    @DisplayName("데모 종목 수는 10종 그대로다 — 늘리거나 줄이면 일정 오프셋(certs.get(n))이 어긋난다")
    void demoCertCountIsStable() {
        assertEquals(10, new DemoDataProvider().demoCertificates().size());
    }
}

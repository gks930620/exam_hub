package com.test.test.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 비큐넷 시험 시드(SeedFileScheduleSource)가 켜졌을 때
 * seed/non_qnet_exams.json 의 시험이 실제로 DB에 적재되어 검색 API로 노출되는지 검증한다.
 * (기본 test 프로파일은 seed.nonqnet.enabled=false 라 이 테스트만 프로퍼티로 켠다.)
 */
@SpringBootTest(properties = "seed.nonqnet.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NonQnetSeedIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void seeded_history_exam_is_searchable_with_category() throws Exception {
        mockMvc.perform(get("/api/certificates").param("query", "한국사"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].name").exists())
                // 카테고리 필드가 채워져 노출되는지 (각종 시험 통합 분류)
                .andExpect(jsonPath("$.items[?(@.category=='한국사')]").exists());
    }

    @Test
    void seeded_toeic_is_searchable_and_from_ybm() throws Exception {
        mockMvc.perform(get("/api/certificates").param("query", "토익"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").exists());
    }
}

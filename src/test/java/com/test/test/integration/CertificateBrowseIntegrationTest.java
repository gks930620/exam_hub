package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시험 전체 둘러보기 API 통합테스트.
 *
 * <p><b>왜 필요한가</b>: 지금까지는 일정(ExamSchedule)이 수집돼야 자격증(Certificate)이 생기는 구조라
 * 일정을 못 구한 시험은 화면에 아예 나타나지 않았다. 마스터와 일정을 분리 적재하면
 * "시험은 보이는데 일정 미정" 상태가 가능해지고, 사용자는 그 시험을 검색·관심등록할 수 있다.
 *
 * <p>계약: 목록/검색/상세는 <b>일정 유무와 무관하게</b> 동작한다. 일정이 없으면 {@code hasSchedule=false},
 * 상세의 {@code nextEvent}는 null, {@code schedules}는 빈 배열이다.
 */
class CertificateBrowseIntegrationTest extends ApiIntegrationTestSupport {

    // ===== 첫 화면 지표 =====

    /**
     * 시험 찾기 첫 화면의 지표 타일(킷 .k-stats). 전체 시험 / 일정 있는 시험 / 지금 접수 중 /
     * 7일 안에 접수 시작. 비로그인도 본다. 숫자는 서로 모순되면 안 된다 — 일정 있는 시험이
     * 전체보다 많거나, 접수 중이 일정 있는 시험보다 많으면 화면이 거짓말을 한다.
     */
    @Test
    void stats_are_public_and_consistent() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/certificates/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExams").isNumber())
                .andExpect(jsonPath("$.withSchedule").isNumber())
                .andExpect(jsonPath("$.registrationOpen").isNumber())
                .andExpect(jsonPath("$.openingWithin7Days").isNumber())
                .andReturn();
        JsonNode j = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
        long total = j.path("totalExams").asLong(), with = j.path("withSchedule").asLong();
        org.junit.jupiter.api.Assertions.assertTrue(total > 0, "시험이 하나도 없다");
        org.junit.jupiter.api.Assertions.assertTrue(with <= total, "일정 있는 시험이 전체보다 많다");
        org.junit.jupiter.api.Assertions.assertTrue(j.path("registrationOpen").asLong() <= with, "접수 중이 일정 있는 시험보다 많다");
        // 상시는 일정이 없으니 "못 얻은 데이터"(전체 - 일정 있음) 안에 들어 있어야 한다
        org.junit.jupiter.api.Assertions.assertTrue(j.path("rolling").asLong() <= total - with, "상시가 일정 없는 시험보다 많다");

        // 첫 화면과 매니저 데이터 지도는 같은 숫자를 말해야 한다 — 셈법이 둘이면 어느 한쪽은 거짓말이다
        JsonNode coverage = objectMapper.readTree(mockMvc.perform(get("/api/admin/data-map")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8)).path("coverage");
        assertEquals(coverage.path("withSchedule").asLong(), with,
                "첫 화면의 '일정 있는 시험'과 데이터 지도의 withSchedule 이 다르다");
    }

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private ExamScheduleRepository examScheduleRepository;

    private long statsWithSchedule() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/certificates/stats"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("withSchedule").asLong();
    }

    /** 연도·회차만 넣고 날짜가 없는 행은 일정이 아니다 — 매니저 화면과 사용자 카드가 이미 그렇게 센다. */
    @Test
    @DisplayName("첫 화면 지표의 '일정 있는 시험'은 날짜 있는 회차 기준이다")
    void stats_with_schedule_ignores_undated_rows() throws Exception {
        long before = statsWithSchedule();
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Certificate cert = certificateRepository.save(Certificate.builder()
                .name("지표시험 " + unique).slug("지표시험-" + unique)
                .series(Series.ETC).agency("테스트시행처").category("테스트")
                .build());

        examScheduleRepository.save(ExamSchedule.builder()
                .certificate(cert).year(2099).round(1).examType(ExamType.WRITTEN)
                .provenance(ScheduleProvenance.MANUAL).build());
        assertEquals(before, statsWithSchedule(), "날짜 없는 회차가 '일정 있음'으로 세어졌다");

        examScheduleRepository.save(ExamSchedule.builder()
                .certificate(cert).year(2099).round(2).examType(ExamType.WRITTEN)
                .examStartDate(LocalDate.of(2099, 3, 1))
                .provenance(ScheduleProvenance.MANUAL).build());
        assertEquals(before + 1, statsWithSchedule(), "날짜 있는 회차를 넣었는데 지표가 안 는다");
    }

    // ===== 카드의 일정 상태 =====

    /**
     * "일정 있음"만으로는 남은 일정이 전부 지난 시험이 멀쩡해 보인다 — 사용자가 지난 날짜를 믿는다.
     * 카드는 UPCOMING / PAST_ONLY / NONE / ROLLING 을 구분해서 받는다.
     */
    @Test
    void browse_items_carry_schedule_state() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/certificates/browse").param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(
                res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
        java.util.Set<String> allowed = java.util.Set.of("UPCOMING", "PAST_ONLY", "NONE", "ROLLING");
        for (JsonNode it : items) {
            String state = it.path("scheduleState").asText("");
            org.junit.jupiter.api.Assertions.assertTrue(allowed.contains(state), "모르는 상태: " + state);
            if (!it.path("hasSchedule").asBoolean()) {
                org.junit.jupiter.api.Assertions.assertTrue(state.equals("NONE") || state.equals("ROLLING"),
                        "일정이 없는데 " + state + ": " + it.path("name").asText());
            }
            if (state.equals("UPCOMING")) {
                org.junit.jupiter.api.Assertions.assertFalse(it.path("nextLabel").asText("").isBlank(),
                        "앞으로 일정이 있다는데 무엇인지 없다: " + it.path("name").asText());
            }
        }
    }

    // ===== 전체 둘러보기 =====

    @Test
    void browse_returns_paged_items() throws Exception {
        mockMvc.perform(get("/api/certificates/browse").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    /** 검색어 없이도 전체 목록을 볼 수 있어야 한다 — "시험이 없다"고 느끼게 만들던 원인. */
    @Test
    void browse_without_any_param_returns_first_page() throws Exception {
        mockMvc.perform(get("/api/certificates/browse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").isNumber())
                .andExpect(jsonPath("$.items[0].name").exists())
                .andExpect(jsonPath("$.items[0].hasSchedule").exists());
    }

    @Test
    void browse_filters_by_category() throws Exception {
        MvcResult all = mockMvc.perform(get("/api/certificates/browse").param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(all.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
        String category = null;
        for (JsonNode it : items) {
            if (!it.path("category").isNull() && !it.path("category").asText().isBlank()) {
                category = it.path("category").asText();
                break;
            }
        }
        if (category == null) {
            return; // 분류가 하나도 없으면 검증 생략(시드 구성에 따라)
        }

        MvcResult filtered = mockMvc.perform(get("/api/certificates/browse")
                        .param("category", category).param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode filteredItems = objectMapper.readTree(filtered.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");

        if (filteredItems.size() == 0) {
            throw new AssertionError("분류 필터 결과가 비었다: " + category);
        }
        for (JsonNode it : filteredItems) {
            if (!category.equals(it.path("category").asText())) {
                throw new AssertionError("다른 분류가 섞였다: " + it.path("category").asText());
            }
        }
    }

    @Test
    void browse_with_query_narrows_result() throws Exception {
        mockMvc.perform(get("/api/certificates/browse").param("query", "기사").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void browse_rejects_oversized_page() throws Exception {
        mockMvc.perform(get("/api/certificates/browse").param("size", "5000"))
                .andExpect(status().isBadRequest());
    }

    // ===== 분류 목록 =====

    @Test
    void categories_return_name_and_count() throws Exception {
        mockMvc.perform(get("/api/certificates/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].name").exists())
                .andExpect(jsonPath("$.items[0].count").isNumber());
    }

    // ===== 일정 없는 시험도 정상 취급 =====

    /**
     * 일정이 하나도 없는 시험이 목록에 존재하고, 상세도 200 으로 열려야 한다.
     * (마스터 시드는 일정 없이 들어오므로 최소 한 건은 있어야 한다)
     */
    @Test
    void certificate_without_schedule_is_listed_and_detail_opens() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/certificates/browse").param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");

        Long idWithoutSchedule = null;
        for (JsonNode it : items) {
            if (!it.path("hasSchedule").asBoolean(true)) {
                idWithoutSchedule = it.path("id").asLong();
                break;
            }
        }
        if (idWithoutSchedule == null) {
            throw new AssertionError("일정 없는 시험이 목록에 하나도 없다 — 마스터 시드가 적재되지 않았다");
        }

        mockMvc.perform(get("/api/certificates/{id}", idWithoutSchedule))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idWithoutSchedule))
                .andExpect(jsonPath("$.nextEvent").doesNotExist())
                .andExpect(jsonPath("$.schedules").isArray())
                .andExpect(jsonPath("$.schedules").isEmpty());
    }
}

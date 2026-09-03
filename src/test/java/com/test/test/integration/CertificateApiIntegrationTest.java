package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.domain.Series;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 자격증 조회 API 통합테스트 (설계 05 §2-1, §2-2 — CertificateController).
 * 공개 API(비로그인). 로그인 상태면 favorited 플래그가 채워진다.
 * 정상 응답 + 에러 경로(400 검색어 짧음/파라미터 누락, 404 없음)를 관통 검증한다.
 */
class CertificateApiIntegrationTest extends ApiIntegrationTestSupport {

    /** 시드된 자격증 하나의 id 를 인기 목록으로 조회해 얻는다(하드코딩 회피). */
    private long anyCertificateId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/certificates/popular"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).path("items");
        return items.get(0).path("id").asLong();
    }

    @Test
    void search_returns_matching_certificates() throws Exception {
        mockMvc.perform(get("/api/certificates").param("query", "정보처리"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].id").isNumber())
                .andExpect(jsonPath("$.items[0].name").exists())
                .andExpect(jsonPath("$.items[0].favorited").value(false));
    }

    @Test
    void search_with_too_short_query_returns_400() throws Exception {
        mockMvc.perform(get("/api/certificates").param("query", "정"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_without_query_param_returns_400() throws Exception {
        mockMvc.perform(get("/api/certificates"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void popular_returns_top_certificates() throws Exception {
        mockMvc.perform(get("/api/certificates/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].id").isNumber());
    }

    @Test
    void detail_returns_certificate_with_schedules() throws Exception {
        long id = anyCertificateId();

        mockMvc.perform(get("/api/certificates/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.favorited").value(false))
                .andExpect(jsonPath("$.schedules").isArray());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("로그인 상태면 관심 등록 여부(favorited)가 채워진다")
    void detail_reflects_favorited_flag_for_logged_in_member() throws Exception {
        long id = anyCertificateId();
        com.test.test.exam.domain.Member m = newMember();

        // 등록 전에는 false
        mockMvc.perform(get("/api/certificates/{id}", id)
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, bearer(m)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(false));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/me/favorites")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"certificateId\": " + id + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/certificates/{id}", id)
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, bearer(m)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(true));
    }

    @Test
    void detail_of_unknown_certificate_returns_404() throws Exception {
        mockMvc.perform(get("/api/certificates/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void detail_by_slug_returns_certificate() throws Exception {
        // 데모 시드의 정보처리기사(slug=정보처리기사) — 구 pSEO /cert/{slug} 대체 API
        mockMvc.perform(get("/api/certificates/by-slug/{slug}", "정보처리기사"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("정보처리기사"))
                .andExpect(jsonPath("$.category").exists())
                .andExpect(jsonPath("$.schedules").isArray());
    }

    @Test
    void detail_by_unknown_slug_returns_404() throws Exception {
        mockMvc.perform(get("/api/certificates/by-slug/{slug}", "없는자격증-xyz"))
                .andExpect(status().isNotFound());
    }

    // ===== 상세의 회차 목록 =====

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private ExamScheduleRepository examScheduleRepository;

    private Certificate newCertificate(String name) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return certificateRepository.save(Certificate.builder()
                .name(name + " " + unique).slug(name + "-" + unique)
                .series(Series.ETC).agency("테스트시행처").category("테스트")
                .build());
    }

    private ExamSchedule newSchedule(Certificate cert, int round, ScheduleStatus status,
                                     LocalDate examStart, LocalDate examEnd) {
        return examScheduleRepository.save(ExamSchedule.builder()
                .certificate(cert).year(TimeUtil.today().getYear()).round(round).examType(ExamType.WRITTEN)
                .examStartDate(examStart).examEndDate(examEnd)
                .status(status).provenance(ScheduleProvenance.MANUAL)
                .build());
    }

    /**
     * 취소 회차는 사용자가 알아야 한다(접수해 둔 사람이 있다) — status 로 구분해 준다.
     * 날짜 없는 회차(연도·회차만)와 검토 보류(PENDING_REVIEW)는 사용자에게 보낼 내용이 아니다.
     */
    @Test
    @DisplayName("상세의 회차 목록: 취소 회차는 status 와 함께 남고, 날짜 없는 회차와 검토 보류는 빠진다")
    void detail_lists_canceled_but_hides_undated_and_pending_review() throws Exception {
        LocalDate today = TimeUtil.today();
        Certificate cert = newCertificate("상세회차시험");
        newSchedule(cert, 1, ScheduleStatus.ACTIVE, today.plusDays(30), today.plusDays(30));
        newSchedule(cert, 2, ScheduleStatus.CANCELED, today.plusDays(60), today.plusDays(60));
        newSchedule(cert, 3, ScheduleStatus.ACTIVE, null, null);
        newSchedule(cert, 4, ScheduleStatus.PENDING_REVIEW, today.plusDays(10), today.plusDays(10));

        MvcResult res = mockMvc.perform(get("/api/certificates/{id}", cert.getId()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
        Map<Integer, String> statusByRound = new HashMap<>();
        for (JsonNode s : body.path("schedules")) {
            statusByRound.put(s.path("round").asInt(), s.path("status").asText());
        }
        assertEquals("ACTIVE", statusByRound.get(1));
        assertEquals("CANCELED", statusByRound.get(2), "취소 회차가 상세에서 사라졌다: " + statusByRound);
        assertFalse(statusByRound.containsKey(3), "날짜 없는 회차가 상세에 보인다");
        assertFalse(statusByRound.containsKey(4), "검토 보류 회차가 사용자에게 보인다");
        assertEquals(2, statusByRound.size());
        // 다음 이벤트는 ACTIVE 만으로 — 보류(10일 뒤)나 취소가 끼어들면 안 된다
        assertEquals(30, body.path("nextEvent").path("dday").asInt(-1));
    }

    @Test
    @DisplayName("시험 기간 중이면 상세의 다음 이벤트는 '시험 진행 중'(D-0)이고 기준 시각은 종료일이다")
    void detail_next_event_is_ongoing_during_exam_period() throws Exception {
        LocalDate today = TimeUtil.today();
        Certificate cert = newCertificate("진행중상세");
        newSchedule(cert, 1, ScheduleStatus.ACTIVE, today.minusDays(2), today.plusDays(2));

        mockMvc.perform(get("/api/certificates/{id}", cert.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextEvent.type").value("EXAM_ONGOING"))
                .andExpect(jsonPath("$.nextEvent.dday").value(0))
                .andExpect(jsonPath("$.nextEvent.at").value(TimeUtil.format(today.plusDays(2).atStartOfDay())));
    }

    @Test
    @DisplayName("없는 시험의 메시지는 '시험'이라고 말한다 — 화면 어디에도 '자격증'이 없다")
    void unknown_certificate_message_says_exam() throws Exception {
        mockMvc.perform(get("/api/certificates/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("시험을 찾을 수 없습니다."));
        mockMvc.perform(get("/api/certificates/by-slug/{slug}", "없는자격증-xyz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("시험을 찾을 수 없습니다."));
    }
}

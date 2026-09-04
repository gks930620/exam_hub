package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.CertificateLifecycle;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import com.test.test.exam.repository.UserFavoriteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인 사용자 API 통합테스트 (설계 08 §4).
 *
 * <p>핵심 계약: <b>비로그인은 401</b>(400 아님). 프런트가 "요청이 잘못됨"과 "로그인이 필요함"을
 * 구분해야 로그인 화면으로 보낼 수 있다.
 */
class MeApiIntegrationTest extends ApiIntegrationTestSupport {

    private long anyCertificateId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/certificates/popular"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
        return items.get(0).path("id").asLong();
    }

    // ===== 인증 경계 =====

    @Test
    @DisplayName("비로그인으로 내 시험 조회 → 401")
    void favorites_without_login_returns_401() throws Exception {
        mockMvc.perform(get("/api/me/favorites"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("잘못된 토큰 → 401")
    void favorites_with_broken_token_returns_401() throws Exception {
        mockMvc.perform(get("/api/me/favorites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인하면 내 정보를 준다")
    void me_returns_profile() throws Exception {
        Member m = newMember();
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(m)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(m.getId()))
                .andExpect(jsonPath("$.nickname").value(m.getNickname()))
                .andExpect(jsonPath("$.provider").value("GOOGLE"));
    }

    // ===== 관심 등록 =====

    @Test
    void add_favorite_returns_201() throws Exception {
        long certId = anyCertificateId();
        Member m = newMember();

        mockMvc.perform(post("/api/me/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"certificateId\": " + certId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.certificateId").value((int) certId));
    }

    @Test
    void add_favorite_duplicate_returns_409() throws Exception {
        long certId = anyCertificateId();
        Member m = newMember();
        String body = "{\"certificateId\": " + certId + "}";

        mockMvc.perform(post("/api/me/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/me/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    /** 유료화를 안 하기로 해서 관심 개수 제한(구 무료 3개)을 없앴다. */
    @Test
    @DisplayName("관심 시험은 3개를 넘겨도 등록된다 (유료화 제한 폐지)")
    void favorites_have_no_count_limit() throws Exception {
        Member m = newMember();
        MvcResult result = mockMvc.perform(get("/api/certificates/browse").param("size", "5"))
                .andExpect(status().isOk()).andReturn();
        JsonNode items = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/me/favorites")
                            .header(HttpHeaders.AUTHORIZATION, bearer(m))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"certificateId\": " + items.get(i).path("id").asLong() + "}"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/me/favorites").header(HttpHeaders.AUTHORIZATION, bearer(m)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5));
    }

    @Test
    void remove_favorite_returns_204() throws Exception {
        long certId = anyCertificateId();
        Member m = newMember();

        mockMvc.perform(post("/api/me/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"certificateId\": " + certId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/me/favorites/{id}", certId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(m)))
                .andExpect(status().isNoContent());
    }

    // ===== 알림 설정 =====

    @Test
    void get_notify_settings_returns_defaults() throws Exception {
        mockMvc.perform(get("/api/me/notify-settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifyReg").value(true))
                .andExpect(jsonPath("$.notifyExam").value(true))
                .andExpect(jsonPath("$.notifyChange").value(true));
    }

    /** 저장한 값이 이후 조회에 반영돼야 한다 — 조회 API 가 없어 항상 전체 ON 으로 보이던 오표시의 회귀 방지. */
    @Test
    void get_notify_settings_returns_saved_values() throws Exception {
        Member m = newMember();

        mockMvc.perform(put("/api/me/notify-settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notifyReg\": false, \"notifyExam\": true, \"notifyChange\": false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/notify-settings").header(HttpHeaders.AUTHORIZATION, bearer(m)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifyReg").value(false))
                .andExpect(jsonPath("$.notifyChange").value(false));
    }

    // ===== 프로필 =====

    @Test
    void change_nickname() throws Exception {
        mockMvc.perform(patch("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \"새닉네임\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새닉네임"));
    }

    @Test
    void blank_nickname_returns_400() throws Exception {
        mockMvc.perform(patch("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ===== 알림 받을 이메일 =====
    //
    // 카카오는 비즈 앱이 아니면 이메일 동의항목을 켤 수 없다(요청하면 KOE205 로 로그인 자체가 막힌다).
    // 그래서 소셜에서 이메일이 안 온다. 알림 채널이 이메일뿐이라 직접 입력이 유일한 수신 경로다.

    @Test
    @DisplayName("이메일을 직접 입력해 저장한다")
    void change_email() throws Exception {
        Member m = newMember();

        mockMvc.perform(put("/api/me/email")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"me@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"));

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(m)))
                .andExpect(jsonPath("$.email").value("me@example.com"));
    }

    @Test
    @DisplayName("형식이 아닌 이메일 → 400 (저장 전에 막는다)")
    void invalid_email_returns_400() throws Exception {
        mockMvc.perform(put("/api/me/email")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"골뱅이없음\"}"))
                .andExpect(status().isBadRequest());
    }

    /** 빈 값은 "알림 안 받겠다"는 뜻이다 — 400 이 아니라 null 로 지운다. */
    @Test
    @DisplayName("빈 이메일은 수신 해제")
    void blank_email_clears_it() throws Exception {
        Member m = newMember();

        mockMvc.perform(put("/api/me/email")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"me@example.com\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/me/email")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void change_email_without_login_returns_401() throws Exception {
        mockMvc.perform(put("/api/me/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"me@example.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ===== 캘린더 =====

    @Test
    void calendar_returns_events_array() throws Exception {
        mockMvc.perform(get("/api/me/calendar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember()))
                        .param("year", "2026").param("month", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events").isArray());
    }

    @Test
    void calendar_without_params_returns_400() throws Exception {
        mockMvc.perform(get("/api/me/calendar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember())))
                .andExpect(status().isBadRequest());
    }

    /**
     * 달이 1~12 밖이면 <b>400</b>이다. 500 이 아니다.
     *
     * <p>QA 에서 잡혔다(2026-09-03): 관심 시험이 있는 계정으로 {@code month=13} 을 부르면
     * {@code LocalDate.of(year, 13, 1)} 이 터져 500 이 났다. 관심이 없으면 앞에서 빈 목록으로
     * 빠져나가 안 터졌기 때문에, 데이터가 있는 계정에서만 나는 사고였다.
     */
    @Test
    @DisplayName("캘린더: 달이 1~12 밖이면 400 (관심 시험이 있어도)")
    void calendar_rejects_month_out_of_range() throws Exception {
        Member m = newMember();
        Certificate cert = newCertificate("캘린더경계시험");
        newSchedule(cert, null, null, TimeUtil.today().plusDays(3), TimeUtil.today().plusDays(3));
        favoriteCard(m, cert);   // 관심이 있어야 달 계산까지 내려간다 — 없으면 앞에서 빈 목록으로 빠진다

        for (String month : new String[]{"0", "13", "-1"}) {
            mockMvc.perform(get("/api/me/calendar")
                            .header(HttpHeaders.AUTHORIZATION, bearer(m))
                            .param("year", "2026").param("month", month))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===== 내 시험 카드의 일정 상태 =====
    //
    // 프런트와 합의한 계약: 카드에 scheduleState(ROLLING|UPCOMING|PAST_ONLY|NONE)·lastExamDate 가 있고,
    // 이벤트가 없으면 dday 는 0 이 아니라 null 이다. 판정은 시험 찾기 카드(CertificateService.toItems)와 같다 —
    // 날짜 없는 회차는 일정이 아니고, 상시가 우선한다.

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private ExamScheduleRepository examScheduleRepository;

    @Autowired
    private UserFavoriteRepository userFavoriteRepository;

    private Certificate newCertificate(String name) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return certificateRepository.save(Certificate.builder()
                .name(name + " " + unique).slug(name + "-" + unique)
                .series(Series.ETC).agency("테스트시행처").category("테스트")
                .build());
    }

    private ExamSchedule newSchedule(Certificate cert, LocalDateTime regStart, LocalDateTime regEnd,
                                     LocalDate examStart, LocalDate examEnd) {
        return examScheduleRepository.save(ExamSchedule.builder()
                .certificate(cert).year(TimeUtil.today().getYear()).round(1).examType(ExamType.WRITTEN)
                .regStartAt(regStart).regEndAt(regEnd).examStartDate(examStart).examEndDate(examEnd)
                .provenance(ScheduleProvenance.MANUAL)
                .build());
    }

    /** 관심 등록한 뒤 그 시험의 카드를 돌려준다. */
    private JsonNode favoriteCard(Member m, Certificate cert) throws Exception {
        mockMvc.perform(post("/api/me/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"certificateId\": " + cert.getId() + "}"))
                .andExpect(status().isCreated());
        return cardOf(m, cert);
    }

    private JsonNode cardOf(Member m, Certificate cert) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/me/favorites").header(HttpHeaders.AUTHORIZATION, bearer(m)))
                .andExpect(status().isOk()).andReturn();
        for (JsonNode it : objectMapper.readTree(
                res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items")) {
            if (it.path("certificateId").asLong() == cert.getId()) {
                return it;
            }
        }
        throw new AssertionError("관심 등록한 시험이 카드 목록에 없다: " + cert.getName());
    }

    @Test
    @DisplayName("시험 기간 중이면 카드는 '시험 진행 중'(D-0)이다 — 종료일까지")
    void card_shows_exam_ongoing_until_end_date() throws Exception {
        LocalDate today = TimeUtil.today();
        Certificate cert = newCertificate("진행중시험");
        newSchedule(cert, null, null, today.minusDays(1), today.plusDays(1));

        JsonNode card = favoriteCard(newMember(), cert);
        assertEquals("EXAM_ONGOING", card.path("badge").asText(), "시험 종료일 전인데 진행 중이 아니다: " + card);
        assertEquals("시험 진행 중", card.path("badgeLabel").asText());
        assertEquals(0, card.path("dday").asInt(-1));
        assertEquals("UPCOMING", card.path("scheduleState").asText());
        assertTrue(card.path("eventLabel").asText().contains("진행 중"), card.path("eventLabel").asText());
        assertEquals(TimeUtil.format(today.plusDays(1).atStartOfDay()), card.path("eventAt").asText());
    }

    @Test
    @DisplayName("시험 당일도 '진행 중'이다")
    void exam_day_itself_is_ongoing() throws Exception {
        LocalDate today = TimeUtil.today();
        Certificate cert = newCertificate("당일시험");
        newSchedule(cert, null, null, today, today);

        JsonNode card = favoriteCard(newMember(), cert);
        assertEquals("EXAM_ONGOING", card.path("badge").asText());
        assertEquals(0, card.path("dday").asInt(-1));
    }

    @Test
    @DisplayName("접수 마감일을 몰라도 시작일이 앞에 있으면 '접수 예정'이다 — 알림은 가는데 카드엔 안 보이던 것")
    void registration_upcoming_without_end_date() throws Exception {
        LocalDate today = TimeUtil.today();
        Certificate cert = newCertificate("마감미상시험");
        newSchedule(cert, today.plusDays(3).atTime(9, 0), null, null, null);

        JsonNode card = favoriteCard(newMember(), cert);
        assertEquals("REG_UPCOMING", card.path("badge").asText(), card.toString());
        assertEquals(3, card.path("dday").asInt(-1));
    }

    @Test
    @DisplayName("이벤트가 없으면 dday 는 0 이 아니라 null 이고, 지난 일정만 있으면 마지막 시험일을 준다")
    void card_without_event_has_null_dday_and_last_exam_date() throws Exception {
        LocalDate today = TimeUtil.today();
        Certificate none = newCertificate("일정없음");
        Certificate past = newCertificate("지난일정만");
        newSchedule(past, null, null, today.minusDays(10), today.minusDays(9));
        Member m = newMember();

        JsonNode noneCard = favoriteCard(m, none);
        assertTrue(noneCard.path("dday").isNull(), "이벤트가 없는데 dday 가 " + noneCard.path("dday"));
        assertEquals("NONE", noneCard.path("badge").asText());
        assertEquals("NONE", noneCard.path("scheduleState").asText());
        assertTrue(noneCard.path("lastExamDate").isNull());

        JsonNode pastCard = favoriteCard(m, past);
        assertTrue(pastCard.path("dday").isNull(), "지난 일정뿐인데 dday 가 " + pastCard.path("dday"));
        assertEquals("PAST_ONLY", pastCard.path("scheduleState").asText());
        assertEquals(today.minusDays(10).toString(), pastCard.path("lastExamDate").asText());
    }

    @Test
    @DisplayName("날짜 없는 회차만 있으면 카드도 '일정 없음'이다 — 시험 찾기 카드와 같은 기준")
    void undated_round_does_not_count_on_card() throws Exception {
        Certificate cert = newCertificate("빈회차");
        newSchedule(cert, null, null, null, null);

        JsonNode card = favoriteCard(newMember(), cert);
        assertEquals("NONE", card.path("scheduleState").asText());
        assertTrue(card.path("dday").isNull());
    }

    // ===== 폐지·개칭 시험 =====

    @Test
    @DisplayName("폐지·개칭된 시험은 관심 등록을 거절한다 → 400")
    void hidden_exam_cannot_be_favorited() throws Exception {
        Certificate cert = newCertificate("폐지시험");
        cert.markLifecycle(CertificateLifecycle.ABOLISHED, null, "테스트");
        certificateRepository.save(cert);

        mockMvc.perform(post("/api/me/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"certificateId\": " + cert.getId() + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("등록해 둔 시험이 개칭되면 카드에 새 이름이 붙고 D-day 는 사라지며 캘린더에서 빠진다")
    void hidden_exam_card_carries_reason_and_leaves_calendar() throws Exception {
        LocalDate today = TimeUtil.today();
        Certificate cert = newCertificate("옛이름시험");
        newSchedule(cert, null, null, today.plusDays(5), today.plusDays(5));
        Member m = newMember();
        assertTrue(favoriteCard(m, cert).path("hiddenReason").isNull());

        cert.markLifecycle(CertificateLifecycle.RENAMED, "새이름시험", "테스트");
        certificateRepository.save(cert);

        JsonNode card = cardOf(m, cert);   // 카드는 남는다 — 사라지면 해제할 길이 없다
        assertTrue(card.path("hiddenReason").asText("").contains("새이름시험"), "새 이름을 안내하지 않는다: " + card);
        assertTrue(card.path("dday").isNull(), "숨긴 시험에 D-day 가 남아 있다");
        assertEquals("NONE", card.path("badge").asText());

        LocalDate examDay = today.plusDays(5);
        MvcResult cal = mockMvc.perform(get("/api/me/calendar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(m))
                        .param("year", String.valueOf(examDay.getYear()))
                        .param("month", String.valueOf(examDay.getMonthValue())))
                .andExpect(status().isOk()).andReturn();
        for (JsonNode ev : objectMapper.readTree(
                cal.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("events")) {
            assertNotEquals(cert.getId(), ev.path("certificateId").asLong(), "개칭된 시험의 일정이 캘린더에 남아 있다");
        }
    }

    // ===== 탈퇴 =====

    @Test
    @DisplayName("탈퇴하면 관심 등록이 지워지고 시험의 관심 수도 줄어든다")
    void withdraw_removes_favorites_and_decrements_count() throws Exception {
        Certificate cert = newCertificate("탈퇴시험");
        Member m = newMember();
        favoriteCard(m, cert);
        assertEquals(1, certificateRepository.findById(cert.getId()).orElseThrow().getFavoriteCount());

        mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(m)))
                .andExpect(status().isNoContent());

        assertFalse(userFavoriteRepository.existsByMemberIdAndCertificateId(m.getId(), cert.getId()),
                "탈퇴했는데 관심 등록이 남아 있다");
        assertEquals(0, certificateRepository.findById(cert.getId()).orElseThrow().getFavoriteCount(),
                "관심 수가 안 줄었다");
        assertNotNull(memberRepository.findById(m.getId()).orElseThrow());
    }
}

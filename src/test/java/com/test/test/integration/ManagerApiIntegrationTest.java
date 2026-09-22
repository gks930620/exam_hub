package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.exam.admin.DataSourceCatalog;
import com.test.test.exam.collect.CollectedSchedule;
import com.test.test.exam.collect.DiffService;
import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.NotificationEventType;
import com.test.test.exam.domain.NotificationSchedule;
import com.test.test.exam.domain.NotificationScheduleStatus;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.domain.Series;
import com.test.test.exam.domain.AuthProvider;
import com.test.test.exam.domain.Member;
import com.test.test.exam.notification.NotificationContentFactory;
import com.test.test.exam.notification.NotificationMessage;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import com.test.test.exam.repository.MemberRepository;
import com.test.test.exam.repository.NotificationScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 매니저(운영자) 계정과 권한 경계 (사용자 결정 2026-08-10).
 *
 * <p>매니저는 시험 일정을 손으로 고칠 수 있는 계정이다. 그래서 두 가지를 못 박는다:
 * <ul>
 *   <li><b>운영 계정은 소셜에 묶이지 않는다</b> — 카카오가 막히면 운영이 멈추면 안 된다</li>
 *   <li><b>일반 회원은 운영 API 에 닿을 수 없다</b> — 로그인했다는 것만으로는 부족하다</li>
 * </ul>
 */
class ManagerApiIntegrationTest extends ApiIntegrationTestSupport {

    private static final String USERNAME = "test-manager";
    private static final String PASSWORD = "test-manager-password";

    @Autowired
    private MemberRepository memberRepositoryForManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DiffService diffService;

    @BeforeEach
    void createManager() {
        memberRepositoryForManager.findByProviderAndProviderId(AuthProvider.LOCAL, USERNAME)
                .ifPresentOrElse(
                        m -> m.changePasswordHash(passwordEncoder.encode(PASSWORD)),
                        () -> memberRepositoryForManager.save(
                                Member.manager(USERNAME, passwordEncoder.encode(PASSWORD), "테스트매니저")));
    }

    private long anyCertificateId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/certificates/popular"))
                .andExpect(status().isOk()).andReturn();
        JsonNode items = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
        return items.get(0).path("id").asLong();
    }

    private String loginBody(String username, String password) {
        return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
    }

    // ===== 로그인 =====

    @Test
    @DisplayName("아이디·비밀번호가 맞으면 토큰을 준다")
    void manager_login_returns_token() throws Exception {
        mockMvc.perform(post("/api/manager/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    /** 비밀번호가 틀렸는지 아이디가 없는지 <b>구분해 주지 않는다</b> — 아이디부터 맞춰볼 수 있게 된다. */
    @Test
    @DisplayName("비밀번호가 틀리면 401")
    void wrong_password_returns_401() throws Exception {
        mockMvc.perform(post("/api/manager/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(USERNAME, "틀린비밀번호")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("없는 아이디도 똑같이 401")
    void unknown_username_returns_401() throws Exception {
        mockMvc.perform(post("/api/manager/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("없는사람", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("빈 값은 400")
    void blank_credentials_return_400() throws Exception {
        mockMvc.perform(post("/api/manager/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("", "")))
                .andExpect(status().isBadRequest());
    }

    /**
     * 소셜 계정은 비밀번호 해시가 없어 폼 로그인을 통과할 수 없다.
     * 이게 뚫리면 카카오로 가입한 사람이 매니저 창으로 들어올 수 있게 된다.
     */
    @Test
    @DisplayName("소셜 계정은 폼 로그인을 통과할 수 없다")
    void social_member_cannot_use_form_login() {
        Member social = newMember();

        assertFalse(social.canLoginWithPassword(), "소셜 계정에 비밀번호 로그인이 열려 있다");
        assertTrue(memberRepositoryForManager
                .findByProviderAndProviderId(AuthProvider.LOCAL, USERNAME).orElseThrow()
                .canLoginWithPassword());
    }

    // ===== 권한 경계 =====

    @Test
    @DisplayName("비로그인은 운영 API 에 못 들어간다 → 401")
    void admin_api_without_login_returns_401() throws Exception {
        mockMvc.perform(get("/api/admin/data-map"))
                .andExpect(status().isUnauthorized());
    }

    /** 핵심 — <b>로그인했다는 것만으로는 부족하다.</b> 일반 회원은 403 이어야 한다. */
    @Test
    @DisplayName("일반 회원은 운영 API 에서 403")
    void admin_api_with_normal_member_returns_403() throws Exception {
        mockMvc.perform(get("/api/admin/data-map")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("매니저는 운영 API 를 쓴다")
    void admin_api_with_admin_returns_200() throws Exception {
        mockMvc.perform(get("/api/admin/data-map")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk());
    }


    // ===== 종목 지정 재수집 =====
    //
    // 전량 수집(613콜) 중 일시 오류로 몇 종목이 비는 일이 실제로 있었다(산업안전기사가 빠짐).
    // 다음 05:00 을 기다리는 대신, 매니저가 빈 종목만 골라 다시 받아온다.

    @Test
    @DisplayName("종목을 지정해 다시 수집한다")
    void collect_by_codes() throws Exception {
        mockMvc.perform(post("/api/admin/collect")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceCodes\": [\"1320\", \"7910\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(2));
    }

    @Test
    @DisplayName("종목 없이 부르면 400 — 실수로 전량이 도는 것을 막는다")
    void collect_without_codes_returns_400() throws Exception {
        mockMvc.perform(post("/api/admin/collect")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceCodes\": []}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("일반 회원은 재수집을 못 부른다 → 403")
    void collect_denies_normal_member() throws Exception {
        mockMvc.perform(post("/api/admin/collect")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceCodes\": [\"1320\"]}"))
                .andExpect(status().isForbidden());
    }

    // ===== 시험 일정 현황 (여러 시험을 한 화면에서) =====
    //
    // 수기 입력 화면만 있으면 매니저는 "무엇이 비어 있는지" 알 수 없다. 480종을 하나씩
    // 검색해 볼 수는 없으니 결국 기억나는 시험만 채우게 된다. 그래서 급한 것부터 세운다.

    @Test
    @DisplayName("상시 시험은 사용자 응답에도 rolling 으로 표시된다")
    void rolling_flag_reaches_public_api() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/certificates/browse")
                        .param("query", "AWS Certified Cloud"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = objectMapper.readTree(
                res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
        assertTrue(items.size() > 0, "AWS 시험을 못 찾았다");
        assertTrue(items.get(0).path("rolling").asBoolean(false),
                "상시 표시가 공개 API 에 안 실렸다 — 화면이 '일정 미정'이라는 거짓말을 하게 된다");
    }

    /** 위 요약 막대도 같은 셈법이어야 한다 — 수기 + 공고 전 + 크롤링 예정 = 일정 없음. */
    @Test
    @DisplayName("데이터 지도의 채움률에도 이유별 개수가 있고 합이 맞는다")
    void coverage_breaks_down_missing_by_reason() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/admin/data-map")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode c = objectMapper.readTree(
                res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("coverage");
        long without = c.path("withoutSchedule").asLong();
        long sum = c.path("manualNeeded").asLong() + c.path("announcementPending").asLong() + c.path("crawlPlanned").asLong();
        assertEquals(without, sum, "이유별 합이 일정 없음과 다르다");
    }

    // ── 행동 중심 현황 (2026-09-02 재설계) ──
    // "일정 있음/없음"으로는 수기 시험의 회차가 지나 다음 회차를 넣어야 하는 상황이 안 보였다.
    // 서버가 시험마다 행동을 판정한다: 첫 일정 입력 / 다음 회차 입력 / 시행처 확인 / 수집 점검.

    @Test
    @DisplayName("현황은 할 일·대기·정상·상시 개수와 목록을 준다")
    void overview_returns_buckets() throws Exception {
        mockMvc.perform(get("/api/admin/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.bucketCounts").exists())
                .andExpect(jsonPath("$.actionCounts").exists())
                .andExpect(jsonPath("$.waitingCounts").exists());
    }

    /** 할 일 탭의 모든 행에는 행동이 있고, 행동별 합이 할 일 전체와 같다 — 요약과 목록이 따로 놀면 안 된다. */
    @Test
    @DisplayName("할 일에는 행동이 붙고 행동별 합이 맞는다")
    void todo_rows_carry_actions() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/admin/overview")
                        .param("bucket", "TODO").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
        java.util.Set<String> allowed = java.util.Set.of("FIRST_INPUT", "REVIEW_MOVE", "NEXT_ROUND", "VERIFY", "CHECK_SOURCE");
        assertTrue(body.path("items").size() > 0, "할 일이 하나도 없다 — 마스터에 수기 시험이 없나?");
        for (JsonNode it : body.path("items")) {
            assertEquals("TODO", it.path("bucket").asText());
            assertTrue(allowed.contains(it.path("action").asText("")), "행동이 없거나 모르는 값: " + it);
            assertFalse(it.path("actionLabel").asText("").isBlank());
        }
        long sum = 0;
        for (JsonNode n : body.path("actionCounts")) sum += n.asLong();
        assertEquals(body.path("totalElements").asLong(), sum, "행동별 합이 할 일 전체와 다르다");
    }

    /** 수기 대상인데 일정이 없으면 '첫 일정 입력' — 매니저가 가장 먼저 볼 것. */
    @Test
    @DisplayName("수기 대상 + 일정 없음 = 첫 일정 입력")
    void manual_without_schedule_is_first_input() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/admin/overview")
                        .param("bucket", "TODO").param("action", "FIRST_INPUT").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
        assertTrue(items.size() > 0, "첫 일정 입력이 하나도 없다");
        for (JsonNode it : items) {
            assertEquals("FIRST_INPUT", it.path("action").asText());
            assertEquals("MANUAL", it.path("source").asText(), "수기 대상이 아닌데 첫 입력을 시킨다: " + it.path("certificateName").asText());
            assertEquals("NONE", it.path("freshness").asText());
        }
    }

    /** 대기는 사람이 손댈 게 없는 것 — 행동이 없고, 왜 기다리면 되는지 이유가 붙는다. */
    @Test
    @DisplayName("대기에는 행동이 없고 기다리는 이유가 있다")
    void waiting_rows_are_automatic() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/admin/overview")
                        .param("bucket", "WAITING").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
        assertTrue(items.size() > 0, "대기가 하나도 없다 — 큐넷 마스터가 안 실렸나?");
        for (JsonNode it : items) {
            assertTrue(it.path("action").isNull(), "대기인데 행동이 있다: " + it.path("certificateName").asText());
            assertFalse(it.path("waitingLabel").asText("").isBlank(), "왜 기다리면 되는지가 없다");
            String src = it.path("source").asText();
            assertTrue(src.equals("AUTO") || src.equals("CRAWL_PLANNED"), "대기인데 출처가 " + src);
        }
    }

    /** 정상은 앞으로의 일정이 있는 것 — 사용자에게 D-day 가 간다. */
    @Test
    @DisplayName("정상은 앞으로의 일정을 갖는다")
    void ok_rows_have_upcoming_event() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/admin/overview")
                        .param("bucket", "OK").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
        for (JsonNode it : items) {
            assertEquals("UPCOMING", it.path("freshness").asText());
            assertFalse(it.path("nextLabel").asText("").isBlank(), "정상인데 다음 일정이 없다: " + it.path("certificateName").asText());
        }
    }

    /** 상시·예약제는 할 일에 섞이면 영원히 못 끝내는 숙제가 된다. */
    @Test
    @DisplayName("상시는 할 일에 없고 상시 탭에 있다")
    void rolling_is_its_own_bucket() throws Exception {
        MvcResult todo = mockMvc.perform(get("/api/admin/overview")
                        .param("bucket", "TODO").param("query", "AWS Certified Cloud").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk()).andReturn();
        for (JsonNode it : objectMapper.readTree(todo.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items")) {
            assertNotEquals("AWS Certified Cloud Practitioner", it.path("certificateName").asText(), "상시가 할 일에 있다");
        }
        MvcResult body = mockMvc.perform(get("/api/admin/overview").param("bucket", "ROLLING")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk()).andReturn();
        assertTrue(objectMapper.readTree(body.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("bucketCounts").path("ROLLING").asLong(0) >= 20, "상시 표시가 안 붙었다");
    }

    /**
     * 연도·회차만 넣고 날짜를 하나도 안 넣은 회차는 일정이 아니다 — 사용자에게 아무것도 못 알려 준다.
     * 실측(BJT, 2026-09-02)에서 이런 시험이 "다음 회차 입력"에 마지막 회차도 없이 걸렸다. 첫 일정 입력이 맞고,
     * 사용자 카드도 "일정 미정"이어야 한다. 매니저 화면과 사용자 화면이 같은 기준을 써야 숫자가 맞는다.
     */
    @Test
    @DisplayName("날짜 없는 회차만 있으면 여전히 '첫 일정 입력'이고 사용자에겐 '일정 미정'이다")
    void undated_round_is_not_a_schedule() throws Exception {
        String token = bearer(newAdmin());
        MvcResult first = mockMvc.perform(get("/api/admin/overview")
                        .param("bucket", "TODO").param("action", "FIRST_INPUT").param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andReturn();
        JsonNode target = objectMapper.readTree(first.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items").get(0);
        long certId = target.path("certificateId").asLong();
        String name = target.path("certificateName").asText();

        // 날짜 없이 연도·회차·구분만 있는 행 — 수기 입력 API 는 이제 이런 행을 거절하므로(날짜 최소 1개)
        // 수집이 만들어 둔 상황을 저장소로 흉내 낸다
        examScheduleRepository.save(ExamSchedule.builder()
                .certificate(certificateRepository.findById(certId).orElseThrow())
                .year(2099).round(1).examType(ExamType.WRITTEN)
                .provenance(ScheduleProvenance.SCRAPED)
                .build());

        MvcResult after = mockMvc.perform(get("/api/admin/overview")
                        .param("bucket", "TODO").param("query", name).param("size", "50")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andReturn();
        JsonNode row = null;
        for (JsonNode it : objectMapper.readTree(after.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items")) {
            if (it.path("certificateId").asLong() == certId) row = it;
        }
        assertNotNull(row, "날짜 없는 회차를 넣었더니 할 일에서 사라졌다");
        assertEquals("FIRST_INPUT", row.path("action").asText(), "날짜 없는 회차가 일정으로 세어졌다");
        assertEquals("NONE", row.path("freshness").asText());
        assertEquals(1, row.path("scheduleCount").asInt(), "회차 행 자체는 보여야 모달에서 지울 수 있다");

        MvcResult pub = mockMvc.perform(get("/api/certificates/browse").param("query", name).param("size", "50"))
                .andExpect(status().isOk()).andReturn();
        for (JsonNode it : objectMapper.readTree(pub.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items")) {
            if (it.path("id").asLong() == certId) {
                assertEquals("NONE", it.path("scheduleState").asText(), "사용자 카드가 날짜 없는 회차를 일정으로 안다");
                assertFalse(it.path("hasSchedule").asBoolean(true));
            }
        }
    }

    /**
     * 화면 위(채움률)와 아래(할 일)는 같은 API 가 아니다 — 셈법이 다르면 "일정 없음 142 인데 첫 일정 입력 63" 같은
     * 어긋남이 생긴다(실측 2026-09-02). 수기로 채워야 할 수 = 첫 일정 입력 수, 있음+없음 = 전체 — 이게 깨지면 실패.
     */
    @Test
    @DisplayName("채움률과 할 일이 같은 숫자를 말한다")
    void coverage_and_todo_agree() throws Exception {
        String token = bearer(newAdmin());
        JsonNode cov = objectMapper.readTree(mockMvc.perform(get("/api/admin/data-map")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("coverage");
        JsonNode ov = objectMapper.readTree(mockMvc.perform(get("/api/admin/overview")
                        .param("bucket", "TODO").param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertEquals(cov.path("totalExams").asLong(), cov.path("withSchedule").asLong() + cov.path("withoutSchedule").asLong(),
                "있음 + 없음 이 전체와 다르다");
        assertEquals(cov.path("manualNeeded").asLong(), ov.path("actionCounts").path("FIRST_INPUT").asLong(0),
                "채움률의 '수기로 넣어야 하는 수'와 할 일의 '첫 일정 입력' 수가 다르다");
        assertEquals(cov.path("rolling").asLong(), ov.path("bucketCounts").path("ROLLING").asLong(0),
                "상시 수가 위아래 다르다");
        long buckets = 0;
        for (JsonNode n : ov.path("bucketCounts")) buckets += n.asLong();
        assertEquals(cov.path("totalExams").asLong() + cov.path("rolling").asLong(), buckets,
                "네 탭의 합이 공개 시험 수와 다르다");
    }

    @Test
    @DisplayName("시험명으로 좁힌다")
    void overview_filters_by_name() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/admin/overview")
                        .param("query", "기사")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = objectMapper.readTree(
                res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
        for (JsonNode it : items) {
            assertTrue(it.path("certificateName").asText().contains("기사"),
                    "검색어와 무관한 시험이 섞였다: " + it.path("certificateName").asText());
        }
    }

    @Test
    @DisplayName("일반 회원은 현황을 못 본다 → 403")
    void overview_denies_normal_member() throws Exception {
        mockMvc.perform(get("/api/admin/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember())))
                .andExpect(status().isForbidden());
    }



    /**
     * <b>같은 시험이 둘로 늘어나지 않는다</b> — 이 프로젝트에서 세 번 반복된 사고다.
     *
     * <p>같은 시험이 시드마다 다른 종목코드를 갖는 일이 잦다(정보보안기사는 마스터가 M0002,
     * 비큐넷 시드가 KCA-SEC). 수집이 코드로만 찾고 없으면 새로 만들던 탓에
     * <b>토익 10개·유통관리사 2개·정보보안기사 2개</b>가 생겼다.
     * 사용자가 "토익"을 검색하면 같은 시험이 열 줄 떴다.
     */
    @Test
    @DisplayName("이름이 같으면 코드가 달라도 시험을 새로 만들지 않는다")
    void does_not_duplicate_exam_when_source_code_differs() throws Exception {
        MvcResult before = mockMvc.perform(get("/api/certificates/browse")
                        .param("query", "정보처리기사").param("size", "50"))
                .andExpect(status().isOk()).andReturn();
        long countBefore = countNamed(before, "정보처리기사");
        assertTrue(countBefore >= 1, "기준이 될 시험이 없다 — 시드가 바뀌었나?");

        // 같은 이름인데 종목코드만 다른 일정이 들어온다(다른 시행처 스크래퍼가 붙는 상황)
        diffService.upsert(new CollectedSchedule(
                "AGENCY-X-9999", "정보처리기사", Series.TECHNICIAN, "다른시행처", "국가기술자격-정보통신",
                2027, 9, ExamType.WRITTEN,
                LocalDateTime.of(2027, 1, 5, 10, 0), LocalDateTime.of(2027, 1, 9, 18, 0),
                LocalDate.of(2027, 2, 6), LocalDate.of(2027, 2, 6), LocalDate.of(2027, 3, 5),
                "https://example.test/", ScheduleProvenance.SCRAPED));

        MvcResult after = mockMvc.perform(get("/api/certificates/browse")
                        .param("query", "정보처리기사").param("size", "50"))
                .andExpect(status().isOk()).andReturn();

        assertEquals(countBefore, countNamed(after, "정보처리기사"),
                "종목코드가 다르다는 이유로 같은 이름의 시험이 하나 더 생겼다");
    }

    private long countNamed(MvcResult result, String name) throws Exception {
        JsonNode items = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
        long n = 0;
        for (JsonNode it : items) {
            if (name.equals(it.path("name").asText())) {
                n++;
            }
        }
        return n;
    }


    /**
     * 매니저 입력 흐름 ②단계 — <b>이미 등록된 일정 보기</b>.
     *
     * <p>이게 500 이면 매니저는 "지금 뭐가 들어 있는지" 모른 채 입력해야 한다(2026-08-28 실제 장애).
     * 원인은 {@code ExamSchedule.certificate} 지연 로딩을 트랜잭션 밖에서 건드린 것 —
     * {@code open-in-view=false} 라 그 순간 {@code LazyInitializationException} 이 난다.
     */
    @Test
    @DisplayName("등록된 일정 조회가 지연 로딩으로 깨지지 않는다")
    void admin_schedule_list_does_not_break_on_lazy_certificate() throws Exception {
        long certId = anyCertificateId();

        mockMvc.perform(get("/api/admin/schedules")
                        .param("certificateId", String.valueOf(certId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /** 일정이 실제로 있는 시험으로도 확인한다 — 빈 목록은 지연 로딩을 건드리지 않아 통과해 버린다. */
    @Test
    @DisplayName("일정이 있는 시험도 시험명까지 내려준다")
    void admin_schedule_list_includes_certificate_name() throws Exception {
        long certId = anyCertificateId();

        mockMvc.perform(post("/api/admin/schedules")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"certificateId": %d, "year": 2027, "round": 1, "examType": "WRITTEN",
                                 "examStartDate": "2027-03-01"}
                                """.formatted(certId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/schedules")
                        .param("certificateId", String.valueOf(certId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].certificateName").isNotEmpty());
    }


    /**
     * 큐넷 일정 스냅샷 내보내기 — 로컬 인메모리 DB 의 수집 결과를 시드로 떠 둔다.
     *
     * <p>서버를 끄면 1,500여 건이 사라지는데, 다시 받으려면 613콜이라 하루 한도(1,000)를 넘긴다.
     * 한 번 받은 걸 파일로 두고 다음부터 공짜로 얹기 위한 것이다.
     */
    @Test
    @DisplayName("일정을 시드 형식으로 내보낸다 — 어느 소스 것이든")
    void exports_schedules_as_seed() throws Exception {
        mockMvc.perform(get("/api/admin/export/schedules")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collectedAt").isNotEmpty())
                .andExpect(jsonPath("$.exams").isArray())
                // 출처를 함께 뜬다 — 스크래핑을 API 로 되살리면 '시행처 확인 필요' 배지가 사라진다
                .andExpect(jsonPath("$.exams[0].schedules[0].provenance").isNotEmpty())
                .andExpect(jsonPath("$._howToRegenerate").isNotEmpty());
    }

    /** 내보내기는 DB 를 통째로 뜨는 것이라 매니저만 할 수 있어야 한다. */
    @Test
    @DisplayName("일반 회원은 내보내기를 못 한다 → 403")
    void export_denies_normal_member() throws Exception {
        mockMvc.perform(get("/api/admin/export/schedules")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newMember())))
                .andExpect(status().isForbidden());
    }

    // ===== 시험 변천사 =====


    //
    // 폐지된 시험을 지우면 "있었다는 사실"까지 사라진다. 사용자 화면에서만 빼고 기록은 남긴다.

    @Test
    @DisplayName("폐지·개칭 기록을 근거와 함께 준다")
    void lifecycle_history_has_reasons() throws Exception {
        mockMvc.perform(get("/api/admin/lifecycle")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isNotEmpty())
                .andExpect(jsonPath("$.items[0].lifecycleLabel").isNotEmpty())
                .andExpect(jsonPath("$.items[0].note").isNotEmpty())
                .andExpect(jsonPath("$.needsCheck").isNumber());
    }

    /**
     * 폐지·개칭된 시험이 사용자 검색에 뜨면 오지 않을 접수를 기다리게 된다.
     * 반대로 매니저 기록에서까지 사라지면 "왜 없어졌냐"에 답할 수 없다 — 둘 다 확인한다.
     */
    @Test
    @DisplayName("개칭된 시험은 검색에서 빠지지만 기록에는 남는다")
    void renamed_exam_hidden_from_search_but_kept_in_history() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/admin/lifecycle")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk()).andReturn();
        JsonNode items = objectMapper.readTree(
                res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");

        String retiredName = null;
        for (JsonNode it : items) {
            if ("RENAMED".equals(it.path("lifecycle").asText())) {
                retiredName = it.path("name").asText();
                break;
            }
        }
        assertNotNull(retiredName, "개칭 기록이 하나도 없다 — 시드가 바뀌었나?");

        MvcResult browse = mockMvc.perform(get("/api/certificates/browse")
                        .param("query", retiredName).param("size", "50"))
                .andExpect(status().isOk()).andReturn();
        JsonNode found = objectMapper.readTree(
                browse.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");

        for (JsonNode it : found) {
            assertNotEquals(retiredName, it.path("name").asText(),
                    "폐지·개칭된 시험이 사용자 검색에 그대로 뜬다: " + retiredName);
        }
    }

    /** 큐넷 613종이 실제로 적재됐는지 — 종목코드(jmCd)가 있어야 일정이 이름 없이 붙는다. */
    @Test
    @DisplayName("큐넷 종목이 종목코드와 함께 들어와 있다")
    void qnet_master_is_loaded_with_source_code() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/certificates/browse")
                        .param("query", "정보처리기사").param("size", "10"))
                .andExpect(status().isOk()).andReturn();
        JsonNode items = objectMapper.readTree(
                res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
        assertTrue(items.size() > 0, "큐넷 대표 종목이 마스터에 없다");
    }

    // ===== 데이터 지도 =====


    /**
     * 매니저가 "무엇을 넣어야 하나"를 알려면 <b>원본 사이트 주소</b>가 있어야 한다.
     * 이게 비면 화면의 "원본 사이트 열기" 버튼이 아무 데도 안 간다.
     */
    @Test
    @DisplayName("데이터 지도는 자동·수기 구분과 원본 사이트를 준다")
    void data_map_has_sources_and_links() throws Exception {
        mockMvc.perform(get("/api/admin/data-map")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage.totalExams").isNumber())
                .andExpect(jsonPath("$.coverage.withoutSchedule").isNumber())
                .andExpect(jsonPath("$.sources").isNotEmpty())
                .andExpect(jsonPath("$.sources[0].sourceUrl").isNotEmpty())
                .andExpect(jsonPath("$.sources[0].modeLabel").isNotEmpty());
    }

    /**
     * 출처 목록은 손으로 관리하는 표라 <b>오타 하나가 화면에서 죽은 링크</b>가 된다.
     * 매니저가 "원본 사이트 열기"를 눌렀는데 안 열리면 그 자리에서 일이 막힌다.
     */
    @Test
    @DisplayName("모든 출처가 열 수 있는 주소와 안내를 갖는다")
    void every_source_is_usable() {
        List<DataSourceCatalog.Entry> entries = DataSourceCatalog.entries();

        assertFalse(entries.isEmpty(), "출처 목록이 비었다");
        for (DataSourceCatalog.Entry e : entries) {
            assertTrue(e.getSourceUrl().startsWith("https://"),
                    e.getGroup() + " 의 원본 주소가 https 가 아니다: " + e.getSourceUrl());
            assertFalse(e.getGroup().isBlank(), "묶음 이름이 비었다");
            assertFalse(e.getExams().isBlank(), e.getGroup() + " 의 시험 목록이 비었다");
            assertFalse(e.getSourceName().isBlank(), e.getGroup() + " 의 시행처가 비었다");
        }

        // 수기 항목이 하나도 없으면 이 화면을 만든 이유가 없다
        assertTrue(entries.stream().anyMatch(e -> e.getMode() == DataSourceCatalog.Mode.MANUAL),
                "수기 입력 대상이 하나도 없다");
    }

    @Test
    @DisplayName("매니저 계정 설정 여부는 비로그인도 물어볼 수 있다 (로그인 화면 안내용)")

    void availability_is_public() throws Exception {
        mockMvc.perform(get("/api/manager/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
    }

    // ═════════════════════════════════════════════════════════════════════
    // 아래는 2026-09-03 결함 수정분 — 로그인 시도 제한 · 입력 검증 · 취소/변경 알림 ·
    // 수집이 수기 행을 건드리지 않는 것 · '자동'의 근거
    // ═════════════════════════════════════════════════════════════════════

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private ExamScheduleRepository examScheduleRepository;

    @Autowired
    private NotificationScheduleRepository notificationScheduleRepository;

    @Autowired
    private NotificationContentFactory contentFactory;

    private Certificate newCertificate(String name, String agency, String codePrefix) {
        return newCertificate(name, agency, codePrefix, Series.ETC);
    }

    private Certificate newCertificate(String name, String agency, String codePrefix, Series series) {
        String unique = UUID.randomUUID().toString().substring(0, 6);
        return certificateRepository.save(Certificate.builder()
                .name(name + unique).slug(name + "-" + unique)
                .series(series).agency(agency).category("테스트")
                .sourceCode(codePrefix == null ? null : codePrefix + "-" + unique)
                .build());
    }

    private String upsertBody(long certId, int year, int round, String examStart) {
        return "{\"certificateId\":%d,\"year\":%d,\"round\":%d,\"examType\":\"WRITTEN\",\"examStartDate\":\"%s\"}"
                .formatted(certId, year, round, examStart);
    }

    private long upsert(String token, String body) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/admin/schedules")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("id").asLong();
    }

    private void expectUpsert400(String token, String body) throws Exception {
        mockMvc.perform(post("/api/admin/schedules")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    private JsonNode overviewRow(String token, Certificate cert) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/admin/overview")
                        .param("query", cert.getName()).param("size", "50")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andReturn();
        for (JsonNode it : objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items")) {
            if (it.path("certificateId").asLong() == cert.getId()) {
                return it;
            }
        }
        throw new AssertionError("현황에 시험이 없다: " + cert.getName());
    }

    private List<NotificationSchedule> notificationsOf(long scheduleId) {
        return notificationScheduleRepository.findByExamSchedule(
                examScheduleRepository.findById(scheduleId).orElseThrow());
    }

    private boolean hasPendingChangeAlert(long scheduleId) {
        return notificationsOf(scheduleId).stream().anyMatch(n ->
                n.getEventType() == NotificationEventType.SCHEDULE_CHANGED
                        && n.getStatus() == NotificationScheduleStatus.PENDING);
    }

    private CollectedSchedule collected(Certificate cert, Series series, int year, int round,
                                        LocalDate examStart, ScheduleProvenance provenance) {
        return new CollectedSchedule(cert.getSourceCode(), cert.getName(), series, cert.getAgency(), cert.getCategory(),
                year, round, ExamType.WRITTEN, null, null, examStart, examStart, null,
                "https://example.test/", provenance);
    }

    // ===== 로그인 시도 제한 =====
    //
    // 운영 계정은 하나뿐이고 아이디가 뻔하다. 비밀번호 대입을 무한정 받아주면 안 된다.

    private void failLogin(String username) throws Exception {
        mockMvc.perform(post("/api/manager/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, "틀린비밀번호")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("같은 아이디로 5회 넘게 틀리면 429 — 기존 에러 JSON 형식 그대로")
    void login_is_locked_after_five_failures() throws Exception {
        String username = "locked-" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 5; i++) {
            failLogin(username);
        }
        mockMvc.perform(post("/api/manager/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, "틀린비밀번호")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_ATTEMPTS"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("로그인에 성공하면 실패 횟수가 초기화된다")
    void successful_login_resets_failure_counter() throws Exception {
        String username = "reset-" + UUID.randomUUID().toString().substring(0, 8);
        memberRepositoryForManager.save(Member.manager(username, passwordEncoder.encode(PASSWORD), "리셋" + username));

        for (int i = 0; i < 4; i++) {
            failLogin(username);
        }
        mockMvc.perform(post("/api/manager/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, PASSWORD)))
                .andExpect(status().isOk());

        // 초기화됐으니 다시 5회까지는 401 이다 (초기화가 안 됐으면 두 번째부터 429)
        for (int i = 0; i < 5; i++) {
            failLogin(username);
        }
        mockMvc.perform(post("/api/manager/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, PASSWORD)))
                .andExpect(status().isTooManyRequests());
    }

    // ===== 수기 입력 검증 =====

    @Test
    @DisplayName("회차 0 · 연도 범위 밖 · 날짜 없음 · 날짜 순서 위반은 400")
    void upsert_rejects_invalid_input() throws Exception {
        long certId = anyCertificateId();
        String token = bearer(newAdmin());
        String head = "{\"certificateId\":" + certId + ",\"examType\":\"WRITTEN\",";

        expectUpsert400(token, head + "\"year\":2027,\"round\":0,\"examStartDate\":\"2027-03-01\"}");
        expectUpsert400(token, head + "\"year\":1999,\"round\":1,\"examStartDate\":\"1999-03-01\"}");
        expectUpsert400(token, head + "\"year\":2101,\"round\":1,\"examStartDate\":\"2101-03-01\"}");
        expectUpsert400(token, head + "\"year\":2027,\"round\":1}");   // 날짜가 하나도 없다
        expectUpsert400(token, head + "\"year\":2027,\"round\":1,"
                + "\"regEndAt\":\"2027-03-02T18:00\",\"examStartDate\":\"2027-03-01\"}");   // 마감이 시험 다음날
        expectUpsert400(token, head + "\"year\":2027,\"round\":1,"
                + "\"examStartDate\":\"2027-03-01\",\"examEndDate\":\"2027-03-03\",\"resultDate\":\"2027-03-02\"}");   // 발표가 종료 전
        expectUpsert400(token, head + "\"year\":2027,\"round\":1,"
                + "\"examStartDate\":\"2027-03-05\",\"examEndDate\":\"2027-03-03\"}");   // 시작이 종료 뒤
        expectUpsert400(token, head + "\"year\":2027,\"round\":1,"
                + "\"regStartAt\":\"2027-01-10T10:00\",\"regEndAt\":\"2027-01-05T18:00\"}");   // 접수 시작이 마감 뒤
    }

    @Test
    @DisplayName("접수 마감일과 시험일이 같은 날인 것은 허용된다 (당일 접수)")
    void upsert_allows_registration_closing_on_exam_day() throws Exception {
        long certId = anyCertificateId();
        upsert(bearer(newAdmin()), "{\"certificateId\":" + certId + ",\"year\":2028,\"round\":7,\"examType\":\"WRITTEN\","
                + "\"regStartAt\":\"2028-02-01T10:00\",\"regEndAt\":\"2028-03-01T18:00\","
                + "\"examStartDate\":\"2028-03-01\",\"examEndDate\":\"2028-03-01\",\"resultDate\":\"2028-03-01\"}");
    }

    @Test
    @DisplayName("현황의 page 음수·size 0·size 100 초과는 400")
    void overview_rejects_bad_paging() throws Exception {
        String token = bearer(newAdmin());
        mockMvc.perform(get("/api/admin/overview").param("page", "-1").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/overview").param("size", "0").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/overview").param("size", "101").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/overview").param("size", "100").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
    }

    // ===== 일정 이동 확인 (PENDING_REVIEW) =====
    //
    // 수집이 30일 넘게 이동한 회차를 보류하면 사용자에게도 매니저에게도 안 보였다 — 영원히 보류.

    @Test
    @DisplayName("보류(PENDING_REVIEW) 회차가 있으면 '일정 이동 확인' 할 일이 되고, 매니저 목록에 status 와 함께 보인다")
    void pending_review_round_becomes_review_move_action() throws Exception {
        String token = bearer(newAdmin());
        LocalDate today = TimeUtil.today();
        Certificate cert = newCertificate("이동보류", "국사편찬위원회", "REVIEW");
        ExamSchedule pending = examScheduleRepository.save(ExamSchedule.builder()
                .certificate(cert).year(today.getYear()).round(1).examType(ExamType.WRITTEN)
                .examStartDate(today.plusDays(40)).status(ScheduleStatus.PENDING_REVIEW)
                .provenance(ScheduleProvenance.SCRAPED).build());

        JsonNode row = overviewRow(token, cert);
        assertEquals("TODO", row.path("bucket").asText(), row.toString());
        assertEquals("REVIEW_MOVE", row.path("action").asText());
        assertEquals("일정 이동 확인", row.path("actionLabel").asText());

        MvcResult list = mockMvc.perform(get("/api/admin/schedules")
                        .param("certificateId", String.valueOf(cert.getId()))
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andReturn();
        JsonNode rows = objectMapper.readTree(list.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(1, rows.size(), "보류 회차가 매니저 목록에 안 보인다 — 확인할 방법이 없다");
        assertEquals("PENDING_REVIEW", rows.get(0).path("status").asText());

        // 매니저가 확인하고 저장하면 ACTIVE 로 돌아오고 할 일에서 빠진다
        mockMvc.perform(post("/api/admin/schedules")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upsertBody(cert.getId(), today.getYear(), 1, today.plusDays(40).toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(pending.getId()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertNotEquals("REVIEW_MOVE", overviewRow(token, cert).path("action").asText(""));
        // 사용자에게 알리지 못했던 이동이 확정됐으니 변경 알림이 나간다
        assertTrue(hasPendingChangeAlert(pending.getId()), "보류에서 확정됐는데 변경 알림이 없다");
    }

    // ===== 취소 → 알림 =====

    @Test
    @DisplayName("회차를 취소하면 대기 중 알림은 취소되고, 관심 등록자에게 취소 안내 1건이 즉시 예약된다")
    void cancel_cancels_pending_alerts_and_schedules_cancel_notice() throws Exception {
        String token = bearer(newAdmin());
        LocalDate today = TimeUtil.today();
        Certificate cert = newCertificate("취소시험", "국사편찬위원회", "CANCEL");
        long scheduleId = upsert(token, ("{\"certificateId\":%d,\"year\":%d,\"round\":1,\"examType\":\"WRITTEN\","
                + "\"regStartAt\":\"%sT09:00\",\"regEndAt\":\"%sT18:00\",\"examStartDate\":\"%s\"}")
                .formatted(cert.getId(), today.getYear(), today.plusDays(10), today.plusDays(20), today.plusDays(40)));
        assertTrue(notificationsOf(scheduleId).stream().anyMatch(n ->
                        n.getStatus() == NotificationScheduleStatus.PENDING
                                && n.getEventType() != NotificationEventType.SCHEDULE_CHANGED),
                "미래 일정인데 대기 알림이 없다");

        mockMvc.perform(delete("/api/admin/schedules/{id}", scheduleId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        List<NotificationSchedule> after = notificationsOf(scheduleId);
        assertTrue(after.stream().anyMatch(n -> n.getEventType() == NotificationEventType.SCHEDULE_CHANGED),
                "취소 안내가 예약되지 않았다");
        for (NotificationSchedule n : after) {
            if (n.getEventType() == NotificationEventType.SCHEDULE_CHANGED) {
                assertEquals(NotificationScheduleStatus.PENDING, n.getStatus(), "취소 안내가 대기 상태가 아니다");
                assertFalse(n.getSendAt().isAfter(TimeUtil.now()), "취소 안내가 즉시 나가지 않는다");
            } else {
                assertEquals(NotificationScheduleStatus.CANCELED, n.getStatus(),
                        "취소된 회차의 알림이 살아 있다: " + n.getEventType());
            }
        }

        ExamSchedule canceled = examScheduleRepository.findById(scheduleId).orElseThrow();
        assertEquals(ScheduleStatus.CANCELED, canceled.getStatus());
        NotificationMessage msg = contentFactory.build(
                new NotificationContentFactory.NotificationSchedule_Ref(
                        canceled, NotificationEventType.SCHEDULE_CHANGED, true));
        assertTrue(msg.getTitle().contains("취소"), "취소인데 문구가 '변경'이라고만 한다: " + msg.getTitle());
        assertEquals("/cert/" + cert.getId(), msg.getData().get("route"), "알림 링크가 옛 주소다");
    }

    // ===== 수기 저장의 변경 알림 =====

    @Test
    @DisplayName("새 회차를 넣는 것은 '변경'이 아니다 — 날짜가 실제로 바뀔 때만 변경 알림")
    void manual_upsert_raises_change_alert_only_when_dates_change() throws Exception {
        String token = bearer(newAdmin());
        LocalDate today = TimeUtil.today();
        Certificate cert = newCertificate("변경알림", "국사편찬위원회", "CHANGE");
        int year = today.getYear();

        long id = upsert(token, upsertBody(cert.getId(), year, 1, today.plusDays(30).toString()));
        assertFalse(hasPendingChangeAlert(id), "신규 회차인데 변경 알림이 나간다");

        assertEquals(id, upsert(token, upsertBody(cert.getId(), year, 1, today.plusDays(30).toString())));
        assertFalse(hasPendingChangeAlert(id), "같은 값을 다시 저장했는데 변경 알림이 나간다");

        upsert(token, upsertBody(cert.getId(), year, 1, today.plusDays(31).toString()));
        assertTrue(hasPendingChangeAlert(id), "시험일이 바뀌었는데 변경 알림이 없다");
    }

    // ===== 수집이 수기·취소 행을 건드리지 않는다 =====
    //
    // 매니저가 공고를 보고 넣은 값을 배치가 시드·추정치로 덮으면 사람이 한 일이 매일 새벽 사라진다.

    @Test
    @DisplayName("매니저가 넣은 행은 수집이 덮어쓰지 않는다")
    void collection_does_not_overwrite_manual_row() throws Exception {
        String token = bearer(newAdmin());
        LocalDate today = TimeUtil.today();
        Certificate cert = newCertificate("수기보호", "국사편찬위원회", "MANUAL");
        int year = today.getYear();
        long id = upsert(token, upsertBody(cert.getId(), year, 1, today.plusDays(30).toString()));

        DiffService.Outcome outcome = diffService.upsert(
                collected(cert, Series.ETC, year, 1, today.plusDays(45), ScheduleProvenance.API));

        assertEquals(DiffService.DiffType.UNCHANGED, outcome.type(), "수기 행을 수집이 건드렸다");
        ExamSchedule row = examScheduleRepository.findById(id).orElseThrow();
        assertEquals(today.plusDays(30), row.getExamStartDate(), "매니저가 넣은 시험일이 덮어써졌다");
        assertEquals(ScheduleProvenance.MANUAL, row.getProvenance());
        mockMvc.perform(get("/api/admin/schedules")
                        .param("certificateId", String.valueOf(cert.getId()))
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].examStartDate").value(today.plusDays(30).toString()));
    }

    @Test
    @DisplayName("취소한 회차는 수집이 되살리지 않는다")
    void collection_does_not_revive_canceled_row() throws Exception {
        String token = bearer(newAdmin());
        LocalDate today = TimeUtil.today();
        Certificate cert = newCertificate("취소보호", "국사편찬위원회", "REVIVE");
        int year = today.getYear();
        long id = upsert(token, upsertBody(cert.getId(), year, 1, today.plusDays(30).toString()));
        mockMvc.perform(delete("/api/admin/schedules/{id}", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        DiffService.Outcome outcome = diffService.upsert(
                collected(cert, Series.ETC, year, 1, today.plusDays(45), ScheduleProvenance.API));

        assertEquals(DiffService.DiffType.UNCHANGED, outcome.type());
        ExamSchedule row = examScheduleRepository.findById(id).orElseThrow();
        assertEquals(ScheduleStatus.CANCELED, row.getStatus(), "취소한 회차가 수집으로 부활했다");
        assertEquals(today.plusDays(30), row.getExamStartDate());
    }

    @Test
    @DisplayName("추정치는 확정된 값(API·스크래핑)을 덮지 않는다 — 출처 서열")
    void approx_does_not_overwrite_confirmed_row() {
        LocalDate today = TimeUtil.today();
        Certificate cert = newCertificate("서열", "한국산업인력공단", "RANK");
        int year = today.getYear();

        DiffService.Outcome first = diffService.upsert(
                collected(cert, Series.ETC, year, 1, today.plusDays(30), ScheduleProvenance.API));
        assertEquals(DiffService.DiffType.NEW, first.type());
        long id = first.schedule().getId();

        DiffService.Outcome approx = diffService.upsert(
                collected(cert, Series.ETC, year, 1, today.plusDays(45), ScheduleProvenance.APPROX));
        assertEquals(DiffService.DiffType.UNCHANGED, approx.type(), "추정치가 API 값을 덮었다");
        ExamSchedule row = examScheduleRepository.findById(id).orElseThrow();
        assertEquals(today.plusDays(30), row.getExamStartDate());
        assertEquals(ScheduleProvenance.API, row.getProvenance(), "출처가 추정으로 강등됐다");

        // 확정된 값끼리는 나중 것이 이긴다
        DiffService.Outcome scraped = diffService.upsert(
                collected(cert, Series.ETC, year, 1, today.plusDays(50), ScheduleProvenance.SCRAPED));
        assertEquals(DiffService.DiffType.UPDATED, scraped.type());
        assertEquals(today.plusDays(50), examScheduleRepository.findById(id).orElseThrow().getExamStartDate());
    }

    @Test
    @DisplayName("수집 레코드의 계열이 '기타'면 기존 계열을 지우지 않는다 — 스냅샷·시드가 673종을 기타로 만들던 것")
    void collection_keeps_series_when_record_says_etc() {
        LocalDate today = TimeUtil.today();
        Certificate cert = newCertificate("계열보호", "한국산업인력공단", "SERIES", Series.TECHNICIAN);
        int year = today.getYear();

        diffService.upsert(collected(cert, Series.ETC, year, 1, today.plusDays(30), ScheduleProvenance.API));
        assertEquals(Series.TECHNICIAN, certificateRepository.findById(cert.getId()).orElseThrow().getSeries(),
                "수집 레코드의 '기타'가 기존 계열을 지웠다");

        diffService.upsert(collected(cert, Series.INDUSTRIAL, year, 2, today.plusDays(60), ScheduleProvenance.API));
        assertEquals(Series.INDUSTRIAL, certificateRepository.findById(cert.getId()).orElseThrow().getSeries(),
                "구체적인 계열은 반영돼야 한다");
    }

    // ===== '자동'의 근거는 살아 있는 소스다 =====
    //
    // 행의 출처(provenance)가 scraped 라는 것은 "언젠가 누가 긁었다"일 뿐이다. 정적 시드의 scraped 행을 근거로
    // '자동'이라 하면, 아무도 안 긁는 시험이 매니저 화면에서 자동으로 보여 영원히 손을 안 댄다.

    @Test
    @DisplayName("정적 시드의 scraped 행은 '자동'의 근거가 못 된다 — 살아 있는 소스가 그 기관을 맡아야 자동이다")
    void static_scraped_row_does_not_make_exam_automatic() throws Exception {
        String token = bearer(newAdmin());
        LocalDate today = TimeUtil.today();

        Certificate history = newCertificate("한국사검정", "국사편찬위원회", "HIST");   // 수기 대상 기관
        diffService.upsert(collected(history, Series.ETC, today.getYear(), 1, today.plusDays(30), ScheduleProvenance.SCRAPED));
        assertEquals("MANUAL", overviewRow(token, history).path("source").asText(),
                "시드의 scraped 표시만 보고 자동이라 한다");

        // 붙일 예정인 기관 → 크롤링 예정이지 자동은 아니다
        // (예시를 삼일회계법인으로 둔다 — 한국데이터산업진흥원은 robots.txt 전체 차단이라
        //  수집을 껐고, 그래서 이제 "크롤링 예정"이 아니다. 2026-09-09)
        Certificate samil = newCertificate("회계자격", "삼일회계법인", "SAMIL");
        diffService.upsert(collected(samil, Series.ETC, today.getYear(), 1, today.plusDays(30), ScheduleProvenance.SCRAPED));
        assertEquals("CRAWL_PLANNED", overviewRow(token, samil).path("source").asText());
    }

    @Test
    @DisplayName("큐넷 4자리 종목코드는 여전히 자동이다")
    void qnet_code_is_automatic() throws Exception {
        Certificate cert = certificateRepository.findBySlug("정보처리기사").orElseThrow();
        assertEquals("AUTO", overviewRow(bearer(newAdmin()), cert).path("source").asText());
    }
}

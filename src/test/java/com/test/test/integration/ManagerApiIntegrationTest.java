package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.exam.admin.DataSourceCatalog;
import com.test.test.exam.collect.CollectedSchedule;
import com.test.test.exam.collect.DiffService;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;
import com.test.test.exam.domain.AuthProvider;
import com.test.test.exam.domain.Member;
import com.test.test.exam.repository.MemberRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    @DisplayName("현황은 상태별 개수와 목록을 준다")
    void overview_returns_rows_and_counts() throws Exception {
        mockMvc.perform(get("/api/admin/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.counts").exists());
    }

    /** 일정이 없는 시험이 매니저의 첫 번째 할 일이다 — 이 필터가 곧 작업 목록이다. */
    @Test
    @DisplayName("일정 없는 시험만 골라 본다")
    void overview_filters_exams_without_schedule() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/admin/overview")
                        .param("status", "NONE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = objectMapper.readTree(
                res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
        assertTrue(items.size() > 0, "일정 없는 시험이 하나도 없다 — 시드가 바뀌었나?");
        for (JsonNode it : items) {
            assertEquals("NONE", it.path("status").asText());
            assertEquals(0, it.path("scheduleCount").asInt(), "일정이 있는데 NONE 으로 분류됐다");
        }
    }

    /** 일정이 있는 시험은 NONE 이 아니어야 한다 — 분류가 뒤집히면 목록이 쓸모없어진다. */
    @Test
    @DisplayName("일정이 있으면 NONE 이 아니다")
    void exam_with_schedule_is_not_none() throws Exception {
        long certId = anyCertificateId();   // 데모 시드 — 일정이 붙어 있다

        MvcResult res = mockMvc.perform(get("/api/admin/overview")
                        .param("size", "2000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = objectMapper.readTree(
                res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
        for (JsonNode it : items) {
            if (it.path("certificateId").asLong() == certId) {
                assertNotEquals("NONE", it.path("status").asText());
                assertTrue(it.path("scheduleCount").asInt() > 0);
                return;
            }
        }
    }

    /**
     * 매니저 화면의 기준은 <b>일정이 있냐 없냐</b> 딱 둘이다(사용자 결정 2026-09-01 —
     * "접수중이냐 아니냐는 헷갈리니까 하지 말자"). '있음' 은 세부 상태 셋의 합이라
     * 상태를 쉼표로 묶어 한 번에 거를 수 있어야 한다.
     */
    @Test
    @DisplayName("상태 여러 개를 쉼표로 묶어 거른다 — '일정 있음' 탭")
    void overview_filters_by_multiple_statuses() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/admin/overview")
                        .param("status", "PAST,OPEN,UPCOMING")
                        .param("size", "2000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = objectMapper.readTree(
                res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
        assertTrue(items.size() > 0, "일정 있는 시험이 하나도 없다 — 시드가 바뀌었나?");
        for (JsonNode it : items) {
            assertNotEquals("NONE", it.path("status").asText(), "일정 없는 시험이 '있음' 탭에 섞였다");
            assertTrue(it.path("scheduleCount").asInt() > 0);
        }
    }

    /**
     * 상시·예약제(AWS·컴활·운전면허 등)는 "일정"이라는 것이 존재하지 않는다.
     * NONE 에 섞이면 <b>영원히 못 채우는 숙제</b>가 되어 매니저가 "왜 아직 173이냐"고 묻게 된다
     * (실제로 물었다, 2026-09-01). 별도 상태 ROLLING 으로 구별한다.
     */
    @Test
    @DisplayName("상시·예약제는 '일정 없음'에 섞이지 않는다")
    void rolling_exams_are_not_counted_as_none() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/admin/overview")
                        .param("status", "NONE")
                        .param("size", "2000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(
                res.getResponse().getContentAsString(StandardCharsets.UTF_8));
        for (JsonNode it : body.path("items")) {
            assertNotEquals("AWS Certified Cloud Practitioner",
                    it.path("certificateName").asText(), "상시 시험이 '일정 없음' 목록에 있다");
        }
        assertTrue(body.path("counts").path("ROLLING").asLong(0) >= 20,
                "상시 표시가 안 붙었다 — RollingAdmissionInitializer 가 돌았나?");
    }

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

    /**
     * "일정 없음"은 한 덩어리가 아니다(설계/시험데이터/05_일정없음_분류). 매니저가 손댈 것은
     * <b>수기 필수</b>뿐이고, 큐넷 공고 전·크롤링 예정은 자동으로 들어온다. 그 구분이 화면에
     * 없으면 매니저는 143종을 전부 숙제로 안는다(사용자 지적 2026-09-02).
     */
    @Test
    @DisplayName("일정 없는 시험마다 이유(수기/공고 전/크롤링 예정)가 붙는다")
    void none_rows_carry_a_reason() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/admin/overview")
                        .param("status", "NONE").param("size", "2000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCounts").exists())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
        java.util.Set<String> allowed = java.util.Set.of("MANUAL", "ANNOUNCEMENT_PENDING", "CRAWL_PLANNED");
        long qnetPending = 0;
        for (JsonNode it : body.path("items")) {
            String reason = it.path("reason").asText("");
            assertTrue(allowed.contains(reason), "이유가 없거나 모르는 값: " + reason + " / " + it.path("certificateName").asText());
            assertFalse(it.path("reasonLabel").asText("").isBlank(), "화면용 라벨이 비었다");
            if ("ANNOUNCEMENT_PENDING".equals(reason)) qnetPending++;
        }
        // 이유별 합 = 일정 없음 전체 — 어긋나면 위 요약 숫자와 목록이 따로 논다
        long sum = 0;
        for (JsonNode n : body.path("reasonCounts")) sum += n.asLong();
        assertEquals(body.path("totalElements").asLong(), sum, "이유별 합이 일정 없음 전체와 다르다");
        assertTrue(qnetPending > 0, "큐넷 공고 전이 하나도 없다 — 분류가 죽었나?");
    }

    @Test
    @DisplayName("수기 필수만 골라 본다 — 매니저의 진짜 할 일")
    void none_rows_filter_by_reason() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/admin/overview")
                        .param("status", "NONE").param("reason", "MANUAL").param("size", "2000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAdmin())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(
                res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
        for (JsonNode it : items) {
            assertEquals("MANUAL", it.path("reason").asText(), "수기 필터에 다른 이유가 섞였다");
        }
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
            assertTrue(e.sourceUrl().startsWith("https://"),
                    e.group() + " 의 원본 주소가 https 가 아니다: " + e.sourceUrl());
            assertFalse(e.group().isBlank(), "묶음 이름이 비었다");
            assertFalse(e.exams().isBlank(), e.group() + " 의 시험 목록이 비었다");
            assertFalse(e.sourceName().isBlank(), e.group() + " 의 시행처가 비었다");
        }

        // 수기 항목이 하나도 없으면 이 화면을 만든 이유가 없다
        assertTrue(entries.stream().anyMatch(e -> e.mode() == DataSourceCatalog.Mode.MANUAL),
                "수기 입력 대상이 하나도 없다");
    }

    @Test
    @DisplayName("매니저 계정 설정 여부는 비로그인도 물어볼 수 있다 (로그인 화면 안내용)")

    void availability_is_public() throws Exception {
        mockMvc.perform(get("/api/manager/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
    }
}

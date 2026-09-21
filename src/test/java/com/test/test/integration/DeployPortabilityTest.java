package com.test.test.integration;

import com.test.test.common.config.RailwayDeploymentValidator;
import com.test.test.exam.auth.IncompleteSocialLoginRemover;
import com.test.test.exam.auth.JwtProvider;
import com.test.test.exam.config.ExamDataInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * <b>같은 코드가 로컬에서도 배포 환경에서도 그대로 돌아야 한다</b>(코드컨벤션 §5)를 지키는 테스트.
 *
 * <p>여기서 막으려는 사고는 "배포하고 나서야 알게 되는 것"들이다. 로컬에서는 멀쩡하니
 * 아무도 눈치채지 못하고, 운영에 올린 뒤에 로그인이 안 되거나 알림 링크가 죽어 있다.
 */
class DeployPortabilityTest {

    /**
     * 소셜 로그인이 <b>oauth.yml 없이</b> 환경변수만으로 떠야 한다.
     *
     * <p>oauth.yml 은 {@code .gitignore} 라 배포 이미지에 없다. 그런데 카카오는 스프링 내장
     * provider 가 없어서 토큰·유저정보 URI 를 누군가는 알려줘야 하는데, 그게 oauth.yml 에만
     * 있으면 운영에서 키를 넣어도 로그인이 안 뜬다. 그래서 provider 는 application.yml 에 둔다.
     */
    @Nested
    class 소셜로그인_설정 {

        // OAuth2 클라이언트 자동설정은 서블릿 웹 컨텍스트에서만 걸린다 → 웹 러너를 쓴다.
        private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class, OAuth2ClientAutoConfiguration.class))
                // 실제 앱에서는 @Component 다 — 덜 채워진 등록을 걷어낸다
                .withBean(IncompleteSocialLoginRemover.class)
                .withPropertyValues(
                        // application.yml 의 provider 블록 — 배포 이미지에 항상 들어 있는 부분
                        "spring.security.oauth2.client.provider.kakao.authorization-uri=https://kauth.kakao.com/oauth/authorize",
                        "spring.security.oauth2.client.provider.kakao.token-uri=https://kauth.kakao.com/oauth/token",
                        "spring.security.oauth2.client.provider.kakao.user-info-uri=https://kapi.kakao.com/v2/user/me",
                        "spring.security.oauth2.client.provider.kakao.user-name-attribute=id");

        @Test
        @DisplayName("키가 없으면 로그인만 꺼지고 앱은 뜬다")
        void starts_without_any_key() {
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(ClientRegistrationRepository.class);
            });
        }

        /**
         * <b>둘 중 하나만 키가 있는 상태</b>가 이 서비스의 현실이다 — 카카오는 붙었고 구글은
         * Client Secret 대기 중이다. 그때 <b>있는 쪽은 뜨고 없는 쪽은 조용히 빠져야</b> 한다.
         *
         * <p>여기가 깨지면 배포에서 키 하나를 빠뜨렸을 때 <b>앱 전체가 기동에 실패</b>한다 —
         * 로그인만 안 되는 게 아니라 시험 조회까지 통째로 죽는다(코드컨벤션 §5).
         */
        @Test
        @DisplayName("한쪽 키만 있으면 그쪽만 등록되고 앱은 뜬다")
        void half_configured_still_starts() {
            runner.withPropertyValues(
                            "spring.security.oauth2.client.registration.kakao.client-id=key",
                            "spring.security.oauth2.client.registration.kakao.client-secret=secret",
                            "spring.security.oauth2.client.registration.kakao.client-authentication-method=client_secret_post",
                            "spring.security.oauth2.client.registration.kakao.authorization-grant-type=authorization_code",
                            "spring.security.oauth2.client.registration.kakao.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
                            "spring.security.oauth2.client.registration.kakao.scope=profile_nickname",
                            // 구글: Client ID 는 발급받았지만 Secret 은 아직 없다
                            "spring.security.oauth2.client.registration.google.client-id=google-id",
                            "spring.security.oauth2.client.registration.google.client-secret=")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        ClientRegistrationRepository repo =
                                context.getBean(ClientRegistrationRepository.class);
                        assertNotNull(repo.findByRegistrationId("kakao"), "카카오가 빠졌다");
                        assertThat((Iterable<?>) repo)
                                .as("Secret 없는 구글이 등록되면 로그인 화면이 눌러도 깨지는 버튼을 그린다")
                                .hasSize(1);
                    });
        }

        /** 거르는 쪽이 과하면 시크릿을 넣어도 구글이 안 붙는다 — 채우면 반드시 살아나야 한다. */
        @Test
        @DisplayName("구글 시크릿을 채우면 구글도 등록된다")
        void google_appears_once_secret_is_filled() {
            runner.withPropertyValues(
                            "spring.security.oauth2.client.registration.google.client-id=google-id",
                            "spring.security.oauth2.client.registration.google.client-secret=google-secret")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        ClientRegistrationRepository repo =
                                context.getBean(ClientRegistrationRepository.class);
                        assertNotNull(repo.findByRegistrationId("google"),
                                "시크릿을 채웠는데도 구글이 안 붙었다");
                    });
        }

        /**
         * 카카오 시크릿은 <b>비어 있는 게 정상</b>이다 — 개발자센터에서 'Client Secret 사용'을
         * 안 켜면 안 쓴다. 구글 기준으로 일괄 필터를 걸면 멀쩡한 카카오가 사라진다.
         */
        @Test
        @DisplayName("카카오는 시크릿이 비어도 남는다")
        void kakao_survives_without_secret() {
            runner.withPropertyValues(
                            "spring.security.oauth2.client.registration.kakao.client-id=key",
                            "spring.security.oauth2.client.registration.kakao.client-secret=",
                            "spring.security.oauth2.client.registration.kakao.client-authentication-method=client_secret_post",
                            "spring.security.oauth2.client.registration.kakao.authorization-grant-type=authorization_code",
                            "spring.security.oauth2.client.registration.kakao.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
                            "spring.security.oauth2.client.registration.kakao.scope=profile_nickname")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertNotNull(context.getBean(ClientRegistrationRepository.class)
                                        .findByRegistrationId("kakao"),
                                "시크릿을 안 쓰는 카카오가 사라졌다");
                    });
        }

        /**
         * 운영(Railway)에서 넣을 환경변수만으로 카카오 등록이 완성되는지 — oauth.yml 없이.
         *
         * <p>여기 적힌 목록이 곧 <b>배포 시 넣어야 할 변수의 정답지</b>다(진행사항/02_내가_할일 4순위와 같아야 한다).
         * 카카오는 스프링 내장 provider 가 아니라서 grant-type·redirect-uri 도 기본값이 없다 —
         * 하나라도 빠지면 {@code authorizationGrantType cannot be null} 로 기동이 깨진다.
         * 반면 <b>provider URI 3종은 안 넘긴다</b>. 그건 application.yml 이 이미 갖고 있어야 하니까.
         */
        @Test
        @DisplayName("oauth.yml 없이 환경변수만으로 카카오 로그인이 완성된다")
        void kakao_registration_completes_from_env_only() {
            runner.withPropertyValues(
                            "spring.security.oauth2.client.registration.kakao.client-id=test-rest-api-key",
                            "spring.security.oauth2.client.registration.kakao.client-secret=test-secret",
                            "spring.security.oauth2.client.registration.kakao.client-authentication-method=client_secret_post",
                            "spring.security.oauth2.client.registration.kakao.authorization-grant-type=authorization_code",
                            "spring.security.oauth2.client.registration.kakao.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
                            "spring.security.oauth2.client.registration.kakao.scope=profile_nickname,profile_image")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        ClientRegistration kakao = context.getBean(ClientRegistrationRepository.class)
                                .findByRegistrationId("kakao");

                        assertNotNull(kakao, "oauth.yml 없이는 카카오 등록이 안 만들어진다");

                        // 핵심: provider URI 를 안 넘겼는데 채워져 있어야 한다(= application.yml 이 갖고 있다)
                        assertThat(kakao.getProviderDetails().getTokenUri())
                                .isEqualTo("https://kauth.kakao.com/oauth/token");
                        assertThat(kakao.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName())
                                .isEqualTo("id");

                        // 도메인을 박아두지 않는다 — {baseUrl} 이라 로컬·운영이 각자 자기 주소로 돌아온다
                        assertThat(kakao.getRedirectUri()).startsWith("{baseUrl}");

                        // KOE205 재발 방지: 비즈 앱 전용 동의항목을 요청하면 로그인 자체가 막힌다
                        assertThat(kakao.getScopes()).doesNotContain("account_email", "talk_message");
                    });
        }

        /** 구글은 스프링 내장 provider 라 키 두 개면 끝 — 우리가 URI 를 적을 필요가 없다. */
        @Test
        @DisplayName("구글은 client-id/secret 두 개만으로 완성된다")
        void google_registration_completes_from_two_keys() {
            runner.withPropertyValues(
                            "spring.security.oauth2.client.registration.google.client-id=test-google-id",
                            "spring.security.oauth2.client.registration.google.client-secret=test-google-secret")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        ClientRegistration google = context.getBean(ClientRegistrationRepository.class)
                                .findByRegistrationId("google");

                        assertNotNull(google);
                        assertThat(google.getProviderDetails().getTokenUri()).contains("googleapis.com");
                    });
        }
    }

    /**
     * 운영에서 <b>조용히 잘못 도는</b> 설정은 기동 때 죽인다.
     * 아래 셋은 전부 "기동도 되고 로그도 멀쩡한데 실제로는 망가진" 상태다.
     */
    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    class 배포검증기 {

        @Autowired
        private RailwayDeploymentValidator validator;

        @Test
        @DisplayName("로컬에서는 아무것도 막지 않는다")
        void local_passes() {
            validator.validate();   // 예외 없이 끝나야 한다
        }

        @Test
        @DisplayName("운영 + 개발용 JWT 기본키 → 기동 중단 (토큰 위조 가능)")
        void prod_with_dev_jwt_secret_fails() {
            assertThatThrownBy(() -> validate(prodValidator(
                    "jdbc:mysql://host/db", JwtProvider.DEV_SECRET, "https://exam.example.com", "운영용-충분히-긴-비밀번호")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT 서명키");
        }

        @Test
        @DisplayName("운영 + localhost 링크 → 기동 중단 (메일 속 링크가 죽는다)")
        void prod_with_localhost_link_fails() {
            assertThatThrownBy(() -> validate(prodValidator(
                    "jdbc:mysql://host/db", "충분히-긴-운영용-비밀키-12345678", "http://localhost:8081", "운영용-충분히-긴-비밀번호")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("localhost");
        }

        @Test
        @DisplayName("운영 + 인메모리 H2 → 기동 중단 (재배포마다 데이터 소실)")
        void prod_with_h2_fails() {
            assertThatThrownBy(() -> validate(prodValidator(
                    "jdbc:h2:mem:exam", "충분히-긴-운영용-비밀키-12345678", "https://exam.example.com", "운영용-충분히-긴-비밀번호")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("데이터 소실");
        }

        @Test
        @DisplayName("운영 설정이 다 맞으면 통과한다")
        void prod_with_everything_set_passes() {
            validate(prodValidator(
                    "jdbc:mysql://host/db", "충분히-긴-운영용-비밀키-12345678", "https://exam.example.com",
                    "운영용-충분히-긴-비밀번호"));
        }


        /**
         * 로컬에서 쓰던 짧은 비밀번호가 그대로 배포되는 건 가장 흔한 사고다.
         * 로컬은 편하게 두되(pass1234), 그 편의가 운영까지 따라가면 운영 화면이 그냥 뚫린다.
         */
        @Test
        @DisplayName("운영 + 짧은 매니저 비밀번호 → 기동 중단")
        void prod_with_short_manager_password_fails() {
            assertThatThrownBy(() -> validate(prodValidator(
                    "jdbc:mysql://host/db", "충분히-긴-운영용-비밀키-12345678", "https://exam.example.com",
                    "pass1234")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("매니저 비밀번호");
        }

        /** 매니저를 아예 설정 안 했으면 문제가 아니다 — 운영 화면만 못 쓸 뿐이다. */
        /**
         * <b>H2 콘솔은 임의 SQL 실행 창이다.</b> 붙을 JDBC 주소를 사람이 직접 입력하는 구조라,
         * 운영 DB 가 MySQL 이어도 그 창에서 운영 DB 로 붙을 수 있다.
         *
         * <p>application-prod.yml 이 끄고는 있지만 그건 <b>prod 프로파일이 켜졌을 때만</b>이다.
         * Railway 에 올리면서 SPRING_PROFILES_ACTIVE 를 빠뜨리면 그대로 열린 채 뜬다 —
         * 나머지 필수 설정(MySQL 주소·JWT 키)만 맞으면 기동은 성공하므로 아무도 눈치채지 못한다.
         * 그래서 프로파일과 무관하게 여기서 한 번 더 막는다.
         */
        @Test
        @DisplayName("운영 + H2 콘솔 켜짐 → 기동 중단 (임의 SQL 실행 창이 열린다)")
        void prod_with_h2_console_fails() {
            RailwayDeploymentValidator v = prodValidator(
                    "jdbc:mysql://host/db", "충분히-긴-운영용-비밀키-12345678", "https://exam.example.com", "운영용-충분히-긴-비밀번호");
            org.springframework.test.util.ReflectionTestUtils.setField(v, "h2ConsoleEnabled", true);

            assertThatThrownBy(() -> validate(v))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("H2 콘솔");
        }

        @Test
        @DisplayName("H2 콘솔이 꺼져 있으면 막지 않는다")
        void prod_with_h2_console_off_passes() {
            RailwayDeploymentValidator v = prodValidator(
                    "jdbc:mysql://host/db", "충분히-긴-운영용-비밀키-12345678", "https://exam.example.com", "운영용-충분히-긴-비밀번호");
            org.springframework.test.util.ReflectionTestUtils.setField(v, "h2ConsoleEnabled", false);

            validate(v);
        }

        @Test
        @DisplayName("매니저를 설정하지 않은 배포는 막지 않는다")
        void prod_without_manager_passes() {
            validate(prodValidator(
                    "jdbc:mysql://host/db", "충분히-긴-운영용-비밀키-12345678", "https://exam.example.com", ""));
        }

        private void validate(RailwayDeploymentValidator v) {
            v.validate();
        }

        /** prod 프로파일이 켜진 것처럼 보이는 검증기를 만든다(실제 컨텍스트를 prod 로 띄우면 DB 가 없어 못 뜬다). */
        private RailwayDeploymentValidator prodValidator(String jdbcUrl, String secret, String linkBase,
                                                         String managerPassword) {
            org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
            env.setActiveProfiles("prod");

            RailwayDeploymentValidator v = new RailwayDeploymentValidator(env);
            org.springframework.test.util.ReflectionTestUtils.setField(v, "datasourceUrl", jdbcUrl);
            org.springframework.test.util.ReflectionTestUtils.setField(v, "jwtSecret", secret);
            org.springframework.test.util.ReflectionTestUtils.setField(v, "linkBase", linkBase);
            org.springframework.test.util.ReflectionTestUtils.setField(v, "managerPassword", managerPassword);
            org.springframework.test.util.ReflectionTestUtils.setField(v, "h2ConsoleEnabled", false);
            return v;
        }
    }

    /**
     * 기동할 때는 <b>네트워크 수집을 하지 않는다 — 명시적으로 켠 경우만.</b>
     *
     * <p>전량 수집은 613콜이라 개발계정 한도(일 1,000회)의 2/3다. 기동마다 돌면 재시작 두 번에
     * 한도를 넘기고, 그날은 아무것도 못 받는다(실제로 613콜이 전부 429 로 돌아온 적이 있다).
     * 예전 규칙("큐넷이 꺼져 있으면 돌린다")은 {@code .env} 없는 새 환경에서 첫 기동에 스크래퍼 8곳을
     * 실호출하게 했다(2026-09-03). 그래서 기본은 파일 시드만 읽고, {@code --collect.on-startup=true} 일 때만 돈다.
     */
    @Nested
    class 기동수집_스위치 {

        private com.test.test.exam.collect.CollectService run(boolean collectOnStartup) {
            com.test.test.exam.repository.CertificateRepository certs =
                    org.mockito.Mockito.mock(com.test.test.exam.repository.CertificateRepository.class);
            org.mockito.Mockito.when(certs.count()).thenReturn(1L);   // 데모 마스터는 이미 있는 상태
            com.test.test.exam.collect.CollectService collect =
                    org.mockito.Mockito.mock(com.test.test.exam.collect.CollectService.class);
            ExamDataInitializer init = new ExamDataInitializer(certs, null, null, null, collect);
            ReflectionTestUtils.setField(init, "collectOnStartup", collectOnStartup);
            init.seed();
            return collect;
        }

        @Test
        @DisplayName("기본은 파일 시드만 읽는다 — 큐넷 한도와 시행처 사이트를 아낀다")
        void default_reads_files_only() {
            com.test.test.exam.collect.CollectService c = run(false);
            org.mockito.Mockito.verify(c).collectWithoutNetwork();
            org.mockito.Mockito.verify(c, org.mockito.Mockito.never()).collectAll();
        }

        @Test
        @DisplayName("--collect.on-startup=true 를 명시하면 네트워크 수집을 한다")
        void explicit_true_collects() {
            com.test.test.exam.collect.CollectService c = run(true);
            org.mockito.Mockito.verify(c).collectAll();
        }

        @Test
        @DisplayName("데이터가 이미 있어도 파일 시드는 매 기동 다시 읽는다 (예전엔 두 번째 기동부터 건너뛰었다)")
        void file_seeds_reload_every_boot() {
            com.test.test.exam.collect.CollectService c = run(false);
            org.mockito.Mockito.verify(c, org.mockito.Mockito.times(1)).collectWithoutNetwork();
        }
    }
}

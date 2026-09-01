package com.test.test.common.config;

import com.test.test.exam.auth.JwtAuthenticationFilter;
import com.test.test.exam.auth.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 보안 설정 (설계 08).
 *
 * <p><b>인증은 소셜 로그인 → JWT</b>. 토큰은 {@link JwtAuthenticationFilter} 가 읽어
 * SecurityContext 에 넣고, 여기서는 <b>어디를 열고 어디를 막을지</b>만 정한다.
 *
 * <p>비로그인 접근은 <b>401</b> 로 끊는다 — 프런트가 로그인 화면으로 보낼 수 있어야 하기 때문.
 * (예전엔 헤더 누락이 400 이라 "요청이 잘못됨"과 구분되지 않았다)
 *
 * <p>OAuth2 로그인은 <b>키가 설정된 경우에만</b> 켜진다({@code ClientRegistrationRepository} 존재 여부).
 * 키 없이도 앱이 뜨고 커뮤니티 읽기·시험 조회는 동작해야 하기 때문이다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** 공개 정적 자원 + React 화면 경로 (SpaWebConfig 가 index.html 로 폴백) */
    private static final String[] PUBLIC_PAGES = {
            "/", "/index.html", "/assets/**",
            "/favicon.ico", "/favicon.svg", "/vite.svg", "/robots.txt",
            // React 라우트 — ⚠️ App.tsx 에 라우트를 추가하면 여기도 추가할 것(빠뜨리면 새로고침 403)
            "/search", "/calendar", "/settings", "/cert/**",
            "/login", "/oauth/callback", "/me",
            "/community", "/community/**", "/admin", "/admin/**",
            "/manager", "/manager/**"
    };

    private static final String[] PUBLIC_INFRA = {
            "/error", "/healthz",
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/h2-console/**",
            "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/swagger-resources/**"
    };

    /**
     * 매니저 비밀번호 해시용. BCrypt 는 솔트를 해시 안에 담고 계산을 일부러 느리게 만들어
     * 유출되더라도 대입 공격이 실용적이지 않다 — 비밀번호 저장의 기본값이다.
     */
    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            ClientRegistrationRepository clientRegistrationRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            OAuth2SuccessHandler oAuth2SuccessHandler) throws Exception {

        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        http.cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PAGES).permitAll()
                .requestMatchers(PUBLIC_INFRA).permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()

                // 공개 API — 비로그인도 본다
                .requestMatchers(HttpMethod.GET,
                        "/api/info",
                        "/api/certificates", "/api/certificates/**",
                        "/api/community/boards",
                        "/api/community/posts", "/api/community/posts/**").permitAll()

                // 매니저 폼 로그인 — 소셜과 별개 경로. 여기 자체는 비로그인이어야 들어온다
                .requestMatchers(HttpMethod.POST, "/api/manager/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/manager/available").permitAll()

                // 운영 기능 — 관리자만
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // 그 외 /api/** 는 로그인 필요 (내 시험·알림설정·캘린더·글쓰기)
                .requestMatchers("/api/**").authenticated()

                // 화이트리스트에 없는 나머지는 차단
                .anyRequest().denyAll());

        // 인증 실패를 401 JSON 으로 — 기본 동작은 로그인 페이지 리다이렉트라 CSR 과 안 맞는다
        http.exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> writeError(res, 401,
                        "UNAUTHENTICATED", "로그인이 필요합니다."))
                .accessDeniedHandler((req, res, ex) -> writeError(res, 403,
                        "ACCESS_DENIED", "권한이 없습니다.")));

        // 소셜 로그인은 키가 있을 때만 활성화
        if (clientRegistrationRepository != null && oAuth2SuccessHandler != null) {
            http.oauth2Login(o -> o.successHandler(oAuth2SuccessHandler));
        }

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse res,
                            int status, String code, String message) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"success\":false,\"errorCode\":\"" + code
                + "\",\"message\":\"" + message + "\"}");
    }
}

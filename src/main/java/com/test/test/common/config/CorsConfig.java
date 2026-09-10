package com.test.test.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CORS 설정 — 어느 웹 출처(origin)의 브라우저가 이 REST API 를 호출할 수 있는지.
 * <ul>
 *   <li>인증은 <b>{@code Authorization: Bearer} JWT</b>(소셜 로그인). 쿠키가 아니다.</li>
 *   <li><b>평소엔 CORS 를 타지 않는다</b> — 로컬은 Vite proxy, 운영은 단일 서버라 화면과 API 가 같은 출처다.
 *       프런트를 별도 도메인에 올릴 때만 필요하다.</li>
 *   <li>그런 날을 위해 운영 출처는 {@code app.cors.allowed-origins}(쉼표구분, 환경변수 {@code APP_CORS_ALLOWED_ORIGINS})
 *       로 넣는다 — 코드를 고치지 않고 환경변수만으로 늘린다(코드컨벤션 §5).</li>
 * </ul>
 */
@Configuration
public class CorsConfig {

    /** 운영 프런트 도메인(쉼표구분). 예: https://exam.example.com,https://www.exam.example.com */
    @Value("${app.cors.allowed-origins:}")
    private String allowedOriginsProp;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = new ArrayList<>(Arrays.asList(
                "http://localhost:5101",   // Vite(React CSR) 개발 서버 — 화면 QA 는 여기서 본다
                "http://localhost:3000",   // 대체 개발 서버
                "http://localhost:8101"    // 로컬 백엔드(단일 서버 — application.yml 기본 포트)
        ));
        if (allowedOriginsProp != null && !allowedOriginsProp.isBlank()) {
            for (String o : allowedOriginsProp.split(",")) {
                if (!o.isBlank()) origins.add(o.trim());
            }
        }
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*")); // Authorization 헤더 허용
        configuration.setAllowCredentials(true);       // 향후 쿠키 로그인 대비(현재는 헤더 인증)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

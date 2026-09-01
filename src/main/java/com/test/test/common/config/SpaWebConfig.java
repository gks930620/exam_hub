package com.test.test.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.List;

/**
 * React CSR 번들(단일 서버 서빙)을 위한 정적 리소스 + SPA 폴백 설정.
 *
 * <p>이 서버 하나로 화면과 API 를 모두 제공한다(2026-08-06 사용자 지시).
 * 번들은 Gradle {@code copyFrontendBuild} 가 {@code src/main/resources/static} 에 넣는다.
 *
 * <p><b>왜 폴백이 필요한가</b>: React Router 는 {@code /search}, {@code /cert/12} 같은 경로를
 * 브라우저에서만 해석한다. 사용자가 그 주소로 <b>직접 들어오거나 새로고침</b>하면 요청이 서버로 오는데,
 * 서버에는 그런 파일이 없어 404 가 난다. 그래서 <b>실제 파일이 없는 경로는 index.html 로 돌려주고</b>
 * 라우팅은 브라우저가 하게 한다.
 *
 * <p>단, {@code /api/**} 같은 백엔드 경로까지 index.html 을 주면 안 된다 — 그러면 클라이언트가
 * JSON 을 기대한 자리에서 HTML 을 받아 파싱 에러가 나고, 없는 API 가 200 으로 보인다.
 * 아래 {@link #BACKEND_PREFIXES} 는 폴백에서 제외해 정상적으로 404 가 나가게 한다.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    /** SPA 폴백에서 제외할 경로 — 여기 걸리면 index.html 대신 404. */
    private static final List<String> BACKEND_PREFIXES = List.of(
            "api/", "actuator/", "swagger-ui/", "v3/api-docs", "h2-console/", "error");

    private static final ClassPathResource INDEX = new ClassPathResource("static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;   // 실제 파일(js/css/이미지)
                        }
                        if (isBackendPath(resourcePath)) {
                            return null;        // 없는 API → 404 (GlobalExceptionHandler 가 처리)
                        }
                        // 프런트 번들이 없으면(빌드 전) 폴백도 없음 → 404
                        return INDEX.exists() ? INDEX : null;
                    }
                });
    }

    private boolean isBackendPath(String resourcePath) {
        return BACKEND_PREFIXES.stream().anyMatch(resourcePath::startsWith);
    }
}

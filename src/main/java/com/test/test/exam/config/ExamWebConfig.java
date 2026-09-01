package com.test.test.exam.config;

import com.test.test.exam.auth.CurrentMemberArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * exam-hub 전용 MVC 설정 — {@code @CurrentMember} 해석기 등록.
 * (구 {@code @CurrentDevice} 해석기를 대체)
 */
@Configuration
@RequiredArgsConstructor
public class ExamWebConfig implements WebMvcConfigurer {

    private final CurrentMemberArgumentResolver currentMemberArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentMemberArgumentResolver);
    }
}

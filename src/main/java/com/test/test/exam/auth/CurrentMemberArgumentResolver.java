package com.test.test.exam.auth;

import com.test.test.common.exception.UnauthenticatedException;
import com.test.test.exam.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@code @CurrentMember Member member} 파라미터를 채운다.
 * 비로그인인데 required=true 면 401 — "요청이 잘못됨(400)"이 아니라 "로그인이 필요함"이다.
 */
@Component
@RequiredArgsConstructor
public class CurrentMemberArgumentResolver implements HandlerMethodArgumentResolver {

    private final MemberService memberService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentMember.class)
                && Member.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        CurrentMember annotation = parameter.getParameterAnnotation(CurrentMember.class);
        boolean required = annotation == null || annotation.required();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof MemberPrincipal principal) {
            return memberService.findActive(principal.memberId())
                    .orElseGet(() -> {
                        if (required) {
                            throw new UnauthenticatedException("로그인이 필요합니다.");
                        }
                        return null;
                    });
        }
        if (required) {
            throw new UnauthenticatedException("로그인이 필요합니다.");
        }
        return null;
    }
}

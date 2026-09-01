package com.test.test.exam.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * {@code Authorization: Bearer <token>} 를 읽어 SecurityContext 에 {@link MemberPrincipal} 을 넣는다.
 *
 * <p>토큰이 없거나 틀려도 <b>여기서 막지 않는다</b> — 그냥 비인증 상태로 통과시키고,
 * 보호가 필요한 경로는 SecurityConfig 의 인가 규칙이 401 로 끊는다.
 * (공개 API 는 토큰이 있으면 개인화, 없으면 그대로 동작해야 하기 때문)
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final MemberService memberService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Long memberId = jwtProvider.parseMemberId(token);
            if (memberId != null) {
                memberService.findActive(memberId).ifPresent(member -> {
                    MemberPrincipal principal = MemberPrincipal.of(member);
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
                });
            }
        }
        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}

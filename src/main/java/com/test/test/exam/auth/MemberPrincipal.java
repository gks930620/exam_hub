package com.test.test.exam.auth;

import com.test.test.exam.domain.Member;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * SecurityContext 에 담기는 인증 주체. 컨트롤러는 {@code @CurrentMember} 로 Member 를 직접 받는다.
 */
public record MemberPrincipal(Long memberId, String nickname, String role) {

    public static MemberPrincipal of(Member m) {
        return new MemberPrincipal(m.getId(), m.getNickname(), m.getRole().name());
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}

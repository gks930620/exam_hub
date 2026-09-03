package com.test.test.exam.manager;

import com.test.test.exam.auth.JwtProvider;
import com.test.test.common.exception.UnauthenticatedException;
import com.test.test.exam.domain.AuthProvider;
import com.test.test.exam.domain.Member;
import com.test.test.exam.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManagerLoginService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final ManagerLoginAttemptLimiter attemptLimiter;

    /**
     * 아이디/비밀번호를 확인하고 JWT 를 발급한다.
     *
     * <p><b>실패 사유를 구분해서 알려주지 않는다</b> — "그런 아이디 없음"과 "비밀번호 틀림"을
     * 나누면 아이디가 맞는지부터 확인해 볼 수 있게 된다. 하나뿐인 운영 계정이라 더 그렇다.
     *
     * <p>같은 (IP, 아이디)로 15분에 5회 넘게 틀리면 429 — {@link ManagerLoginAttemptLimiter}.
     */
    public String login(String username, String rawPassword, String clientIp) {
        attemptLimiter.checkAllowed(clientIp, username);

        Optional<Member> found = memberRepository.findByProviderAndProviderId(AuthProvider.LOCAL, username);

        if (found.isEmpty() || !found.get().canLoginWithPassword()
                || !passwordEncoder.matches(rawPassword, found.get().getPasswordHash())) {
            attemptLimiter.recordFailure(clientIp, username);
            log.warn("[매니저] 로그인 실패 id={} ip={}", username, clientIp);
            throw new UnauthenticatedException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        attemptLimiter.reset(clientIp, username);
        Member manager = found.get();
        log.info("[매니저] 로그인 성공 id={} memberId={}", username, manager.getId());
        return jwtProvider.issue(manager.getId(), manager.getNickname(), manager.getRole().name());
    }

    /** 매니저 계정이 하나라도 있는지(환경변수 설정 여부). */
    public boolean isConfigured() {
        return memberRepository.existsByProviderAndPasswordHashIsNotNull(AuthProvider.LOCAL);
    }
}

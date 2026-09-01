package com.test.test.exam.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 발급·검증 (설계 08 §3).
 *
 * <p><b>왜 세션이 아니라 JWT 인가</b>: 프런트가 별도 도메인에 배포될 수 있는 CSR 이라
 * 쿠키 세션은 SameSite·CORS 처리가 번거롭다. 서버는 상태를 두지 않고 토큰만 검증한다.
 *
 * <p>refresh 토큰은 이번 범위 밖 — 만료되면 다시 로그인한다.
 */
@Slf4j
@Component
public class JwtProvider {

    /** 운영에서 기본값이 그대로 쓰이면 위조가 가능하다 → RailwayDeploymentValidator 가 fail-fast 시킨다. */
    public static final String DEV_SECRET = "exam-hub-local-dev-secret-key-please-change-in-production";

    private final SecretKey key;
    private final long validitySeconds;

    public JwtProvider(@Value("${app.jwt.secret:" + DEV_SECRET + "}") String secret,
                       @Value("${app.jwt.validity-seconds:86400}") long validitySeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT 서명 키는 32바이트 이상이어야 합니다. JWT_SECRET_KEY 를 확인하세요.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validitySeconds = validitySeconds;
    }

    /** 로그인 성공 시 발급. subject = member id. */
    public String issue(Long memberId, String nickname, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("nickname", nickname)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validitySeconds, ChronoUnit.SECONDS)))
                .signWith(key)
                .compact();
    }

    /**
     * 토큰에서 member id 를 꺼낸다.
     * 서명 불일치·만료·형식 오류는 전부 <b>null</b> 로 돌려준다 — 인증 실패는 예외가 아니라 정상 흐름이다.
     */
    public Long parseMemberId(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("[JWT] 검증 실패: {}", e.getMessage());
            return null;
        }
    }

    public long getValiditySeconds() {
        return validitySeconds;
    }
}

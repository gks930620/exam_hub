package com.test.test.exam.manager;

import com.test.test.common.exception.TooManyAttemptsException;
import com.test.test.exam.common.TimeUtil;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 매니저 로그인 시도 제한 — 같은 (IP, 아이디)로 15분 안에 5회 넘게 틀리면 그 창이 지날 때까지 429.
 *
 * <p>운영 계정은 하나뿐이고 아이디가 뻔하다(data.sql 에 그대로 있다). 무한정 대입을 받아주면
 * 비밀번호 강도만이 유일한 방어가 된다. 외부 라이브러리 없이 메모리 맵으로 충분하다 —
 * 서버가 하나고, 재기동으로 초기화되는 것도 문제가 아니다(그동안 15분이 지났을 가능성이 크다).
 *
 * <p>성공하면 그 키의 실패 기록을 지운다. 정상 사용자가 몇 번 헛갈린 뒤 맞게 넣은 것은 공격이 아니다.
 */
@Component
public class ManagerLoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    /** 맵이 이보다 커지면 만료된 항목을 비운다 — 아이디를 바꿔 가며 두드려도 메모리가 새지 않게. */
    private static final int PURGE_THRESHOLD = 1_000;

    private final ConcurrentHashMap<String, Failures> failures = new ConcurrentHashMap<>();

    /** 실패 횟수와 첫 실패 시각 — 창은 첫 실패로부터 15분이다. */
    private record Failures(int count, LocalDateTime since) {
        boolean expired(LocalDateTime now) {
            return since.plus(WINDOW).isBefore(now);
        }
    }

    /** 잠겨 있으면 429. 로그인 검증 전에 부른다 — 잠긴 뒤에는 맞는 비밀번호도 통과시키지 않는다. */
    public void checkAllowed(String clientIp, String username) {
        Failures f = failures.get(key(clientIp, username));
        if (f != null && !f.expired(TimeUtil.now()) && f.count() >= MAX_FAILURES) {
            throw new TooManyAttemptsException(
                    "로그인 시도가 너무 많습니다. " + WINDOW.toMinutes() + "분 뒤에 다시 시도하세요.");
        }
    }

    public void recordFailure(String clientIp, String username) {
        LocalDateTime now = TimeUtil.now();
        failures.compute(key(clientIp, username), (k, prev) ->
                prev == null || prev.expired(now) ? new Failures(1, now) : new Failures(prev.count() + 1, prev.since()));
        purgeIfLarge(now);
    }

    public void reset(String clientIp, String username) {
        failures.remove(key(clientIp, username));
    }

    private String key(String clientIp, String username) {
        return (clientIp == null ? "?" : clientIp) + "|" + (username == null ? "" : username.trim().toLowerCase());
    }

    private void purgeIfLarge(LocalDateTime now) {
        if (failures.size() > PURGE_THRESHOLD) {
            failures.entrySet().removeIf(e -> e.getValue().expired(now));
        }
    }
}

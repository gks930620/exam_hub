package com.test.test.common.config;

import org.springframework.core.env.Environment;

/**
 * "지금 운영인가?" 한 곳에서만 판단한다.
 *
 * <p>여러 곳에서 각자 판단하면 하나는 prod 로 보고 하나는 로컬로 봐서, 로컬에서 편하자고 푼
 * 규칙이 운영까지 따라가는 일이 생긴다. 그 판단이 갈리는 순간이 가장 위험하다.
 */
public final class RuntimeEnv {

    private RuntimeEnv() {
    }

    /** prod 프로파일이 활성이거나 Railway 주입 환경변수(RAILWAY_*)가 있으면 운영으로 본다. */
    public static boolean isProdLike(Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return System.getenv().keySet().stream().anyMatch(k -> k.startsWith("RAILWAY_"));
    }
}

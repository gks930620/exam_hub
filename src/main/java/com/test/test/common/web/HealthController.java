package com.test.test.common.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 헬스체크 엔드포인트 (코드컨벤션 §5-4).
 * 로드밸런서/Railway/모니터링이 인증 없이 호출한다 → SecurityConfig 에서 permitAll.
 * 항상 200 + {"status":"UP"} 를 반환한다(의존성 상태는 /actuator/health 참고).
 */
@RestController
public class HealthController {

    @GetMapping("/healthz")
    public Map<String, String> healthz() {
        return Map.of("status", "UP");
    }
}

package com.test.test;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 서비스 메타 정보.
 *
 * <p>루트({@code /})는 <b>React 화면</b>이 차지한다 — 이 서버 하나가 화면과 API 를 모두 서빙하기 때문
 * ({@code SpaWebConfig}). 그래서 예전에 {@code /} 에 있던 이 JSON 응답은 {@code /api/info} 로 옮겼다.
 */
@RestController
public class HomeController {

    @GetMapping("/api/info")
    public Map<String, String> info() {
        return Map.of(
                "service", "exam-hub",
                "web", "이 서버가 React 번들을 함께 서빙한다 — 루트(/) 로 접속",
                "docs", "/swagger-ui/index.html");
    }
}

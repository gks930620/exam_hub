package com.test.test.exam.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 매니저용 <b>수집 건강</b> — "지금 뭐가 고장났나".
 *
 * <p>그전에는 수집 결과가 {@code crawl_log} 와 서버 로그에만 남았다. 화면도 API 도 없어서
 * 매니저가 볼 방법이 없었다 — {@code CollectService.handleFailure} 의 주석은
 * "운영 주 1회 crawl_log 점검으로 커버"라고 했지만 <b>점검할 수단이 없었다.</b>
 *
 * <p>고장이 조용하다는 게 이 서비스의 특별한 위험이다. 스크래퍼가 깨져도 DB 에는 옛 일정이
 * 남아 있어 사용자 화면은 멀쩡해 보인다. 그 사이 사용자는 지난 날짜를 믿고 준비한다.
 * 그래서 <b>성공했지만 0건</b>·<b>오래 안 돔</b> 도 고장으로 센다({@link CollectHealth}).
 *
 * <p>판정은 {@link HealthService} 한 곳에서 한다 — 고장 경보 메일과 같은 답을 내야 한다.
 *
 * <p>{@code /api/admin/**} 이라 ADMIN 만 볼 수 있다.
 */
@RestController
@RequestMapping("/api/admin/collect-health")
@RequiredArgsConstructor
public class AdminCollectHealthController {

    private final HealthService healthService;

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        List<CollectHealth> rows = healthService.collectHealth();
        return ResponseEntity.ok(new HealthResponse(
                rows,
                rows.size(),
                (int) rows.stream().filter(CollectHealth::isNeedsAttention).count()));
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthResponse {
        private List<CollectHealth> items;
        private int total;
        /** 손봐야 하는 소스 수 — 0 이 아니면 화면이 경고 띠를 띄운다 */
        private int needsAttention;
    }
}

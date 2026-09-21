package com.test.test.exam.admin;

import com.test.test.exam.collect.ScheduleSource;
import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.CrawlLog;
import com.test.test.exam.repository.CrawlLogRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
 * <p>{@code /api/admin/**} 이라 ADMIN 만 볼 수 있다.
 */
@RestController
@RequestMapping("/api/admin/collect-health")
@RequiredArgsConstructor
public class AdminCollectHealthController {

    /** 판정에 쓰는 기록 범위. 연속 실패를 세려면 며칠치는 있어야 한다 */
    private static final int LOOKBACK_DAYS = 30;

    private final CrawlLogRepository crawlLogRepository;
    private final List<ScheduleSource> sources;

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        Map<String, List<CrawlLog>> bySource = crawlLogRepository
                .findByStartedAtAfterOrderByStartedAtDesc(TimeUtil.now().minusDays(LOOKBACK_DAYS))
                .stream().collect(Collectors.groupingBy(CrawlLog::getSource));

        // 살아 있는 소스를 기준으로 돈다 — 기록이 없는 소스(한 번도 안 돈 것)도 나와야 한다.
        // 기록만 훑으면 "안 도는 소스"가 목록에서 통째로 빠져 조용히 넘어간다.
        List<CollectHealth> rows = sources.stream()
                .filter(ScheduleSource::usesNetwork)
                .map(s -> CollectHealth.of(s.sourceId(), bySource.get(s.sourceId()), TimeUtil.now()))
                .sorted(Comparator.comparing((CollectHealth h) -> !h.isNeedsAttention())
                        .thenComparing(CollectHealth::getSource))
                .toList();

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

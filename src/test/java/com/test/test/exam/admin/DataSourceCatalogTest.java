package com.test.test.exam.admin;

import com.test.test.exam.collect.ScheduleSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 매니저 데이터 지도({@link DataSourceCatalog})와 <b>실제로 살아 있는 수집기</b>가 어긋나지 않는지.
 *
 * <p>지도는 손으로 관리하는 표라 스크래퍼를 붙이고 표를 안 고치면 매니저가 "수기"라고 믿고
 * 헛일을 하거나, 반대로 "자동"이라 믿고 손을 놓는다. 그래서 자동(AUTO) 행의 기관은 반드시
 * 어떤 소스가 {@link ScheduleSource#coveredAgencies()} 로 맡고 있어야 하고, 그 역도 성립해야 한다.
 *
 * <p>기본 테스트 설정에는 스크래퍼·큐넷 빈이 없어(네트워크 차단) 여기서만 켠다.
 * 빈이 뜨는 것과 네트워크를 타는 것은 별개다 — 기동 시 수집은 {@code collect.on-startup} 이 기본 false 라 안 돈다.
 */
@SpringBootTest(properties = {"scrape.enabled=true", "qnet.api.enabled=true"})
@ActiveProfiles("test")
class DataSourceCatalogTest {

    @Autowired
    private List<ScheduleSource> sources;

    private Set<String> liveCoverage() {
        Set<String> covered = new LinkedHashSet<>();
        for (ScheduleSource s : sources) {
            covered.addAll(s.coveredAgencies());
        }
        return covered;
    }

    @Test
    @DisplayName("살아 있는 소스가 하나 이상 기관을 맡고 있다 (빈 목록이면 이 테스트가 아무것도 검증하지 않는다)")
    void live_sources_cover_something() {
        assertFalse(liveCoverage().isEmpty(), "기관을 맡은 소스가 하나도 없다 — scrape.enabled 가 안 먹었나?");
    }

    @Test
    @DisplayName("지도의 AUTO 행은 살아 있는 소스가 그 기관을 맡고 있어야 한다")
    void every_auto_row_is_backed_by_a_live_source() {
        Set<String> covered = liveCoverage();
        for (DataSourceCatalog.Entry e : DataSourceCatalog.entries()) {
            if (e.mode() != DataSourceCatalog.Mode.AUTO) {
                continue;
            }
            assertTrue(AgencyMatcher.matches(e.sourceName(), covered),
                    "지도는 '자동'이라는데 맡은 소스가 없다: " + e.group() + " / " + e.sourceName() + " — 살아 있는 기관 " + covered);
        }
    }

    @Test
    @DisplayName("살아 있는 소스가 맡은 기관은 지도에 AUTO 로 실려 있어야 한다")
    void every_live_agency_appears_as_auto_in_catalog() {
        Set<String> autoNames = new HashSet<>();
        for (DataSourceCatalog.Entry e : DataSourceCatalog.entries()) {
            if (e.mode() == DataSourceCatalog.Mode.AUTO) {
                autoNames.add(e.sourceName());
            }
        }
        for (ScheduleSource s : sources) {
            for (String agency : s.coveredAgencies()) {
                assertTrue(AgencyMatcher.matches(agency, autoNames),
                        s.sourceId() + " 가 맡은 " + agency + " 이(가) 지도에 자동으로 안 실려 있다 — 매니저가 수기로 넣는다");
            }
        }
    }

    @Test
    @DisplayName("자동(대기 중) 행은 크롤링 예정 기관이어야 한다 — 일정 없음 이유 판정과 같은 목록")
    void auto_pending_rows_match_crawl_planned_agencies() {
        for (DataSourceCatalog.Entry e : DataSourceCatalog.entries()) {
            if (e.mode() != DataSourceCatalog.Mode.AUTO_PENDING) {
                continue;
            }
            assertTrue(AgencyMatcher.matches(e.sourceName(), NoScheduleReason.CRAWL_PLANNED_AGENCIES),
                    "지도는 '자동(대기 중)'인데 일정 없음 이유는 수기라고 한다: " + e.sourceName());
        }
    }

    @Test
    @DisplayName("같은 시험이 두 행에 실리지 않는다 — 한 행은 '넣지 마라', 다른 행은 '넣어라'가 된다")
    void no_exam_is_listed_twice() {
        Set<String> seen = new HashSet<>();
        for (DataSourceCatalog.Entry e : DataSourceCatalog.entries()) {
            for (String exam : e.exams().split(",")) {
                String key = exam.replaceAll("\\s+", "").toLowerCase();
                if (key.isBlank()) {
                    continue;
                }
                assertTrue(seen.add(key), "두 행에 실린 시험: " + exam.trim() + " (" + e.group() + ")");
            }
        }
    }
}

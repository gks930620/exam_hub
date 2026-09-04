package com.test.test.exam.collect;

import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.Series;
import com.test.test.exam.notification.NotificationScheduleService;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.CrawlLogRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;

/**
 * 수집 소스의 <b>실행 순서와 선별</b> — 컨트롤러를 거치지 않는 순수 조율 로직이라 작은 단위테스트로 둔다.
 *
 * <p>왜 순서가 중요한가: 같은 (시험, 연도, 회차, 구분) 키를 여러 소스가 줄 수 있다. 나중에 쓴 쪽이 이긴다.
 * 그래서 확정도가 낮은 시드·스냅샷(0)이 먼저, 스크래퍼(50), 큐넷 API(100)가 마지막에 써야
 * 실데이터가 시드에 밀리지 않는다. 스냅샷은 기동 전용이라 배치에서는 아예 돌지 않는다.
 */
class CollectServiceOrderingTest {

    private final List<String> calls = new ArrayList<>();
    private CollectService service;
    private List<ScheduleSource> sources;

    /** 호출 기록만 남기는 가짜 소스. */
    private ScheduleSource fake(String id, int priority, boolean startupOnly, boolean partial,
                                boolean network, boolean explode) {
        return new ScheduleSource() {
            @Override public String sourceId() { return id; }
            @Override public int priority() { return priority; }
            @Override public boolean startupOnly() { return startupOnly; }
            @Override public boolean supportsPartialFetch() { return partial; }
            @Override public boolean usesNetwork() { return network; }
            @Override public List<CollectedSchedule> fetchAll() {
                calls.add(id + ":all");
                if (explode) throw new IllegalStateException(id + " 죽음");
                return List.of();
            }
            @Override public List<CollectedSchedule> fetchByCertificateCodes(List<String> codes) {
                calls.add(id + ":codes");
                if (explode) throw new IllegalStateException(id + " 죽음");
                return List.of();
            }
        };
    }

    @BeforeEach
    void setUp() {
        // 일부러 뒤섞어 넣는다 — 스프링이 빈을 어떤 순서로 주든 priority 로 정렬돼야 한다
        sources = List.of(
                fake("QNET", 100, false, true, true, false),
                fake("SNAPSHOT", 0, true, false, false, false),
                fake("SCRAPER_B", 50, false, false, true, true),
                fake("SEED", 0, false, false, false, false),
                fake("SCRAPER_A", 50, false, false, true, false));

        ExamScheduleRepository examScheduleRepository = Mockito.mock(ExamScheduleRepository.class);
        CertificateRepository certificateRepository = Mockito.mock(CertificateRepository.class);
        Mockito.when(examScheduleRepository.findCertificateIdsWithImminentRegistration(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(1L));
        Mockito.when(certificateRepository.findAllById(anyList()))
                .thenReturn(List.of(Certificate.builder().name("x").slug("x").series(Series.ETC).agency("a").sourceCode("1320").build()));

        service = new CollectService(sources,
                Mockito.mock(DiffService.class),
                Mockito.mock(NotificationScheduleService.class),
                Mockito.mock(CrawlLogRepository.class),
                examScheduleRepository, certificateRepository,
                Mockito.mock(com.test.test.exam.repository.NotificationScheduleRepository.class));
    }

    @Test
    @DisplayName("전체 수집은 priority 순으로 돌고 기동 전용(스냅샷)은 건너뛴다 — 큐넷이 마지막에 써서 이긴다")
    void collect_all_runs_by_priority_and_skips_startup_only() {
        service.collectAll();

        assertEquals(List.of("SEED:all", "SCRAPER_B:all", "SCRAPER_A:all", "QNET:all"), calls);
    }

    @Test
    @DisplayName("종목 지정 재수집: 그 종목과 무관한 스크래퍼는 안 부른다")
    void collect_by_codes_skips_unrelated_scrapers() {
        service.collectByCodes(List.of("1320"));   // 담당 기관이 '한국산업인력공단'인 큐넷 종목

        assertEquals(List.of("QNET:codes"), calls);
    }

    /**
     * 스크래퍼가 담당하는 종목을 재수집하면 <b>그 스크래퍼가 돌아야 한다.</b>
     *
     * <p>부분 조회를 지원하는 소스(큐넷)만 부르게 했더니, 매니저가 KCA 종목(정보보안기사)에
     * "다시 받아오기"를 눌러도 큐넷만 돌고 아무 일도 안 일어났다(2026-09-04 실측).
     * 스크래퍼는 사이트를 통째로 읽고 걸러 주므로, 담당 기관이 걸리면 불러야 한다.
     */
    @Test
    @DisplayName("종목 지정 재수집: 그 기관을 담당하는 스크래퍼는 부른다")
    void collect_by_codes_runs_the_scraper_that_covers_the_agency() {
        CertificateRepository repo = Mockito.mock(CertificateRepository.class);
        Mockito.when(repo.findBySourceCodeIn(anyList())).thenReturn(List.of(
                Certificate.builder().name("정보보안기사").slug("s").series(Series.ETC)
                        .agency("한국방송통신전파진흥원").sourceCode("KCA-SEC").build()));
        List<ScheduleSource> withKca = new ArrayList<>(sources);
        withKca.add(covering("KCA", 50, "한국방송통신전파진흥원"));
        service = new CollectService(withKca, Mockito.mock(DiffService.class),
                Mockito.mock(NotificationScheduleService.class), Mockito.mock(CrawlLogRepository.class),
                mockImminentRepo(), repo,
                Mockito.mock(com.test.test.exam.repository.NotificationScheduleRepository.class));

        service.collectByCodes(List.of("KCA-SEC"));

        // 큐넷은 부분 조회를 지원해 늘 불리지만, 4자리 코드가 없으면 스스로 빈 목록을 돌려준다
        // (QnetApiScheduleSource.fetchByCertificateCodes) — 실제 API 호출은 없다.
        assertEquals(List.of("KCA:codes", "QNET:codes"), calls,
                "담당 스크래퍼가 안 돌면 매니저의 '다시 받아오기'가 아무 일도 안 한다");
    }

    /** 담당 기관을 밝히는 가짜 스크래퍼(부분 조회는 지원하지 않는다). */
    private ScheduleSource covering(String id, int priority, String agency) {
        ScheduleSource base = fake(id, priority, false, false, true, false);
        return new ScheduleSource() {
            @Override public String sourceId() { return base.sourceId(); }
            @Override public int priority() { return base.priority(); }
            @Override public java.util.Set<String> coveredAgencies() { return java.util.Set.of(agency); }
            @Override public List<CollectedSchedule> fetchAll() { return base.fetchAll(); }
            @Override public List<CollectedSchedule> fetchByCertificateCodes(List<String> codes) {
                return base.fetchByCertificateCodes(codes);
            }
        };
    }

    @Test
    @DisplayName("임박 재확인도 부분 조회 소스만, 하나가 죽어도 계속")
    void collect_imminent_calls_only_partial_fetch_sources_and_survives_failure() {
        // 부분 조회를 지원하면서 죽는 소스를 하나 더 끼운다
        List<ScheduleSource> withExploding = new ArrayList<>(sources);
        withExploding.add(fake("FLAKY_API", 90, false, true, true, true));
        service = new CollectService(withExploding,
                Mockito.mock(DiffService.class), Mockito.mock(NotificationScheduleService.class),
                Mockito.mock(CrawlLogRepository.class),
                mockImminentRepo(), mockCertRepo(),
                Mockito.mock(com.test.test.exam.repository.NotificationScheduleRepository.class));

        service.collectImminent();

        assertEquals(List.of("FLAKY_API:codes", "QNET:codes"), calls, "죽은 소스 뒤의 소스가 안 돌았다");
    }

    @Test
    @DisplayName("기동 시 파일 수집은 네트워크 없는 소스만 — 스냅샷 포함, priority 순")
    void collect_without_network_reads_file_sources_including_startup_only() {
        service.collectWithoutNetwork();

        assertEquals(List.of("SNAPSHOT:all", "SEED:all"), calls);
    }

    private ExamScheduleRepository mockImminentRepo() {
        ExamScheduleRepository repo = Mockito.mock(ExamScheduleRepository.class);
        Mockito.when(repo.findCertificateIdsWithImminentRegistration(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(1L));
        return repo;
    }

    private CertificateRepository mockCertRepo() {
        CertificateRepository repo = Mockito.mock(CertificateRepository.class);
        Mockito.when(repo.findAllById(anyList()))
                .thenReturn(List.of(Certificate.builder().name("x").slug("x").series(Series.ETC).agency("a").sourceCode("1320").build()));
        return repo;
    }
}

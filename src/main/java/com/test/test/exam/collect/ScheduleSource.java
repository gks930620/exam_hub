package com.test.test.exam.collect;

import java.util.List;
import java.util.Set;

/**
 * 시험 일정 수집 소스 추상화 (DR-01, 리스크 1순위 대응).
 * 공공 API 스펙 변경/소스 추가·교체에 대비해 수집기를 인터페이스로 격리한다.
 * 구현: {@link MockScheduleSource}(키 없음/개발), {@link QnetApiScheduleSource}(실 큐넷 API),
 * 시행처 스크래퍼({@link AbstractHtmlScheduleSource} 계열), 파일 시드({@link SeedFileScheduleSource}·{@link SnapshotScheduleSource}).
 *
 * <p><b>같은 키를 여러 소스가 줄 수 있다</b> — (시험, 연도, 회차, 구분)이 같으면 나중에 쓴 쪽이 이긴다.
 * 그래서 {@link #priority()} 로 실행 순서를 못 박는다: 시드·스냅샷(0) → 스크래퍼(50) → 큐넷 API(100).
 * 확정도가 높은 소스가 마지막에 써야 실데이터가 시드에 밀리지 않는다.
 */
public interface ScheduleSource {

    /** 시드·스냅샷 — 가장 먼저 쓴다(나중 소스가 덮도록). */
    int PRIORITY_FILE = 0;
    /** 시행처 스크래퍼. */
    int PRIORITY_SCRAPER = 50;
    /** 공공 API — 마지막에 써서 이긴다. */
    int PRIORITY_API = 100;

    /** crawl_log.source 에 기록될 소스 식별자 (예: QNET_API, MOCK). */
    String sourceId();

    /** 전체 종목 일정 수집. */
    List<CollectedSchedule> fetchAll();

    /**
     * 이 소스가 <b>바깥 네트워크를 타는가</b>.
     *
     * <p>파일만 읽는 소스(시드·스냅샷)는 공짜라서 <b>기동할 때마다 돌려도 된다.</b>
     * 반면 큐넷 API 는 종목당 1콜이라 613콜이고, 스크래퍼는 상대 사이트에 부담을 준다 —
     * 그건 배치와 명시적 요청에만 맡긴다.
     */
    default boolean usesNetwork() {
        return true;
    }

    /** 실행 순서. 낮을수록 먼저 쓰고, 나중에 쓴 소스가 같은 키를 덮는다. */
    default int priority() {
        return PRIORITY_SCRAPER;
    }

    /**
     * <b>기동할 때만</b> 도는 소스인가(스냅샷). 배치·재수집에서는 건너뛴다 —
     * 실 API 가 받아온 최신 값을 낡은 파일이 매일 새벽 덮으면 안 된다.
     */
    default boolean startupOnly() {
        return false;
    }

    /**
     * 종목코드를 지정한 <b>부분 조회</b>가 되는가. 큐넷 API 는 jmCd 별 호출이라 된다.
     * 스크래퍼는 페이지 하나를 통째로 읽는 구조라 "몇 종목만"이 없다.
     *
     * <p>이 값은 <b>부를지 말지</b>가 아니라 <b>어떻게 부를지</b>를 가른다. 지원하지 않는 소스도
     * 담당 기관이 걸리면 부른다 — 사이트를 한 번 읽고 걸러 주면 되니까. 부르는 조건은
     * {@code CollectService.runForCodes} 에 있다.
     */
    default boolean supportsPartialFetch() {
        return false;
    }

    /**
     * 접수 임박 종목만 재확인(17:00 배치)·매니저 재수집용. 종목코드 필터.
     *
     * <p>기본 구현은 <b>전량을 읽고 코드로 거른다</b> — 스크래퍼가 이 경로로 들어온다.
     * 예전엔 여기서 예외를 던졌는데, 그러면 매니저가 KCA 종목에 "다시 받아오기"를 눌렀을 때
     * 스크래퍼가 예외로 죽고 아무 일도 안 일어났다(2026-09-04 실측). 큐넷처럼 종목별 호출이
     * 되는 소스만 이 메서드를 재정의해 필요한 것만 부른다.
     */
    default List<CollectedSchedule> fetchByCertificateCodes(List<String> sourceCodes) {
        return fetchAll().stream()
                .filter(c -> sourceCodes.contains(c.sourceCode()))
                .toList();
    }

    /**
     * 이 소스가 <b>지금 실제로</b> 맡고 있는 시행기관. 매니저 화면이 "자동/수기"를 판정하는 근거다 —
     * 행의 출처(provenance)가 scraped 라는 것은 "언젠가 누가 긁었다"일 뿐, 살아 있는 소스가
     * 그 기관을 맡고 있어야 자동이다. 파일·데모 소스는 아무 기관도 맡지 않는다(빈 집합).
     * 매칭 규칙은 {@code AgencyMatcher}(괄호 접미사 무시·양방향 포함).
     */
    default Set<String> coveredAgencies() {
        return Set.of();
    }
}

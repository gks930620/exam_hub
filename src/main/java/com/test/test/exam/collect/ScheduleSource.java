package com.test.test.exam.collect;

import java.util.List;

/**
 * 시험 일정 수집 소스 추상화 (DR-01, 리스크 1순위 대응).
 * 공공 API 스펙 변경/소스 추가·교체에 대비해 수집기를 인터페이스로 격리한다.
 * 구현: {@link MockScheduleSource}(키 없음/개발), {@link QnetApiScheduleSource}(실 큐넷 API).
 */
public interface ScheduleSource {

    /** crawl_log.source 에 기록될 소스 식별자 (예: QNET_API, MOCK). */
    String sourceId();

    /** 전체 종목 일정 수집. */
    List<CollectedSchedule> fetchAll();

    /**
     * 접수 임박 종목만 재확인(17:00 배치용). 종목코드 필터.
     * 기본 구현은 fetchAll 후 필터 (소스가 부분 조회를 지원하면 오버라이드).
     */
    default List<CollectedSchedule> fetchByCertificateCodes(List<String> sourceCodes) {
        return fetchAll().stream()
                .filter(c -> sourceCodes.contains(c.sourceCode()))
                .toList();
    }
}

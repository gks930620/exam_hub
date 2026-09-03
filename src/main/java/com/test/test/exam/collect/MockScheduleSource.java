package com.test.test.exam.collect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 큐넷 API 키가 없을 때 동작하는 목(mock) 수집 소스.
 * {@code qnet.api.enabled=false}(기본) 일 때 활성 — 실 API 키 없이도 수집→diff→알림 파이프라인 전체를 검증한다.
 * (설계 지시: 공공 API 키 부재 시 seed/mock 으로 동작)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "qnet.api.enabled", havingValue = "false", matchIfMissing = true)
public class MockScheduleSource implements ScheduleSource {

    private final DemoDataProvider demoDataProvider;

    @Override
    public boolean usesNetwork() {
        return false;   // 파일만 읽는다
    }

    /** 데모 데이터는 시드와 같은 급 — 실데이터가 있으면 그쪽이 덮는다. */
    @Override
    public int priority() {
        return PRIORITY_FILE;
    }

    @Override
    public String sourceId() {
        return "MOCK";
    }

    @Override
    public List<CollectedSchedule> fetchAll() {
        List<CollectedSchedule> data = demoDataProvider.collectedSchedules();
        log.info("[MockScheduleSource] 데모 일정 {}건 반환 (실 큐넷 API 미연동)", data.size());
        return data;
    }
}

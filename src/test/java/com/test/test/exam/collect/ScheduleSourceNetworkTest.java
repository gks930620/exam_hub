package com.test.test.exam.collect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 수집 소스가 <b>네트워크를 타는지</b>의 분류.
 *
 * <p>로컬은 인메모리 DB라 재시작하면 일정이 사라진다. 그래서 <b>파일만 읽는 소스는 기동마다</b>
 * 돌려 화면이 비지 않게 한다. 반대로 네트워크 소스를 기동마다 돌리면 큐넷은 613콜을 쓰고
 * (하루 한도 1,000) 스크래퍼는 남의 사이트를 두드린다.
 *
 * <p>여기서 지키는 것은 <b>틀리는 방향</b>이다. 파일 소스를 네트워크로 잘못 분류하면 화면이
 * 비는 정도지만, 네트워크 소스를 파일로 잘못 분류하면 <b>재시작할 때마다 바깥을 두드린다.</b>
 * 그래서 기본값이 "네트워크를 탄다"여야 한다 — 새 소스를 추가하며 아무것도 안 적어도 안전하다.
 */
class ScheduleSourceNetworkTest {

    /** 새 소스가 아무 선언도 안 했을 때 어느 쪽으로 기우는가 — 안전한 쪽이어야 한다. */
    @Test
    @DisplayName("아무것도 안 밝히면 네트워크를 타는 것으로 본다")
    void defaults_to_network() {
        ScheduleSource unspecified = new ScheduleSource() {
            @Override
            public String sourceId() {
                return "NEW_SOURCE";
            }

            @Override
            public List<CollectedSchedule> fetchAll() {
                return List.of();
            }
        };

        assertTrue(unspecified.usesNetwork(),
                "기본이 '파일'이면 새 소스가 기동마다 바깥을 두드리게 된다");
    }

    @Test
    @DisplayName("큐넷 일정 스냅샷은 파일만 읽는다")
    void qnet_snapshot_is_offline() {
        assertFalse(new QnetSeedScheduleSource(new ObjectMapper()).usesNetwork());
    }

    @Test
    @DisplayName("비큐넷 시험 시드는 파일만 읽는다")
    void nonqnet_seed_is_offline() {
        assertFalse(new SeedFileScheduleSource(new ObjectMapper()).usesNetwork());
    }
}

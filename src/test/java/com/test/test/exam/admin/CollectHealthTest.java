package com.test.test.exam.admin;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.CrawlLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>수집이 고장난 것을 매니저가 알 수 있는가.</b>
 *
 * <p>지금까지 수집 결과는 {@code crawl_log} 테이블과 서버 로그에만 남았다. 화면도 API 도 없어서
 * 매니저가 볼 방법이 없었다 — 코드 주석은 "운영 주 1회 crawl_log 점검으로 커버"라고 적혀 있었지만,
 * 점검할 <b>수단이 없었다.</b>
 *
 * <p>그리고 더 조용한 고장이 있다: 스크래퍼가 <b>0건을 가져와도 "성공"</b> 이다. 시행처가 화면을
 * 개편하면 선택자가 안 맞아 0건이 오는데, 예외가 아니라 성공으로 기록된다. 그동안 화면에는
 * <b>옛 일정이 그대로</b> 보이고, 매니저도 사용자도 아무 이상을 못 느낀다.
 * 이 서비스에서 가장 나쁜 실패는 <b>틀린 걸 모르는 것</b>이다.
 */
class CollectHealthTest {

    private static CrawlLog log(String source, boolean success, int fetched, LocalDateTime at) {
        return log(source, success, fetched, at, false);
    }

    private static CrawlLog log(String source, boolean success, int fetched, LocalDateTime at, boolean partial) {
        CrawlLog l = CrawlLog.start(source, partial);
        org.springframework.test.util.ReflectionTestUtils.setField(l, "startedAt", at);
        if (success) {
            l.finishSuccess(fetched, 0, 0, 0, 0);
        } else {
            l.finishFailure("연결 실패");
        }
        return l;
    }

    @Test
    @DisplayName("한 번도 안 돈 소스는 '기록 없음'으로 나온다 — 조용히 빠지지 않는다")
    void never_ran_is_reported() {
        CollectHealth h = CollectHealth.of("HSK_WEB", List.of(), TimeUtil.now());

        assertEquals(CollectHealth.State.NEVER_RAN, h.getState());
        assertTrue(h.isNeedsAttention(), "한 번도 안 돈 소스를 정상으로 보면 안 된다");
    }

    @Test
    @DisplayName("마지막 실행이 실패면 그 사실과 사유를 보여준다")
    void failure_is_reported_with_reason() {
        LocalDateTime now = TimeUtil.now();
        CollectHealth h = CollectHealth.of("KCA_WEB", List.of(log("KCA_WEB", false, 0, now.minusHours(3))), now);

        assertEquals(CollectHealth.State.FAILED, h.getState());
        assertTrue(h.isNeedsAttention());
        assertTrue(h.getMessage().contains("연결 실패"), "사유를 안 보여주면 매니저가 손댈 데가 없다: " + h.getMessage());
    }

    /**
     * <b>가장 조용한 고장.</b> 예외 없이 0건이 오면 지금까지는 "성공"이었다.
     * 시행처가 화면을 개편하면 이렇게 된다 — 옛 일정이 그대로 서비스되고 아무도 모른다.
     */
    @Test
    @DisplayName("성공했지만 0건을 가져왔으면 고장으로 본다")
    void success_with_zero_rows_is_a_failure() {
        LocalDateTime now = TimeUtil.now();
        CollectHealth h = CollectHealth.of("TOPIK_WEB", List.of(log("TOPIK_WEB", true, 0, now.minusHours(3))), now);

        assertEquals(CollectHealth.State.EMPTY, h.getState());
        assertTrue(h.isNeedsAttention(), "0건을 정상으로 보면 사이트 개편을 영영 못 알아챈다");
    }

    @Test
    @DisplayName("오래 안 돌았으면 멈춘 것으로 본다 — 배치가 죽어도 화면은 멀쩡해 보인다")
    void stale_run_is_reported() {
        LocalDateTime now = TimeUtil.now();
        CollectHealth h = CollectHealth.of("YBM_WEB", List.of(log("YBM_WEB", true, 30, now.minusDays(5))), now);

        assertEquals(CollectHealth.State.STALE, h.getState());
        assertTrue(h.isNeedsAttention());
    }

    @Test
    @DisplayName("어제 성공했고 건수도 있으면 정상이다")
    void healthy_run_needs_no_attention() {
        LocalDateTime now = TimeUtil.now();
        CollectHealth h = CollectHealth.of("YBM_WEB", List.of(log("YBM_WEB", true, 112, now.minusHours(10))), now);

        assertEquals(CollectHealth.State.OK, h.getState());
        assertFalse(h.isNeedsAttention());
        assertEquals(112, h.getFetched());
    }

    @Test
    @DisplayName("연속 실패 횟수를 센다 — 한 번 실패와 사흘째 실패는 급함이 다르다")
    void counts_consecutive_failures() {
        LocalDateTime now = TimeUtil.now();
        CollectHealth h = CollectHealth.of("KCA_WEB", List.of(
                log("KCA_WEB", false, 0, now.minusHours(3)),
                log("KCA_WEB", false, 0, now.minusDays(1)),
                log("KCA_WEB", false, 0, now.minusDays(2)),
                log("KCA_WEB", true, 18, now.minusDays(3))), now);

        assertEquals(3, h.getConsecutiveFailures());
    }

    /**
     * <b>거짓 경보를 내지 않는다.</b> 매니저가 종목 몇 개를 다시 받으면, 그 종목을 안 맡는 소스는
     * 0건이 <b>정상</b>이다 — 큐넷 API 는 4자리 종목코드가 없으면 아예 호출하지 않는다.
     * 그 0건을 전체 배치의 0건과 같게 보면 "고장"이라고 뜨고, 매니저는 경고를 무시하게 된다.
     */
    @Test
    @DisplayName("부분 수집(재수집)의 0건은 고장이 아니다 — 전체 배치만 본다")
    void partial_runs_are_not_judged() {
        LocalDateTime now = TimeUtil.now();
        CollectHealth h = CollectHealth.of("QNET_API", List.of(
                log("QNET_API", true, 0, now.minusMinutes(10), true),   // 매니저가 비큐넷 종목만 재수집
                log("QNET_API", true, 2469, now.minusHours(12))), now); // 오늘 새벽 전체 배치는 멀쩡

        assertEquals(CollectHealth.State.OK, h.getState(),
                "부분 수집의 0건 때문에 멀쩡한 소스가 고장으로 떴다");
        assertEquals(2469, h.getFetched());
    }

    @Test
    @DisplayName("부분 수집 기록밖에 없으면 '기록 없음'이다 — 0건이라고 단정하지 않는다")
    void only_partial_runs_means_unknown() {
        LocalDateTime now = TimeUtil.now();
        CollectHealth h = CollectHealth.of("QNET_API",
                List.of(log("QNET_API", true, 0, now.minusMinutes(10), true)), now);

        assertEquals(CollectHealth.State.NEVER_RAN, h.getState());
    }

    /** 최근 것이 먼저 오든 나중에 오든 같은 답이어야 한다 — 정렬에 기대면 조용히 틀린다. */
    @Test
    @DisplayName("기록 순서가 뒤집혀 있어도 가장 최근 것을 본다")
    void picks_the_latest_regardless_of_order() {
        LocalDateTime now = TimeUtil.now();
        List<CrawlLog> reversed = List.of(
                log("YBM_WEB", false, 0, now.minusDays(2)),
                log("YBM_WEB", true, 112, now.minusHours(2)));

        CollectHealth h = CollectHealth.of("YBM_WEB", reversed, now);

        assertEquals(CollectHealth.State.OK, h.getState());
        assertEquals(112, h.getFetched());
    }
}

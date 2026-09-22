package com.test.test.exam.admin;

import com.test.test.exam.collect.QnetExamInfoCollector;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시험 상세 정보(응시료·시험과목·검정방법·합격기준)를 <b>지금 한 묶음 채운다</b>.
 *
 * <p>평소에는 주 1회 배치가 조금씩 채운다({@code ExamInfoScheduler}). 그런데 새로 붙인 시험이나
 * 비어 보이는 시험을 <b>다음 주까지 기다리지 않고</b> 채우고 싶을 때가 있다.
 *
 * <p><b>한 번에 {@code qnet.info.batch-size} 만큼만 나간다.</b> 눌러도 호출량이 정해져 있어서
 * 실수로 하루 한도를 태울 수 없다 — 큐넷 한도가 1,000회인데 일정 수집이 이미 613콜을 쓴다.
 * 종목 전량을 한 번에 지르는 버튼은 두지 않는다.
 *
 * <p>{@code /api/admin/**} 이라 ADMIN 만 부를 수 있다.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/exam-info")
@RequiredArgsConstructor
public class AdminExamInfoController {

    private final QnetExamInfoCollector collector;

    @PostMapping
    public ResponseEntity<FillResponse> fillNext() {
        int saved = collector.collectNext();
        long total = collector.collectedCount();
        log.info("[매니저] 시험 정보 채우기 — {}종 저장(누적 {}종)", saved, total);
        return ResponseEntity.ok(new FillResponse(saved, total, message(saved)));
    }

    private String message(int saved) {
        if (saved > 0) {
            return saved + "종을 채웠습니다. 더 있으면 다시 누르세요.";
        }
        return "채울 것이 없습니다. 꺼져 있거나(QNET_INFO_ENABLED) 이미 다 받았습니다.";
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FillResponse {
        /** 이번에 채운 종목 수 */
        private int saved;
        /** 지금까지 채운 전체 */
        private long total;
        private String message;
    }
}

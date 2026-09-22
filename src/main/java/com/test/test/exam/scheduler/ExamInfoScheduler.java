package com.test.test.exam.scheduler;

import com.test.test.exam.collect.QnetExamInfoCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시험 상세 정보(응시료·시험과목·검정방법·합격기준)를 <b>조금씩</b> 채운다.
 *
 * <p><b>왜 매일이 아닌가</b>: 이 정보는 일정과 달리 거의 안 변한다. 응시료나 시험과목은 해가
 * 바뀔 때나 달라진다. 매일 받을 이유가 없고, 받으면 큐넷 호출 예산만 축낸다.
 *
 * <p><b>왜 07:00 인가</b>: 일정 수집(05:00)이 <b>먼저</b> 예산을 쓰게 하려고 뒤에 둔다.
 * 큐넷 하루 한도가 1,000회인데 일정 수집이 613콜을 쓴다. 한도가 계정당인지 오퍼레이션당인지는
 * 문서마다 다르게 적혀 있어 확실하지 않다 — 모르면 <b>심장 쪽에 먼저 준다.</b>
 * 일정이 없으면 이 서비스는 할 일이 없지만, 응시료가 없어도 알림은 나간다.
 *
 * <p>한 번에 {@code qnet.info.batch-size} 만큼만 받고 안 받은 것부터 채우므로,
 * 매주 돌면 몇 달에 걸쳐 전량이 채워지고 그 뒤로는 오래된 것만 다시 받는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamInfoScheduler {

    private final QnetExamInfoCollector collector;

    /** 매주 월요일 07:00 — 일정 수집(05:00)과 고장 경보(06:00) 다음이다. */
    @Scheduled(cron = "0 0 7 * * MON", zone = "Asia/Seoul")
    public void collectSome() {
        int saved = collector.collectNext();
        if (saved > 0) {
            log.info("[Scheduler] 시험 상세 정보 {}종 채움 (누적 {}종)", saved, collector.collectedCount());
        }
    }
}

package com.test.test.exam.collect;

import com.test.test.exam.notification.NotificationScheduleService;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.CrawlLogRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import com.test.test.exam.repository.NotificationScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 매니저가 "다시 받아오기"를 눌렀을 때 <b>어떤 수집기를 부르는가</b>.
 *
 * <p>기관 이름으로만 고르면 <b>표기 차이에서 조용히 깨진다.</b> JLPT 가 그랬다(2026-09-09):
 * 소스는 "JEES / 국제교류기금", 시험 쪽은 "일본국제교류기금·JEES" 라 서로 포함되지 않아
 * 그 수집기가 영영 안 돌았다. 아무 오류도 안 나고 그냥 아무 일도 안 일어난다.
 *
 * <p>그래서 소스가 <b>종목코드를 대 놓고 밝혔으면</b> 그걸 먼저 본다 — 코드는 표기가 흔들리지 않는다.
 */
class ReCollectSourcePickTest {

    private final CertificateRepository certs = Mockito.mock(CertificateRepository.class);
    private final ExamScheduleRepository schedules = Mockito.mock(ExamScheduleRepository.class);

    /** 기관 이름은 안 맞고 종목코드만 맞는 소스. */
    private ScheduleSource sourceThatOnlyKnowsCodes(AtomicBoolean ran, Set<String> codes) {
        return new ScheduleSource() {
            @Override public String sourceId() { return "SPELLING_MISMATCH_WEB"; }
            @Override public int priority() { return 50; }
            @Override public Set<String> coveredAgencies() { return Set.of("JEES / 국제교류기금"); }
            @Override public Set<String> coveredExamCodes() { return codes; }
            @Override public List<CollectedSchedule> fetchAll() { return List.of(); }
            @Override public List<CollectedSchedule> fetchByCertificateCodes(List<String> requested) {
                ran.set(true);
                return List.of();
            }
        };
    }

    private CollectService serviceWith(ScheduleSource source) {
        Mockito.when(certs.findBySourceCodeIn(Mockito.anyList())).thenReturn(List.of());
        Mockito.when(schedules.findByCertificateIdInAndStatus(Mockito.anyList(), Mockito.any()))
                .thenReturn(List.of());
        return new CollectService(List.of(source), Mockito.mock(DiffService.class),
                Mockito.mock(NotificationScheduleService.class), Mockito.mock(CrawlLogRepository.class),
                schedules, certs, Mockito.mock(NotificationScheduleRepository.class));
    }

    @Test
    @DisplayName("기관 이름이 안 맞아도 종목코드를 밝힌 수집기는 부른다")
    void a_source_is_picked_by_the_codes_it_declares() {
        AtomicBoolean ran = new AtomicBoolean(false);

        serviceWith(sourceThatOnlyKnowsCodes(ran, Set.of("JLPT", "M0336"))).collectByCodes(List.of("JLPT"));

        assertTrue(ran.get(), "종목코드가 맞는데도 그 수집기를 안 불렀다 — 매니저가 눌러도 아무 일이 안 일어난다");
    }

    /** 상관없는 시험을 다시 받자고 시행처를 두드리면 안 된다 — 그러려고 조건을 좁혀 둔 것이다. */
    @Test
    @DisplayName("맡지 않는 종목이면 그 수집기는 안 부른다")
    void an_unrelated_source_is_left_alone() {
        AtomicBoolean ran = new AtomicBoolean(false);

        serviceWith(sourceThatOnlyKnowsCodes(ran, Set.of("JLPT"))).collectByCodes(List.of("M0473"));

        assertFalse(ran.get(), "맡지도 않는 종목 때문에 시행처를 두드렸다");
    }
}

package com.test.test.exam.collect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 스냅샷 시드는 <b>기동 전용</b>이고, DB 가 파일보다 새로우면 건너뛴다.
 *
 * <p>로컬 DB 가 파일 H2 가 되면서 재기동마다 2,600건을 다시 upsert 하는 것은 낭비고,
 * 더 큰 문제는 실 API 로 받은 최신 값을 낡은 스냅샷이 덮는 것이다. 그래서 DB 의 API 출처
 * 최신 수집 시각이 파일의 수집일보다 뒤면 아무것도 하지 않는다.
 *
 * <p>테스트 설정은 이 빈을 끄므로({@code seed.snapshot.enabled=false}) 직접 만들어 검사한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SnapshotScheduleSourceTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExamScheduleRepository examScheduleRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    private SnapshotScheduleSource source() {
        return new SnapshotScheduleSource(objectMapper, examScheduleRepository);
    }

    @Test
    @DisplayName("스냅샷은 파일 소스이고 기동 전용이며 가장 먼저 쓴다(실데이터가 나중에 덮도록)")
    void snapshot_is_startup_only_file_source_with_lowest_priority() {
        SnapshotScheduleSource s = source();
        assertFalse(s.usesNetwork());
        assertTrue(s.startupOnly(), "스냅샷이 배치에서도 돈다 — 실 API 값을 낡은 파일이 덮는다");
        assertEquals(0, s.priority());
        assertTrue(s.coveredAgencies().isEmpty(), "파일 소스가 기관을 맡았다고 주장한다");
    }

    @Test
    @DisplayName("DB 에 API 출처 행이 없으면 스냅샷을 읽는다")
    void loads_when_db_has_no_api_rows() {
        assertFalse(source().fetchAll().isEmpty(), "스냅샷 파일을 읽지 못했다");
    }

    @Test
    @DisplayName("DB 의 API 수집 시각이 파일보다 새로우면 건너뛴다")
    void skips_when_db_api_rows_are_newer_than_file() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Certificate cert = certificateRepository.save(Certificate.builder()
                .name("스냅샷비교 " + unique).slug("스냅샷비교-" + unique)
                .series(Series.ETC).agency("한국산업인력공단").build());
        examScheduleRepository.save(ExamSchedule.builder()
                .certificate(cert).year(2099).round(1).examType(ExamType.WRITTEN)
                .examStartDate(LocalDate.of(2099, 1, 1))
                .provenance(ScheduleProvenance.API)
                .collectedAt(TimeUtil.now())   // 파일(2026-09-01)보다 뒤
                .build());

        assertTrue(source().fetchAll().isEmpty(), "DB 가 더 새로운데 스냅샷을 다시 얹는다");
    }
}

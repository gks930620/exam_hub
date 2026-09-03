package com.test.test.exam.collect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 비(非)큐넷 시험 통합 수집 소스.
 * <p>{@code seed/non_qnet_exams.json}(07 카탈로그의 큐넷 밖 시험 — 한국사·어학·민간 IT/사무·금융)을 읽어
 * {@link CollectedSchedule} 로 변환한다. {@link CollectService} 가 다른 소스(Mock/Qnet)와 함께 자동 순회하므로,
 * 기동 시 {@code collectAll()} 한 번으로 Certificate + ExamSchedule + notification_schedule 이 파생된다.
 * <p>사용자 요청마다 사이트를 스크래핑하지 않고 <b>한 번 DB에 적재</b>하는 방식(일정은 실시간이 아님).
 * 로컬/개발에서만 활성({@code @Profile("!prod")}), 운영은 소스별 실 스크래퍼가 대체할 자리.
 * 테스트는 {@code seed.nonqnet.enabled=false} 로 비활성(기존 통합테스트 픽스처 불변).
 */
@Slf4j
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "seed.nonqnet.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SeedFileScheduleSource implements ScheduleSource {

    private static final String RESOURCE = "seed/non_qnet_exams.json";

    private final ObjectMapper objectMapper;

    @Override
    public boolean usesNetwork() {
        return false;   // 파일만 읽는다
    }

    /** 시드는 가장 먼저 쓴다 — 스크래퍼·API 실데이터가 뒤에 와서 덮도록. */
    @Override
    public int priority() {
        return PRIORITY_FILE;
    }

    @Override
    public String sourceId() {
        return "SEED_NONQNET";
    }

    @Override
    public List<CollectedSchedule> fetchAll() {
        SeedFile file = read();
        if (file == null || file.exams() == null) {
            return List.of();
        }
        List<CollectedSchedule> out = new ArrayList<>();
        for (SeedExam exam : file.exams()) {
            if (exam.schedules() == null) {
                continue;
            }
            for (SeedSchedule s : exam.schedules()) {
                out.add(new CollectedSchedule(
                        exam.sourceCode(), exam.name(), Series.ETC, exam.agency(), exam.category(),
                        s.year(), s.round(), parseType(s.examType()),
                        s.regStart(), s.regEnd(), s.examStart(), s.examEnd(), s.result(),
                        exam.sourceUrl(),
                        ScheduleProvenance.from(exam.provenance())));
            }
        }
        log.info("[SeedFileScheduleSource] 비큐넷 시험 {}종 → 일정 {}건 반환", file.exams().size(), out.size());
        return out;
    }

    private ExamType parseType(String raw) {
        try {
            return ExamType.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ExamType.WRITTEN; // 필기/실기 구분 없는 시험은 WRITTEN 로 통일
        }
    }

    private SeedFile read() {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.exists()) {
            log.warn("[SeedFileScheduleSource] {} 없음 — 비큐넷 시드 스킵", RESOURCE);
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, SeedFile.class);
        } catch (Exception e) {
            log.error("[SeedFileScheduleSource] {} 파싱 실패 — 비큐넷 시드 스킵", RESOURCE, e);
            return null;
        }
    }

    // ===== JSON 매핑 DTO =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SeedFile(List<SeedExam> exams) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SeedExam(String name, String agency, String category,
                    String sourceCode, String sourceUrl, String provenance,
                    List<SeedSchedule> schedules) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SeedSchedule(int year, int round, String examType,
                        LocalDateTime regStart, LocalDateTime regEnd,
                        LocalDate examStart, LocalDate examEnd, LocalDate result) {
    }
}

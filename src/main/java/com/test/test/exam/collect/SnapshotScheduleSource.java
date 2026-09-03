package com.test.test.exam.collect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;
import com.test.test.exam.repository.ExamScheduleRepository;
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
 * 전 소스 시험일정 <b>스냅샷</b> 시드 — 로컬에서 API 호출 없이 일정을 보기 위한 것.
 *
 * <p><b>왜 필요한가</b>: 켤 때마다 실 API 를 부르면 <b>613콜</b>이 나가서 하루 한도(1,000)를 금방 넘긴다 —
 * 실제로 재시작 몇 번에 한도를 태워 그날 아무것도 못 받은 적이 있다.
 * 그래서 한 번 받은 결과를 파일로 두고, 기동할 때 그대로 얹는다.
 *
 * <p><b>기동 전용</b>({@link #startupOnly()})이고 <b>가장 먼저</b> 쓴다({@link #priority()} 0) —
 * 배치에서 실 API 가 받아온 최신 값을 낡은 파일이 덮으면 안 되고, 기동 시에도 실데이터 소스가 뒤에 오면 그쪽이 이긴다.
 * DB 의 API 출처 최신 수집일이 파일의 수집일보다 뒤면 아예 읽지 않는다 — 파일 H2 로 바뀐 뒤 재기동마다
 * 2,600건을 다시 쓰는 것은 낭비다.
 *
 * <p><b>운영에서는 안 쓴다</b>({@code @Profile("!prod")}). 운영은 매일 05:00 에 실 API 가 받아오므로
 * 스냅샷은 오히려 낡은 값이 된다.
 */
@Slf4j
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "seed.snapshot.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SnapshotScheduleSource implements ScheduleSource {

    private static final String RESOURCE = "seed/schedules_snapshot.json";

    private final ObjectMapper objectMapper;
    private final ExamScheduleRepository examScheduleRepository;

    @Override
    public boolean usesNetwork() {
        return false;   // 파일만 읽는다
    }

    @Override
    public int priority() {
        return PRIORITY_FILE;
    }

    @Override
    public boolean startupOnly() {
        return true;
    }

    @Override
    public String sourceId() {
        return "SEED_SNAPSHOT";
    }

    @Override
    public List<CollectedSchedule> fetchAll() {
        SeedFile file = read();
        if (file == null || file.exams() == null || file.exams().isEmpty()) {
            return List.of();
        }
        if (dbIsNewerThan(file)) {
            return List.of();
        }
        List<CollectedSchedule> out = new ArrayList<>();
        for (SeedExam exam : file.exams()) {
            if (exam.schedules() == null) {
                continue;
            }
            for (SeedSchedule s : exam.schedules()) {
                out.add(new CollectedSchedule(
                        exam.sourceCode(), exam.name(), Series.ETC,
                        exam.agency() == null ? "한국산업인력공단" : exam.agency(),
                        exam.category(),
                        s.year(), s.round(), parseType(s.examType()),
                        dateTime(s.regStartAt()), dateTime(s.regEndAt()),
                        date(s.examStartDate()), date(s.examEndDate()), date(s.resultDate()),
                        s.sourceUrl() == null ? "https://www.q-net.or.kr/" : s.sourceUrl(),
                        // 수집 당시의 출처를 그대로 되살린다 — 스크래핑해 온 것을 API 로 표시하면
                        // "시행처 확인 필요" 배지가 사라져 사용자가 추정치를 확정으로 읽는다.
                        provenance(s.provenance())));
            }
        }
        log.info("[{}] 일정 스냅샷 {}종 → {}건 (수집일 {})",
                sourceId(), file.exams().size(), out.size(), file.collectedAt());
        return out;
    }

    /** DB 에 이 파일보다 뒤에 받은 API 값이 있으면 파일은 낡은 것이다. */
    private boolean dbIsNewerThan(SeedFile file) {
        LocalDate fileDate = date(file.collectedAt());
        if (fileDate == null || examScheduleRepository == null) {
            return false;
        }
        LocalDateTime dbLatest = examScheduleRepository.findLatestCollectedAt(ScheduleProvenance.API);
        if (dbLatest != null && dbLatest.toLocalDate().isAfter(fileDate)) {
            log.info("[{}] DB 의 API 수집일({})이 스냅샷 수집일({})보다 뒤 — 건너뛴다",
                    sourceId(), dbLatest.toLocalDate(), fileDate);
            return true;
        }
        return false;
    }

    /** 예전 스냅샷(큐넷만 담던 시절)에는 출처 칸이 없다 — 그건 전부 실 API 값이었다. */
    private ScheduleProvenance provenance(String raw) {
        if (raw == null || raw.isBlank()) {
            return ScheduleProvenance.API;
        }
        try {
            return ScheduleProvenance.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return ScheduleProvenance.API;
        }
    }

    private ExamType parseType(String raw) {
        return "PRACTICAL".equalsIgnoreCase(raw) ? ExamType.PRACTICAL : ExamType.WRITTEN;
    }

    /** 저장 형식은 API 응답을 그대로 옮긴 것이라 {@code 2026-01-12T10:00} 또는 {@code 2026-01-12} 다. */
    private LocalDateTime dateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return raw.contains("T") ? LocalDateTime.parse(raw) : LocalDate.parse(raw).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate date(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.length() > 10 ? raw.substring(0, 10) : raw);
        } catch (Exception e) {
            return null;
        }
    }

    private SeedFile read() {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.exists()) {
            log.info("[{}] {} 없음 — 스냅샷 없이 시작한다(실 API 를 켜면 채워진다)", sourceId(), RESOURCE);
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, SeedFile.class);
        } catch (Exception e) {
            log.error("[{}] {} 파싱 실패 — 스킵", sourceId(), RESOURCE, e);
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SeedFile(String collectedAt, List<SeedExam> exams) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SeedExam(String name, String sourceCode, String agency, String category,
                    List<SeedSchedule> schedules) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SeedSchedule(int year, int round, String examType,
                        String regStartAt, String regEndAt,
                        String examStartDate, String examEndDate, String resultDate,
                        String provenance, String sourceUrl) {
    }
}

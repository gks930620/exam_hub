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
 * 큐넷 시험일정 <b>스냅샷</b> 시드 — 로컬에서 API 호출 없이 일정을 보기 위한 것.
 *
 * <p><b>왜 필요한가</b>: 로컬 DB 는 인메모리라 서버를 끄면 수집한 1,500여 건이 사라진다.
 * 그렇다고 켤 때마다 실 API 를 부르면 <b>613콜</b>이 나가서 하루 한도(1,000)를 금방 넘긴다 —
 * 실제로 재시작 몇 번에 한도를 태워 그날 아무것도 못 받은 적이 있다.
 * 그래서 한 번 받은 결과를 파일로 두고, 기동할 때 그대로 얹는다.
 *
 * <p><b>운영에서는 안 쓴다</b>({@code @Profile("!prod")}). 운영은 매일 05:00 에 실 API 가 받아오므로
 * 스냅샷은 오히려 낡은 값이 된다.
 *
 * <p>실 수집이 같이 돌면 같은 {@code (시험, 연도, 회차, 구분)} 키를 덮어쓴다 —
 * 스냅샷이 실데이터를 밀어내지 않는다.
 */
@Slf4j
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "seed.qnet-schedules.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class QnetSeedScheduleSource implements ScheduleSource {

    private static final String RESOURCE = "seed/qnet_schedules.json";

    private final ObjectMapper objectMapper;

    @Override
    public boolean usesNetwork() {
        return false;   // 파일만 읽는다
    }

    @Override
    public String sourceId() {
        return "SEED_QNET";
    }

    @Override
    public List<CollectedSchedule> fetchAll() {
        SeedFile file = read();
        if (file == null || file.exams() == null || file.exams().isEmpty()) {
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
                        "https://www.q-net.or.kr/",
                        // 실 API 에서 받은 값을 저장해 둔 것이라 출처는 API 다.
                        // 다만 스냅샷이라 시행처가 바꾸면 낡는다 — 실 수집이 돌면 덮어쓴다.
                        ScheduleProvenance.API));
            }
        }
        log.info("[{}] 큐넷 일정 스냅샷 {}종 → {}건 (수집일 {})",
                sourceId(), file.exams().size(), out.size(), file.collectedAt());
        return out;
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
                        String examStartDate, String examEndDate, String resultDate) {
    }
}

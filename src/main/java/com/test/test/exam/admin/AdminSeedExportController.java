package com.test.test.exam.admin;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 지금 DB 에 있는 큐넷 일정을 <b>시드 파일 형식으로</b> 내보낸다.
 *
 * <p><b>왜 필요한가</b>: 로컬 DB 는 인메모리라 서버를 끄면 수집한 1,500여 건이 사라진다.
 * 그렇다고 켤 때마다 실 API 를 부르면 613콜이라 하루 한도(1,000)를 금방 넘긴다.
 * 그래서 한 번 받은 걸 파일로 떠서 {@code seed/qnet_schedules.json} 에 두고
 * ({@link com.test.test.exam.collect.QnetSeedScheduleSource} 가 읽는다), 다음부터는 공짜로 얹는다.
 *
 * <pre>
 * curl -H "Authorization: Bearer &lt;매니저토큰&gt;" http://localhost:8081/api/admin/export/qnet-schedules \
 *   -o src/main/resources/seed/qnet_schedules.json
 * </pre>
 *
 * <p>종목코드가 있는 시험만 담는다 — 시드를 다시 읽을 때 <b>코드로 시험을 찾기</b> 때문이다.
 * 어학·상의 등 다른 소스가 담당하는 시험은 각자 스크래퍼가 매번 받아오므로 여기 넣지 않는다.
 */
@RestController
@RequestMapping("/api/admin/export")
@RequiredArgsConstructor
public class AdminSeedExportController {

    private final CertificateRepository certificateRepository;
    private final ExamScheduleRepository examScheduleRepository;

    @GetMapping(value = "/qnet-schedules", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<SeedFile> exportQnetSchedules() {
        // 큐넷 종목코드는 숫자 4자리(jmCd)다. 다른 소스의 코드(TOEIC·KCA-SEC 등)와 이걸로 가른다.
        List<Certificate> qnetCerts = certificateRepository.findAll().stream()
                .filter(c -> c.getSourceCode() != null && c.getSourceCode().matches("[0-9]{4}"))
                .sorted(Comparator.comparing(Certificate::getName))
                .toList();

        Map<Long, Certificate> byId = new LinkedHashMap<>();
        qnetCerts.forEach(c -> byId.put(c.getId(), c));

        Map<Long, List<ExamSchedule>> schedules = examScheduleRepository
                .findByCertificateIdInAndStatus(List.copyOf(byId.keySet()), ScheduleStatus.ACTIVE)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(s -> s.getCertificate().getId()));

        List<SeedExam> exams = new ArrayList<>();
        int total = 0;
        for (Certificate c : qnetCerts) {
            List<ExamSchedule> rows = schedules.get(c.getId());
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            List<SeedSchedule> out = rows.stream()
                    .sorted(Comparator.comparingInt(ExamSchedule::getYear)
                            .thenComparingInt(ExamSchedule::getRound)
                            .thenComparing(s -> s.getExamType().name()))
                    .map(SeedSchedule::of)
                    .toList();
            total += out.size();
            exams.add(new SeedExam(c.getName(), c.getSourceCode(), c.getAgency(), c.getCategory(), out));
        }

        return ResponseEntity.ok(new SeedFile(
                "큐넷 시험일정 스냅샷 — 로컬에서 API 호출 없이 일정을 보기 위한 시드.",
                "로컬 DB 는 인메모리라 서버를 끄면 사라지고, 매번 받으면 613콜이라 하루 한도(1,000)를 넘긴다.",
                "@Profile(\"!prod\") — 운영은 05:00 실 API 가 받아오므로 이 스냅샷을 쓰지 않는다.",
                "스냅샷이라 시행처가 일정을 바꾸면 낡는다. 실 수집이 돌면 같은 (연도,회차,구분) 키를 덮어쓴다.",
                "GET /api/admin/export/qnet-schedules 로 다시 뜬다(AdminSeedExportController).",
                TimeUtil.today().toString(), exams.size(), total, exams));
    }

    public record SeedFile(String _comment, String _why, String _prod, String _caution, String _howToRegenerate,
                           String collectedAt, int count, int scheduleCount, List<SeedExam> exams) {
    }

    public record SeedExam(String name, String sourceCode, String agency, String category,
                           List<SeedSchedule> schedules) {
    }

    public record SeedSchedule(int year, int round, String examType,
                               String regStartAt, String regEndAt,
                               String examStartDate, String examEndDate, String resultDate) {
        static SeedSchedule of(ExamSchedule s) {
            return new SeedSchedule(
                    s.getYear(), s.getRound(), s.getExamType().name(),
                    s.getRegStartAt() == null ? null : s.getRegStartAt().toString(),
                    s.getRegEndAt() == null ? null : s.getRegEndAt().toString(),
                    s.getExamStartDate() == null ? null : s.getExamStartDate().toString(),
                    s.getExamEndDate() == null ? null : s.getExamEndDate().toString(),
                    s.getResultDate() == null ? null : s.getResultDate().toString());
        }
    }
}

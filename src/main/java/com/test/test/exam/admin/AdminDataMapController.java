package com.test.test.exam.admin;

import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 매니저용 <b>데이터 지도</b> — "지금 뭐가 들어와 있고, 무엇을 내가 넣어야 하나".
 *
 * <p>수기 입력 화면 옆에 이게 없으면 매니저는 <b>무엇을 넣어야 할지 모른다.</b>
 * 자동 수집되는 시험을 손으로 넣다가 다음 수집에 덮어써지는 헛일도 흔하다.
 * 그래서 출처 목록({@link DataSourceCatalog})과 현재 적재량을 같이 준다.
 *
 * <p>{@code /api/admin/**} 이라 ADMIN 만 볼 수 있다.
 */
@RestController
@RequestMapping("/api/admin/data-map")
@RequiredArgsConstructor
public class AdminDataMapController {

    private final CertificateRepository certificateRepository;
    private final ExamScheduleRepository examScheduleRepository;

    @GetMapping
    public ResponseEntity<DataMapResponse> dataMap() {
        // 화면에 보이는 시험만 센다 — 폐지·개칭까지 세면 "일정 없는 시험" 이 24 부풀어
        // 일정 현황 화면(846종)과 숫자가 어긋난다.
        long totalExams = certificateRepository.countVisible();
        long withSchedule = certificateRepository.countVisibleWithSchedule();

        return ResponseEntity.ok(new DataMapResponse(
                new Coverage(totalExams, withSchedule, totalExams - withSchedule),
                DataSourceCatalog.entries().stream().map(SourceRow::of).toList()));
    }

    /**
     * 적재 현황 한 줄. <b>일정이 없는 시험 수</b>가 곧 매니저가 할 일의 크기다 —
     * 이 서비스는 "언제 접수하는지"를 알려주는 게 존재 이유라, 이름만 있는 시험은 반쪽이다.
     */
    public record Coverage(long totalExams, long withSchedule, long withoutSchedule) {
    }

    public record SourceRow(
            String group,
            String exams,
            String mode,
            String modeLabel,
            String modeGuide,
            String sourceName,
            String sourceUrl,
            String checkPath,
            String frequency,
            String note
    ) {
        static SourceRow of(DataSourceCatalog.Entry e) {
            return new SourceRow(
                    e.group(), e.exams(),
                    e.mode().name(), e.mode().getLabel(), e.mode().getGuide(),
                    e.sourceName(), e.sourceUrl(), e.checkPath(), e.frequency(), e.note());
        }
    }

    public record DataMapResponse(Coverage coverage, List<SourceRow> sources) {
    }
}

package com.test.test.exam.admin;

import com.test.test.exam.collect.ScheduleSource;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
    /** 판정을 할 일 화면과 맞추려면 "지금 어떤 소스가 떠 있나"를 여기서도 알아야 한다 */
    private final List<ScheduleSource> sources;

    @GetMapping
    public ResponseEntity<DataMapResponse> dataMap() {
        // 화면에 보이는 시험만 센다 — 폐지·개칭까지 세면 "일정 없는 시험" 이 24 부풀어
        // 일정 현황 화면(841종)과 숫자가 어긋난다.
        // 상시·예약제는 "일정"이 존재하지 않아 채울 대상이 아니다 — 분모에서 빼고 따로 알린다.
        long rolling = certificateRepository.countVisibleRolling();
        long totalExams = certificateRepository.countVisible() - rolling;
        // 일정 없음을 이유별로 — 사람이 넣어야 하는 건 수기 필수뿐이다(설계/시험데이터/05_일정없음_분류)
        java.util.List<com.test.test.exam.domain.Certificate> all = certificateRepository.findAll();
        // "일정 있음" = 살아 있는(ACTIVE) 일정 중 날짜가 하나라도 있는 회차를 가진 시험.
        // 할 일 판정(AdminOverviewController.judge)과 같은 셈법이어야 위(채움률)·아래(할 일) 숫자가 맞는다 —
        // SQL count 로 세면 연도·회차만 넣은 빈 행도 "있음"이 돼 할 일의 '첫 일정 입력'보다 하나 적게 나온다(실측 BJT).
        java.util.Set<Long> having = examScheduleRepository
                .findByCertificateIdInAndStatus(all.stream().map(c -> c.getId()).toList(), com.test.test.exam.domain.ScheduleStatus.ACTIVE)
                .stream().filter(com.test.test.exam.domain.ExamSchedule::hasAnyDate)
                .map(sch -> sch.getCertificate().getId()).collect(java.util.stream.Collectors.toSet());
        long withSchedule = all.stream()
                .filter(c -> c.isVisibleToUsers() && !c.isRollingAdmission() && having.contains(c.getId())).count();
        // 할 일 화면(AdminOverviewController.judge)과 같은 근거를 쓴다 — 안 그러면 같은 시험을
        // 한쪽은 "수기 필수", 다른 쪽은 "공고 전 — 자동"으로 세서 매니저가 헛일을 한다.
        java.util.Set<String> liveExamCodes = sources.stream()
                .flatMap(s -> s.coveredExamCodes().stream())
                .collect(java.util.stream.Collectors.toSet());
        long manual = 0, pending = 0, planned = 0;
        for (com.test.test.exam.domain.Certificate c : all) {
            if (!c.isVisibleToUsers() || c.isRollingAdmission() || having.contains(c.getId())) {
                continue;
            }
            switch (NoScheduleReason.of(c, liveExamCodes)) {
                case MANUAL -> manual++;
                case ANNOUNCEMENT_PENDING -> pending++;
                case CRAWL_PLANNED -> planned++;
            }
        }

        return ResponseEntity.ok(new DataMapResponse(
                new Coverage(totalExams, withSchedule, totalExams - withSchedule, rolling,
                        manual, pending, planned),
                DataSourceCatalog.entries().stream().map(SourceRow::of).toList()));
    }

    /**
     * 적재 현황 한 줄. <b>일정이 없는 시험 수</b>가 곧 매니저가 할 일의 크기다 —
     * 이 서비스는 "언제 접수하는지"를 알려주는 게 존재 이유라, 이름만 있는 시험은 반쪽이다.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coverage {
        private long totalExams;
        private long withSchedule;
        private long withoutSchedule;
        /** 상시·예약제 — 일정 대상 아님 */
        private long rolling;
        /** 일정 없음 중 사람이 넣어야 하는 것 */
        private long manualNeeded;
        /** 일정 없음 중 큐넷 공고 전 — 자동 */
        private long announcementPending;
        /** 일정 없음 중 스크래퍼 붙일 예정 — 자동 */
        private long crawlPlanned;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceRow {
        private String group;
        private String exams;
        private String mode;
        private String modeLabel;
        private String modeGuide;
        private String sourceName;
        private String sourceUrl;
        private String checkPath;
        private String frequency;
        private String note;

        static SourceRow of(DataSourceCatalog.Entry e) {
            return new SourceRow(
                    e.getGroup(), e.getExams(),
                    e.getMode().name(), e.getMode().getLabel(), e.getMode().getGuide(),
                    e.getSourceName(), e.getSourceUrl(), e.getCheckPath(), e.getFrequency(), e.getNote());
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataMapResponse {
        private Coverage coverage;
        private List<SourceRow> sources;
    }
}

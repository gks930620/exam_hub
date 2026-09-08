package com.test.test.exam.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import com.test.test.exam.repository.NotificationScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 상시·예약제 시험 표시 — {@code seed/rolling_exams.json}.
 *
 * <p>AWS·MOS·컴활·운전면허·TOEFL 처럼 <b>원하는 날짜에 신청하는</b> 시험은 "일정"이라는 것이
 * 존재하지 않는다. 표시가 없으면 매니저 화면의 "일정 없음"에 섞여 <b>영원히 못 채우는 숙제</b>로
 * 보이고, 사용자 화면은 "등록해 두면 알려드립니다"라는 지키지 못할 약속을 하게 된다.
 *
 * <p>폐지·개칭({@link CertificateMasterInitializer} 의 lifecycle)과 같은 부류의 <b>마스터
 * 메타데이터</b>라 운영에서도 돈다. 시행처가 정기시험으로 바꾸면 시드에서 빼면 된다 —
 * 기동할 때마다 시드 기준으로 맞추므로 양방향 전환이 다 반영된다.
 *
 * <p>매칭은 <b>종목코드, 안 되면 이름(공백 무시)</b>이다. 같은 시험이 시드마다 다른 코드를 갖는 일이
 * 잦아(로컬 데모의 C011 vs 마스터의 M0024) 코드만 보면 표시가 빠진다.
 *
 * <p>마스터 적재(@Order(100)) 뒤에 돌아야 표시할 행이 존재한다.
 */
@Slf4j
@Component
@Order(110)
@RequiredArgsConstructor
public class RollingAdmissionInitializer {

    private static final String RESOURCE = "seed/rolling_exams.json";

    private static final java.util.List<ScheduleStatus> TO_DROP =
            java.util.List.of(ScheduleStatus.ACTIVE, ScheduleStatus.PENDING_REVIEW);

    private final CertificateRepository certificateRepository;
    private final ExamScheduleRepository examScheduleRepository;
    private final NotificationScheduleRepository notificationScheduleRepository;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void mark() {
        RollingSeed seed = readSeed();
        if (seed.isEmpty()) {
            return;
        }
        int[] marked = {0};
        int[] cleared = {0};
        int[] dropped = {0};
        certificateRepository.findAll().forEach(c -> {
            boolean shouldBe = seed.matches(c);
            if (shouldBe && !c.isRollingAdmission()) {
                c.markRollingAdmission();
                marked[0]++;
                dropped[0] += dropSchedules(c);
            } else if (shouldBe && c.isRollingAdmission()) {
                // 이미 표시된 시험에 나중에 일정이 들어왔을 수도 있다(시드·수집). 상시엔 회차가 없다.
                dropped[0] += dropSchedules(c);
            } else if (!shouldBe && c.isRollingAdmission()) {
                // 시행처가 정기시험으로 바꿔 시드에서 빠진 경우 — 다시 일정 대상이 된다
                c.clearRollingAdmission();
                cleared[0]++;
            }
        });
        log.info("[상시표시] 상시·예약제 {}종 (새로 표시 {} · 해제 {} · 남은 일정 정리 {})",
                seed.codes.size(), marked[0], cleared[0], dropped[0]);
    }

    /**
     * 상시 시험에 남아 있는 회차를 지운다.
     *
     * <p>표시만 바꾸고 일정을 두면 <b>화면마다 다른 말을 한다</b>(2026-09-04 실측: IELTS·OPIc·TOEFL·
     * 워드프로세서·컴퓨터활용능력 2급 다섯 종). 카드는 "상시시험 — 정해진 일정이 없습니다"라고 하는데
     * 상세엔 D-day 히어로와 회차표가 뜨고, 캘린더엔 접수 마감이 찍히고, <b>접수 마감 알림 메일까지 나간다.</b>
     * 상시에는 회차가 없다는 게 이 표시의 뜻이므로, 남은 회차는 지운다(알림 예약도 함께).
     */
    private int dropSchedules(Certificate c) {
        List<ExamSchedule> rows = examScheduleRepository
                .findByCertificateIdAndStatusInOrderByExamStartDateAsc(c.getId(), TO_DROP);
        for (ExamSchedule s : rows) {
            notificationScheduleRepository.deleteAll(notificationScheduleRepository.findByExamSchedule(s));
            examScheduleRepository.delete(s);
            log.info("[상시표시] {} 의 회차 정리 — {}년 {}회 {} (상시 시험엔 회차가 없다)",
                    c.getName(), s.getYear(), s.getRound(), s.getExamType());
        }
        return rows.size();
    }

    /** 시드가 아는 종목코드와 이름(공백 제거). */
    private record RollingSeed(Set<String> codes, Set<String> names) {
        boolean isEmpty() {
            return codes.isEmpty() && names.isEmpty();
        }

        boolean matches(Certificate c) {
            return (c.getSourceCode() != null && codes.contains(c.getSourceCode()))
                    || names.contains(normalize(c.getName()));
        }
    }

    private static String normalize(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "");
    }

    private RollingSeed readSeed() {
        Set<String> codes = new HashSet<>();
        Set<String> names = new HashSet<>();
        try {
            JsonNode root = objectMapper.readTree(
                    new ClassPathResource(RESOURCE).getInputStream());
            root.path("exams").forEach(e -> {
                String code = e.path("sourceCode").asText("");
                if (!code.isBlank()) {
                    codes.add(code);
                }
                String name = normalize(e.path("name").asText(""));
                if (!name.isBlank()) {
                    names.add(name);
                }
            });
        } catch (Exception e) {
            // 시드가 없거나 깨져도 기동은 한다 — 표시가 안 될 뿐 서비스는 돌아야 한다
            log.warn("[상시표시] {} 를 읽지 못했다: {}", RESOURCE, e.toString());
        }
        return new RollingSeed(codes, names);
    }
}

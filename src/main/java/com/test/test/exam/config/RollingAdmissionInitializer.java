package com.test.test.exam.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.test.exam.repository.CertificateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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
 * <p>마스터 적재(@Order(100)) 뒤에 돌아야 표시할 행이 존재한다.
 */
@Slf4j
@Component
@Order(110)
@RequiredArgsConstructor
public class RollingAdmissionInitializer {

    private static final String RESOURCE = "seed/rolling_exams.json";

    private final CertificateRepository certificateRepository;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void mark() {
        List<String> codes = readCodes();
        if (codes.isEmpty()) {
            return;
        }
        int[] marked = {0};
        int[] cleared = {0};
        certificateRepository.findAll().forEach(c -> {
            boolean shouldBe = c.getSourceCode() != null && codes.contains(c.getSourceCode());
            if (shouldBe && !c.isRollingAdmission()) {
                c.markRollingAdmission();
                marked[0]++;
            } else if (!shouldBe && c.isRollingAdmission()) {
                // 시행처가 정기시험으로 바꿔 시드에서 빠진 경우 — 다시 일정 대상이 된다
                c.clearRollingAdmission();
                cleared[0]++;
            }
        });
        log.info("[상시표시] 상시·예약제 {}종 (새로 표시 {} · 해제 {})", codes.size(), marked[0], cleared[0]);
    }

    private List<String> readCodes() {
        try {
            JsonNode root = objectMapper.readTree(
                    new ClassPathResource(RESOURCE).getInputStream());
            List<String> codes = new ArrayList<>();
            root.path("exams").forEach(e -> {
                String code = e.path("sourceCode").asText("");
                if (!code.isBlank()) {
                    codes.add(code);
                }
            });
            return codes;
        } catch (Exception e) {
            // 시드가 없거나 깨져도 기동은 한다 — 표시가 안 될 뿐 서비스는 돌아야 한다
            log.warn("[상시표시] {} 를 읽지 못했다: {}", RESOURCE, e.toString());
            return List.of();
        }
    }
}

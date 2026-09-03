package com.test.test.exam.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.CertificateLifecycle;
import com.test.test.exam.domain.Series;
import com.test.test.exam.repository.CertificateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 시험 <b>마스터</b> 시드 — "어떤 시험이 존재하는가"만 적재한다(일정 없음).
 *
 * <p><b>왜 별도인가</b>: 기존 수집 파이프라인은 일정(ExamSchedule)이 있어야 자격증(Certificate)이 생긴다.
 * 그래서 일정을 아직 못 구한 시험은 검색에도 안 잡히고 존재 자체가 사라졌다.
 * 마스터를 먼저 깔아 두면 <b>"시험은 보이는데 일정 미정"</b> 상태가 가능해지고,
 * 사용자는 그 시험을 찾아 관심 등록해 둘 수 있다 — 일정이 붙는 순간 알림이 나간다.
 *
 * <p>이미 있는 종목(데모/비큐넷 시드가 만든 것)은 건너뛴다. 이름은 공백을 무시하고 비교해
 * "컴퓨터활용능력 1급" 과 "컴퓨터활용능력1급" 이 중복 생성되지 않게 한다.
 *
 * <p>{@link ExamDataInitializer}(일정 있는 시드 + 수집 배치)가 먼저 돌아야 하므로 {@link Order} 로 뒤에 세운다.
 *
 * <p><b>운영에서도 돈다</b>(2026-08-10). 이건 데모 데이터가 아니라 <b>"어떤 시험이 존재하는가"라는
 * 실참조 데이터</b>다(코드컨벤션 §5-2). {@code @Profile("!prod")} 를 걸어 두었더니 운영은 빈 DB 로
 * 떠서 사이트에 시험이 하나도 없고, 매니저가 일정을 붙일 대상조차 없었다.
 * 이미 있는 종목은 건너뛰므로 매 기동 돌려도 안전하다. 끄려면 {@code seed.master.enabled=false}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "seed.master.enabled", havingValue = "true", matchIfMissing = true)
@Order(100)
@RequiredArgsConstructor
public class CertificateMasterInitializer {

    /**
     * 읽는 순서가 중요하다. <b>큐넷 목록이 먼저</b>다 — 종목코드(jmCd)를 가진 쪽이 기준이 되어야
     * 나중에 시험일정을 이름이 아니라 코드로 붙일 수 있다. 그다음 비큐넷(어학·공무원 등)을 얹는다.
     */
    private static final List<String> RESOURCES = List.of(
            "seed/qnet_master.json",
            "seed/exam_master.json");

    /** 폐지·개칭 기록 */
    private static final String LIFECYCLE_RESOURCE = "seed/certificate_lifecycle.json";

    private final CertificateRepository certificateRepository;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedMaster() {
        // 이미 있는 이름·slug 는 건너뛴다(공백 무시 비교)
        Map<String, Certificate> existing = new HashMap<>();
        Set<String> existingSlugs = new HashSet<>();
        Set<String> usedSourceCodes = new HashSet<>();
        certificateRepository.findAll().forEach(c -> {
            existing.put(normalize(c.getName()), c);
            existingSlugs.add(c.getSlug());
            if (c.getSourceCode() != null) {
                usedSourceCodes.add(c.getSourceCode());
            }
        });

        int created = 0, skipped = 0, recategorized = 0, linked = 0;

        for (String resource : RESOURCES) {
            MasterFile file = read(resource);
            if (file == null || file.exams() == null || file.exams().isEmpty()) {
                log.warn("[MasterSeed] {} 없음/비어 있음 — 스킵", resource);
                continue;
            }

            for (MasterExam e : file.exams()) {
                if (e.name() == null || e.name().isBlank()) {
                    continue;
                }
                Certificate already = existing.get(normalize(e.name()));
                if (already != null) {
                    // 데모/수집 시드가 만든 종목은 분류가 성기다(예: "국가기술자격").
                    // 마스터가 더 구체적인 분류를 알고 있으면 그걸로 맞춘다 — 필터가 쪼개져 보이지 않게.
                    // 계열도 같다: 스냅샷·비큐넷 시드는 계열을 모르고 '기타'로 만드는데, 마스터가 아는 값이 있으면 되돌린다.
                    String category = e.category() != null ? e.category() : already.getCategory();
                    Series series = already.getSeries() == Series.ETC ? parseSeries(e.series()) : already.getSeries();
                    if (!java.util.Objects.equals(category, already.getCategory()) || series != already.getSeries()) {
                        already.updateMeta(already.getName(), series, already.getAgency(), category);
                        recategorized++;
                    }
                    // 종목코드를 붙여 둔다. 이게 있어야 일정 연동이 이름 매칭 없이 된다.
                    if (e.sourceCode() != null && already.getSourceCode() == null
                            && usedSourceCodes.add(e.sourceCode())) {
                        already.linkSourceCode(e.sourceCode());
                        linked++;
                    }
                    certificateRepository.save(already);
                    skipped++;
                    continue;
                }

                String code = e.sourceCode() != null && usedSourceCodes.add(e.sourceCode())
                        ? e.sourceCode() : null;
                String slug = uniqueSlug(e.name(), existingSlugs);
                Certificate saved = certificateRepository.save(Certificate.builder()
                        .name(e.name())
                        .slug(slug)
                        .series(parseSeries(e.series()))
                        .agency(e.agency() == null ? "미상" : e.agency())
                        .category(e.category())
                        .sourceCode(code)
                        .build());
                existing.put(normalize(e.name()), saved);
                created++;
            }
        }

        log.info("[MasterSeed] 시험 마스터 신규 {}종 (기존 {}종 중 분류 갱신 {} · 종목코드 연결 {})",
                created, skipped, recategorized, linked);

        applyLifecycle(existing);
    }

    /**
     * 폐지·개칭 표시. <b>지우지 않는다</b> — 삭제하면 "웹디자인기능사 왜 없어졌냐"에 답할 근거가 사라진다.
     * 사용자 화면(검색·목록)에서만 빠지고, 매니저 화면의 시험 변천사에는 남는다.
     */
    private void applyLifecycle(Map<String, Certificate> existing) {
        LifecycleFile file = readLifecycle();
        if (file == null || file.items() == null) {
            return;
        }
        int marked = 0, notFound = 0;
        for (LifecycleItem item : file.items()) {
            Certificate cert = existing.get(normalize(item.name()));
            if (cert == null) {
                notFound++;
                continue;
            }
            CertificateLifecycle lifecycle;
            try {
                lifecycle = CertificateLifecycle.valueOf(item.lifecycle());
            } catch (IllegalArgumentException | NullPointerException e) {
                log.warn("[MasterSeed] 알 수 없는 lifecycle 값 '{}' ({}) — 건너뜀", item.lifecycle(), item.name());
                continue;
            }
            cert.markLifecycle(lifecycle, item.supersededBy(), item.note());
            certificateRepository.save(cert);
            marked++;
        }
        log.info("[MasterSeed] 시험 변천사 {}건 표시 (마스터에 없어 건너뜀 {}건)", marked, notFound);
    }

    private LifecycleFile readLifecycle() {
        ClassPathResource resource = new ClassPathResource(LIFECYCLE_RESOURCE);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, LifecycleFile.class);
        } catch (Exception e) {
            log.error("[MasterSeed] {} 파싱 실패 — 스킵", LIFECYCLE_RESOURCE, e);
            return null;
        }
    }

    /** slug 는 unique 제약이 있다. 같은 값이 있으면 뒤에 번호를 붙인다. */
    private String uniqueSlug(String name, Set<String> used) {
        String base = name.replace(" ", "");
        String slug = base;
        int n = 2;
        while (!used.add(slug)) {
            slug = base + "-" + n++;
        }
        return slug;
    }

    private String normalize(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "");
    }

    private Series parseSeries(String raw) {
        try {
            return Series.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            return Series.ETC;
        }
    }

    private MasterFile read(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, MasterFile.class);
        } catch (Exception e) {
            log.error("[MasterSeed] {} 파싱 실패 — 스킵", path, e);
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MasterFile(List<MasterExam> exams) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MasterExam(String name, String series, String agency, String category,
                      String sourceCode, String sourceUrl) {
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record LifecycleFile(List<LifecycleItem> items) {
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record LifecycleItem(String name, String lifecycle, String supersededBy, String note) {
    }
}

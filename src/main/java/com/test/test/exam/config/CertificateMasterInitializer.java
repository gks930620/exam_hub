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

        int created = 0, skipped = 0, recategorized = 0, linked = 0, relinked = 0;

        // 마스터가 아는 종목코드 전부. "이 코드가 진짜 있는 코드인가"를 판단하는 근거다.
        Set<String> knownCodes = new HashSet<>();
        for (String resource : RESOURCES) {
            MasterFile f = read(resource);
            if (f != null && f.exams() != null) {
                f.exams().stream().map(MasterExam::sourceCode)
                        .filter(java.util.Objects::nonNull).forEach(knownCodes::add);
            }
        }

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
                    //
                    // <b>죽은 코드도 갈아 끼운다</b>: 마스터 어디에도 없는 코드를 달고 있으면 코드 매칭이
                    // 통째로 실패하고, 남는 건 이름 매칭이라는 약한 고리뿐이다 — 시행처가 표기를 한 글자만
                    // 바꾸면 그날로 일정이 안 붙는다. 데모 시드가 전기기사에 1230, 건축기사에 1450,
                    // 지게차운전기능사에 5836 이라는 없는 코드를 달아 놓은 채였다(2026-09-10 실측).
                    // 남이 쓰고 있는 코드는 뺏지 않는다(usedSourceCodes).
                    boolean deadCode = already.getSourceCode() != null
                            && !knownCodes.contains(already.getSourceCode());
                    if (e.sourceCode() != null && (already.getSourceCode() == null || deadCode)
                            && usedSourceCodes.add(e.sourceCode())) {
                        if (deadCode) {
                            log.info("[MasterSeed] {} 의 죽은 종목코드 {} → {} 로 교체",
                                    already.getName(), already.getSourceCode(), e.sourceCode());
                            relinked++;
                        } else {
                            linked++;
                        }
                        already.linkSourceCode(e.sourceCode());
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

        log.info("[MasterSeed] 시험 마스터 신규 {}종 (기존 {}종 중 분류 갱신 {} · 종목코드 연결 {} · 죽은 코드 교체 {})",
                created, skipped, recategorized, linked, relinked);

        applyLifecycle(existing);
        reconcileSlugs();
    }

    /**
     * slug 을 이름과 다시 맞춘다. <b>규칙은 하나다: 이름에서 공백을 뺀 것.</b> 겹치면 뒤에 번호를 붙인다.
     *
     * <p><b>왜 매 기동 도나</b>: slug 은 이름에서 나온 파생값인데, 수집은 종목코드로 행을 찾아
     * <b>이름만 고치고 slug 은 그대로 둔다.</b> 그래서 이름이 바뀐 행은 남의 slug 을 단 채로 남는다 —
     * 데모 시드가 산업안전기사에 정보처리산업기사의 코드(2290)를 달아 둔 탓에 실제로 네 종목이
     * 어긋나 있었고, {@code /api/certificates/by-slug/산업안전기사} 가 정보처리산업기사를 돌려줬다
     * (2026-09-10 실측). 코드를 고쳐도 <b>이미 만들어진 DB 는 안 낫기 때문에</b> 여기서 되돌린다.
     *
     * <p>덤으로 규칙이 하나가 된다. 그전엔 만드는 경로마다 달라 절반은 공백을 남기고("TOEIC 토익")
     * 절반은 뺐다("FLEX영어"). 프런트는 {@code /cert/:id} 로 다니므로 slug 이 바뀌어도 화면은 그대로다.
     *
     * <p><b>두 번에 나눠 쓴다</b> — 3번이 놓아 준 이름을 644번이 가져가는 식의 맞교환이 있어서,
     * 한 번에 저장하면 slug 의 unique 제약에 걸린다. 임시값으로 자리를 비운 뒤 확정값을 넣는다.
     */
    private void reconcileSlugs() {
        List<Certificate> all = certificateRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Certificate::getId))
                .toList();

        Map<Long, String> desired = new HashMap<>();
        Set<String> taken = new HashSet<>();
        for (Certificate c : all) {
            String base = normalize(c.getName());
            if (base.isEmpty()) {
                continue;   // 이름이 없는 행은 손대지 않는다 — 만들 근거가 없다
            }
            String slug = base;
            for (int n = 2; !taken.add(slug); n++) {
                slug = base + "-" + n;
            }
            desired.put(c.getId(), slug);
        }

        List<Certificate> changed = all.stream()
                .filter(c -> desired.containsKey(c.getId()))
                .filter(c -> !desired.get(c.getId()).equals(c.getSlug()))
                .toList();
        if (changed.isEmpty()) {
            return;
        }

        List<String> before = changed.stream()
                .map(c -> c.getName() + "(" + c.getSlug() + " → " + desired.get(c.getId()) + ")")
                .toList();

        changed.forEach(c -> c.renameSlug("__reslug__" + c.getId()));
        certificateRepository.saveAllAndFlush(changed);
        changed.forEach(c -> c.renameSlug(desired.get(c.getId())));
        certificateRepository.saveAllAndFlush(changed);

        log.info("[MasterSeed] slug 재조정 {}건{}", changed.size(),
                changed.size() <= 20 ? " — " + before : "");
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
        int marked = 0;
        List<String> notFound = new java.util.ArrayList<>();
        for (LifecycleItem item : file.items()) {
            Certificate cert = existing.get(normalize(item.name()));
            if (cert == null) {
                notFound.add(item.name());
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
        log.info("[MasterSeed] 시험 변천사 {}건 표시", marked);
        if (!notFound.isEmpty()) {
            // 이름이 안 맞으면 그 기록은 아무 일도 안 한다 — 뺐다고 믿은 시험이 그대로 남는다.
            // 건수만 찍어 두면 어느 것이 죽은 기록인지 알 수 없어 몇 달을 그냥 지나간다(실제로 그랬다).
            log.warn("[MasterSeed] 변천사에 적혔지만 그런 이름의 시험이 없다 — 이 기록은 아무 일도 하지 않는다: {}",
                    notFound);
        }
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

    /** slug 는 unique 제약이 있다. 같은 값이 있으면 뒤에 번호를 붙인다. 규칙은 {@link #reconcileSlugs()} 와 같다. */
    private String uniqueSlug(String name, Set<String> used) {
        String base = normalize(name);
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

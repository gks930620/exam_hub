package com.test.test.exam.collect;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * <b>같은 시험에 시드마다 다른 종목코드가 붙는다.</b> 그 둘을 같이 밝히기 위한 도우미.
 *
 * <p>왜 이런 일이 생기나: 비큐넷 시드({@code seed/non_qnet_exams.json})가 마스터보다 먼저 종목을
 * 만들면서 자기 코드를 붙인다({@code TESAT}). 마스터는 같은 시험을 {@code M0440} 으로 부른다.
 * 그런데 비큐넷 시드는 {@code @Profile("!prod")} 라 <b>운영에서는 안 돈다</b> —
 * 그래서 <b>로컬은 시드 코드, 운영은 마스터 코드</b>가 붙는다.
 *
 * <p>수집은 어느 쪽이든 붙는다 — {@link DiffService} 가 코드로 못 찾으면 이름으로 한 번 더 찾는다.
 * 문제는 {@link ScheduleSource#coveredExamCodes()} 다. 이건 <b>코드로만</b> 맞춰 보기 때문에
 * 마스터 코드만 밝히면 로컬에서 그 판정이 조용히 안 먹는다(2026-09-08 TESAT 에서 실측).
 *
 * <p>그래서 소스는 두 코드를 다 밝힌다. 빠뜨리면 {@code ScheduleSourceCoverageTest} 가 짚어 준다.
 *
 * <p><b>임시 방편이다.</b> 시드 코드를 마스터 코드로 통일하면 이 클래스는 통째로 사라진다
 * (진행사항/01_AI_작업큐).
 */
final class SeedCodeAlias {

    private SeedCodeAlias() {
    }

    /** 마스터 코드에 비큐넷 시드가 쓰는 코드를 더한다. */
    static Set<String> plus(Collection<String> masterCodes, String... seedCodes) {
        Set<String> all = new LinkedHashSet<>(masterCodes);
        all.addAll(Set.of(seedCodes));
        return Set.copyOf(all);
    }
}

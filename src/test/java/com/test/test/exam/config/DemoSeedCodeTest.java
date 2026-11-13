package com.test.test.exam.config;

import com.test.test.exam.collect.DemoDataProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>데모 시드의 큐넷 종목코드가 진짜 그 시험의 코드인가.</b>
 *
 * <p>큐넷 수집은 이름이 아니라 <b>종목코드(jmCd)로 행을 찾는다</b>. 그래서 데모가 A 시험에 B 시험의
 * 코드를 달아 두면, 수집이 그 행을 B 로 알고 <b>이름을 B 로 바꿔 버린다</b>. slug 은 안 바뀌므로
 * 그 행은 "이름은 B, slug 은 A" 가 된다.
 *
 * <p>실제로 그랬다(2026-09-10 실측). 산업안전기사에 2290(정보처리산업기사)을 달아 둔 탓에
 * {@code /api/certificates/by-slug/산업안전기사} 가 <b>정보처리산업기사</b>를 돌려줬고,
 * 진짜 산업안전기사는 slug 을 뺏겨 {@code 산업안전기사-2} 를 달고 있었다.
 * 소방설비기사(전기분야)↔에너지관리기능사, 정보처리산업기사↔조경산업기사도 같은 사고였다.
 *
 * <p>없는 코드(1230·1450·5836)도 셋 있었다. 이건 이름이 안 바뀌니 눈에 안 띄지만,
 * <b>코드 매칭이 통째로 실패해</b> 이름 매칭이라는 약한 고리에만 기대게 된다 — 표기가 한 글자만
 * 달라지면 그날로 일정이 안 붙는다.
 *
 * <p>사람 눈으로는 절대 못 잡는 종류의 오류다. <b>시드와의 대조는
 * {@link LocalSeedSqlTest#demo_codes_match_the_seeded_master()} 로 옮겼다</b> —
 * 시드가 SQL 하나가 되면서 기준이 JSON 파일이 아니라 적재된 DB 가 됐기 때문이다.
 * 여기엔 시드 없이도 되는 검사만 남긴다.
 */
class DemoSeedCodeTest {

    @Test
    @DisplayName("데모 시드 안에서 종목코드가 겹치지 않는다")
    void demoCodesAreUnique() {
        Map<String, String> seen = new HashMap<>();
        List<String> clash = new ArrayList<>();
        for (DemoDataProvider.DemoCert cert : new DemoDataProvider().demoCertificates()) {
            String prev = seen.put(cert.sourceCode(), cert.name());
            if (prev != null) {
                clash.add(cert.sourceCode() + " → " + prev + " / " + cert.name());
            }
        }
        assertTrue(clash.isEmpty(), "같은 종목코드를 둘이 나눠 쓰면 수집이 한 행에 몰아넣는다: " + clash);
    }

    @Test
    @DisplayName("데모 종목 수는 10종 그대로다 — 늘리거나 줄이면 일정 오프셋(certs.get(n))이 어긋난다")
    void demoCertCountIsStable() {
        assertEquals(10, new DemoDataProvider().demoCertificates().size());
    }
}

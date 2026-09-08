package com.test.test.exam.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 시험을 목록에서 빼는 <b>세 가지 서로 다른 이유</b>가 뒤섞이지 않는지.
 *
 * <p>폐지·개칭은 <b>시험 쪽 사정</b>이다. 그런데 2026-09-08 에 세 번째 사정이 생겼다 —
 * 시험은 멀쩡히 살아 있는데 <b>우리가 일정을 구할 방법이 없어서</b> 빼는 경우다
 * (보험계리사·손해사정사·보험중개사: 시행처 시험 사이트가 아예 없다).
 *
 * <p>이걸 "폐지"로 적으면 <b>거짓말</b>이 된다. 매년 치르는 국가전문자격을 두고
 * "폐지된 시험입니다"라고 안내하는 셈이다. 그래서 상태를 따로 뒀다.
 */
class CertificateLifecycleTest {

    private static Certificate cert(CertificateLifecycle lifecycle, String supersededBy, String note) {
        Certificate c = Certificate.builder()
                .name("보험계리사").slug("insurance-actuary").series(Series.ETC)
                .agency("금융감독원").category("국가전문자격").build();
        c.markLifecycle(lifecycle, supersededBy, note);
        return c;
    }

    @Test
    @DisplayName("다루지 않는 시험은 사용자 목록·검색에서 빠진다")
    void excluded_is_hidden_from_users() {
        assertFalse(CertificateLifecycle.EXCLUDED.isVisibleToUsers());
        assertFalse(cert(CertificateLifecycle.EXCLUDED, null, "시험 사이트 없음").isVisibleToUsers());
    }

    /**
     * <b>"폐지"라고 말하면 안 된다.</b> 살아 있는 시험이다. 사용자에게는 우리 사정을 솔직히 밝힌다.
     */
    @Test
    @DisplayName("다루지 않는 이유를 폐지라고 하지 않는다")
    void excluded_never_claims_the_exam_was_abolished() {
        String reason = cert(CertificateLifecycle.EXCLUDED, null, "시행처 시험 사이트를 못 찾았다").hiddenReason();

        assertNotNull(reason);
        assertFalse(reason.contains("폐지"), "살아 있는 시험을 폐지라고 안내한다: " + reason);
        assertTrue(reason.contains("다루지"), "왜 안 보이는지 설명이 없다: " + reason);
    }

    /** 기존 상태의 뜻은 그대로여야 한다 — 새 상태를 넣다가 옛 안내가 바뀌면 안 된다. */
    @Test
    @DisplayName("폐지·개칭 안내는 그대로다")
    void existing_states_keep_their_meaning() {
        assertTrue(cert(CertificateLifecycle.ABOLISHED, null, null).hiddenReason().contains("폐지"));
        assertTrue(cert(CertificateLifecycle.RENAMED, "새이름", null).hiddenReason().contains("새이름"));
        assertTrue(CertificateLifecycle.ACTIVE.isVisibleToUsers());
        assertTrue(CertificateLifecycle.UNVERIFIED.isVisibleToUsers(), "확인 필요는 아직 보여야 한다");
        assertNull(cert(CertificateLifecycle.ACTIVE, null, null).hiddenReason());
    }
}

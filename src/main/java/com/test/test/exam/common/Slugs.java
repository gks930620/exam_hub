package com.test.test.exam.common;

/**
 * 시험 주소(slug) 만들기 — <b>이름을 그대로 쓰지 않는다.</b>
 *
 * <p>이름에 {@code /} 가 든 시험이 있다("관광통역안내사(말레이/인도네시아어)"). 그걸 slug 으로
 * 쓰면 {@code /api/certificates/by-slug/...} 의 경로 한 조각 안에 슬래시가 들어가고,
 * 톰캣이 인코딩된 슬래시를 기본으로 거부해 <b>HTTP 400 + 톰캣 HTML 페이지</b>가 돌아온다
 * (2026-11-14 실측 — 841종 중 이 한 종만 주소로 접근이 안 됐다). id 로는 열리니 더 눈에 안 띈다.
 *
 * <p>톰캣 설정을 열어 주는 방법도 있지만 그건 경로 해석 규칙을 느슨하게 만드는 일이다.
 * <b>주소에 못 쓰는 글자를 애초에 넣지 않는 쪽</b>이 맞다.
 */
public final class Slugs {

    private Slugs() {
    }

    /**
     * 이름에서 slug 을 만든다. 한글은 그대로 둔다 — 주소가 읽히는 편이 공유에 낫고,
     * 지금까지 쌓인 주소도 한글이다(바꾸면 기존 링크가 전부 죽는다).
     *
     * <p>바꾸는 것은 <b>경로를 깨뜨리는 글자</b>뿐이다: {@code / \ ? # %}
     */
    public static String of(String name) {
        if (name == null) {
            return null;
        }
        return name.trim()
                .replaceAll("[/\\\\]", "-")     // 경로 구분자 → 하이픈
                .replaceAll("[?#%]", "")        // 질의·프래그먼트·인코딩 시작 글자는 뺀다
                .replaceAll("\\s+", " ")
                .trim();
    }
}

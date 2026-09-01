package com.test.test.exam.collect;

import java.util.Map;

/**
 * 큐넷 <b>직무분야</b>(obligfldnm) → 우리 <b>분류</b>(Certificate.category).
 *
 * <p><b>왜 필요한가</b>: 수집 시 {@code DiffService.ensureCertificate} 가 마스터 메타를
 * 수집값으로 덮어쓴다. 여기서 "국가기술자격" 같은 성긴 값을 주면 시드로 넣어 둔
 * <b>613종의 세분류가 통째로 뭉개진다</b>(필터 칩이 한 덩어리가 된다).
 * 그래서 수집기도 시드와 <b>같은 분류 체계</b>를 써야 한다.
 *
 * <p>이 표는 {@code seed/qnet_master.json} 을 만든 것과 같은 매핑이다.
 * 큐넷이 직무분야를 새로 만들면 여기에 추가한다(없으면 "국가기술자격-기타"로 떨어진다).
 */
final class QnetFieldCategory {

    private static final Map<String, String> MAP = Map.ofEntries(
            Map.entry("건설", "국가기술자격-건설"),
            Map.entry("건축", "국가기술자격-건설"),
            Map.entry("기계", "국가기술자격-기계"),
            Map.entry("재료", "국가기술자격-재료"),
            Map.entry("광업자원", "국가기술자격-재료"),
            Map.entry("화학", "국가기술자격-화학환경"),
            Map.entry("환경.에너지", "국가기술자격-화학환경"),
            Map.entry("전기.전자", "국가기술자격-전기전자"),
            Map.entry("정보통신", "국가기술자격-정보통신"),
            Map.entry("안전관리", "국가기술자격-안전"),
            Map.entry("경영.회계.사무", "국가기술자격-경영사무"),
            Map.entry("영업.판매", "국가기술자격-경영사무"),
            Map.entry("사업관리", "국가기술자격-경영사무"),
            Map.entry("교육.자연.과학.사회과학", "국가기술자격-경영사무"),
            Map.entry("농림어업", "국가기술자격-농림어업"),
            Map.entry("식품.가공", "국가기술자격-식품조리"),
            Map.entry("음식서비스", "국가기술자격-식품조리"),
            Map.entry("섬유.의복", "국가기술자격-섬유공예"),
            Map.entry("인쇄.목재.가구.공예", "국가기술자격-섬유공예"),
            Map.entry("보건.의료", "국가기술자격-보건의료"),
            Map.entry("사회복지.종교", "국가기술자격-사회복지"),
            Map.entry("문화.예술.디자인.방송", "국가기술자격-디자인"),
            Map.entry("이용.숙박.여행.오락.스포츠", "국가기술자격-서비스"),
            Map.entry("운전.운송", "국가기술자격-서비스"));

    private QnetFieldCategory() {
    }

    /**
     * @param qualgbNm 자격구분명(국가기술자격/국가전문자격 등)
     * @param fieldNm  직무분야명
     */
    static String of(String qualgbNm, String fieldNm) {
        if (qualgbNm != null && !qualgbNm.isBlank() && !"국가기술자격".equals(qualgbNm)) {
            return qualgbNm;   // 국가전문자격·과정평가형·일학습병행은 그대로
        }
        String mapped = MAP.get(fieldNm == null ? "" : fieldNm.trim());
        return mapped != null ? mapped : "국가기술자격-기타";
    }
}

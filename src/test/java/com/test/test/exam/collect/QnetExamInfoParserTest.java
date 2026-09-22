package com.test.test.exam.collect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>큐넷이 주는 덩어리 글을 사람이 읽을 조각으로 가른다.</b>
 *
 * <p>상세 화면에 날짜 말고는 아무것도 없었다 — 응시료·시험과목·합격기준이 데이터 모델에
 * 필드조차 없어서, 취준생이 "이 시험 볼까"를 여기서 못 정하고 큐넷으로 나갔다.
 *
 * <p>큐넷 API 는 이 정보를 <b>구조화해서 주지 않는다.</b> {@code contents} 한 칸에 원문이
 * 통째로 들어 있고, 앞에는 <b>CSS 블록이 박혀 있고</b>(BODY {'{'} FONT-SIZE: 10pt ...{'}'}),
 * 항목 구분은 원문자(①~⑤)인데 그마저 <b>이중 이스케이프</b>되어 온다({@code &amp;#9312;}).
 * 여기 있는 문자열은 전부 저장소의 실제 응답 샘플에서 그대로 가져왔다
 * ({@code 설계/시험데이터/큐넷_API/samples/}).
 *
 * <p>순수 파서라 컨벤션 §6 의 예외로 둔다 — 네트워크 없이 시험할 수 있어야 한다.
 */
class QnetExamInfoParserTest {

    /** 실제 응답의 취득방법 원문 그대로. CSS 앞머리와 이중 이스케이프까지 포함한다. */
    private static final String ACQUISITION = "BODY {\tFONT-SIZE: 10pt; FONT-FAMILY: Malgun Gothic; "
            + "COLOR: #000000; MARGIN: 0px}P {\tMARGIN-BOTTOM: 0px; MARGIN-TOP: 0px; LINE-HEIGHT: 1.2}"
            + "LI {\tMARGIN-BOTTOM: 0px; MARGIN-TOP: 0px; LINE-HEIGHT: 1.2}"
            + "&amp;#9312; 시 행 처 : 한국산업인력공단 "
            + "&amp;#9313; 관련학과 :모든 학과 응시가능"
            + "&amp;#9314; 시험과목 - 필기 1. 소프트웨어설계 2. 소프트웨어개발 3. 데이터베이스구축 "
            + "4. 프로그래밍언어활용 5. 정보시스템구축관리 - 실기 : 정보처리 실무 "
            + "&amp;#9315; 검정방법 - 필기 : 객관식 4지 택일형, 과목당 20문항(과목당 30분)- 실기 : 필답형(2시간30분)"
            + "&amp;#9316; 합격기준 - 필기 : 100점을 만점으로 하여 과목당 40점 이상, 전과목 평균 60점 이상."
            + "- 실기 : 100점을 만점으로 하여 60점 이상.";

    // ── 응시료 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("응시료를 필기·실기로 가른다 — 1차가 필기, 2차가 실기다")
    void fee_is_split_into_written_and_practical() {
        QnetExamInfoParser.Fee fee = QnetExamInfoParser.parseFee("1차 : 19400, 2차 : 22600 ");

        assertThat(fee.getWritten()).isEqualTo(19400);
        assertThat(fee.getPractical()).isEqualTo(22600);
    }

    /** 실기가 없는 시험은 1차만 온다 — 없는 값을 0 으로 채우면 "무료"로 보인다. */
    @Test
    @DisplayName("한쪽만 있으면 나머지는 비워 둔다 — 0 원이 아니다")
    void missing_half_stays_empty() {
        QnetExamInfoParser.Fee fee = QnetExamInfoParser.parseFee("1차 : 19400");

        assertThat(fee.getWritten()).isEqualTo(19400);
        assertThat(fee.getPractical()).isNull();
    }

    @Test
    @DisplayName("모르는 모양이면 원문만 남기고 숫자는 비운다 — 지어내지 않는다")
    void unknown_shape_keeps_the_raw_text_only() {
        QnetExamInfoParser.Fee fee = QnetExamInfoParser.parseFee("종목별 상이(홈페이지 참조)");

        assertThat(fee.getWritten()).isNull();
        assertThat(fee.getPractical()).isNull();
        assertThat(fee.getRaw()).isEqualTo("종목별 상이(홈페이지 참조)");
    }

    @Test
    @DisplayName("빈 값도 터지지 않는다")
    void blank_fee_is_safe() {
        assertThat(QnetExamInfoParser.parseFee(null).getWritten()).isNull();
        assertThat(QnetExamInfoParser.parseFee("  ").getWritten()).isNull();
    }

    // ── 취득방법 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CSS 앞머리를 걷어낸다 — 그대로 두면 화면에 스타일 코드가 뜬다")
    void css_preamble_is_stripped() {
        QnetExamInfoParser.Acquisition a = QnetExamInfoParser.parseAcquisition(ACQUISITION);

        assertThat(a.getSubjects()).doesNotContain("FONT-SIZE").doesNotContain("MARGIN");
        assertThat(a.getAgency()).doesNotContain("BODY");
    }

    @Test
    @DisplayName("원문자로 나뉜 다섯 조각을 각각 제자리에 담는다")
    void five_sections_land_in_their_places() {
        QnetExamInfoParser.Acquisition a = QnetExamInfoParser.parseAcquisition(ACQUISITION);

        assertThat(a.getAgency()).isEqualTo("한국산업인력공단");
        assertThat(a.getRelatedMajor()).isEqualTo("모든 학과 응시가능");
        assertThat(a.getSubjects()).contains("소프트웨어설계").contains("정보처리 실무");
        assertThat(a.getMethod()).contains("객관식 4지 택일형").contains("필답형");
        assertThat(a.getPassStandard()).contains("과목당 40점 이상").contains("60점 이상");
    }

    /** {@code &amp;#9312;} 는 XML 을 풀면 {@code &#9312;} 이고 그것이 ① 이다. 두 번 풀어야 한다. */
    @Test
    @DisplayName("이중 이스케이프된 원문자를 풀어 낸다")
    void double_escaped_markers_are_decoded() {
        QnetExamInfoParser.Acquisition a = QnetExamInfoParser.parseAcquisition(ACQUISITION);

        assertThat(a.getSubjects()).doesNotContain("9314").doesNotContain("&#").doesNotContain("amp;");
        assertThat(a.getSubjects()).doesNotContain("\u2462");   // 원문자도 안 남는다
    }

    /** 표기가 시험마다 다르다 — 못 가르면 빈 칸으로 두고 원문을 남긴다(613종을 다 믿을 수 없다). */
    @Test
    @DisplayName("모르는 모양이면 조각은 비우고 원문은 지키다")
    void unknown_shape_keeps_raw() {
        QnetExamInfoParser.Acquisition a = QnetExamInfoParser.parseAcquisition("아무 형식도 없는 안내문입니다.");

        assertThat(a.getSubjects()).isNull();
        assertThat(a.getPassStandard()).isNull();
        assertThat(a.getRaw()).isEqualTo("아무 형식도 없는 안내문입니다.");
    }

    @Test
    @DisplayName("빈 값도 터지지 않는다")
    void blank_acquisition_is_safe() {
        assertThat(QnetExamInfoParser.parseAcquisition(null).getRaw()).isNull();
        assertThat(QnetExamInfoParser.parseAcquisition("").getSubjects()).isNull();
    }

    /** 원문자가 실제 문자(①)로 바로 오는 응답도 있을 수 있다 — 같은 규칙으로 읽힌다. */
    @Test
    @DisplayName("원문자가 문자 그대로 와도 읽는다")
    void literal_circled_numbers_also_work() {
        QnetExamInfoParser.Acquisition a = QnetExamInfoParser.parseAcquisition(
                "① 시 행 처 : 대한상공회의소 ③ 시험과목 - 필기 : 유통상식 ⑤ 합격기준 - 매 과목 40점 이상");

        assertThat(a.getAgency()).isEqualTo("대한상공회의소");
        assertThat(a.getSubjects()).contains("유통상식");
        assertThat(a.getPassStandard()).contains("40점 이상");
    }

    /**
     * <b>2026-09-22 실호출에서 발견.</b> 응답에 캐리지리턴이 16진수 엔티티({@code &#xD;})로 섞여 온다.
     * 10진수만 풀면 화면에 {@code "한국산업인력공단&#xD;"} 처럼 그대로 뜬다 — 실제로 그렇게 나왔다.
     */
    @Test
    @DisplayName("16진수 엔티티도 푼다 — 10진수만 풀면 &#xD; 가 화면에 뜬다")
    void hex_entities_are_decoded_too() {
        QnetExamInfoParser.Acquisition a = QnetExamInfoParser.parseAcquisition(
                "&amp;#9312; 시 행 처 : 한국산업인력공단&amp;#xD;"
                        + "&amp;#9314; 시험과목 - 건축시공, 공정관리&amp;#xD;"
                        + "&amp;#9316; 합격기준 - 100점 만점에 60점 이상.");

        assertThat(a.getAgency()).isEqualTo("한국산업인력공단");
        assertThat(a.getSubjects()).doesNotContain("&#").doesNotContain("xD");
        assertThat(a.getPassStandard()).isEqualTo("- 100점 만점에 60점 이상.");
    }

    /** 줄바꿈이 값 안에 남으면 화면에서 줄이 들쭉날쭉해진다 — 한 칸으로 줄인다. */
    @Test
    @DisplayName("값 안의 줄바꿈은 한 칸으로 줄인다")
    void newlines_collapse_to_one_space() {
        QnetExamInfoParser.Acquisition a = QnetExamInfoParser.parseAcquisition(
                "\u2462 시험과목 : 가스관계\r\n 및 산업안전관계법규");   // \u2462 는 시험과목 자리다

        assertThat(a.getSubjects()).isEqualTo("가스관계 및 산업안전관계법규");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 실호출 응답 (2026-09-22). 저장된 샘플과 모양이 다르다 — 원문자가 없다.
    // 샘플만 보고 원문자로 가르게 짜 놨더니 5종 중 3종이 하나도 안 갈렸다.
    // ─────────────────────────────────────────────────────────────────────

    /** 정보처리기사 취득방법 실제 응답. 이름표만 있고 원문자는 없다. */
    private static final String LIVE_1320 =
            "정보처리기사 취득방법 시 행 처 : 한국산업인력공단 관련학과 :모든 학과 응시가능 "
                    + "시험과목 - 필기 1. 소프트웨어설계 2. 소프트웨어개발 3. 데이터베이스구축 "
                    + "4. 프로그래밍언어활용 5. 정보시스템구축관리 - 실기 : 정보처리 실무 "
                    + "검정방법 - 필기 : 객관식 4지 택일형, 과목당 20문항(과목당 30분) "
                    + "- 실기 : 필답형(2시간30분) "
                    + "합격기준 - 필기 : 100점을 만점으로 하여 과목당 40점 이상, 전과목 평균 60점 이상. "
                    + "- 실기 : 100점을 만점으로 하여 60점 이상.";

    @Test
    @DisplayName("원문자가 없는 실제 응답도 가른다 — 이게 대다수다")
    void real_response_without_markers_is_parsed() {
        QnetExamInfoParser.Acquisition a = QnetExamInfoParser.parseAcquisition(LIVE_1320);

        assertThat(a.getAgency()).isEqualTo("한국산업인력공단");
        assertThat(a.getRelatedMajor()).isEqualTo("모든 학과 응시가능");
        assertThat(a.getSubjects()).startsWith("- 필기").contains("정보처리 실무");
        assertThat(a.getMethod()).contains("객관식 4지 택일형");
        assertThat(a.getPassStandard()).contains("과목당 40점 이상");
    }

    /** 한 항목이 뒤 항목을 삼키면 안 된다 — 시험과목 칸에 합격기준이 딸려 오면 화면이 엉킨다. */
    @Test
    @DisplayName("한 항목이 뒤 항목을 삼키지 않는다")
    void a_section_does_not_swallow_the_next() {
        QnetExamInfoParser.Acquisition a = QnetExamInfoParser.parseAcquisition(LIVE_1320);

        assertThat(a.getSubjects()).doesNotContain("검정방법").doesNotContain("합격기준");
        assertThat(a.getMethod()).doesNotContain("합격기준");
    }

    /** 관련학과가 없는 시험이 있다 — 그때 시행처가 시험과목까지 삼키면 안 된다. */
    @Test
    @DisplayName("빠진 항목이 있어도 앞 항목이 번지지 않는다")
    void missing_label_does_not_bleed() {
        QnetExamInfoParser.Acquisition a = QnetExamInfoParser.parseAcquisition(
                "시행처 : 대한상공회의소 시험과목 - 유통상식 합격기준 - 매 과목 40점 이상");

        assertThat(a.getAgency()).isEqualTo("대한상공회의소");
        assertThat(a.getRelatedMajor()).isNull();
        assertThat(a.getSubjects()).isEqualTo("- 유통상식");
    }

    /** 공인중개사 응답에서 실제로 온 이름 엔티티. 안 풀면 화면에 &middot; 가 그대로 뜬다. */
    @Test
    @DisplayName("이름 엔티티도 푼다")
    void named_entities_are_decoded() {
        assertThat(QnetExamInfoParser.clean("시&middot;도별로 준비물이 다를 수 있음"))
                .isEqualTo("시·도별로 준비물이 다를 수 있음");
    }

    /**
     * 시험 종류마다 오는 항목이 다르다 — 전문자격은 {@code 응시자격} 이 따로 온다.
     * 그런 항목은 가르지 않고 다듬기만 해서 그대로 보여준다.
     */
    @Test
    @DisplayName("가르지 않는 항목도 읽을 수 있게 다듬는다")
    void standalone_items_are_tidied() {
        String cleaned = QnetExamInfoParser.clean(
                "BODY {\tFONT-SIZE: 10pt}\u2460 응시자격 o 제한 없음&#xD;  ※ 단, 5년이 경과되지 않은 자");

        assertThat(cleaned).isEqualTo("응시자격 o 제한 없음 ※ 단, 5년이 경과되지 않은 자");
    }
}

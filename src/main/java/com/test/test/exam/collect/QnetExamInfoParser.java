package com.test.test.exam.collect;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 큐넷이 주는 <b>덩어리 글</b>을 사람이 읽을 조각으로 가른다.
 *
 * <p>상세 화면에 날짜 말고는 아무것도 없었다 — 응시료·시험과목·합격기준이 모델에 필드조차 없어서
 * 취준생이 "이 시험 볼까"를 여기서 못 정하고 큐넷으로 나갔다. 큐넷 API 는 이 정보를 주긴 하는데
 * <b>구조화해서 주지 않는다.</b>
 *
 * <h3>실호출로 알게 된 것 (2026-09-22, 5종)</h3>
 * <ul>
 *   <li><b>저장된 샘플에는 원문자(①~⑤)가 있는데 실제 응답에는 없다.</b> 이름표("시험과목",
 *       "합격기준")만 있다. 원문자에만 기대면 대부분의 시험에서 하나도 못 가른다 —
 *       그래서 <b>이름표로 가른다.</b> 원문자는 있으면 떼고 없어도 상관없다.</li>
 *   <li>{@code contents} 앞에 CSS 블록이 박혀 있다 — {@code BODY { FONT-SIZE: 10pt; ... }}</li>
 *   <li>엔티티가 10진수({@code &#9312;})·16진수({@code &#xD;})·이름({@code &middot;})으로 섞여 온다</li>
 *   <li>응시료는 {@code "1차 : 19400, 2차 : 22600"} 한 줄 글이다(5종 모두 이 모양이라 잘 갈렸다)</li>
 * </ul>
 *
 * <p><b>못 가르면 비워 두고 원문을 지킨다.</b> 613종의 표기 편차를 다 맞출 수 있다고 믿지 않는다 —
 * 억지로 채운 값은 틀린 값이고, 틀린 응시료는 없는 응시료보다 나쁘다. 화면은 조각이 없으면
 * 원문을 그대로 보여주면 된다.
 */
public final class QnetExamInfoParser {

    private QnetExamInfoParser() {
    }

    /**
     * {@code &#9312;}(10진수)와 {@code &#xD;}(16진수) 둘 다 푼다.
     *
     * <p>16진수를 빼먹으면 캐리지리턴이 그대로 새어 화면에 {@code "한국산업인력공단&#xD;"} 처럼 뜬다
     * — 2026-09-22 실호출에서 실제로 그렇게 나왔다(건축시공기술사·가스기술사).
     */
    private static final Pattern ENTITY_PATTERN = Pattern.compile("&#([xX]?[0-9A-Fa-f]{1,5});");

    /** 이름 엔티티도 섞여 온다 — 공인중개사 응답에서 {@code &middot;} 를 봤다. */
    private static final Map<String, String> NAMED = Map.of(
            "&middot;", "·", "&nbsp;", " ", "&quot;", "\"",
            "&lt;", "<", "&gt;", ">", "&apos;", "'");

    /** {@code "1차 : 19400"} / {@code "2차 : 22600"} */
    private static final Pattern FEE_ROUND =
            Pattern.compile("([12])\\s*차\\s*[:：]?\\s*([0-9][0-9,]*)");

    /**
     * 항목 이름표. 시험마다 띄어쓰기가 달라서 변형을 같이 둔다.
     * 순서가 곧 {@link Acquisition} 의 칸 순서다.
     */
    private static final String[][] LABELS = {
            {"시 행 처", "시행처"},
            {"관련학과"},
            {"시험과목"},
            {"검정방법"},
            {"합격기준"},
    };

    // ── 응시료 ────────────────────────────────────────────────────────────────

    /**
     * {@code "1차 : 19400, 2차 : 22600"} → 필기 19400 · 실기 22600.
     *
     * <p>큐넷은 필기를 1차, 실기를 2차라고 부른다. 한쪽만 오는 시험이 있는데
     * 그때 없는 쪽을 0 으로 채우면 화면에 <b>"무료"</b> 로 보인다 — 그래서 비워 둔다.
     */
    public static Fee parseFee(String contents) {
        String raw = contents == null ? null : clean(contents);
        if (raw == null || raw.isBlank()) {
            return new Fee(null, null, raw);
        }
        Integer written = null;
        Integer practical = null;
        Matcher m = FEE_ROUND.matcher(raw);
        while (m.find()) {
            int won = Integer.parseInt(m.group(2).replace(",", ""));
            if ("1".equals(m.group(1))) {
                written = won;
            } else {
                practical = won;
            }
        }
        return new Fee(written, practical, raw);
    }

    // ── 취득방법 ──────────────────────────────────────────────────────────────

    /**
     * 시행처 · 관련학과 · 시험과목 · 검정방법 · 합격기준으로 가른다.
     *
     * <p>한 항목은 그 이름표부터 <b>다음에 나오는 다른 이름표</b> 앞까지다. 빠진 항목이 있어도
     * 뒤 항목을 통째로 삼키지 않는다 — 실제 응답에 관련학과가 없는 시험이 있다.
     */
    public static Acquisition parseAcquisition(String contents) {
        if (contents == null || contents.isBlank()) {
            return new Acquisition(null, null, null, null, null, contents);
        }
        String text = clean(contents);

        int[] at = new int[LABELS.length];
        int[] len = new int[LABELS.length];
        for (int i = 0; i < LABELS.length; i++) {
            at[i] = -1;
            for (String label : LABELS[i]) {
                int p = text.indexOf(label);
                if (p >= 0) {
                    at[i] = p;
                    len[i] = label.length();
                    break;
                }
            }
        }

        String[] v = new String[LABELS.length];
        for (int i = 0; i < LABELS.length; i++) {
            if (at[i] < 0) {
                continue;
            }
            int start = at[i] + len[i];
            int end = text.length();
            for (int k = 0; k < LABELS.length; k++) {
                if (at[k] > at[i] && at[k] < end) {
                    end = at[k];
                }
            }
            v[i] = tidy(text.substring(start, end));
        }

        return new Acquisition(v[0], v[1], v[2], v[3], v[4], contents);
    }

    /**
     * 사람이 읽을 한 덩어리로 — 엔티티·CSS·원문자·잉여 공백을 걷어낸다.
     *
     * <p>시험 종류마다 오는 항목이 달라서({@code 응시자격}, {@code 시험과목 및 배점} 처럼)
     * 가르지 않고 그대로 보여 줄 항목도 이걸 거쳐 나간다.
     */
    public static String clean(String s) {
        if (s == null) {
            return null;
        }
        String t = decodeEntities(s);
        t = t.replaceAll("(?i)[A-Z][A-Z0-9]*\\s*\\{[^}]*\\}", " ");   // CSS 블록
        t = t.replaceAll("[\\u2460-\\u2473]", " ");                    // 원문자 ①~⑳
        return t.replaceAll("\\s+", " ").trim();
    }

    private static String decodeEntities(String s) {
        String t = s.replace("&amp;", "&");
        for (Map.Entry<String, String> e : NAMED.entrySet()) {
            t = t.replace(e.getKey(), e.getValue());
        }
        StringBuilder out = new StringBuilder(t.length());
        Matcher m = ENTITY_PATTERN.matcher(t);
        int last = 0;
        while (m.find()) {
            String body = m.group(1);
            int cp = (body.charAt(0) == 'x' || body.charAt(0) == 'X')
                    ? Integer.parseInt(body.substring(1), 16)
                    : Integer.parseInt(body);
            out.append(t, last, m.start()).append((char) cp);
            last = m.end();
        }
        return out.append(t.substring(last)).toString();
    }

    /** 이름표 뒤에 남은 콜론·공백을 떼어 낸다. */
    private static String tidy(String s) {
        String t = s.replaceFirst("^\\s*[:：]\\s*", "").replaceAll("\\s+", " ").trim();
        return t.isBlank() ? null : t;
    }

    // ── 값 ───────────────────────────────────────────────────────────────────

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Fee {
        /** 필기(1차) 응시료. 못 읽었으면 null — 0 이 아니다 */
        private Integer written;
        /** 실기(2차) 응시료. 실기가 없는 시험이면 null */
        private Integer practical;
        /** 시행처가 준 원문. 못 갈랐을 때 화면이 이걸 그대로 보여준다 */
        private String raw;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Acquisition {
        private String agency;
        /**
         * 관련학과. <b>응시자격이 아니다.</b>
         *
         * <p>응시자격은 전문자격에서 {@code infogb=응시자격} 이라는 <b>별도 항목</b>으로 온다
         * (공인중개사에서 확인, 2026-09-22). 기술자격에는 그 항목이 없다 —
         * 두 말을 같은 말로 쓰면 사용자가 그 말을 믿고 원서를 낸다.
         */
        private String relatedMajor;
        private String subjects;
        /** 검정방법 — 객관식인지 필답형인지 */
        private String method;
        private String passStandard;
        /** 원문. 못 갈라도 이건 남는다 */
        private String raw;
    }
}

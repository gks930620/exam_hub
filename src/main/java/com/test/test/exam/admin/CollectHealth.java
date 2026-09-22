package com.test.test.exam.admin;

import com.test.test.exam.domain.CrawlLog;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 수집 소스 하나의 <b>건강 상태</b> — "지금 뭐가 고장났나"에 대한 답.
 *
 * <p><b>왜 필요한가</b>: 수집 결과는 {@code crawl_log} 와 서버 로그에만 남았고 화면도 API 도 없었다.
 * 코드 주석은 "운영 주 1회 crawl_log 점검으로 커버"라고 적혀 있었지만 <b>점검할 수단이 없었다.</b>
 *
 * <p>그리고 더 조용한 고장이 있다 — 스크래퍼가 <b>0건을 가져와도 예외가 아니라 성공</b>이다.
 * 시행처가 화면을 개편하면 선택자가 안 맞아 0건이 오는데, 그동안 사용자 화면에는 <b>옛 일정이
 * 그대로</b> 보인다. 매니저도 사용자도 아무 이상을 못 느낀다. 이 서비스에서 가장 나쁜 실패는
 * 틀린 걸 모르는 것이다 — 그래서 0건을 고장으로 본다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CollectHealth {

    /** 하루 한 번 도는 배치라, 이만큼 넘게 소식이 없으면 배치가 멈춘 것이다 */
    private static final Duration STALE_AFTER = Duration.ofDays(2);

    public enum State {
        /** 최근 실행이 성공했고 건수도 있다 */
        OK("정상"),
        /** 마지막 실행이 실패했다 */
        FAILED("실패"),
        /** 성공했지만 0건 — 시행처 화면이 바뀌었을 수 있다 */
        EMPTY("0건"),
        /** 오래 안 돌았다 — 배치가 멈췄을 수 있다 */
        STALE("멈춤"),
        /** 기록이 없다 — 한 번도 안 돌았다 */
        NEVER_RAN("기록 없음");

        private final String label;

        State(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private String source;
    private State state;
    private String stateLabel;
    /** 매니저가 읽을 한 줄 — 무엇이 문제고 어디를 보면 되는지 */
    private String message;
    private String lastRunAt;
    private int fetched;
    private int consecutiveFailures;
    /** 손봐야 하는가 — 화면이 이 값으로 경고를 띄운다 */
    private boolean needsAttention;
    /**
     * 눈으로 확인할 원본 주소. 없는 소스(API·시드)는 {@code null}.
     *
     * <p>"원본 사이트를 열어 확인해 주세요"라고 해 놓고 주소를 안 주면 그날 안 본다.
     */
    private String siteUrl;

    /**
     * @param logs 그 소스의 최근 실행 기록. 순서는 상관없다 — 시각으로 가장 최근을 고른다.
     *             정렬에 기대면 쿼리를 한 번 고칠 때 조용히 틀린다.
     *             <b>부분 수집은 빼고 본다</b> — 그 소스가 맡는 종목이 대상에 없으면 0건이 정상이라,
     *             같이 세면 멀쩡한 소스가 "고장"으로 뜬다(거짓 경보는 경보가 없는 것보다 나쁘다).
     */
    public static CollectHealth of(String source, List<CrawlLog> logs, LocalDateTime now) {
        return of(source, logs, now, null);
    }

    /** @param siteUrl 사람이 열어 볼 원본 주소(없으면 null) */
    public static CollectHealth of(String source, List<CrawlLog> logs, LocalDateTime now, String siteUrl) {
        logs = logs == null ? List.of() : logs.stream().filter(l -> !l.isPartial()).toList();
        if (logs.isEmpty()) {
            return new CollectHealth(source, State.NEVER_RAN, State.NEVER_RAN.getLabel(),
                    "아직 한 번도 돌지 않았습니다. 매일 05:00 배치를 기다려 보세요 — 내일도 비어 있으면 원본 사이트를 확인해 주세요.",
                    null, 0, 0, true, siteUrl);
        }

        List<CrawlLog> newestFirst = logs.stream()
                .sorted(Comparator.comparing(CrawlLog::getStartedAt).reversed())
                .toList();
        CrawlLog last = newestFirst.get(0);

        int consecutive = 0;
        for (CrawlLog l : newestFirst) {
            if (l.isSuccess()) {
                break;
            }
            consecutive++;
        }

        int fetched = last.getFetchedCount() == null ? 0 : last.getFetchedCount();
        String at = last.getStartedAt().toString();

        if (!last.isSuccess()) {
            String why = last.getErrorMessage() == null ? "사유가 기록되지 않았습니다" : last.getErrorMessage();
            return new CollectHealth(source, State.FAILED, State.FAILED.getLabel(),
                    (consecutive >= 2 ? consecutive + "회 연속 실패 — " : "") + why,
                    at, fetched, consecutive, true, siteUrl);
        }
        if (fetched == 0) {
            return new CollectHealth(source, State.EMPTY, State.EMPTY.getLabel(),
                    "오류 없이 0건을 가져왔습니다. 시행처가 화면을 바꿨을 수 있습니다 — 원본 사이트를 열어 확인해 주세요.",
                    at, 0, 0, true, siteUrl);
        }
        if (Duration.between(last.getStartedAt(), now).compareTo(STALE_AFTER) > 0) {
            return new CollectHealth(source, State.STALE, State.STALE.getLabel(),
                    "마지막 수집이 " + Duration.between(last.getStartedAt(), now).toDays()
                            + "일 전입니다. 배치가 멈췄을 수 있습니다.",
                    at, fetched, 0, true, siteUrl);
        }
        return new CollectHealth(source, State.OK, State.OK.getLabel(),
                fetched + "건을 가져왔습니다.", at, fetched, 0, false, siteUrl);
    }
}

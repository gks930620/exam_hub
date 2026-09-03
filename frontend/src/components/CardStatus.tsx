import { ddayLabel } from '../lib/format';
import { badgeLabel, badgeTone, eventAtLabel } from '../lib/status';
import type { CardBadge, ScheduleState } from '../api/types';

/**
 * 카드 하단 상태 — "일정 있음/없음"이 아니라 <b>지금 무엇이 다가오나</b>.
 *
 * 시험 찾기·내 시험이 같은 어휘·같은 배지 색을 쓰도록 한 곳에 둔다. 남은 일정이 전부 지난
 * 시험은 '다음 회차 미정'이다 — 지난 날짜를 멀쩡한 일정처럼 보여주면 사용자가 그 날짜를 믿는다.
 * 상시시험에 "등록해 두면 알려드립니다"는 지키지 못할 약속이라 다른 말을 한다.
 */
export default function CardStatus({
  state, badge, badgeFallback = '예정', label, at, dday, lastExamDate, inlineDday = false,
}: {
  state: ScheduleState;
  /** 서버 CardBadge 코드 — 어휘·색은 lib/status 가 정한다 */
  badge: CardBadge | null;
  /** 모르는 코드일 때 쓸 문구(내 시험은 서버가 준 badgeLabel) */
  badgeFallback?: string;
  label: string | null;
  at: string | null;
  dday: number | null;
  lastExamDate: string | null;
  /** 배지 안에 D-n 을 붙일지 — 내 시험 카드는 큰 D-day 를 따로 두므로 뺀다 */
  inlineDday?: boolean;
}) {
  if (state === 'ROLLING') {
    return (
      <>
        <span className="k-badge">상시시험</span>
        <span className="when">원하는 날짜에 신청하는 시험이라 정해진 일정이 없습니다</span>
      </>
    );
  }
  if (state === 'UPCOMING') {
    return (
      <>
        <span className={`k-badge ${badgeTone(badge)}`}>
          {badgeLabel(badge, badgeFallback)}
          {inlineDday && dday != null && <> · {ddayLabel(dday)}</>}
        </span>
        {label && <span className="when">{label} · {eventAtLabel(badge, at)}</span>}
      </>
    );
  }
  if (state === 'PAST_ONLY') {
    return (
      <>
        <span className="k-badge k-badge--warn">다음 회차 미정</span>
        {lastExamDate && <span className="when">마지막 시험 {lastExamDate}</span>}
      </>
    );
  }
  return (
    <>
      <span className="k-badge k-badge--warn">일정 미정</span>
      <span className="when">일정이 확인되면 알려 드립니다</span>
    </>
  );
}

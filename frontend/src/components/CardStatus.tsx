import { ddayLabel } from '../lib/format';
import { badgeLabel, badgeTone, eventAtLabel } from '../lib/status';
import type { CardBadge, ScheduleState } from '../api/types';

/**
 * 카드 하단 상태 — "일정 있음/없음"이 아니라 <b>지금 무엇이 다가오나</b>.
 *
 * 시험 찾기·내 시험이 같은 어휘·같은 배지 색을 쓰도록 한 곳에 둔다. 남은 일정이 전부 지난
 * 시험은 '다음 회차 미정'이다 — 지난 날짜를 멀쩡한 일정처럼 보여주면 사용자가 그 날짜를 믿는다.
 * 상시시험에 "등록해 두면 알려드립니다"는 지키지 못할 약속이라 다른 말을 한다.
 *
 * <p><b>두 화면은 묻는 것이 다르다</b>(2026-09-10). 시험 찾기는 <b>어떤 시험인지 고르는</b> 화면이라
 * 회차·구분·시각까지 늘어놓을 자리가 아니다 — 그건 상세의 몫이다({@code compact}).
 * 내 시험은 이미 고른 시험을 <b>지키는</b> 화면이라 "몇 회 무슨 시험이 언제"가 본문이다.
 */
export default function CardStatus({
  state, badge, badgeFallback = '예정', label, at, dday, lastExamDate,
  inlineDday = false, compact = false, plain = false,
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
  /** 목록용 — 배지 한 줄만 남긴다. 설명 줄이 접히며 만들던 들쭉날쭉함이 사라진다 */
  compact?: boolean;
  /**
   * 내 시험용 — 배지 없이 <b>문장 한 줄</b>. D-day 는 카드 윗줄에 크게 있고, "접수 시작"이라는 말이
   * 상태를 이미 담고 있어 배지까지 붙이면 같은 말을 세 번 한다(설계 05 §19-6).
   * 배지가 있던 자리에 두 줄로 접히는 문장이 붙어 눈높이가 어긋나던 것도 같이 사라진다.
   */
  plain?: boolean;
}) {
  const dot = inlineDday && dday != null ? <> · {ddayLabel(dday)}</> : null;

  if (plain) {
    const text =
      state === 'ROLLING' ? '상시시험 · 원하는 날짜에 신청하는 시험이라 정해진 일정이 없습니다'
      // 상태 말(접수 예정·시험 진행 중)은 라벨이 못 담는다 — "1회 시험" 만으로는 진행 중인지 모른다
      : state === 'UPCOMING' ? [badgeLabel(badge, badgeFallback), label, eventAtLabel(badge, at)].filter(Boolean).join(' · ')
      : state === 'PAST_ONLY' ? `다음 회차 미정${lastExamDate ? ` · 마지막 시험 ${lastExamDate}` : ''}`
      : '일정 미정 · 일정이 확인되면 알려 드립니다';
    return <span className="when">{text}</span>;
  }

  if (state === 'ROLLING') {
    return (
      <>
        {/* 킷 기본 배지는 면이 --surface-alt 다. 카드를 그 색으로 칠한 화면에서는 통째로 사라지므로
            (설계 05 §19-7) 이름을 붙여 두고, 그 화면이 면만 한 단 올린다. */}
        <span className="k-badge badge-neutral">상시시험</span>
        {!compact && <span className="when">원하는 날짜에 신청하는 시험이라 정해진 일정이 없습니다</span>}
      </>
    );
  }
  if (state === 'UPCOMING') {
    return (
      <>
        <span className={`k-badge ${badgeTone(badge)}`}>{badgeLabel(badge, badgeFallback)}{dot}</span>
        {!compact && label && <span className="when">{label} · {eventAtLabel(badge, at)}</span>}
      </>
    );
  }
  if (state === 'PAST_ONLY') {
    return (
      <>
        <span className="k-badge k-badge--warn">다음 회차 미정</span>
        {!compact && lastExamDate && <span className="when">마지막 시험 {lastExamDate}</span>}
      </>
    );
  }
  return (
    <>
      <span className="k-badge k-badge--warn">일정 미정</span>
      {!compact && <span className="when">일정이 확인되면 알려 드립니다</span>}
    </>
  );
}

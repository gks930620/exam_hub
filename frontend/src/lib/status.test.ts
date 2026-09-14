import { describe, expect, it } from 'vitest';
import { badgeLabel, badgeTone, eventAtLabel, roundLabel, scheduleStateOf } from './status';

/**
 * 상태 배지 어휘 — 시험 찾기·내 시험·매니저 현황이 <b>같은 말</b>을 쓰도록 한 곳에서 정한다.
 * 서버 코드(CardBadge)가 늘면(EXAM_ONGOING) 여기 한 줄만 더한다.
 */
describe('badgeLabel', () => {
  it('서버 코드를 한 어휘로 옮긴다', () => {
    expect(badgeLabel('REG_OPEN', '')).toBe('접수 중');
    expect(badgeLabel('REG_UPCOMING', '')).toBe('접수 예정');
    expect(badgeLabel('EXAM_UPCOMING', '')).toBe('시험 예정');
    expect(badgeLabel('EXAM_ONGOING', '')).toBe('시험 진행 중');
    expect(badgeLabel('RESULT_PENDING', '')).toBe('합격 발표 예정');
  });

  it('모르는 코드면 대체 문구를 쓴다 — 영문 코드를 화면에 내지 않는다', () => {
    expect(badgeLabel('SOMETHING_NEW', '예정')).toBe('예정');
    expect(badgeLabel(null, '예정')).toBe('예정');
    expect(badgeLabel(undefined, '예정')).toBe('예정');
  });
});

describe('badgeTone', () => {
  it('초록은 "지금 접수할 수 있다"에만, 나머지는 인디고', () => {
    expect(badgeTone('REG_OPEN')).toBe('k-badge--ok');
    expect(badgeTone('REG_UPCOMING')).toBe('k-badge--point');
    expect(badgeTone('EXAM_ONGOING')).toBe('k-badge--point');
  });
});

describe('scheduleStateOf', () => {
  it('서버가 주면 그대로 쓴다', () => {
    expect(scheduleStateOf({ scheduleState: 'PAST_ONLY', rolling: false, hasSchedule: true })).toBe('PAST_ONLY');
  });

  it('없으면 상시 → 일정 유무 순으로 유도한다', () => {
    expect(scheduleStateOf({ scheduleState: null, rolling: true, hasSchedule: false })).toBe('ROLLING');
    expect(scheduleStateOf({ scheduleState: null, rolling: false, hasSchedule: true })).toBe('UPCOMING');
    expect(scheduleStateOf({ scheduleState: null, rolling: false, hasSchedule: false })).toBe('NONE');
  });
});

describe('eventAtLabel', () => {
  it('보통은 시각까지', () => {
    expect(eventAtLabel('REG_UPCOMING', '2026-09-10T10:00')).toBe('2026-09-10 10:00');
  });

  it('시험 진행 중은 종료일 00:00 이 오므로 "~ 날짜"로', () => {
    expect(eventAtLabel('EXAM_ONGOING', '2026-09-05T00:00')).toBe('~ 2026-09-05');
  });

  /**
   * 시험은 끝났고 발표만 남은 상태. 시행처가 발표 <b>시각</b>까지 알려주지 않아 at 이 00:00 이라,
   * "2026-09-16 00:00" 으로 찍으면 자정에 발표하는 것처럼 읽힌다.
   */
  it('합격 발표 예정은 날짜만', () => {
    expect(eventAtLabel('RESULT_PENDING', '2026-09-16T00:00')).toBe('2026-09-16');
  });

  it('시각이 없으면 - 로 둔다', () => {
    expect(eventAtLabel('REG_UPCOMING', null)).toBe('-');
  });
});

/**
 * 회차가 없는 시험(토익스피킹·TOEIC Bridge)은 서버가 시험일을 회차 자리에 멱등 키로 넣는다.
 * 그 숫자는 우리 내부 키다 — 화면에 "2026년 20261011회"로 새어 나가면 안 된다.
 */
describe('roundLabel', () => {
  it('시행처가 매긴 회차는 그대로 보여준다', () => {
    expect(roundLabel(2026, 576)).toBe('2026년 576회');
  });

  it('시험일을 키로 쓴 회차는 회차로 보여주지 않는다', () => {
    expect(roundLabel(2026, 20261011)).toBe('2026년');
  });

  /**
   * 시드는 연·월(202609)을 회차 자리에 넣는다. 기준을 백만으로 잡았더니 이게 그 밑이라
   * "2026년 202609회"가 그대로 화면에 떴다(2026-09-08 실측).
   */
  it('연·월을 키로 쓴 회차도 회차로 보여주지 않는다', () => {
    expect(roundLabel(2026, 202609)).toBe('2026년');
  });

  it('실제 회차 중 가장 큰 것(DIAT 2612회)은 그대로 보여준다', () => {
    expect(roundLabel(2026, 2612)).toBe('2026년 2612회');
  });

  /** 큐넷이 실기 회차를 0 으로 주는 행이 있다 — "2026년 0회"로 뜨면 안 된다. */
  it('0 이하는 회차로 보여주지 않는다', () => {
    expect(roundLabel(2026, 0)).toBe('2026년');
    expect(roundLabel(2026, -3)).toBe('2026년');
  });
});

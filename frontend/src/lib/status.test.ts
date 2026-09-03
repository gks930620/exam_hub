import { describe, expect, it } from 'vitest';
import { badgeLabel, badgeTone, eventAtLabel, scheduleStateOf } from './status';

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

  it('시각이 없으면 - 로 둔다', () => {
    expect(eventAtLabel('REG_UPCOMING', null)).toBe('-');
  });
});

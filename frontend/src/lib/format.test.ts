import { describe, expect, it } from 'vitest';
import { ddayLabel, fmtAt, fmtDate, weekdayOf } from './format';

/**
 * 시각·D-day 표기 — 화면마다 제각각이던 것을 한 곳으로 모았다.
 * ISO 원문(2026-09-01T12:34:56)이 그대로 노출되던 자리(커뮤니티·글 상세·일정 모달)가 전부 여기를 탄다.
 */
describe('fmtAt', () => {
  it('ISO 의 T 를 공백으로 바꾸고 초는 뗀다', () => {
    expect(fmtAt('2026-09-21T10:00:00')).toBe('2026-09-21 10:00');
  });

  it('비어 있으면 - 로 둔다', () => {
    expect(fmtAt(null)).toBe('-');
    expect(fmtAt(undefined)).toBe('-');
    expect(fmtAt('')).toBe('-');
  });

  it('날짜만 있으면 그대로 둔다', () => {
    expect(fmtAt('2026-03-12')).toBe('2026-03-12');
  });
});

describe('fmtDate', () => {
  it('시각을 떼고 날짜만 남긴다 — 시험 종료일처럼 00:00 이 뜻이 없는 값용', () => {
    expect(fmtDate('2026-09-05T00:00')).toBe('2026-09-05');
  });

  it('비어 있으면 - 로 둔다', () => {
    expect(fmtDate(null)).toBe('-');
  });
});

describe('ddayLabel', () => {
  it('당일은 D-Day', () => {
    expect(ddayLabel(0)).toBe('D-Day');
  });

  it('앞으로는 D-n', () => {
    expect(ddayLabel(3)).toBe('D-3');
  });

  it('지났으면 D+n', () => {
    expect(ddayLabel(-2)).toBe('D+2');
  });
});

describe('weekdayOf', () => {
  it('날짜 문자열을 현지 자정으로 읽어 요일을 낸다 — new Date("2026-09-06") 은 UTC 라 서쪽에서 하루가 밀린다', () => {
    expect(weekdayOf('2026-09-06')).toBe('일');
    expect(weekdayOf('2026-09-07')).toBe('월');
  });
});

import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import CardStatus from './CardStatus';

/**
 * <b>지어낸 날짜를 확정처럼 보여주지 않는다.</b>
 *
 * <p>시행처 일정을 못 구한 시험에는 시드가 추정 날짜를 넣는다(APPROX). 그 값이 카드에서
 * <b>확신 있는 D-day</b> 로 보이면 사용자는 그 날을 믿고 준비하다 진짜 마감을 놓친다 —
 * 일정을 안 알려 주는 것보다 나쁘다.
 *
 * <p>2026-09-21 실측: 상세 표에만 "시행처 확인 필요"가 있었고, 정작 사람들이 보는 카드에는
 * 아무 표시가 없었다. 19종이 그 상태였다.
 */
describe('CardStatus — 추정 날짜 표시', () => {
  const base = {
    state: 'UPCOMING' as const,
    badge: 'REG_UPCOMING',
    label: '1회 필기 접수 시작',
    at: '2026-10-17T10:00',
    dday: 26,
    lastExamDate: null,
  };

  it('확인된 날짜에는 아무것도 붙이지 않는다', () => {
    render(<CardStatus {...base} confirmed inlineDday compact />);

    expect(screen.queryByText('추정')).toBeNull();
    expect(screen.getByText(/접수 예정/)).toBeTruthy();
  });

  it('추정 날짜면 카드에 "추정"이 붙는다', () => {
    render(<CardStatus {...base} confirmed={false} inlineDday compact />);

    const mark = screen.getByText('추정');
    expect(mark).toBeTruthy();
    expect(mark.getAttribute('title')).toContain('시행처');
  });

  /** 내 시험 카드는 배지가 없고 문장 한 줄이다 — 거기에도 붙어야 한다 */
  it('내 시험 카드(문장형)에도 붙는다', () => {
    render(<CardStatus {...base} badgeFallback="접수 예정" confirmed={false} plain />);

    expect(screen.getByText('추정')).toBeTruthy();
  });

  /** 값을 안 주면 확정으로 본다 — 기존 호출부가 조용히 "추정"으로 바뀌면 안 된다 */
  it('confirmed 를 안 주면 표시하지 않는다', () => {
    render(<CardStatus {...base} inlineDday compact />);

    expect(screen.queryByText('추정')).toBeNull();
  });

  it('일정이 없는 상태에는 추정 표시가 나오지 않는다', () => {
    render(<CardStatus state="NONE" badge={null} label={null} at={null} dday={null}
                       lastExamDate={null} confirmed={false} compact />);

    expect(screen.queryByText('추정')).toBeNull();
    expect(screen.getByText('일정 미정')).toBeTruthy();
  });
});

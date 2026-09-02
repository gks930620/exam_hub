import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import HomePage from './HomePage';
import { examApi } from '../api/exams';
import type { FavoriteCard } from '../api/types';

/**
 * 내 시험(홈) — 킷 데모 구조: 히어로(가장 급한 것 하나) → 이번 주 지표 타일 → 등록한 시험 카드.
 *
 * <p>지표는 서버를 더 부르지 않고 관심 목록에서 센다 — 접수 중 = 배지 REG_OPEN,
 * 이번 주 = D-day 0~7. 숫자가 화면과 어긋나면 사용자가 "왜 3개라는데 카드는 2개냐"고 묻게 된다.
 */
function card(over: Partial<FavoriteCard>): FavoriteCard {
  return {
    certificateId: 1, name: '정보처리기사', badge: 'REG_SOON', badgeLabel: '접수 예정',
    eventLabel: '1회 필기 접수 시작', eventAt: '2026-09-10T10:00', dday: 5,
    ...over,
  };
}

describe('HomePage — 내 시험', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('가장 급한 시험이 히어로에, 이번 주 개수가 제목에 들어간다', async () => {
    vi.spyOn(examApi, 'favorites').mockResolvedValue({
      items: [
        card({ certificateId: 1, name: '정보처리기사', dday: 2, badge: 'REG_OPEN', badgeLabel: '접수 중' }),
        card({ certificateId: 2, name: '전기기사', dday: 6 }),
        card({ certificateId: 3, name: '한국사능력검정', dday: 40 }),
      ],
    });

    render(<MemoryRouter><HomePage /></MemoryRouter>);

    await waitFor(() => expect(screen.getByRole('heading', { level: 1 })).toBeTruthy());
    expect(screen.getByRole('heading', { level: 1 }).textContent).toContain('2개');
    // 히어로 안에 가장 급한 시험 이름
    expect(document.querySelector('.k-hero')?.textContent).toContain('정보처리기사');
  });

  it('지표 타일이 목록과 같은 숫자를 센다', async () => {
    vi.spyOn(examApi, 'favorites').mockResolvedValue({
      items: [
        card({ certificateId: 1, dday: 2, badge: 'REG_OPEN', badgeLabel: '접수 중' }),
        card({ certificateId: 2, dday: 6 }),
        card({ certificateId: 3, dday: 40 }),
      ],
    });

    render(<MemoryRouter><HomePage /></MemoryRouter>);
    await waitFor(() => expect(document.querySelector('.k-stats')).toBeTruthy());

    const values = Array.from(document.querySelectorAll('.k-stat__value')).map((el) => el.textContent);
    // 접수 중 1 · 이번 주 2 · 등록 3 · 가장 가까운 D-2
    expect(values).toEqual(['1', '2', '3', 'D-2']);
  });

  it('등록한 시험이 전부 카드로 나온다 (히어로에 올라간 것 포함)', async () => {
    vi.spyOn(examApi, 'favorites').mockResolvedValue({
      items: [card({ certificateId: 1, name: '정보처리기사', dday: 2 }), card({ certificateId: 2, name: '전기기사', dday: 6 })],
    });

    render(<MemoryRouter><HomePage /></MemoryRouter>);
    await waitFor(() => expect(document.querySelectorAll('.exam-card').length).toBe(2));
  });

  it('등록한 시험이 없으면 시험 찾기로 이끈다', async () => {
    vi.spyOn(examApi, 'favorites').mockResolvedValue({ items: [] });

    render(<MemoryRouter><HomePage /></MemoryRouter>);

    expect(await screen.findByText(/시험 찾으러 가기/)).toBeTruthy();
    expect(document.querySelector('.k-stats')).toBeNull();
  });
});

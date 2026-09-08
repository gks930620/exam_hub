import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import HomePage from './HomePage';
import { examApi } from '../api/exams';
import type { FavoriteCard } from '../api/types';

/**
 * 내 시험(홈) — 킷 데모 구조: 히어로(가장 급한 것 하나) → 지표 타일 → 등록한 시험 카드.
 *
 * <p>지표는 서버를 더 부르지 않고 관심 목록에서 센다. 숫자가 화면과 어긋나면 사용자가
 * "왜 3개라는데 카드는 2개냐"고 묻게 된다. 접수 중·이번 주 지표는 뺐다(익숙해지면 다시).
 *
 * <p>카드는 <b>scheduleState</b> 로 갈린다 — 일정이 없는 관심 시험이 빨간 "D-0" 으로 보이던
 * 결함(2026-09-03)을 여기서 막는다. D-day 숫자는 다가오는 일정이 있을 때만 존재한다.
 */
function card(over: Partial<FavoriteCard>): FavoriteCard {
  return {
    certificateId: 1, name: '정보처리기사', badge: 'REG_UPCOMING', badgeLabel: '접수 예정',
    eventLabel: '1회 필기 접수 시작', eventAt: '2026-09-10T10:00', dday: 5,
    scheduleState: 'UPCOMING', lastExamDate: null, hiddenReason: null,
    ...over,
  };
}

/**
 * 폐지·개칭된 시험은 카드가 남는다(사라지면 해제할 길이 없다). 그때 "일정이 확인되면 알려 드립니다"는
 * 지킬 수 없는 약속이라 사유를 그대로 말해야 한다 — 서버가 hiddenReason 을 주는데 화면이 버리고 있었다(2026-09-04).
 */
describe('내 시험 — 폐지·개칭된 관심 시험', () => {
  it('오지 않을 일정을 약속하지 않고 사유를 보여준다', async () => {
    vi.spyOn(examApi, 'favorites').mockResolvedValue({
      items: [card({
        certificateId: 9, name: '옛이름기사', badge: 'NONE', badgeLabel: '일정 없음',
        eventLabel: '', eventAt: null, dday: null, scheduleState: 'NONE',
        hiddenReason: "'새이름기사'(으)로 이름이 바뀌었습니다. 새 이름으로 다시 등록해 주세요.",
      })],
    });

    render(<MemoryRouter><HomePage /></MemoryRouter>);

    expect(await screen.findByText(/이름이 바뀌었습니다/)).toBeTruthy();
    expect(screen.queryByText(/일정이 확인되면 알려 드립니다/)).toBeNull();
  });
});

/** 일정이 없는 카드 — 서버는 dday/eventAt 을 null 로 준다. */
function noSchedule(over: Partial<FavoriteCard>): FavoriteCard {
  return card({
    badge: 'NONE', badgeLabel: '일정 없음', eventLabel: '', eventAt: null, dday: null,
    scheduleState: 'NONE', ...over,
  });
}

function cardOf(name: string): Element {
  const el = Array.from(document.querySelectorAll('.exam-card')).find((c) => c.textContent?.includes(name));
  if (!el) throw new Error(`카드 없음: ${name}`);
  return el;
}

describe('HomePage — 내 시험', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('가장 급한 시험이 히어로에, 등록한 개수가 제목에 들어간다', async () => {
    vi.spyOn(examApi, 'favorites').mockResolvedValue({
      items: [
        card({ certificateId: 1, name: '정보처리기사', dday: 2, badge: 'REG_OPEN', badgeLabel: '접수 중' }),
        card({ certificateId: 2, name: '전기기사', dday: 6 }),
        card({ certificateId: 3, name: '한국사능력검정', dday: 40 }),
      ],
    });

    render(<MemoryRouter><HomePage /></MemoryRouter>);

    await waitFor(() => expect(screen.getByRole('heading', { level: 1 })).toBeTruthy());
    expect(screen.getByRole('heading', { level: 1 }).textContent).toContain('3개');
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
    // 등록 3 · 가장 가까운 D-2 — 접수 중·이번 주는 뺐다(헷갈린다는 사용자 결정, 익숙해지면 다시)
    expect(values).toEqual(['3', 'D-2']);
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

  /** 화면 캡처로 확인된 결함 — 일정 없는 관심 시험이 빨간 "D-0" 이었다. */
  it('일정 없는 시험은 D-0 이 아니라 "일정 미정"이다', async () => {
    vi.spyOn(examApi, 'favorites').mockResolvedValue({
      items: [card({ certificateId: 1, dday: 2 }), noSchedule({ certificateId: 9, name: '국가직 9급' })],
    });

    render(<MemoryRouter><HomePage /></MemoryRouter>);
    await waitFor(() => expect(document.querySelectorAll('.exam-card').length).toBe(2));

    const c = cardOf('국가직 9급');
    expect(c.querySelector('.dday')).toBeNull();
    expect(c.textContent).toContain('일정 미정');
    expect(c.textContent).not.toContain('D-0');
  });

  it('상시시험은 "상시시험"으로 보여준다', async () => {
    vi.spyOn(examApi, 'favorites').mockResolvedValue({
      items: [noSchedule({ certificateId: 5, name: 'CCNA', scheduleState: 'ROLLING' })],
    });

    render(<MemoryRouter><HomePage /></MemoryRouter>);
    await waitFor(() => expect(document.querySelectorAll('.exam-card').length).toBe(1));

    const c = cardOf('CCNA');
    expect(c.querySelector('.dday')).toBeNull();
    expect(c.textContent).toContain('상시시험');
  });

  it('지난 일정만 있으면 "다음 회차 미정"과 마지막 시험일을 보여준다', async () => {
    vi.spyOn(examApi, 'favorites').mockResolvedValue({
      items: [noSchedule({ certificateId: 6, name: '경찰간부후보생', scheduleState: 'PAST_ONLY', lastExamDate: '2026-03-12' })],
    });

    render(<MemoryRouter><HomePage /></MemoryRouter>);
    await waitFor(() => expect(document.querySelectorAll('.exam-card').length).toBe(1));

    const c = cardOf('경찰간부후보생');
    expect(c.querySelector('.dday')).toBeNull();
    expect(c.textContent).toContain('다음 회차 미정');
    expect(c.textContent).toContain('마지막 시험 2026-03-12');
  });

  /** 히어로는 "가장 가까운 일정"이다 — 서버 정렬 첫 카드가 아니라 다가오는 일정이 있는 첫 카드. */
  it('히어로는 다가오는 일정이 있는 첫 카드를 올린다', async () => {
    vi.spyOn(examApi, 'favorites').mockResolvedValue({
      items: [noSchedule({ certificateId: 9, name: '국가직 9급' }), card({ certificateId: 2, name: '전기기사', dday: 6 })],
    });

    render(<MemoryRouter><HomePage /></MemoryRouter>);
    await waitFor(() => expect(document.querySelector('.k-hero')).toBeTruthy());

    expect(document.querySelector('.k-hero')?.textContent).toContain('전기기사');
    expect(document.querySelector('.k-hero')?.textContent).not.toContain('국가직 9급');
  });

  it('다가오는 일정이 하나도 없으면 히어로가 "다가오는 일정 없음"을 말한다', async () => {
    vi.spyOn(examApi, 'favorites').mockResolvedValue({
      items: [noSchedule({ certificateId: 9, name: '국가직 9급' })],
    });

    render(<MemoryRouter><HomePage /></MemoryRouter>);
    await waitFor(() => expect(document.querySelector('.k-hero')).toBeTruthy());

    expect(document.querySelector('.k-hero')?.textContent).toContain('다가오는 일정 없음');
    expect(screen.queryByText('일정 자세히 보기')).toBeNull();
    const values = Array.from(document.querySelectorAll('.k-stat__value')).map((el) => el.textContent);
    expect(values).toEqual(['1', '없음']);
  });

  /** 시험 진행 중(EXAM_ONGOING) — dday 0, eventAt 은 종료일 00:00. 시각이 아니라 "~ 종료일"로. */
  it('시험 진행 중은 종료일까지로 보여준다', async () => {
    vi.spyOn(examApi, 'favorites').mockResolvedValue({
      items: [card({
        certificateId: 3, name: '한국사능력검정', badge: 'EXAM_ONGOING', badgeLabel: '시험 진행 중',
        eventLabel: '1회 시험', eventAt: '2026-09-05T00:00', dday: 0,
      })],
    });

    render(<MemoryRouter><HomePage /></MemoryRouter>);
    await waitFor(() => expect(document.querySelectorAll('.exam-card').length).toBe(1));

    const c = cardOf('한국사능력검정');
    expect(c.textContent).toContain('시험 진행 중');
    expect(c.textContent).toContain('~ 2026-09-05');
    expect(c.textContent).not.toContain('00:00');
    expect(c.querySelector('.dday')?.textContent).toBe('D-Day');
  });

  it('빨간 D-day 는 7일 이내에만', async () => {
    vi.spyOn(examApi, 'favorites').mockResolvedValue({
      items: [
        card({ certificateId: 1, name: '정보처리기사', dday: 7 }),
        card({ certificateId: 2, name: '전기기사', dday: 8 }),
      ],
    });

    render(<MemoryRouter><HomePage /></MemoryRouter>);
    await waitFor(() => expect(document.querySelectorAll('.exam-card').length).toBe(2));

    expect(cardOf('정보처리기사').querySelector('.dday.soon')).toBeTruthy();
    expect(cardOf('전기기사').querySelector('.dday.soon')).toBeNull();
    expect(cardOf('전기기사').querySelector('.dday')?.textContent).toBe('D-8');
  });
});

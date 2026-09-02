import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import SearchPage from './SearchPage';
import { examApi } from '../api/exams';
import * as auth from '../auth';

/**
 * 시험 찾기 — <b>일정이 없는 시험도 목록에 나와야 한다</b>는 계약을 못 박는다.
 *
 * 예전에는 일정이 있어야 시험이 존재하는 구조라 대부분의 시험이 화면에서 사라졌다.
 * 이 테스트가 깨지면 그 회귀다.
 */
function item(over: Partial<Record<string, unknown>> = {}) {
  return {
    id: 1, name: '정보처리기사', slug: '정보처리기사',
    series: 'TECHNICIAN', seriesLabel: '기사',
    category: '국가기술자격-정보통신', agency: '한국산업인력공단',
    favorited: false, hasSchedule: true, rolling: false,
    ...over,
  };
}

describe('SearchPage', () => {
  beforeEach(() => {
    // 관심 별표가 로그인 상태를 보게 되면서 필요해졌다(비로그인이면 로그인 화면으로 보낸다).
    vi.spyOn(auth, 'useAuth').mockReturnValue({
      me: null, loading: false,
      login: vi.fn(), loginWithToken: vi.fn(), logout: vi.fn(), refresh: vi.fn(),
    });
    vi.spyOn(examApi, 'categories').mockResolvedValue({
      items: [{ name: '국가기술자격-정보통신', count: 23 }, { name: '어학-영어', count: 13 }],
    });
  });

  it('전체 목록과 개수를 보여준다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue({
      items: [item(), item({ id: 2, name: '전기기사' })],
      page: 0, size: 24, totalElements: 480, totalPages: 20,
    });

    render(<MemoryRouter><SearchPage /></MemoryRouter>);

    expect(await screen.findByText('정보처리기사')).toBeInTheDocument();
    expect(screen.getByText('전기기사')).toBeInTheDocument();
    expect(screen.getByText('480개')).toBeInTheDocument();
  });

  it('일정이 없는 시험은 "일정 미정"으로 표시된다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue({
      items: [item({ id: 3, name: '국가직 9급 공개경쟁채용', hasSchedule: false })],
      page: 0, size: 24, totalElements: 1, totalPages: 1,
    });

    render(<MemoryRouter><SearchPage /></MemoryRouter>);

    expect(await screen.findByText('국가직 9급 공개경쟁채용')).toBeInTheDocument();
    expect(screen.getByText('일정 미정')).toBeInTheDocument();
  });

  it('일정이 있는 시험에는 "일정 미정"을 붙이지 않는다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue({
      items: [item({ hasSchedule: true })],
      page: 0, size: 24, totalElements: 1, totalPages: 1,
    });

    render(<MemoryRouter><SearchPage /></MemoryRouter>);

    await screen.findByText('정보처리기사');
    expect(screen.queryByText('일정 미정')).not.toBeInTheDocument();
  });

  /** 분류 38개를 칩으로 늘어놓으면 5줄이라 드롭다운으로 바꿨다(2026-09-02). 개수는 옵션에 붙는다. */
  it('분류를 개수와 함께 드롭다운으로 보여준다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue({
      items: [item()], page: 0, size: 24, totalElements: 1, totalPages: 1,
    });

    render(<MemoryRouter><SearchPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByRole('option', { name: '어학-영어 (13)' })).toBeInTheDocument();
    });
  });

  it('결과가 없으면 빈 상태를 보여준다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue({
      items: [], page: 0, size: 24, totalElements: 0, totalPages: 0,
    });

    render(<MemoryRouter><SearchPage /></MemoryRouter>);

    expect(await screen.findByText('결과가 없습니다')).toBeInTheDocument();
  });

  /** 상세와 같은 계약 — 비로그인은 401 대신 로그인 화면으로 간다. */
  it('비로그인이 별표를 누르면 API 를 부르지 않는다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue({
      items: [item()], page: 0, size: 24, totalElements: 1, totalPages: 1,
    });
    const add = vi.spyOn(examApi, 'addFavorite');

    render(<MemoryRouter><SearchPage /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText('정보처리기사')).toBeTruthy());
    fireEvent.click(screen.getByRole('button', { name: '관심 등록' }));

    expect(add).not.toHaveBeenCalled();
  });

  /** 상시시험에 "등록해 두면 알려드립니다"는 지키지 못할 약속이다 — 다른 말을 해야 한다. */
  it('상시시험은 "일정 미정"이 아니라 "상시시험"으로 보여준다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue({
      items: [item({ name: 'CCNA', hasSchedule: false, rolling: true })],
      page: 0, size: 24, totalElements: 1, totalPages: 1,
    });

    render(<MemoryRouter><SearchPage /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText('CCNA')).toBeTruthy());

    expect(screen.getByText('상시시험')).toBeTruthy();
    expect(screen.queryByText('일정 미정')).toBeNull();
    // 카드 안의 설명만 본다 — 페이지 상단 안내문에도 '알려 드립니다'가 있다
    expect(document.querySelector('.exam-card .when')?.textContent).toContain('원하는 날짜');
  });
});

import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import SearchPage from './SearchPage';
import { examApi } from '../api/exams';

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
    favorited: false, hasSchedule: true,
    ...over,
  };
}

describe('SearchPage', () => {
  beforeEach(() => {
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

  it('분류 칩을 개수와 함께 보여준다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue({
      items: [item()], page: 0, size: 24, totalElements: 1, totalPages: 1,
    });

    render(<MemoryRouter><SearchPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('어학-영어')).toBeInTheDocument();
    });
  });

  it('결과가 없으면 빈 상태를 보여준다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue({
      items: [], page: 0, size: 24, totalElements: 0, totalPages: 0,
    });

    render(<MemoryRouter><SearchPage /></MemoryRouter>);

    expect(await screen.findByText('결과가 없습니다')).toBeInTheDocument();
  });
});

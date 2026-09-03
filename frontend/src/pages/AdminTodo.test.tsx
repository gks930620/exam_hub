import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AdminTodo from './AdminTodo';
import { examApi } from '../api/exams';

/**
 * 운영 첫 화면 — 채움률 조회가 죽으면 띠를 <b>숨기지 않고</b> 실패를 말한다.
 * 조용히 사라지면 매니저는 "원래 없는 화면인가" 하고 넘어간다.
 */
describe('AdminTodo', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(examApi, 'adminOverview').mockResolvedValue({
      items: [], totalElements: 0, page: 0, bucketCounts: {}, actionCounts: {}, waitingCounts: {},
    });
  });

  it('채움률을 못 불러오면 오류로 알린다', async () => {
    vi.spyOn(examApi, 'adminDataMap').mockRejectedValue(new Error('지도 서버 오류'));

    render(<MemoryRouter><AdminTodo /></MemoryRouter>);

    const alert = await screen.findByText(/지도 서버 오류/);
    expect(alert.closest('.k-alert--err')).toBeTruthy();
  });
});

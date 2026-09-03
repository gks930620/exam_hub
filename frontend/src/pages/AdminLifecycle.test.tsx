import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import AdminLifecyclePage from './AdminLifecyclePage';
import { examApi } from '../api/exams';

/**
 * 변천사 — "검색에 나오지 않는다"는 폐지·개칭이 <b>확정된</b> 것만 해당한다.
 * '확인 필요'(UNVERIFIED)는 판단 전이라 아직 검색에 나온다. 문구가 이를 뭉뚱그리면
 * 매니저가 멀쩡히 검색되는 시험을 빠진 줄 안다.
 */
describe('AdminLifecyclePage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(examApi, 'adminLifecycle').mockResolvedValue({
      needsCheck: 1,
      items: [
        { certificateId: 1, name: '웹디자인기능사', category: null, lifecycle: 'ABOLISHED', lifecycleLabel: '폐지', supersededBy: null, note: null },
        { certificateId: 2, name: '컴퓨터활용능력', category: null, lifecycle: 'UNVERIFIED', lifecycleLabel: '확인 필요', supersededBy: null, note: null },
      ],
    });
  });

  it('확정된 것만 검색에서 빠지고, 확인 필요는 아직 나온다고 말한다', async () => {
    render(<AdminLifecyclePage />);

    expect(screen.getByText(/확정된/)).toBeTruthy();
    expect(screen.queryByText(/여기 있는 시험은 검색에 나오지 않습니다/)).toBeNull();

    fireEvent.click(await screen.findByRole('button', { name: /폐지·개칭 2건/ }));

    expect(screen.getByText(/아직 검색에 나옵니다/)).toBeTruthy();
  });
});

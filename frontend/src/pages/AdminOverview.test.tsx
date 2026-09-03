import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AdminOverview from './AdminOverview';
import { examApi } from '../api/exams';
import type { OverviewResponse, OverviewRow } from '../api/types';

/**
 * 매니저 현황 — <b>할 일이 먼저, 넣기는 그 자리에서.</b>
 *
 * <p>"일정 있음/없음"만으로는 수기 시험의 회차가 지나 다음 회차를 넣어야 하는 상황이 안 보였고,
 * '일정 넣기' 탭이 따로 있어 흐름이 끊겼다(사용자 지적 2026-09-02). 서버가 행동을 판정해 주고,
 * 화면은 [넣기]를 누른 자리에서 모달을 연다.
 */
function row(over: Partial<OverviewRow>): OverviewRow {
  return {
    certificateId: 1, certificateName: '국가직 9급', category: '공무원', agency: '인사혁신처',
    scheduleCount: 1, source: 'MANUAL', sourceLabel: '수기', freshness: 'PAST_ONLY', needsReview: false,
    action: 'NEXT_ROUND', actionLabel: '다음 회차 입력', waitingReason: null, waitingLabel: null,
    bucket: 'TODO', lastExamDate: '2026-03-12', lastLabel: '2026년 1회 필기', daysSinceLast: 174,
    nextLabel: null, nextAt: null, nextDday: null, nextBadge: null,
    ...over,
  };
}

function res(items: OverviewRow[], over: Partial<OverviewResponse> = {}): OverviewResponse {
  return {
    items, totalElements: items.length, page: 0,
    bucketCounts: { TODO: 3, WAITING: 80, OK: 600, ROLLING: 35 },
    actionCounts: { FIRST_INPUT: 2, NEXT_ROUND: 1 },
    waitingCounts: {},
    ...over,
  };
}

describe('AdminOverview — 할 일', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('할 일 개수가 맨 위에, 행마다 해야 할 것이 붙는다', async () => {
    vi.spyOn(examApi, 'adminOverview').mockResolvedValue(res([
      row({ certificateId: 1, certificateName: '국가직 9급' }),
      row({ certificateId: 2, certificateName: '경찰간부후보생', action: 'FIRST_INPUT', actionLabel: '첫 일정 입력', freshness: 'NONE', scheduleCount: 0, lastLabel: null, lastExamDate: null, daysSinceLast: null }),
    ]));

    render(<MemoryRouter><AdminOverview /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('국가직 9급')).toBeTruthy());
    expect(document.querySelector('.todo-strip__num')?.textContent).toBe('3');
    // 위 요약 칩에도 같은 글자가 있으니 표 안에서 찾는다
    const table = within(screen.getByRole('table'));
    expect(table.getByText('다음 회차 입력')).toBeTruthy();
    expect(table.getByText('첫 일정 입력')).toBeTruthy();
    // 다음 회차를 넣으려면 "어디까지 넣었더라"가 보여야 한다
    expect(screen.getByText(/174일 지남/)).toBeTruthy();
  });

  /** 탭을 옮기지 않는다 — 그 자리에서 모달. */
  it('[넣기]를 누르면 그 시험의 일정 모달이 뜬다', async () => {
    vi.spyOn(examApi, 'adminOverview').mockResolvedValue(res([row({})]));
    vi.spyOn(examApi, 'adminSchedules').mockResolvedValue([
      { id: 9, year: 2026, round: 1, examType: 'WRITTEN', regStartAt: '2026-01-10 10:00', regEndAt: '2026-01-20 18:00', examStartDate: '2026-03-12', examEndDate: null, resultDate: null, sourceUrl: null } as never,
    ]);

    render(<MemoryRouter><AdminOverview /></MemoryRouter>);
    await waitFor(() => expect(screen.getByRole('button', { name: '넣기' })).toBeTruthy());
    fireEvent.click(screen.getByRole('button', { name: '넣기' }));

    const dialog = await screen.findByRole('dialog');
    expect(dialog.textContent).toContain('국가직 9급');
    // 이미 들어간 회차가 폼 위에 보인다
    await waitFor(() => expect(dialog.textContent).toContain('2026년 1회'));
    // 폼은 다음 회차(같은 해 2회)로 미리 채워진다
    await waitFor(() =>
      expect((within(dialog).getByLabelText('회차') as HTMLInputElement).value).toBe('2'));
  });

  /** 자동으로 들어올 시험에 [넣기]를 주면 손으로 넣었다가 덮어써지는 헛일이 생긴다. */
  it('자동으로 올 시험에는 넣기 버튼이 없다', async () => {
    vi.spyOn(examApi, 'adminOverview').mockResolvedValue(res([
      row({ source: 'AUTO', sourceLabel: '자동', action: null, actionLabel: null, bucket: 'WAITING', waitingReason: 'ANNOUNCEMENT_PENDING', waitingLabel: '공고 전 — 나오면 자동', freshness: 'NONE' }),
    ]));

    render(<MemoryRouter><AdminOverview /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText('국가직 9급')).toBeTruthy());

    expect(screen.queryByRole('button', { name: '넣기' })).toBeNull();
    expect(screen.getByText('공고 전 — 나오면 자동')).toBeTruthy();
  });

  /**
   * 수집된 일정이 30일 넘게 움직여 보류(PENDING_REVIEW)된 회차 — 자동 소스지만 사람이 공고와
   * 대조해 저장해야 풀린다. 그래서 [넣기]가 있다.
   */
  it('일정 이동 확인은 자동 소스여도 넣기 버튼이 있다', async () => {
    vi.spyOn(examApi, 'adminOverview').mockResolvedValue(res([
      row({ source: 'AUTO', sourceLabel: '자동', action: 'REVIEW_MOVE', actionLabel: '일정 이동 확인', freshness: 'UPCOMING' }),
    ], { actionCounts: { REVIEW_MOVE: 1 } }));

    render(<MemoryRouter><AdminOverview /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText('국가직 9급')).toBeTruthy());

    const table = within(screen.getByRole('table'));
    expect(table.getByText('일정 이동 확인').classList.contains('k-badge--warn')).toBe(true);
    expect(screen.getByRole('button', { name: '넣기' })).toBeTruthy();
    // 요약 칩에도 있고, 0건인 칩은 흐리다
    const chips = Array.from(document.querySelectorAll('.todo-strip__parts .k-chip'));
    const move = chips.find((c) => c.textContent?.includes('일정 이동 확인'));
    const first = chips.find((c) => c.textContent?.includes('첫 일정 입력'));
    expect(move?.classList.contains('k-dim')).toBe(false);
    expect(first?.classList.contains('k-dim')).toBe(true);
  });

  /** 한 글자마다 서버를 부르지 않는다 — 300ms 멈추면 그때 한 번. */
  it('검색은 디바운스된다', async () => {
    const overview = vi.spyOn(examApi, 'adminOverview').mockResolvedValue(res([row({})]));
    render(<MemoryRouter><AdminOverview /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText('국가직 9급')).toBeTruthy());

    fireEvent.change(screen.getByPlaceholderText('시험명으로 좁히기'), { target: { value: '국' } });
    fireEvent.change(screen.getByPlaceholderText('시험명으로 좁히기'), { target: { value: '국가' } });
    expect(overview).not.toHaveBeenCalledWith(expect.objectContaining({ query: '국' }));
    expect(overview).not.toHaveBeenCalledWith(expect.objectContaining({ query: '국가' }));

    await waitFor(() => expect(overview).toHaveBeenCalledWith(expect.objectContaining({ query: '국가', page: 0 })));
    expect(overview).not.toHaveBeenCalledWith(expect.objectContaining({ query: '국' }));
  });

  /** 다시 불러오는 동안 표가 사라지지 않고 흐려진다 — 자리가 흔들리면 어디를 보고 있었는지 잃는다. */
  it('다시 불러오는 동안 표는 남고 aria-busy 가 켜진다', async () => {
    let resolveSecond!: (v: OverviewResponse) => void;
    const second = new Promise<OverviewResponse>((res2) => { resolveSecond = res2; });
    vi.spyOn(examApi, 'adminOverview')
      .mockResolvedValueOnce(res([row({})]))
      .mockImplementationOnce(() => second);

    render(<MemoryRouter><AdminOverview /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText('국가직 9급')).toBeTruthy());

    fireEvent.click(screen.getByRole('tab', { name: /정상/ }));

    await waitFor(() => expect(document.querySelector('[aria-busy="true"]')).toBeTruthy());
    expect(screen.getByText('국가직 9급')).toBeTruthy();

    resolveSecond(res([row({ certificateName: '전기기사', bucket: 'OK', action: null, actionLabel: null, freshness: 'UPCOMING', nextLabel: '1회 필기 접수 시작', nextAt: '2026-10-01T10:00', nextDday: 28, nextBadge: 'REG_UPCOMING' })]));
    await waitFor(() => expect(screen.getByText('전기기사')).toBeTruthy());
    expect(document.querySelector('[aria-busy="true"]')).toBeNull();
  });
});

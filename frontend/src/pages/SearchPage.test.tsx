import { describe, expect, it, vi, beforeEach } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import SearchPage from './SearchPage';
import { examApi } from '../api/exams';
import * as auth from '../auth';
import type { BrowseResponse } from '../api/types';

/**
 * 시험 찾기 — <b>일정이 없는 시험도 목록에 나와야 한다</b>는 계약을 못 박는다.
 *
 * 예전에는 일정이 있어야 시험이 존재하는 구조라 대부분의 시험이 화면에서 사라졌다.
 * 이 테스트가 깨지면 그 회귀다.
 *
 * <p>검색어·분류·쪽은 URL 에 산다 — 상세를 보고 돌아와도, 링크를 공유해도 같은 화면이어야 한다.
 */
function item(over: Partial<Record<string, unknown>> = {}) {
  return {
    id: 1, name: '정보처리기사', slug: '정보처리기사',
    series: 'TECHNICIAN', seriesLabel: '기사',
    category: '국가기술자격-정보통신', agency: '한국산업인력공단',
    favorited: false, hasSchedule: true,
    // 카드 상태는 서버가 준다. 픽스처는 null 로 두어 hasSchedule/rolling 로 유도되게 한다
    scheduleState: null, nextLabel: null, nextAt: null, nextDday: null, nextBadge: null, lastExamDate: null, rolling: false,
    ...over,
  };
}

function page(items: ReturnType<typeof item>[], over: Partial<BrowseResponse> = {}): BrowseResponse {
  return { items, page: 0, size: 48, totalElements: items.length, totalPages: 1, ...over };
}

/** 주소창을 들여다보는 표식 — 검색어·쪽이 URL 에 실제로 쓰였는지 본다. */
function LocationProbe() {
  const l = useLocation();
  return <div data-testid="loc">{l.search}</div>;
}

function paramsOf(): URLSearchParams {
  return new URLSearchParams(screen.getByTestId('loc').textContent ?? '');
}

function renderAt(path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <SearchPage />
      <LocationProbe />
    </MemoryRouter>
  );
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
    vi.spyOn(examApi, 'browse').mockResolvedValue(page(
      [item(), item({ id: 2, name: '전기기사' })], { totalElements: 480, totalPages: 10 },
    ));

    renderAt();

    expect(await screen.findByText('정보처리기사')).toBeInTheDocument();
    expect(screen.getByText('전기기사')).toBeInTheDocument();
    expect(screen.getByText('480개')).toBeInTheDocument();
    // 한 쪽 48개 — 24개씩 35번 "더 보기"를 누르던 것을 번호 페이징으로 바꿨다
    expect(examApi.browse).toHaveBeenCalledWith({ query: undefined, category: undefined, page: 0, size: 48 });
  });

  it('일정이 없는 시험은 "일정 미정"으로 표시된다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue(page([item({ id: 3, name: '국가직 9급 공개경쟁채용', hasSchedule: false })]));

    renderAt();

    expect(await screen.findByText('국가직 9급 공개경쟁채용')).toBeInTheDocument();
    expect(screen.getByText('일정 미정')).toBeInTheDocument();
  });

  it('일정이 있는 시험에는 "일정 미정"을 붙이지 않는다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue(page([item({ hasSchedule: true })]));

    renderAt();

    await screen.findByText('정보처리기사');
    expect(screen.queryByText('일정 미정')).not.toBeInTheDocument();
  });

  /** 분류 38개를 칩으로 늘어놓으면 5줄이라 드롭다운으로 바꿨다(2026-09-02). 개수는 옵션에 붙는다. */
  it('분류를 개수와 함께 드롭다운으로 보여준다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue(page([item()]));

    renderAt();

    await waitFor(() => {
      expect(screen.getByRole('option', { name: '어학-영어 (13)' })).toBeInTheDocument();
    });
  });

  /**
   * 0건 문구는 3분기다(설계 05 §8). 한 문구로 뭉치면 <b>사용자가 고칠 수 있는 게 있는지</b>를 알 수 없다 —
   * 조건 때문에 0건인 것과 목록이 통째로 빈 것은 할 일이 정반대다.
   */
  describe('0건', () => {
    beforeEach(() => {
      vi.spyOn(examApi, 'browse').mockResolvedValue(page([], { totalPages: 0 }));
    });

    it('검색어가 있으면 그 말을 되읽어 주고 지울 길을 준다', async () => {
      renderAt('/?q=없는시험');

      expect(await screen.findByText('‘없는시험’ 검색 결과가 없어요')).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: '검색어 지우기' }));
      await waitFor(() => expect(paramsOf().get('q')).toBeNull());
    });

    it('분류만 걸렸으면 그 분류를 말하고 초기화 버튼을 준다', async () => {
      renderAt('/?cat=어학-영어');

      expect(await screen.findByText('조건에 맞는 시험이 없어요')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: '검색어 지우기' })).not.toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: '분류 초기화' }));
      await waitFor(() => expect(paramsOf().get('cat')).toBeNull());
    });

    /** 사용자가 고칠 수 있는 게 없는 상태다 — "초기화"를 권하면 거짓 안내가 된다. */
    it('조건이 하나도 없는데 0건이면 초기화를 권하지 않는다', async () => {
      renderAt();

      expect(await screen.findByText('아직 준비 중이에요')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /초기화|지우기/ })).not.toBeInTheDocument();
    });
  });

  /** 상세와 같은 계약 — 비로그인은 401 대신 로그인 화면으로 간다. */
  it('비로그인이 별표를 누르면 API 를 부르지 않는다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue(page([item()]));
    const add = vi.spyOn(examApi, 'addFavorite');

    renderAt();
    await waitFor(() => expect(screen.getByText('정보처리기사')).toBeTruthy());
    fireEvent.click(screen.getByRole('button', { name: '관심 등록' }));

    expect(add).not.toHaveBeenCalled();
  });

  /** 상시시험에 "등록해 두면 알려드립니다"는 지키지 못할 약속이다 — 다른 말을 해야 한다. */
  it('상시시험은 "일정 미정"이 아니라 "상시시험"으로 보여준다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue(page([item({ name: 'CCNA', hasSchedule: false, rolling: true })]));

    renderAt();
    await waitFor(() => expect(screen.getByText('CCNA')).toBeTruthy());

    expect(screen.getByText('상시시험')).toBeTruthy();
    expect(screen.queryByText('일정 미정')).toBeNull();
  });

  /**
   * 목록은 <b>어떤 시험인지 고르는</b> 화면이다 — 회차·구분·시각은 상세의 몫이다(2026-09-10 사용자 판단).
   *
   * <p>설명 줄을 같이 실었을 때 카드 523장 중 86%가 두 줄로 접혔고, 배지를 어디에 맞춰도
   * 그리드가 들쭉날쭉했다. 줄이 맞는 목록은 정렬을 고쳐서가 아니라 <b>덜어내서</b> 나왔다.
   */
  it('목록 카드는 배지 한 줄만 남기고 회차·시각은 싣지 않는다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue(page([item({
      scheduleState: 'UPCOMING', nextBadge: 'REG_UPCOMING', nextLabel: '3회 실기 접수 시작',
      nextAt: '2026-09-21T10:00', nextDday: 11,
    })]));

    renderAt();
    await waitFor(() => expect(screen.getByText('정보처리기사')).toBeTruthy());

    expect(document.querySelector('.exam-card .k-badge')?.textContent).toBe('접수 예정 · D-11');
    expect(document.querySelector('.exam-card .when')).toBeNull();
    expect(screen.queryByText(/3회 실기/)).toBeNull();
    expect(screen.queryByText(/10:00/)).toBeNull();
  });

  // ── URL 동기화 ──────────────────────────────────────────────

  it('URL 의 q·cat·page 로 검색한다 — 공유한 링크, 상세에서 돌아온 뒤에도 같은 화면', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue(page([item({ name: '토익' })], { totalElements: 13, totalPages: 1 }));

    renderAt('/?q=토익&cat=어학-영어&page=3');

    await waitFor(() => expect(examApi.browse).toHaveBeenCalledWith({ query: '토익', category: '어학-영어', page: 2, size: 48 }));
    expect((screen.getByLabelText('시험명 검색') as HTMLInputElement).value).toBe('토익');
    await waitFor(() => expect((screen.getByLabelText('분류') as HTMLSelectElement).value).toBe('어학-영어'));
  });

  it('검색어를 치면 URL 에 q 가 붙고 첫 쪽부터 다시 찾는다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue(page([item()], { totalPages: 5 }));

    renderAt('/?page=2');
    await waitFor(() => expect(examApi.browse).toHaveBeenCalledWith(expect.objectContaining({ page: 1 })));

    fireEvent.change(screen.getByLabelText('시험명 검색'), { target: { value: '토익' } });

    await waitFor(() => expect(paramsOf().get('q')).toBe('토익'));
    expect(paramsOf().get('page')).toBeNull();
    await waitFor(() => expect(examApi.browse).toHaveBeenCalledWith({ query: '토익', category: undefined, page: 0, size: 48 }));
  });

  it('쪽을 바꾸면 URL 에 page 가 붙고 그 쪽을 찾는다 — "더 보기"는 없다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue(page([item()], { totalElements: 845, totalPages: 18 }));

    renderAt();
    await waitFor(() => expect(document.querySelector('.pagination')).toBeTruthy());
    expect(screen.queryByText(/더 보기/)).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '2' }));

    await waitFor(() => expect(paramsOf().get('page')).toBe('2'));
    await waitFor(() => expect(examApi.browse).toHaveBeenCalledWith(expect.objectContaining({ page: 1 })));
  });

  /** 요청은 순서대로 돌아오지 않는다 — 먼저 보낸 느린 응답이 나중에 와서 새 결과를 덮으면 안 된다. */
  it('늦게 온 이전 응답은 버린다', async () => {
    let resolveFirst!: (v: BrowseResponse) => void;
    const first = new Promise<BrowseResponse>((res) => { resolveFirst = res; });
    const browse = vi.spyOn(examApi, 'browse')
      .mockImplementationOnce(() => first)
      .mockResolvedValue(page([item({ id: 2, name: '전기기사' })]));

    renderAt();
    await waitFor(() => expect(browse).toHaveBeenCalledTimes(1));
    await screen.findByRole('option', { name: '어학-영어 (13)' });

    fireEvent.change(screen.getByLabelText('분류'), { target: { value: '어학-영어' } });
    expect(await screen.findByText('전기기사')).toBeTruthy();

    await act(async () => {
      resolveFirst(page([item({ id: 1, name: '정보처리기사' })]));
      await Promise.resolve();
    });

    expect(screen.queryByText('정보처리기사')).toBeNull();
    expect(screen.getByText('전기기사')).toBeTruthy();
  });

  // ── 히어로 제목 ─────────────────────────────────────────────

  /** "시험 800종" 같은 지어낸 숫자를 쓰지 않는다 — 전체 개수는 목록 API 가 준다. */
  it('히어로 제목의 숫자는 전체 시험 수다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue(page([item()], { totalElements: 845, totalPages: 18 }));

    renderAt();

    await waitFor(() => expect(screen.getByRole('heading', { level: 1 }).textContent).toContain('845종'));
  });

  it('분류로 좁힌 채 들어오면 전체 수를 모르므로 숫자 없이 말한다', async () => {
    vi.spyOn(examApi, 'browse').mockResolvedValue(page([item()], { totalElements: 13, totalPages: 1 }));

    renderAt('/?cat=어학-영어');

    await screen.findByText('정보처리기사');
    const h1 = screen.getByRole('heading', { level: 1 }).textContent ?? '';
    expect(h1).not.toContain('13종');
    expect(h1).not.toContain('800');
    expect(h1).toContain('시험 일정, 접수 마감을 놓치지 않게');
  });

  /** 분류 조회가 죽어도 목록은 나온다 — 대신 왜 드롭다운이 비었는지는 말해 준다. */
  it('분류를 못 불러오면 드롭다운 옆에 알린다', async () => {
    vi.spyOn(examApi, 'categories').mockRejectedValue(new Error('500'));
    vi.spyOn(examApi, 'browse').mockResolvedValue(page([item()]));

    renderAt();

    expect(await screen.findByText(/분류를 불러오지 못했습니다/)).toBeTruthy();
    expect(screen.getByText('정보처리기사')).toBeTruthy();
  });
});

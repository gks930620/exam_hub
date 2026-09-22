import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import CommunityPage from './CommunityPage';
import * as auth from '../auth';
import { examApi } from '../api/exams';
import type { PostListResponse, PostSummary } from '../api/types';

/**
 * 커뮤니티 목록 — "더 보기"가 조용히 실패하지 않고, 시각은 사람이 읽는 표기로.
 */
function post(over: Partial<PostSummary> = {}): PostSummary {
  return {
    id: 1, boardCode: 'FREE', boardName: '자유', title: '첫 글', authorName: '홍길동', authorId: 1,
    viewCount: 3, commentCount: 0, createdAt: '2026-09-01T12:34:56', ...over,
  };
}

function list(items: PostSummary[], over: Partial<PostListResponse> = {}): PostListResponse {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1, ...over };
}

/** 검색어가 주소에 실제로 쓰였는지 보려고 둔다 — 링크를 공유해도 같은 화면이어야 한다. */
function LocationProbe() {
  const l = useLocation();
  return <div data-testid="loc">{l.search}</div>;
}

function paramsOf(): URLSearchParams {
  return new URLSearchParams(screen.getByTestId('loc').textContent ?? '');
}

function renderAt(path = '/community') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <CommunityPage />
      <LocationProbe />
    </MemoryRouter>,
  );
}

describe('CommunityPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(auth, 'useAuth').mockReturnValue({
      me: null, loading: false, login: vi.fn(), loginWithToken: vi.fn(), logout: vi.fn(), refresh: vi.fn(),
    });
    vi.spyOn(examApi, 'boards').mockResolvedValue({ items: [] });
  });

  it('작성 시각은 ISO 원문이 아니라 읽는 표기다', async () => {
    vi.spyOn(examApi, 'posts').mockResolvedValue(list([post()]));

    render(<MemoryRouter><CommunityPage /></MemoryRouter>);

    expect(await screen.findByText(/2026-09-01 12:34/)).toBeTruthy();
    expect(document.body.textContent).not.toContain('T12:34');
  });

  it('더 보기가 실패하면 알리고, 불러오는 동안은 버튼이 잠긴다', async () => {
    let reject!: (e: Error) => void;
    const pending = new Promise<PostListResponse>((_, rej) => { reject = rej; });
    vi.spyOn(examApi, 'posts')
      .mockResolvedValueOnce(list([post()], { totalPages: 2 }))
      .mockImplementationOnce(() => pending);

    render(<MemoryRouter><CommunityPage /></MemoryRouter>);
    const more = await screen.findByRole('button', { name: /더 보기/ });
    fireEvent.click(more);

    await waitFor(() => expect((more as HTMLButtonElement).disabled).toBe(true));
    reject(new Error('네트워크 오류'));

    expect(await screen.findByText('네트워크 오류')).toBeTruthy();
    await waitFor(() => expect((screen.getByRole('button', { name: /더 보기/ }) as HTMLButtonElement).disabled).toBe(false));
  });

  /**
   * 2026-09-23 QA 지적: 검색창이 들어간 커밋에 프런트 테스트가 없었다.
   * 글이 쌓이면 "그때 그 글"을 다시 못 찾는다 — 더 보기를 스무 번 누르는 것 말고 길이 없었다.
   */
  it('검색어를 치면 주소에 남고 그걸로 다시 불러온다', async () => {
    vi.spyOn(examApi, 'posts').mockResolvedValue(list([post()]));

    renderAt();
    const box = await screen.findByPlaceholderText(/글 내용으로 찾기/);

    fireEvent.change(box, { target: { value: '수제비' } });

    await waitFor(() => expect(paramsOf().get('q')).toBe('수제비'), { timeout: 2000 });
    await waitFor(() => expect(examApi.posts).toHaveBeenLastCalledWith(
      expect.objectContaining({ query: '수제비' })));
  });

  it('주소에 검색어가 있으면 그 상태로 시작한다', async () => {
    vi.spyOn(examApi, 'posts').mockResolvedValue(list([post()]));

    renderAt('/community?q=수제비');

    expect(await screen.findByDisplayValue('수제비')).toBeTruthy();
    expect(await screen.findByRole('heading', { name: /검색 결과/ })).toBeTruthy();
  });
});

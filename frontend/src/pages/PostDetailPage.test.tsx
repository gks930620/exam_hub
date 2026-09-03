import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import PostDetailPage from './PostDetailPage';
import * as auth from '../auth';
import { examApi } from '../api/exams';
import type { CommentItem, PostDetail } from '../api/types';

/**
 * 글 상세 — 댓글의 <b>로딩·실패·없음</b>은 다른 상태다.
 *
 * <p>예전엔 댓글 목록을 [] 로 시작해서, 아직 안 왔거나 조회가 죽어도 "첫 댓글을 남겨 보세요"가 떴다.
 */
const POST: PostDetail = {
  id: 7, boardCode: 'FREE', boardName: '자유', title: '제목', content: '본문', authorName: '홍길동',
  authorImage: null, authorId: 1, viewCount: 1, commentCount: 1,
  createdAt: '2026-09-01T12:34:56', updatedAt: '2026-09-01T12:34:56', mine: false,
};

const COMMENT: CommentItem = {
  id: 1, content: '댓글', authorName: '김철수', authorImage: null, authorId: 2,
  createdAt: '2026-09-02T08:00:00', mine: false,
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/community/posts/7']}>
      <Routes><Route path="/community/posts/:id" element={<PostDetailPage />} /></Routes>
    </MemoryRouter>
  );
}

describe('PostDetailPage — 댓글 상태', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(auth, 'useAuth').mockReturnValue({
      me: null, loading: false, login: vi.fn(), loginWithToken: vi.fn(), logout: vi.fn(), refresh: vi.fn(),
    });
    vi.spyOn(examApi, 'post').mockResolvedValue(POST);
  });

  it('댓글이 아직 안 왔으면 "첫 댓글"이 아니라 불러오는 중이다', async () => {
    vi.spyOn(examApi, 'comments').mockImplementation(() => new Promise(() => { /* 영원히 대기 */ }));
    renderPage();

    await screen.findByText('본문');
    expect(screen.queryByText(/첫 댓글을 남겨 보세요/)).toBeNull();
    expect(screen.getByText(/댓글을 불러오는 중/)).toBeTruthy();
  });

  it('댓글 조회가 실패하면 실패라고 말한다', async () => {
    vi.spyOn(examApi, 'comments').mockRejectedValue(new Error('댓글 서버 오류'));
    renderPage();

    expect(await screen.findByText(/댓글 서버 오류/)).toBeTruthy();
    expect(screen.queryByText(/첫 댓글을 남겨 보세요/)).toBeNull();
  });

  it('댓글이 없을 때만 "첫 댓글"을 권한다', async () => {
    vi.spyOn(examApi, 'comments').mockResolvedValue({ items: [] });
    renderPage();

    expect(await screen.findByText(/첫 댓글을 남겨 보세요/)).toBeTruthy();
  });

  it('글과 댓글의 시각은 읽는 표기다', async () => {
    vi.spyOn(examApi, 'comments').mockResolvedValue({ items: [COMMENT] });
    renderPage();

    expect(await screen.findByText(/2026-09-01 12:34/)).toBeTruthy();
    expect(screen.getByText('2026-09-02 08:00')).toBeTruthy();
    expect(document.body.textContent).not.toContain('T12:34');
  });
});

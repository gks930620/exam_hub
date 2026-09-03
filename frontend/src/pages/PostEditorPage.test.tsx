import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import PostEditorPage from './PostEditorPage';
import { examApi } from '../api/exams';
import type { PostDetail } from '../api/types';

/**
 * 글 쓰기·수정 — 남의 글은 폼을 보여주지 않고, 쓰던 내용은 묻지 않고 버리지 않는다.
 */
const POST: PostDetail = {
  id: 7, boardCode: 'FREE', boardName: '자유', title: '남의 글', content: '본문', authorName: '홍길동',
  authorImage: null, authorId: 1, viewCount: 1, commentCount: 0,
  createdAt: '2026-09-01T12:34:56', updatedAt: '2026-09-01T12:34:56', mine: false,
};

function renderEdit() {
  return render(
    <MemoryRouter initialEntries={['/community/posts/7/edit']}>
      <Routes><Route path="/community/posts/:id/edit" element={<PostEditorPage />} /></Routes>
    </MemoryRouter>
  );
}

function renderWrite() {
  return render(
    <MemoryRouter initialEntries={['/community', '/community/write']} initialIndex={1}>
      <Routes>
        <Route path="/community" element={<div>커뮤니티 목록</div>} />
        <Route path="/community/write" element={<PostEditorPage />} />
      </Routes>
    </MemoryRouter>
  );
}

describe('PostEditorPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(examApi, 'boards').mockResolvedValue({ items: [{ code: 'FREE', name: '자유', description: '' }] });
  });

  it('남의 글이면 폼 대신 돌아갈 길을 준다', async () => {
    vi.spyOn(examApi, 'post').mockResolvedValue(POST);
    renderEdit();

    const back = await screen.findByRole('link', { name: /글로 돌아가기/ });
    expect(back.getAttribute('href')).toBe('/community/posts/7');
    expect(screen.queryByLabelText('제목')).toBeNull();
  });

  it('입력칸에는 이름(label)이 붙어 있다', async () => {
    renderWrite();

    expect(await screen.findByLabelText('제목')).toBeTruthy();
    expect(screen.getByLabelText('내용')).toBeTruthy();
  });

  it('쓰던 내용이 있으면 취소할 때 묻고, 아니라면 남는다', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    renderWrite();

    fireEvent.change(await screen.findByLabelText('제목'), { target: { value: '쓰다 만 글' } });
    fireEvent.click(screen.getByRole('button', { name: '취소' }));

    expect(confirm).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText('제목')).toBeTruthy();
    expect(screen.queryByText('커뮤니티 목록')).toBeNull();
  });

  it('비어 있으면 묻지 않고 나간다', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    renderWrite();

    fireEvent.click(await screen.findByRole('button', { name: '취소' }));

    expect(confirm).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.getByText('커뮤니티 목록')).toBeTruthy());
  });
});

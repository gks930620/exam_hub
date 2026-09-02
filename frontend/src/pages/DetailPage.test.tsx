import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import DetailPage from './DetailPage';
import * as auth from '../auth';
import { examApi } from '../api/exams';
import type { DetailResponse, MeResponse } from '../api/types';

/**
 * 시험 상세 — <b>비로그인이 관심 등록을 누르면 로그인으로 보낸다.</b>
 *
 * <p>예전에는 그냥 API 를 불러 401 을 받고 "요청 실패 (401)" 이라는 빨간 줄만 떴다.
 * 사용자는 무엇이 잘못됐는지도, 어디로 가야 하는지도 알 수 없었다 — 기능이 없는 것과 같았다.
 */
function detail(over: Partial<DetailResponse> = {}): DetailResponse {
  return {
    id: 2, name: '전기기사', category: '국가기술자격-전기전자', agency: '한국산업인력공단',
    sourceUrl: null, collectedAt: null, favorited: false, rolling: false,
    nextEvent: null, schedules: [],
    ...over,
  };
}

function mockAuth(me: MeResponse | null) {
  vi.spyOn(auth, 'useAuth').mockReturnValue({
    me, loading: false,
    login: vi.fn(), loginWithToken: vi.fn(), logout: vi.fn(), refresh: vi.fn(),
  });
}

const MEMBER: MeResponse = {
  id: 1, nickname: '테스터', email: null, profileImage: null,
  phoneNumber: null, provider: 'KAKAO', role: 'USER',
};

/** 로그인 화면으로 갔는지 보려고 그 자리에 표식을 둔다. */
function renderDetail() {
  return render(
    <MemoryRouter initialEntries={['/cert/2']}>
      <Routes>
        <Route path="/cert/:id" element={<DetailPage />} />
        <Route path="/login" element={<div>로그인 화면</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('DetailPage — 관심 등록', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(examApi, 'detail').mockResolvedValue(detail());
  });

  it('비로그인이면 버튼이 로그인하라고 말한다', async () => {
    mockAuth(null);
    renderDetail();

    await waitFor(() => expect(screen.getByText('전기기사')).toBeTruthy());
    expect(screen.getByRole('button', { name: /로그인/ })).toBeTruthy();
  });

  /** 401 을 받아 빨간 줄을 띄우는 대신, 갈 곳으로 보낸다. */
  it('비로그인이 누르면 API 를 부르지 않고 로그인으로 간다', async () => {
    mockAuth(null);
    const add = vi.spyOn(examApi, 'addFavorite');
    renderDetail();

    await waitFor(() => expect(screen.getByText('전기기사')).toBeTruthy());
    fireEvent.click(screen.getByRole('button', { name: /로그인/ }));

    await waitFor(() => expect(screen.getByText('로그인 화면')).toBeTruthy());
    expect(add).not.toHaveBeenCalled();
  });

  it('로그인 상태면 관심 등록을 부른다', async () => {
    mockAuth(MEMBER);
    const add = vi.spyOn(examApi, 'addFavorite').mockResolvedValue({ certificateId: 2 } as never);
    renderDetail();

    await waitFor(() => expect(screen.getByText('전기기사')).toBeTruthy());
    fireEvent.click(screen.getByRole('button', { name: /관심 등록/ }));

    await waitFor(() => expect(add).toHaveBeenCalledWith(2));
    await waitFor(() => expect(screen.getByRole('button', { name: /등록됨/ })).toBeTruthy());
  });

  it('이미 등록했으면 해제할 수 있다', async () => {
    mockAuth(MEMBER);
    vi.spyOn(examApi, 'detail').mockResolvedValue(detail({ favorited: true }));
    const remove = vi.spyOn(examApi, 'removeFavorite').mockResolvedValue(undefined as never);
    renderDetail();

    await waitFor(() => expect(screen.getByRole('button', { name: /등록됨/ })).toBeTruthy());
    fireEvent.click(screen.getByRole('button', { name: /등록됨/ }));

    await waitFor(() => expect(remove).toHaveBeenCalledWith(2));
  });
});

import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import LoginPage from './LoginPage';
import * as auth from '../auth';
import { api } from '../api/client';

/**
 * 로그인 화면은 <b>실제로 쓸 수 있는 제공자만</b> 그린다.
 *
 * 예전엔 카카오·구글 버튼을 무조건 그렸다. 그런데 키를 안 넣은 제공자는 서버에 등록 자체가
 * 없어서, 누르면 흰 화면 500(<code>Invalid Client Registration</code>)이 떴다.
 * 키를 하나만 넣은 상태는 이 서비스의 정상 상태라(구글은 Client Secret 대기 중)
 * 반드시 겪는 일이었다.
 */
function mockAuth() {
  vi.spyOn(auth, 'useAuth').mockReturnValue({
    me: null, loading: false,
    login: vi.fn(), loginWithToken: vi.fn(), logout: vi.fn(), refresh: vi.fn(),
  });
}

function mockProviders(providers: string[]) {
  return vi.spyOn(api, 'get').mockResolvedValue({ providers });
}

describe('LoginPage — 쓸 수 있는 제공자만 보여준다', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockAuth();
  });

  it('둘 다 설정돼 있으면 둘 다 그린다', async () => {
    mockProviders(['kakao', 'google']);
    render(<MemoryRouter><LoginPage /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText(/카카오로 시작하기/)).toBeTruthy());
    expect(screen.getByText(/구글로 시작하기/)).toBeTruthy();
  });

  /** 지금 로컬이 이 상태다 — 구글 버튼이 보이면 안 된다. */
  it('구글이 설정 안 됐으면 구글 버튼을 안 그린다', async () => {
    mockProviders(['kakao']);
    render(<MemoryRouter><LoginPage /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText(/카카오로 시작하기/)).toBeTruthy());
    expect(screen.queryByText(/구글로 시작하기/)).toBeNull();
  });

  /**
   * 하나도 없으면 빈 카드만 남아 사용자가 왜 못 들어가는지 모른다 — 이유를 말해준다.
   */
  it('하나도 없으면 버튼 대신 안내를 보여준다', async () => {
    mockProviders([]);
    render(<MemoryRouter><LoginPage /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText(/로그인을 쓸 수 없습니다/)).toBeTruthy());
    expect(screen.queryByText(/카카오로 시작하기/)).toBeNull();
    expect(screen.queryByText(/구글로 시작하기/)).toBeNull();
  });

  /**
   * 조회가 실패했다고 로그인 화면을 못 쓰게 만들면 안 된다 — 그때는 원래대로 다 그린다.
   * 눌러서 실패하는 편이, 멀쩡한 카카오까지 막히는 것보다 낫다.
   */
  it('조회가 실패하면 일단 다 그린다', async () => {
    vi.spyOn(api, 'get').mockRejectedValue(new Error('network'));
    render(<MemoryRouter><LoginPage /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText(/카카오로 시작하기/)).toBeTruthy());
    expect(screen.getByText(/구글로 시작하기/)).toBeTruthy();
  });

  /**
   * 토큰이 만료돼 로그인으로 튕겼다가 다른 탭에서 로그인을 마친 경우처럼, 이미 로그인된 채
   * 돌아갈 곳(from)을 들고 오면 홈이 아니라 <b>그곳</b>으로 보낸다.
   */
  it('이미 로그인돼 있고 돌아갈 곳이 있으면 그리로 보낸다', async () => {
    vi.spyOn(auth, 'useAuth').mockReturnValue({
      me: { id: 1, nickname: '테스터', email: null, profileImage: null, phoneNumber: null, provider: 'KAKAO', role: 'USER' },
      loading: false, login: vi.fn(), loginWithToken: vi.fn(), logout: vi.fn(), refresh: vi.fn(),
    });
    mockProviders(['kakao']);

    render(
      <MemoryRouter initialEntries={[{ pathname: '/login', state: { from: '/settings?tab=1' } }]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/settings" element={<div>알림 설정 화면</div>} />
          <Route path="/" element={<div>홈 화면</div>} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText('알림 설정 화면')).toBeTruthy();
  });
});

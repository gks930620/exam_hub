import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import MePage from './MePage';
import * as auth from '../auth';

/** 내 정보 — 입력칸마다 이름(label)이 붙어 있어야 스크린리더와 자동완성이 칸을 안다. */
describe('MePage — 입력칸 이름', () => {
  it('이메일·휴대폰·닉네임 칸을 이름으로 찾을 수 있다', () => {
    vi.spyOn(auth, 'useAuth').mockReturnValue({
      me: { id: 1, nickname: '테스터', email: 'a@b.com', profileImage: null, phoneNumber: null, provider: 'KAKAO', role: 'USER' },
      loading: false, login: vi.fn(), loginWithToken: vi.fn(), logout: vi.fn(), refresh: vi.fn(),
    });
    render(<MemoryRouter><MePage /></MemoryRouter>);

    expect(screen.getByLabelText(/이메일/)).toBeTruthy();
    expect(screen.getByLabelText(/휴대폰번호/)).toBeTruthy();
    expect(screen.getByLabelText(/닉네임/)).toBeTruthy();
  });
});

/**
 * 로그인 수단을 <b>사실대로</b> 적는다.
 *
 * <p>QA 에서 잡혔다(2026-09-03): 매니저 계정(provider=LOCAL)으로 내 정보를 열면
 * "구글 계정으로 로그인했습니다"가 떴다. 카카오가 아니면 전부 구글로 적었기 때문이다.
 */
describe('MePage — 로그인 수단 표기', () => {
  const meWith = (provider: 'KAKAO' | 'GOOGLE' | 'LOCAL') => {
    vi.spyOn(auth, 'useAuth').mockReturnValue({
      me: { id: 1, nickname: '테스터', email: 'a@b.com', profileImage: null, phoneNumber: null, provider, role: 'USER' },
      loading: false, login: vi.fn(), loginWithToken: vi.fn(), logout: vi.fn(), refresh: vi.fn(),
    });
    render(<MemoryRouter><MePage /></MemoryRouter>);
  };

  it('카카오 계정', () => { meWith('KAKAO'); expect(screen.getByText(/카카오 계정으로 로그인/)).toBeTruthy(); });

  it('구글 계정', () => { meWith('GOOGLE'); expect(screen.getByText(/구글 계정으로 로그인/)).toBeTruthy(); });

  it('매니저(LOCAL)는 구글이라고 하지 않는다', () => {
    meWith('LOCAL');
    expect(screen.queryByText(/구글 계정으로 로그인/)).toBeNull();
    expect(screen.getByText(/매니저 계정/)).toBeTruthy();
  });
});

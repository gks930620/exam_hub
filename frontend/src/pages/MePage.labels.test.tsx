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

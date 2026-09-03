import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ManagerLoginPage from './ManagerLoginPage';
import * as auth from '../auth';
import { examApi } from '../api/exams';
import { ApiError } from '../api/client';

/**
 * 매니저 로그인 — 서버가 잠근 이유(429)를 그대로 보여주고, 입력칸에는 이름이 붙어 있다.
 */
describe('ManagerLoginPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(auth, 'useAuth').mockReturnValue({
      me: null, loading: false, login: vi.fn(), loginWithToken: vi.fn(), logout: vi.fn(), refresh: vi.fn(),
    });
    vi.spyOn(examApi, 'managerAvailable').mockResolvedValue({ configured: true });
  });

  it('아이디·비밀번호 칸에 이름이 붙어 있다', () => {
    render(<MemoryRouter><ManagerLoginPage /></MemoryRouter>);

    expect(screen.getByLabelText('아이디')).toBeTruthy();
    expect(screen.getByLabelText('비밀번호')).toBeTruthy();
  });

  it('5회 실패로 잠기면(429) 서버 문장을 그대로 보여준다', async () => {
    vi.spyOn(examApi, 'managerLogin')
      .mockRejectedValue(new ApiError(429, '로그인 시도가 너무 많습니다. 15분 뒤 다시 시도하세요.'));
    render(<MemoryRouter><ManagerLoginPage /></MemoryRouter>);

    fireEvent.change(screen.getByLabelText('아이디'), { target: { value: 'manager' } });
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'wrong' } });
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));

    expect(await screen.findByText('로그인 시도가 너무 많습니다. 15분 뒤 다시 시도하세요.')).toBeTruthy();
  });

  it('일반 로그인 안내는 앱 안 링크다 — 새로고침으로 상태를 잃지 않는다', () => {
    render(<MemoryRouter><ManagerLoginPage /></MemoryRouter>);

    expect(screen.getByRole('link', { name: '이쪽' }).getAttribute('href')).toBe('/login');
  });
});

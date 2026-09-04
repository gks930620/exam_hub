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

  /**
   * 이 화면은 <b>로그인 전에 누구나</b> 열 수 있다. 서버 설정 방법을 적어 두면
   * 공격자에게 "이 서버는 무엇을 바꾸면 매니저가 되는가"를 알려 주는 셈이다(QA 2026-09-03).
   */
  it('서버 환경변수 이름·설정 절차를 노출하지 않는다', async () => {
    render(<MemoryRouter><ManagerLoginPage /></MemoryRouter>);
    const text = document.body.textContent ?? '';

    expect(text).not.toContain('MANAGER_PASSWORD');
    expect(text).not.toContain('MANAGER_USERNAME');
    expect(text).not.toContain('.env');
  });

  it('계정이 없을 때도 설정 방법 대신 담당자 문의로 안내한다', async () => {
    vi.spyOn(examApi, 'managerAvailable').mockResolvedValue({ configured: false });
    render(<MemoryRouter><ManagerLoginPage /></MemoryRouter>);

    expect(await screen.findByText(/매니저 계정이 아직 없습니다/)).toBeTruthy();
    expect(document.body.textContent ?? '').not.toContain('MANAGER_USERNAME');
  });

  /** 로그인 수단이 둘(카카오·구글)인데 하나만 적으면 다른 쪽 사용자가 길을 잃는다. */
  it('일반 이용자 안내에 특정 제공자만 적지 않는다', () => {
    render(<MemoryRouter><ManagerLoginPage /></MemoryRouter>);
    const text = document.body.textContent ?? '';

    expect(text).toContain('이쪽');
    expect(text).not.toContain('카카오로 로그인');
  });
});

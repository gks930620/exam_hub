import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import SettingsPage from './SettingsPage';
import * as auth from '../auth';
import { examApi } from '../api/exams';
import type { MeResponse } from '../api/types';

/**
 * 알림 설정 — 화면이 하는 말이 <b>서버가 실제로 하는 일</b>과 같아야 한다.
 *
 * <p>발송 시각은 NotificationScheduleService 가 정한다(접수 시작 전날 20:00·당일 09:00, 마감 전날 20:00,
 * 시험 7일 전 09:00·하루 전 20:00). 화면에 "오전 9시"라고 뭉뚱그려 적혀 있었다.
 * "이 브라우저에만 저장" · "기기 식별자" 는 계정 도입 전의 옛 문구다.
 */
function mockAuth(email: string | null) {
  const me: MeResponse = {
    id: 1, nickname: '테스터', email, profileImage: null,
    phoneNumber: null, provider: 'KAKAO', role: 'USER',
  };
  vi.spyOn(auth, 'useAuth').mockReturnValue({
    me, loading: false, login: vi.fn(), loginWithToken: vi.fn(), logout: vi.fn(), refresh: vi.fn(),
  });
}

describe('SettingsPage — 알림 설정 문구', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(examApi, 'notifySettings').mockResolvedValue({ notifyReg: true, notifyExam: true, notifyChange: false });
  });

  it('발송 시각을 서버가 실제로 보내는 시각으로 말한다', async () => {
    mockAuth('a@b.com');
    render(<MemoryRouter><SettingsPage /></MemoryRouter>);

    expect(await screen.findByText(/접수 시작 전날 20:00/)).toBeTruthy();
    expect(screen.getByText(/마감 전날 20:00/)).toBeTruthy();
    expect(screen.getByText(/7일 전 09:00/)).toBeTruthy();
    expect(screen.getByText(/하루 전 20:00/)).toBeTruthy();
  });

  it('설정은 계정에 저장된다고 말한다 — 브라우저·기기 얘기는 옛말이다', async () => {
    mockAuth('a@b.com');
    render(<MemoryRouter><SettingsPage /></MemoryRouter>);

    expect(await screen.findByText(/계정에 저장됩니다/)).toBeTruthy();
    expect(screen.queryByText(/이 브라우저에만/)).toBeNull();
    expect(screen.queryByText(/기기 식별자/)).toBeNull();
    expect(screen.queryByText(/실제로 도착하지 않습니다/)).toBeNull();
  });

  it('이메일이 있으면 경고 없이 이메일로 보낸다고 말한다', async () => {
    mockAuth('a@b.com');
    render(<MemoryRouter><SettingsPage /></MemoryRouter>);

    expect(await screen.findByText(/지금은 이메일로 발송합니다/)).toBeTruthy();
    expect(document.querySelector('.k-alert--warn')).toBeNull();
  });

  /** 이메일이 없으면 켜 둔 알림이 전부 허공에 간다 — 어디서 넣는지까지 알려 준다. */
  it('이메일이 없으면 내 정보로 안내한다', async () => {
    mockAuth(null);
    render(<MemoryRouter><SettingsPage /></MemoryRouter>);

    const warn = (await screen.findByText(/알림을 받을 이메일이 없습니다/)).closest('.k-alert--warn');
    expect(warn).toBeTruthy();
    const link = screen.getByRole('link', { name: /내 정보에서 입력/ });
    expect(link.getAttribute('href')).toBe('/me');
  });
});

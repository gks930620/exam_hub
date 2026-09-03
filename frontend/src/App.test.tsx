import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import App from './App';
import * as auth from './auth';
import { examApi } from './api/exams';
import { api } from './api/client';
import type { MeResponse } from './api/types';

/**
 * 앱 골격 — <b>홈은 시험 찾기</b>이고, <b>메뉴는 헤더 안</b>에 있다.
 *
 * <p>예전 홈은 '내 시험'이라 처음 온 사람에게 로그인부터 요구했고, 로그인해도 관심 시험이
 * 없으면 빈 화면이었다. 이 서비스에서 처음 할 일은 시험을 찾는 것이라 홈을 그리로 옮겼다.
 *
 * <p>메뉴는 왼쪽 사이드바에서 헤더로 옮겼다(사용자 지시). 사이드바가 되살아나면 여기서 걸린다.
 */
function mockLoggedOut(login = vi.fn()) {
  vi.spyOn(auth, 'useAuth').mockReturnValue({
    me: null, loading: false,
    login, loginWithToken: vi.fn(), logout: vi.fn(), refresh: vi.fn(),
  });
  return login;
}

function mockLoggedIn(over: Partial<MeResponse> = {}) {
  const me: MeResponse = {
    id: 1, nickname: '테스터', email: null, profileImage: null,
    phoneNumber: null, provider: 'KAKAO', role: 'USER', ...over,
  };
  vi.spyOn(auth, 'useAuth').mockReturnValue({
    me, loading: false,
    login: vi.fn(), loginWithToken: vi.fn(), logout: vi.fn(), refresh: vi.fn(),
  });
  return me;
}

function renderAt(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>);
}

function navLabels(): string[] {
  return Array.from(document.querySelectorAll('.top-nav .nav-item')).map((el) => el.textContent ?? '');
}

describe('App 골격', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockLoggedOut();
    vi.spyOn(examApi, 'categories').mockResolvedValue({ items: [] });
    vi.spyOn(examApi, 'browse').mockResolvedValue({
      items: [], page: 0, size: 24, totalElements: 0, totalPages: 0,
    });
  });

  /** 비로그인으로 들어와도 바로 시험을 찾을 수 있어야 한다 — 로그인 화면으로 튕기면 안 된다. */
  it('홈(/)은 시험 찾기다', async () => {
    renderAt('/');

    await waitFor(() => expect(examApi.browse).toHaveBeenCalled());
    expect(screen.queryByText(/로그인이 필요합니다/)).toBeNull();
  });

  /** 눌러 둔 링크·북마크가 죽지 않아야 한다. */
  it('예전 주소 /search 는 홈으로 보낸다', async () => {
    renderAt('/search');

    await waitFor(() => expect(examApi.browse).toHaveBeenCalled());
  });

  it('메뉴가 헤더 안에 있다', async () => {
    renderAt('/');

    await waitFor(() => expect(document.querySelector('.app-header .top-nav')).toBeTruthy());
  });

  /** 사이드바를 지웠다 — 되살아나면 화면이 두 군데서 길을 안내하게 된다. */
  it('왼쪽 사이드바가 없다', async () => {
    renderAt('/');

    await waitFor(() => expect(document.querySelector('.top-nav')).toBeTruthy());
    expect(document.querySelector('.sidebar')).toBeNull();
  });

  it('메뉴에 주요 화면이 다 있다', async () => {
    renderAt('/');

    await waitFor(() => expect(document.querySelector('.top-nav')).toBeTruthy());
    const labels = navLabels();
    for (const expected of ['시험 찾기', '내 시험', '캘린더', '커뮤니티', '알림 설정']) {
      expect(labels.some((l) => l.includes(expected))).toBe(true);
    }
  });

  /** 매니저 메뉴는 매니저에게만 — 일반 회원에게 보이면 눌러도 막히는 길이 된다. */
  it('운영 메뉴는 매니저가 아니면 안 보인다', async () => {
    renderAt('/');

    await waitFor(() => expect(document.querySelector('.top-nav')).toBeTruthy());
    expect(navLabels()).not.toContain('운영');
  });

  /** 운영 화면은 할 일·수집 지도·변천사 세 갈래다 — '수기 일정 입력'은 그중 하나를 부르던 옛 이름이다. */
  it('매니저에게는 "운영" 메뉴가 보인다', async () => {
    mockLoggedIn({ role: 'ADMIN' });
    renderAt('/');

    await waitFor(() => expect(document.querySelector('.top-nav')).toBeTruthy());
    expect(navLabels()).toContain('운영');
    expect(navLabels()).not.toContain('수기 일정 입력');
  });

  /** 좁은 화면에서는 닉네임이 숨어 아바타만 남는다 — 이름 없는 링크가 되지 않게 aria-label 을 둔다. */
  it('상단 프로필 링크에 이름이 있다', async () => {
    mockLoggedIn({ nickname: '홍길동' });
    renderAt('/');

    await waitFor(() => expect(document.querySelector('.who')).toBeTruthy());
    expect(document.querySelector('.who')?.getAttribute('aria-label')).toContain('홍길동');
  });

  /**
   * 로그인이 필요한 화면에서 튕길 때는 <b>검색어까지 포함한</b> 주소를 들고 간다 —
   * 로그인 뒤 "/calendar" 가 아니라 보던 "/calendar?y=2026" 으로 돌아와야 한다(useRequireLogin 과 같은 규칙).
   */
  it('로그인 필요 화면은 보던 주소(검색어 포함)를 갖고 로그인으로 간다', async () => {
    const login = mockLoggedOut();
    vi.spyOn(api, 'get').mockResolvedValue({ providers: ['kakao'] });
    renderAt('/calendar?y=2026');

    fireEvent.click(await screen.findByText(/카카오로 시작하기/));

    expect(login).toHaveBeenCalledWith('kakao', '/calendar?y=2026');
  });
});

import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AccountMenu from './AccountMenu';
import type { MeResponse } from '../api/types';

/**
 * 상단바 계정 메뉴 — <b>사람 아이콘을 누르면 내 정보·로그아웃이 나온다</b>는 계약.
 *
 * 그전엔 닉네임 칩이 곧바로 내 정보로 가는 링크였고 로그아웃은 그 페이지 안에 있었다.
 * 이름만 보고는 "저기가 내 계정"인지 알 수 없고, 로그아웃은 두 번 들어가야 보였다(2026-09-11).
 */
const me: MeResponse = {
  id: 1, nickname: '홍길동', email: 'hong@example.com', profileImage: null,
  phoneNumber: null, provider: 'GOOGLE', role: 'USER',
};

function renderMenu(over: Partial<MeResponse> = {}, onLogout = vi.fn()) {
  render(
    <MemoryRouter>
      <AccountMenu me={{ ...me, ...over }} onLogout={onLogout} />
    </MemoryRouter>
  );
  return onLogout;
}

describe('AccountMenu', () => {
  it('닫혀 있을 땐 사람 아이콘 버튼만 있고, 이름은 적혀 있지 않다', () => {
    renderMenu();

    const btn = screen.getByRole('button', { name: /홍길동/ });
    expect(btn).toHaveAttribute('aria-haspopup', 'menu');
    expect(btn).toHaveAttribute('aria-expanded', 'false');
    expect(btn.querySelector('svg')).toBeTruthy();
    expect(screen.queryByText('홍길동')).toBeNull();
    expect(screen.queryByRole('menu')).toBeNull();
  });

  it('누르면 내 정보 링크와 로그아웃이 내려오고, 머리에 닉네임이 적힌다', () => {
    renderMenu();

    fireEvent.click(screen.getByRole('button', { name: /홍길동/ }));

    expect(screen.getByRole('button', { name: /홍길동/ })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('menu')).toBeTruthy();
    expect(screen.getByText('홍길동')).toBeTruthy();
    expect(screen.getByRole('menuitem', { name: /내 정보/ })).toHaveAttribute('href', '/me');
    expect(screen.getByRole('menuitem', { name: /로그아웃/ })).toBeTruthy();
  });

  it('로그아웃을 고르면 로그아웃이 실행되고 메뉴가 닫힌다', () => {
    const onLogout = renderMenu();

    fireEvent.click(screen.getByRole('button', { name: /홍길동/ }));
    fireEvent.click(screen.getByRole('menuitem', { name: /로그아웃/ }));

    expect(onLogout).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('menu')).toBeNull();
  });

  it('Esc 로 닫히고, 바깥을 누르면 닫힌다', () => {
    renderMenu();
    const btn = screen.getByRole('button', { name: /홍길동/ });

    fireEvent.click(btn);
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('menu')).toBeNull();

    fireEvent.click(btn);
    fireEvent.mouseDown(document.body);
    expect(screen.queryByRole('menu')).toBeNull();
  });

  /** 사진이 있으면 사진이 곧 "나"다 — 사람 아이콘으로 덮지 않는다 */
  it('프로필 사진이 있으면 아이콘 대신 사진을 쓴다', () => {
    renderMenu({ profileImage: 'https://example.com/p.jpg' });

    const btn = screen.getByRole('button', { name: /홍길동/ });
    expect(btn.querySelector('img')).toBeTruthy();
    expect(btn.querySelector('svg')).toBeNull();
  });
});

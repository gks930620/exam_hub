import { describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { AuthProvider, useAuth } from './auth';
import { examApi } from './api/exams';
import { UNAUTHENTICATED_EVENT } from './api/client';

/**
 * 토큰 만료 — 요청 중 401 이 나면 화면 전체가 <b>비로그인</b>으로 바뀌어야 한다.
 *
 * <p>예전엔 토큰이 만료돼도 헤더에는 닉네임이 남고, 눌러야 "요청 실패 (401)" 빨간 줄만 떴다.
 * 클라이언트가 401 에 이벤트를 띄우고, 여기서 그걸 들어 me 를 비운다.
 */
function Probe() {
  const { me, loading } = useAuth();
  return <div>{loading ? '로딩' : me ? `회원 ${me.nickname}` : '비로그인'}</div>;
}

describe('AuthProvider — 토큰 만료', () => {
  it('요청 중 401 이 나면 비로그인으로 바뀐다', async () => {
    vi.spyOn(examApi, 'me').mockResolvedValue({
      id: 1, nickname: '테스터', email: null, profileImage: null,
      phoneNumber: null, provider: 'KAKAO', role: 'USER',
    });

    render(<AuthProvider><Probe /></AuthProvider>);
    expect(await screen.findByText('회원 테스터')).toBeTruthy();

    act(() => { window.dispatchEvent(new Event(UNAUTHENTICATED_EVENT)); });

    expect(screen.getByText('비로그인')).toBeTruthy();
  });
});

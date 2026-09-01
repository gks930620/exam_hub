import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import MePage from './MePage';
import * as auth from '../auth';
import { examApi } from '../api/exams';
import type { MeResponse } from '../api/types';

/**
 * 내 정보 — <b>지금 알림이 어디로 가는지</b>가 화면에서 분명해야 한다.
 *
 * 알림은 현재 <b>이메일로만</b> 간다(알림톡은 사업자등록이 필요해 보류).
 * 그런데 카카오 로그인은 이메일을 주지 않는다 — 비즈 앱이 아니면 이메일 동의항목 자체를
 * 켤 수 없고, 그대로 요청하면 KOE205 로 로그인이 막힌다. 그래서 이메일을 직접 받는다.
 *
 * 이메일이 비어 있으면 <b>알림이 하나도 안 간다</b>. 사용자가 그 사실을 모르고 마감을
 * 놓치는 게 최악이라, 비어 있을 때는 경고를 띄운다.
 */
function mockMe(over: Partial<MeResponse> = {}) {
  const me: MeResponse = {
    id: 1, nickname: '테스터', email: 'a@b.com', profileImage: null,
    phoneNumber: null, provider: 'KAKAO', role: 'USER',
    ...over,
  };
  vi.spyOn(auth, 'useAuth').mockReturnValue({
    me, loading: false, login: vi.fn(), loginWithToken: vi.fn(), logout: vi.fn(), refresh: vi.fn(),
  });
  return me;
}

describe('MePage — 알림 수신 상태', () => {
  /** 수신 상태 안내는 .notice 한 곳에만 뜬다 — 그 박스만 골라 본다. */
  function noticeText(): string {
    return document.querySelector('.notice.info, .notice.warn')?.textContent ?? '';
  }

  it('이메일이 있으면 그 주소로 간다고 알려준다', () => {
    mockMe({ email: 'a@b.com' });
    render(<MemoryRouter><MePage /></MemoryRouter>);

    expect(noticeText()).toContain('a@b.com');
    expect(noticeText()).not.toContain('받을 수 없는');
  });

  it('이메일이 없으면 알림을 못 받는다고 경고한다', () => {
    mockMe({ email: null });
    render(<MemoryRouter><MePage /></MemoryRouter>);

    expect(noticeText()).toContain('알림을 받을 수 없는 상태입니다');
  });

  /** 카카오는 이메일을 아예 안 주므로 "왜 비어 있는지"까지 말해 줘야 사용자가 납득한다. */
  it('카카오 로그인이면 직접 입력이 필요한 이유를 덧붙인다', () => {
    mockMe({ email: null, provider: 'KAKAO' });
    render(<MemoryRouter><MePage /></MemoryRouter>);

    expect(noticeText()).toContain('카카오 계정은 이메일을 알려주지 않아');
  });

  it('저장된 이메일을 입력칸에 채워 준다', () => {
    mockMe({ email: 'a@b.com' });
    render(<MemoryRouter><MePage /></MemoryRouter>);

    expect(screen.getByDisplayValue('a@b.com')).toBeInTheDocument();
  });

  it('입력한 이메일을 저장한다', async () => {
    const me = mockMe({ email: null });
    const changeEmail = vi.spyOn(examApi, 'changeEmail').mockResolvedValue(me);
    render(<MemoryRouter><MePage /></MemoryRouter>);

    fireEvent.change(screen.getByPlaceholderText('name@example.com'),
      { target: { value: ' me@example.com ' } });
    fireEvent.click(screen.getByRole('button', { name: '이메일 저장' }));

    // 앞뒤 공백은 떼고 보낸다 — 복붙하면 흔히 딸려온다
    await waitFor(() => expect(changeEmail).toHaveBeenCalledWith('me@example.com'));
  });

  it('저장된 번호를 하이픈 넣어 보여준다', () => {
    mockMe({ phoneNumber: '01012345678' });
    render(<MemoryRouter><MePage /></MemoryRouter>);

    expect(screen.getByDisplayValue('010-1234-5678')).toBeInTheDocument();
  });

  /** 번호는 알림톡을 붙일 때 쓰려고 미리 받아 둘 뿐 — 지금 발송에는 안 쓴다. */
  it('휴대폰번호는 아직 안 쓴다고 밝힌다', () => {
    mockMe({ phoneNumber: null });
    render(<MemoryRouter><MePage /></MemoryRouter>);

    expect(screen.getByText(/지금은 발송하지 않습니다/)).toBeInTheDocument();
  });
});

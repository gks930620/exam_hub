import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import Avatar from './Avatar';

/**
 * 헤더의 프로필 사진.
 *
 * <p>구글로 로그인하면 사진이 <b>깨진 이미지</b>로 나왔다. 구글 프로필 사진
 * (<code>lh3.googleusercontent.com</code>)은 다른 사이트에서 불러올 때 <code>Referer</code> 헤더가
 * 붙으면 403 을 돌려준다. 그래서 <code>referrerpolicy=no-referrer</code> 로 요청해야 한다.
 *
 * <p>그리고 어떤 이유로든 못 불러왔을 때 <b>깨진 아이콘을 보여주지 않는다</b> —
 * 사진은 장식이라, 못 받으면 닉네임 첫 글자로 조용히 물러나는 게 맞다.
 */
describe('Avatar', () => {
  it('사진이 있으면 그린다', () => {
    render(<Avatar src="https://lh3.googleusercontent.com/a/x" nickname="창희" />);

    const img = document.querySelector('img');
    expect(img).toBeTruthy();
    expect(img?.getAttribute('src')).toBe('https://lh3.googleusercontent.com/a/x');
  });

  /** 이게 빠지면 구글 사진이 403 으로 깨진다 — 깨짐의 실제 원인이었다. */
  it('레퍼러를 보내지 않는다', () => {
    render(<Avatar src="https://lh3.googleusercontent.com/a/x" nickname="창희" />);

    expect(document.querySelector('img')?.getAttribute('referrerpolicy')).toBe('no-referrer');
  });

  it('사진이 없으면 닉네임 첫 글자를 보여준다', () => {
    render(<Avatar src={null} nickname="창희" />);

    expect(document.querySelector('img')).toBeNull();
    expect(screen.getByText('창')).toBeTruthy();
  });

  /** 깨진 이미지 아이콘은 "고장 났다"로 읽힌다 — 첫 글자로 물러난다. */
  it('불러오기에 실패하면 첫 글자로 물러난다', () => {
    render(<Avatar src="https://lh3.googleusercontent.com/a/x" nickname="창희" />);

    fireEvent.error(document.querySelector('img')!);

    expect(document.querySelector('img')).toBeNull();
    expect(screen.getByText('창')).toBeTruthy();
  });

  /** 닉네임이 없을 수도 있다(동의항목을 안 켜면 카카오가 닉네임을 안 준다). */
  it('닉네임이 비어도 깨지지 않는다', () => {
    render(<Avatar src={null} nickname="" />);

    expect(document.querySelector('.avatar-fallback')).toBeTruthy();
  });
});

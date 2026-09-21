import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import Skeleton from './Skeleton';

/**
 * 첫 로딩 자리표시 — 자리를 잡되, 낭독기에는 무엇을 불러오는 중인지 말한다(설계 05 §8·§19-6).
 */
describe('Skeleton', () => {
  it('요청한 개수만큼 자리를 잡고 role=status 다', () => {
    render(<Skeleton rows={3} label="댓글을 불러오는 중…" />);

    expect(screen.getByRole('status')).toBeTruthy();
    expect(document.querySelectorAll('.k-skeleton').length).toBe(3);
  });

  it('무엇을 불러오는지는 낭독기용 글자로 남긴다 — 눈에서만 지운다', () => {
    render(<Skeleton label="댓글을 불러오는 중…" />);

    const sr = screen.getByText('댓글을 불러오는 중…');
    expect(sr.className).toContain('k-sr');
  });

  it('panel 은 카드 한 장 크기, row 는 목록 한 행 크기다', () => {
    render(<><Skeleton kind="panel" rows={1} label="a" /><Skeleton kind="row" rows={1} label="b" /></>);

    expect(document.querySelector('.sk-panel')).toBeTruthy();
    expect(document.querySelector('.sk-row')).toBeTruthy();
  });
});

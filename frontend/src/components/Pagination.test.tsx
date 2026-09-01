import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import Pagination from './Pagination';

/**
 * 번호 페이징 — "이전/다음"만으로는 846종 29쪽을 오갈 수 없다(사용자 지적).
 * 12쪽으로 가려면 다음을 열한 번 눌러야 하고, 지금 몇 쪽인지도 모른다.
 */
function numbers(): string[] {
  return Array.from(document.querySelectorAll('.page-btn:not(.arrow)')).map((b) => b.textContent ?? '');
}

describe('Pagination', () => {
  it('현재 쪽 주변과 처음·끝을 번호로 보여준다', () => {
    render(<Pagination page={5} totalPages={20} onChange={() => {}} />);

    // 1 … 4 5 [6] 7 8 … 20  (page 는 0부터, 표시는 1부터)
    expect(numbers()).toEqual(['1', '4', '5', '6', '7', '8', '20']);
    expect(screen.getByRole('button', { name: '6' }).getAttribute('aria-current')).toBe('page');
    expect(document.querySelectorAll('.page-gap').length).toBe(2);
  });

  it('번호를 누르면 0부터 세는 쪽수로 알려준다', () => {
    const onChange = vi.fn();
    render(<Pagination page={5} totalPages={20} onChange={onChange} />);

    fireEvent.click(screen.getByRole('button', { name: '8' }));

    expect(onChange).toHaveBeenCalledWith(7);
  });

  it('앞쪽에 있으면 앞 생략표가 없다', () => {
    render(<Pagination page={1} totalPages={20} onChange={() => {}} />);

    expect(numbers()).toEqual(['1', '2', '3', '4', '20']);
    expect(document.querySelectorAll('.page-gap').length).toBe(1);
  });

  it('첫 쪽에서는 이전이 잠긴다', () => {
    render(<Pagination page={0} totalPages={3} onChange={() => {}} />);

    expect((screen.getByRole('button', { name: '이전 쪽' }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole('button', { name: '다음 쪽' }) as HTMLButtonElement).disabled).toBe(false);
  });

  /** 한 쪽뿐이면 누를 것도 없다 — 그리지 않는다. */
  it('한 쪽이면 아무것도 안 그린다', () => {
    render(<Pagination page={0} totalPages={1} onChange={() => {}} />);

    expect(document.querySelector('.pagination')).toBeNull();
  });
});

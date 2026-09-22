import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import CalendarPage from './CalendarPage';
import { examApi } from '../api/exams';

/**
 * 캘린더 — <b>실패를 "일정 없음"으로 둔갑시키지 않는다.</b>
 *
 * <p>예전엔 조회가 죽어도 events=[] 로 두어 "이 달 일정이 없습니다"가 떴다. 사용자는 정말 없는 줄 안다.
 * 실패면 실패라고 말하고, 다시 시도할 길을 준다.
 */
describe('CalendarPage', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('조회가 실패하면 "일정 없음"이 아니라 오류와 다시 시도를 보여준다', async () => {
    const cal = vi.spyOn(examApi, 'calendar')
      .mockRejectedValueOnce(new Error('서버 오류'))
      .mockResolvedValue({ events: [] });

    render(<MemoryRouter><CalendarPage /></MemoryRouter>);

    expect(await screen.findByText('서버 오류')).toBeTruthy();
    expect(screen.queryByText(/이 달 일정이 없습니다/)).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: /다시 시도/ }));

    await waitFor(() => expect(cal).toHaveBeenCalledTimes(2));
    expect(await screen.findByText(/이 달 일정이 없습니다/)).toBeTruthy();
  });

  it('요일은 날짜 문자열 그대로(현지 자정) 계산한다 — UTC 파싱이면 서쪽 시간대에서 하루가 밀린다', async () => {
    vi.spyOn(examApi, 'calendar').mockResolvedValue({
      events: [{ date: '2026-09-06', type: 'EXAM', certificateId: 1, name: '정보처리기사', label: '1회 필기 시험' }],
    });

    render(<MemoryRouter><CalendarPage /></MemoryRouter>);

    await screen.findByText('정보처리기사');
    // 2026-09-06 은 일요일
    expect(document.querySelector('.day-row .date span')?.textContent).toBe('일');
  });

  /**
   * 2026-09-23 QA 지적: 이 버튼이 들어간 커밋에 프런트 테스트가 하나도 없었다.
   * 우리 메일이 스팸함에 빠져도 휴대폰 달력 알림은 울린다 — 이 버튼이 그 두 번째 줄이다.
   */
  it('내 달력에 넣기를 누르면 파일을 받는다', async () => {
    const dl = vi.spyOn(examApi, 'downloadCalendar').mockResolvedValue(undefined);

    render(<MemoryRouter><CalendarPage /></MemoryRouter>);
    fireEvent.click(await screen.findByRole('button', { name: /내 달력에 넣기/ }));

    await waitFor(() => expect(dl).toHaveBeenCalled());
  });

  /** 못 받았으면 조용히 넘어가지 않는다 — 사용자는 눌렀는데 아무 일도 안 난 줄 안다. */
  it('못 받으면 이유를 보여준다', async () => {
    vi.spyOn(examApi, 'downloadCalendar').mockRejectedValue(new Error('권한이 없습니다'));

    render(<MemoryRouter><CalendarPage /></MemoryRouter>);
    fireEvent.click(await screen.findByRole('button', { name: /내 달력에 넣기/ }));

    expect(await screen.findByText(/권한이 없습니다/)).toBeTruthy();
  });
});

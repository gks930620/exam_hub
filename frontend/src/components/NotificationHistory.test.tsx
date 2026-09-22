import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import NotificationHistory from './NotificationHistory';
import { examApi } from '../api/exams';
import type { NotificationHistoryItem } from '../api/types';

/**
 * <b>나한테 뭘 보냈다는 건지</b> 보여주는 목록.
 *
 * <p>발송 기록은 쌓이지만 매니저 집계에만 쓰였다. 사용자는 "왔다는데 나는 못 받았다"를
 * 확인할 길이 없었다. 확인 메일 버튼과 짝이다 — 그 버튼은 "지금 보내면 오나",
 * 이 목록은 "그동안 뭘 보냈나"를 답한다. 둘이 어긋나면 접수를 놓치기 전에 알아챈다.
 */
function item(over: Partial<NotificationHistoryItem> = {}): NotificationHistoryItem {
  return {
    certificateName: '정보처리기사', round: '3회 필기',
    eventLabel: '접수 마감 하루 전', sentAt: '2026-09-21T20:00',
    channel: 'EMAIL', delivered: true,
    ...over,
  };
}

describe('NotificationHistory', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('언제 어떤 시험으로 무엇을 보냈는지 한 줄씩 보여준다', async () => {
    vi.spyOn(examApi, 'notificationHistory').mockResolvedValue({ items: [item()] });

    render(<NotificationHistory />);

    expect(await screen.findByText(/정보처리기사/)).toBeTruthy();
    expect(screen.getByText(/접수 마감 하루 전/)).toBeTruthy();
  });

  /** 아직 아무것도 안 받은 사람에게 빈 표를 보여주면 고장인 줄 안다. */
  it('받은 게 없으면 왜 없는지 말해 준다', async () => {
    vi.spyOn(examApi, 'notificationHistory').mockResolvedValue({ items: [] });

    render(<NotificationHistory />);

    expect(await screen.findByText(/아직 보낸 알림이 없습니다/)).toBeTruthy();
  });

  /**
   * 가장 중요한 한 줄. 서버 로그로만 나간 건은 성공으로 기록되지만 사람에게는 안 갔다.
   * "보냈습니다"라고 하면 오지도 않은 메일을 계속 기다린다.
   */
  it('사람에게 안 간 건은 보냈다고 하지 않는다', async () => {
    vi.spyOn(examApi, 'notificationHistory').mockResolvedValue({
      items: [item({ channel: 'LOG', delivered: false })],
    });

    render(<NotificationHistory />);

    expect(await screen.findByText(/보내지 못함/)).toBeTruthy();
  });

  it('불러오지 못하면 빈 목록이 아니라 이유를 남긴다', async () => {
    vi.spyOn(examApi, 'notificationHistory').mockRejectedValue(new Error('권한이 없습니다'));

    render(<NotificationHistory />);

    expect(await screen.findByText(/권한이 없습니다/)).toBeTruthy();
  });
});

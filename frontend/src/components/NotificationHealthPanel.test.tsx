import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import NotificationHealthPanel from './NotificationHealthPanel';
import { examApi } from '../api/exams';
import type { NotificationHealthResponse } from '../api/types';

/**
 * 알림 건강 — <b>약속이 지켜지고 있나</b>.
 *
 * <p>이 서비스의 약속은 하나다: 접수 마감을 놓치지 않게 알려 준다. 그런데 가장 위험한 실패가
 * 조용하다 — 발송 체인의 마지막 LOG 채널은 서버 로그에 한 줄 찍고 <b>언제나 성공을 돌려준다.</b>
 * 그래서 성공률만 보면 아무도 못 받은 날도 100% 다. 화면은 성공률이 아니라 <b>도달</b>을 보여준다.
 */
function res(over: Partial<NotificationHealthResponse['health']> = {},
             live: string[] = ['EMAIL', 'LOG']): NotificationHealthResponse {
  return {
    health: {
      overdue: 0, upcoming: 143, delivered: 88, loggedOnly: 0, failed: 0, armable: 300,
      byChannel: { ALIMTALK: 0, KAKAO_MEMO: 0, EMAIL: 88, LOG: 0 },
      message: '최근 88건이 정상 발송됐습니다.', needsAttention: false,
      ...over,
    },
    liveChannels: live,
    lookbackDays: 30,
  };
}

describe('NotificationHealthPanel', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('정상이면 몇 건이 나갔는지 말한다', async () => {
    vi.spyOn(examApi, 'adminNotificationHealth').mockResolvedValue(res());

    render(<NotificationHealthPanel />);

    expect(await screen.findByText(/최근 88건이 정상 발송됐습니다/)).toBeTruthy();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  /** 가장 중요한 한 줄 — 통계는 성공인데 받은 사람이 없다. */
  it('전부 로그로만 나갔으면 아무도 못 받았다고 경고한다', async () => {
    vi.spyOn(examApi, 'adminNotificationHealth').mockResolvedValue(res({
      delivered: 0, loggedOnly: 42, needsAttention: true,
      byChannel: { ALIMTALK: 0, KAKAO_MEMO: 0, EMAIL: 0, LOG: 42 },
      message: '최근 발송 42건이 전부 서버 로그로만 나갔습니다 — 실제로는 아무에게도 가지 않았습니다. 알림톡·이메일 중 최소 하나를 켜야 합니다.',
    }, ['LOG']));

    render(<NotificationHealthPanel />);

    expect(await screen.findByRole('alert')).toBeTruthy();
    expect(screen.getByText(/아무에게도 가지 않았습니다/)).toBeTruthy();
  });

  it('발송이 막혔으면 밀린 건수를 보여준다', async () => {
    vi.spyOn(examApi, 'adminNotificationHealth').mockResolvedValue(res({
      overdue: 7, needsAttention: true,
      message: '발송 시각이 지난 예약이 7건 남아 있습니다. 발송이 막혔을 수 있습니다 — 메일 설정(MAIL_ENABLED·자격증명)을 확인하세요.',
    }));

    render(<NotificationHealthPanel />);

    expect(await screen.findByText(/발송이 막혔을 수 있습니다/)).toBeTruthy();
  });

  /** 켜진 채널이 LOG 뿐이라는 사실 자체가 답이다 — 숫자를 세기 전에 이미 보낼 데가 없다. */
  it('켜진 발송 채널을 보여준다', async () => {
    vi.spyOn(examApi, 'adminNotificationHealth').mockResolvedValue(res({}, ['EMAIL', 'LOG']));

    render(<NotificationHealthPanel />);

    expect(await screen.findByText('EMAIL')).toBeTruthy();
  });

  /** 알림이 안 보인다고 수집 표까지 사라지면 안 된다 — 그래서 각자 불러오고 각자 실패한다. */
  it('불러오지 못해도 조용히 비우지 않고 이유를 남긴다', async () => {
    vi.spyOn(examApi, 'adminNotificationHealth').mockRejectedValue(new Error('권한이 없습니다'));

    render(<NotificationHealthPanel />);

    expect(await screen.findByText(/권한이 없습니다/)).toBeTruthy();
  });
});

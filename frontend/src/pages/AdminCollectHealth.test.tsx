import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import AdminCollectHealth from './AdminCollectHealth';
import { examApi } from '../api/exams';
import type { CollectHealthRow } from '../api/types';

/**
 * 수집 건강 화면 — <b>고장을 알아챌 수 있는가</b>.
 *
 * <p>이 화면이 없을 때가 이 서비스의 가장 큰 위험이었다. 스크래퍼가 깨져도 DB 에는 옛 일정이
 * 남아 있어 사용자 화면은 멀쩡해 보인다. 수집 결과는 서버 로그에만 남아 매니저가 볼 수 없었다.
 */
function row(over: Partial<CollectHealthRow> = {}): CollectHealthRow {
  return {
    source: 'YBM_WEB', state: 'OK', stateLabel: '정상', message: '112건을 가져왔습니다.',
    lastRunAt: '2026-09-21T05:00', fetched: 112, consecutiveFailures: 0, needsAttention: false,
    ...over,
  };
}

describe('AdminCollectHealth', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('전부 정상이면 그렇다고 말한다', async () => {
    vi.spyOn(examApi, 'adminCollectHealth').mockResolvedValue({
      items: [row()], total: 1, needsAttention: 0,
    });

    render(<AdminCollectHealth />);

    expect(await screen.findByText(/전부 정상입니다/)).toBeTruthy();
    expect(screen.queryByText(/손봐야 합니다/)).toBeNull();
  });

  /** 0건은 예외가 아니라 성공으로 기록된다 — 시행처 개편을 알아채는 유일한 신호다. */
  it('0건이면 시행처 화면이 바뀌었을 수 있다고 알린다', async () => {
    vi.spyOn(examApi, 'adminCollectHealth').mockResolvedValue({
      items: [row({
        source: 'TOPIK_WEB', state: 'EMPTY', stateLabel: '0건', fetched: 0, needsAttention: true,
        message: '오류 없이 0건을 가져왔습니다. 시행처가 화면을 바꿨을 수 있습니다 — 원본 사이트를 열어 확인해 주세요.',
      })],
      total: 1, needsAttention: 1,
    });

    render(<AdminCollectHealth />);

    expect(await screen.findByText(/1개 소스를 손봐야 합니다/)).toBeTruthy();
    expect(screen.getByText(/시행처가 화면을 바꿨을 수 있습니다/)).toBeTruthy();
    expect(screen.getByText('0건')).toBeTruthy();
  });

  it('실패는 사유와 함께 보여준다 — 사유가 없으면 손댈 데를 모른다', async () => {
    vi.spyOn(examApi, 'adminCollectHealth').mockResolvedValue({
      items: [row({
        source: 'KCA_WEB', state: 'FAILED', stateLabel: '실패', needsAttention: true,
        consecutiveFailures: 3, message: '3회 연속 실패 — 연결 시간 초과',
      })],
      total: 1, needsAttention: 1,
    });

    render(<AdminCollectHealth />);

    expect(await screen.findByText(/3회 연속 실패 — 연결 시간 초과/)).toBeTruthy();
  });

  /** 한 번도 안 돈 소스는 건수를 0 이라고 단정하지 않는다 — 모르는 것과 0 은 다르다. */
  it('기록이 없으면 건수 자리에 0 이 아니라 — 를 쓴다', async () => {
    vi.spyOn(examApi, 'adminCollectHealth').mockResolvedValue({
      items: [row({ source: 'HSK_WEB', state: 'NEVER_RAN', stateLabel: '기록 없음', lastRunAt: null, fetched: 0, needsAttention: true })],
      total: 1, needsAttention: 1,
    });

    render(<AdminCollectHealth />);

    await waitFor(() => expect(screen.getByText('기록 없음')).toBeTruthy());
    expect(document.querySelectorAll('td.k-num')[0]?.textContent).toBe('—');
  });

  it('불러오지 못하면 빈 표가 아니라 오류를 보여준다', async () => {
    vi.spyOn(examApi, 'adminCollectHealth').mockRejectedValue(new Error('권한이 없습니다'));

    render(<AdminCollectHealth />);

    expect(await screen.findByRole('alert')).toBeTruthy();
    expect(screen.getByText(/권한이 없습니다/)).toBeTruthy();
  });
});

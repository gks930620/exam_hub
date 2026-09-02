import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import AdminDataMap from './AdminDataMap';
import { examApi } from '../api/exams';
import type { DataSourceRow, SourceMode } from '../api/types';

/**
 * 수집 지도 — 처음 열면 <b>세 갈래가 전부 접힌 채</b> 보인다.
 *
 * <p>예전엔 '수기 입력'이 펼쳐진 채 열려서, 첫눈에 들어오는 게 갈래가 아니라 한 갈래의
 * 세부 목록이었다(사용자: "수기입력이 클릭된 상태여서 좀 그렇네 — 접혀 있어야
 * 세 종류가 있구나 하고 바로 이해할 듯").
 */
function row(mode: SourceMode, group: string): DataSourceRow {
  return {
    group, exams: '시험들', mode,
    modeLabel: mode === 'MANUAL' ? '수기 입력' : mode === 'AUTO' ? '자동화 (API·크롤링)' : '대상 아님',
    modeGuide: `${group} 안내문`,
    sourceName: '시행처', sourceUrl: 'https://example.com',
    checkPath: '시험일정', frequency: '연 2회', note: '',
  };
}

describe('AdminDataMap', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(examApi, 'adminDataMap').mockResolvedValue({
      coverage: { totalExams: 818, withSchedule: 656, withoutSchedule: 162, rolling: 28 },
      sources: [row('MANUAL', '보건의료'), row('AUTO', '큐넷'), row('EXCLUDED', 'IT벤더')],
    });
  });

  it('처음엔 세 갈래가 전부 접혀 있다', async () => {
    render(<AdminDataMap />);

    await waitFor(() => expect(screen.getByText(/수기 입력/)).toBeTruthy());
    expect(screen.getByText(/자동화/)).toBeTruthy();
    expect(screen.getByText(/대상 아님/)).toBeTruthy();
    // 어느 갈래의 세부(안내문·시행처)도 아직 안 보인다
    expect(screen.queryByText(/안내문/)).toBeNull();
    expect(screen.queryByText('시행처')).toBeNull();
  });

  it('갈래를 누르면 그때 펼쳐진다', async () => {
    render(<AdminDataMap />);

    await waitFor(() => expect(screen.getByText(/수기 입력/)).toBeTruthy());
    fireEvent.click(screen.getByText(/수기 입력/));

    expect(screen.getByText('보건의료 안내문')).toBeTruthy();
  });
});

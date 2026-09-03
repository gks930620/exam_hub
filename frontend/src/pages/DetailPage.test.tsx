import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import DetailPage from './DetailPage';
import * as auth from '../auth';
import { examApi } from '../api/exams';
import type { DetailResponse, MeResponse, ScheduleDto } from '../api/types';

/**
 * 시험 상세 — <b>비로그인이 관심 등록을 누르면 로그인으로 보낸다.</b>
 *
 * <p>예전에는 그냥 API 를 불러 401 을 받고 "요청 실패 (401)" 이라는 빨간 줄만 떴다.
 * 사용자는 무엇이 잘못됐는지도, 어디로 가야 하는지도 알 수 없었다 — 기능이 없는 것과 같았다.
 */
function detail(over: Partial<DetailResponse> = {}): DetailResponse {
  return {
    id: 2, name: '전기기사', category: '국가기술자격-전기전자', agency: '한국산업인력공단',
    sourceUrl: null, collectedAt: null, favorited: false, rolling: false,
    nextEvent: null, schedules: [],
    ...over,
  };
}

function mockAuth(me: MeResponse | null) {
  vi.spyOn(auth, 'useAuth').mockReturnValue({
    me, loading: false,
    login: vi.fn(), loginWithToken: vi.fn(), logout: vi.fn(), refresh: vi.fn(),
  });
}

const MEMBER: MeResponse = {
  id: 1, nickname: '테스터', email: null, profileImage: null,
  phoneNumber: null, provider: 'KAKAO', role: 'USER',
};

/** 로그인 화면으로 갔는지 보려고 그 자리에 표식을 둔다. */
function renderDetail() {
  return render(
    <MemoryRouter initialEntries={['/cert/2']}>
      <Routes>
        <Route path="/cert/:id" element={<DetailPage />} />
        <Route path="/login" element={<div>로그인 화면</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('DetailPage — 관심 등록', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(examApi, 'detail').mockResolvedValue(detail());
  });

  it('비로그인이면 버튼이 로그인하라고 말한다', async () => {
    mockAuth(null);
    renderDetail();

    await waitFor(() => expect(screen.getByText('전기기사')).toBeTruthy());
    expect(screen.getByRole('button', { name: /로그인/ })).toBeTruthy();
  });

  /** 401 을 받아 빨간 줄을 띄우는 대신, 갈 곳으로 보낸다. */
  it('비로그인이 누르면 API 를 부르지 않고 로그인으로 간다', async () => {
    mockAuth(null);
    const add = vi.spyOn(examApi, 'addFavorite');
    renderDetail();

    await waitFor(() => expect(screen.getByText('전기기사')).toBeTruthy());
    fireEvent.click(screen.getByRole('button', { name: /로그인/ }));

    await waitFor(() => expect(screen.getByText('로그인 화면')).toBeTruthy());
    expect(add).not.toHaveBeenCalled();
  });

  it('로그인 상태면 관심 등록을 부른다', async () => {
    mockAuth(MEMBER);
    const add = vi.spyOn(examApi, 'addFavorite').mockResolvedValue({ certificateId: 2 } as never);
    renderDetail();

    await waitFor(() => expect(screen.getByText('전기기사')).toBeTruthy());
    fireEvent.click(screen.getByRole('button', { name: /관심 등록/ }));

    await waitFor(() => expect(add).toHaveBeenCalledWith(2));
    await waitFor(() => expect(screen.getByRole('button', { name: /등록됨/ })).toBeTruthy());
  });

  it('이미 등록했으면 해제할 수 있다', async () => {
    mockAuth(MEMBER);
    vi.spyOn(examApi, 'detail').mockResolvedValue(detail({ favorited: true }));
    const remove = vi.spyOn(examApi, 'removeFavorite').mockResolvedValue(undefined as never);
    renderDetail();

    await waitFor(() => expect(screen.getByRole('button', { name: /등록됨/ })).toBeTruthy());
    fireEvent.click(screen.getByRole('button', { name: /등록됨/ }));

    await waitFor(() => expect(remove).toHaveBeenCalledWith(2));
  });
});

function sched(over: Partial<ScheduleDto> = {}): ScheduleDto {
  return {
    id: 1, year: 2026, round: 1, examType: 'WRITTEN',
    regStartAt: '2026-01-10T10:00', regEndAt: '2026-01-20T18:00',
    examStartDate: '2026-03-12', examEndDate: null, resultDate: null, status: 'ACTIVE',
    ...over,
  };
}

/**
 * 문구와 회차 표 — <b>지키지 못할 약속을 하지 않는다.</b>
 *
 * <p>상시시험에 "접수 마감에 알려 드린다"고 하거나, 취소된 회차를 멀쩡한 일정처럼 보여주면
 * 사용자가 그 말을 믿는다. 상태 원문(CANCELED)을 그대로 내는 것도 화면이 아니다.
 */
describe('DetailPage — 문구와 회차 표', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockAuth(null);
  });

  it('상시시험은 "모아 볼 수 있다"고 권한다', async () => {
    vi.spyOn(examApi, 'detail').mockResolvedValue(detail({ rolling: true }));
    renderDetail();

    expect(await screen.findByText(/모아 볼 수 있습니다/)).toBeTruthy();
    expect(screen.queryByText(/원서접수 시작·마감에 알림/)).toBeNull();
  });

  it('일정이 없으면 "확인되면 알려 드린다"고 권한다', async () => {
    vi.spyOn(examApi, 'detail').mockResolvedValue(detail({ schedules: [] }));
    renderDetail();

    expect(await screen.findByText(/일정이 확인되면 알려 드립니다/)).toBeTruthy();
    expect(screen.queryByText(/원서접수 시작·마감에 알림/)).toBeNull();
  });

  it('일정이 있으면 접수 알림을 약속한다', async () => {
    vi.spyOn(examApi, 'detail').mockResolvedValue(detail({
      schedules: [sched()], nextEvent: { type: 'REG_OPEN', label: '1회 필기 접수 시작', dday: 3, at: '2026-01-10T10:00' },
    }));
    renderDetail();

    expect(await screen.findByText(/원서접수 시작·마감에 알림/)).toBeTruthy();
  });

  it('취소된 회차는 "취소됨"으로 보이고 영문 상태는 숨긴다', async () => {
    vi.spyOn(examApi, 'detail').mockResolvedValue(detail({ schedules: [sched({ status: 'CANCELED' })] }));
    renderDetail();

    expect(await screen.findByText('취소됨')).toBeTruthy();
    expect(screen.queryByText('CANCELED')).toBeNull();
    expect(document.querySelector('tr.is-canceled')).toBeTruthy();
    // 취소된 회차만 있으면 "지났다"가 아니라 "없다"다 — 다음 회차 미정 경고를 띄우지 않는다
    expect(screen.queryByText(/다음 회차 미정/)).toBeNull();
  });

  it('지난 회차만 남았으면 "다음 회차 미정"을 알린다', async () => {
    vi.spyOn(examApi, 'detail').mockResolvedValue(detail({ schedules: [sched()], nextEvent: null }));
    renderDetail();

    expect(await screen.findByText(/다음 회차 미정/)).toBeTruthy();
  });

  it('수집 시각은 사람이 읽는 표기로', async () => {
    vi.spyOn(examApi, 'detail').mockResolvedValue(detail({
      sourceUrl: 'https://www.q-net.or.kr', collectedAt: '2026-09-01T05:00:12',
    }));
    renderDetail();

    expect(await screen.findByText(/수집 2026-09-01 05:00/)).toBeTruthy();
    expect(document.body.textContent).not.toContain('T05:00');
  });

  /** 390px 에서 표가 화면 밖으로 나갔다 — 표는 가로 스크롤 래퍼 안에만 둔다. */
  it('회차 표는 가로 스크롤 래퍼 안에 있다', async () => {
    vi.spyOn(examApi, 'detail').mockResolvedValue(detail({ schedules: [sched()] }));
    renderDetail();

    await screen.findByText('전기기사');
    expect(document.querySelector('.k-tablewrap > table')).toBeTruthy();
  });
});

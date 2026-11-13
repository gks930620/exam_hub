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
    nextEvent: null, splitsByExamType: true, examInfo: null,
    hiddenReason: null, supersededBy: null, schedules: [],
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
      schedules: [sched()], nextEvent: { type: 'REG_OPEN', badge: 'REG_OPEN', label: '1회 필기 접수 시작', dday: 3, at: '2026-01-10T10:00' },
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

  /**
   * 2026-09-22 전수 실측: 공개 841종 중 206종이 실기가 하나도 없는데 "필기"를 달고 있었다
   * (어학 47 · 국가전문자격 99 · 금융 19 · 보건의료 17 …). 토익스피킹 회차에 "필기"라고
   * 쓰는 건 그냥 틀린 말이다. 없는 구분을 지어내느니 안 쓰는 편이 낫다.
   */
  it('한 종류만 치르는 시험이면 회차에 구분을 붙이지 않는다', async () => {
    vi.spyOn(examApi, 'detail').mockResolvedValue(detail({
      splitsByExamType: false, schedules: [sched()],
    }));

    mockAuth(null);
    renderDetail();

    const cell = await screen.findByText(/2026년/);
    expect(cell.textContent).not.toContain('필기');
  });

  it('필기·실기를 둘 다 치르면 구분을 보여준다 — 접수일이 서로 다르다', async () => {
    vi.spyOn(examApi, 'detail').mockResolvedValue(detail({
      splitsByExamType: true, schedules: [sched()],
    }));

    mockAuth(null);
    renderDetail();

    const cell = await screen.findByText(/2026년/);
    expect(cell.textContent).toContain('필기');
  });
/**
   * 폐지·개칭된 시험은 검색에 안 나오지만 <b>옛 북마크·공유 링크로는 들어온다.</b>
   * 그 화면이 "일정이 확인되면 알려 드립니다"라고 하면 올 일이 없는 것을 약속하는 셈이다.
   * 내 시험 화면은 이미 사유를 보여주고 있었는데 상세 계약에만 빠져 있었다(2026-11-14 실측).
   */
  it("폐지된 시험은 사유를 말하고 일정을 약속하지 않는다", async () => {
    vi.spyOn(examApi, "detail").mockResolvedValue(detail({
      name: "도자기공예기능사",
      hiddenReason: "폐지된 시험입니다. 더 이상 일정이 없습니다.",
      schedules: [],
    }));
    mockAuth(null);
    renderDetail();

    await screen.findByText("도자기공예기능사");
    expect(screen.getByText(/폐지된 시험입니다/)).toBeTruthy();
    expect(screen.queryByText(/일정이 확인되면 알려 드립니다/)).toBeNull();
    expect(screen.queryByText(/시행처 공고가 나오면 채워집니다/)).toBeNull();
  });

  it("개칭된 시험은 새 이름으로 가는 길을 준다", async () => {
    vi.spyOn(examApi, "detail").mockResolvedValue(detail({
      name: "SQL개발자(SQLD)",
      hiddenReason: "'SQLD SQL 개발자'(으)로 이름이 바뀌었습니다. 새 이름으로 다시 등록해 주세요.",
      supersededBy: "SQLD SQL 개발자",
    }));
    mockAuth(null);
    renderDetail();

    await screen.findByText("SQL개발자(SQLD)");
    expect(screen.getByText("새 이름으로 찾기").getAttribute("href"))
      .toContain(encodeURIComponent("SQLD SQL 개발자"));
  });

  /**
   * 히어로가 카드와 다른 말을 했다 — 시험 진행 중인데 `2026-11-28 00:00` 이라 적혀
   * "그날 시험"으로 읽혔다(실제 기간 11/14~11/28). 규약은 `eventAtLabel` 한 곳에 있다.
   * 2026-11-14 실측으로 EXAM_ONGOING 174종 + RESULT_PENDING 177종 = 351종이 걸려 있었다.
   */
  it("시험 진행 중이면 히어로가 '~ 종료일' 로 쓴다 — 뜻 없는 00:00 을 찍지 않는다", async () => {
    vi.spyOn(examApi, "detail").mockResolvedValue(detail({
      nextEvent: { type: "EXAM", badge: "EXAM_ONGOING", label: "시험 진행 중", dday: 0, at: "2026-11-28T00:00:00" },
    }));
    mockAuth(null);
    renderDetail();

    await screen.findByText("시험 진행 중");
    expect(screen.getByText("~ 2026-11-28")).toBeTruthy();
    expect(screen.queryByText(/00:00/)).toBeNull();
  });

  it("합격 발표 예정이면 날짜만 쓴다 — 시행처가 시각을 안 준다", async () => {
    vi.spyOn(examApi, "detail").mockResolvedValue(detail({
      nextEvent: { type: "RESULT", badge: "RESULT_PENDING", label: "합격자 발표", dday: 27, at: "2026-12-11T00:00:00" },
    }));
    mockAuth(null);
    renderDetail();

    await screen.findByText("합격자 발표");
    expect(screen.getByText("2026-12-11")).toBeTruthy();
    expect(screen.queryByText(/00:00/)).toBeNull();
  });
});

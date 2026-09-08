import { fmtAt, fmtDate } from './format';
import type { ScheduleState } from '../api/types';

/**
 * 상태 배지 어휘 — 시험 찾기·내 시험·매니저 현황이 <b>같은 말</b>을 쓰도록 한 곳에서 정한다.
 *
 * 서버 CardBadge 코드가 늘면(EXAM_ONGOING 처럼) 여기 한 줄만 더한다. 모르는 코드는 대체 문구로 —
 * 영문 코드를 화면에 그대로 내지 않는다.
 */
const LABELS: Record<string, string> = {
  REG_OPEN: '접수 중',
  REG_UPCOMING: '접수 예정',
  EXAM_UPCOMING: '시험 예정',
  EXAM_ONGOING: '시험 진행 중',
};

export function badgeLabel(code: string | null | undefined, fallback: string): string {
  return (code && LABELS[code]) || fallback;
}

/** 초록은 "지금 접수할 수 있다"에만. 나머지는 강조색 하나(킷 원칙 ②). */
export function badgeTone(code: string | null | undefined): string {
  return code === 'REG_OPEN' ? 'k-badge--ok' : 'k-badge--point';
}

/** 서버가 scheduleState 를 주면 그대로, 없으면 상시 → 일정 유무 순으로 유도한다. */
export function scheduleStateOf(c: {
  scheduleState: ScheduleState | null;
  rolling: boolean;
  hasSchedule: boolean;
}): ScheduleState {
  return c.scheduleState ?? (c.rolling ? 'ROLLING' : c.hasSchedule ? 'UPCOMING' : 'NONE');
}

/**
 * 이벤트 시각 표기. 시험 진행 중(EXAM_ONGOING)은 at 이 <b>종료일 00:00</b> 이라 시각을 보여주면
 * "00:00 에 뭐가 있나" 싶다 — "~ 종료일" 로 날짜만.
 */
export function eventAtLabel(code: string | null | undefined, at: string | null | undefined): string {
  return code === 'EXAM_ONGOING' ? `~ ${fmtDate(at)}` : fmtAt(at);
}

/**
 * 회차 표기 — `2026년 576회`, 시행처가 회차를 안 매기는 시험이면 `2026년`.
 *
 * <p>토익스피킹·TOEIC Bridge 처럼 회차가 없는 시험은 서버가 시험일(`YYYYMMDD`)을 회차 자리에
 * 멱등 키로 넣는다. 그건 우리 내부 키지 시행처가 부르는 이름이 아니다 — 그대로 찍으면
 * "2026년 20261011회" 가 되고, 사용자는 우리가 뭔가 잘못 읽었다고 생각한다(2026-09-08 실측).
 * 서버의 `ExamSchedule.roundLabel()` 과 같은 규칙이다.
 */
export function roundLabel(year: number, round: number): string {
  return round < 1_000_000 ? `${year}년 ${round}회` : `${year}년`;
}

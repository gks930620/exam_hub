import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import ExamInfo from './ExamInfo';
import type { ExamInfo as ExamInfoData } from '../api/types';

/**
 * 상세 화면의 <b>날짜가 아닌 정보</b>.
 *
 * <p>그전에는 회차 일정표밖에 없어서 취준생이 "이 시험 볼까"를 여기서 못 정하고 큐넷으로 나갔다.
 * 큐넷 API 가 이 정보를 주긴 하는데 덩어리 글이라, 못 가르는 시험이 있다 —
 * 그때 빈 칸을 늘어놓으면 화면만 길어지고 "정보가 없구나"로 읽힌다.
 */
function info(over: Partial<ExamInfoData> = {}): ExamInfoData {
  return {
    feeWritten: 19400, feePractical: 22600, feeRaw: '1차 : 19400, 2차 : 22600',
    relatedMajor: '모든 학과 응시가능',
    subjects: '- 필기 1. 소프트웨어설계 2. 소프트웨어개발 - 실기 : 정보처리 실무',
    examMethod: '- 필기 : 객관식 4지 택일형 - 실기 : 필답형(2시간30분)',
    passStandard: '- 필기 : 과목당 40점 이상, 전과목 평균 60점 이상',
    acquisitionRaw: '원문 전체',
    collectedAt: '2026-09-22T07:00',
    ...over,
  };
}

describe('ExamInfo', () => {
  it('응시료를 필기·실기로 나눠 보여준다', () => {
    render(<ExamInfo info={info()} />);

    expect(screen.getByText(/필기 19,400원 · 실기 22,600원/)).toBeTruthy();
  });

  /** 없는 쪽을 0원으로 채우면 화면에 "무료"로 보인다. */
  it('실기가 없으면 실기 값을 지어내지 않는다', () => {
    render(<ExamInfo info={info({ feePractical: null })} />);

    expect(screen.getByText(/필기 19,400원/)).toBeTruthy();
    expect(screen.queryByText(/실기 0원/)).toBeNull();
  });

  it('숫자로 못 가른 응시료는 원문을 그대로 쓴다', () => {
    render(<ExamInfo info={info({
      feeWritten: null, feePractical: null, feeRaw: '종목별 상이(홈페이지 참조)',
    })} />);

    expect(screen.getByText(/종목별 상이/)).toBeTruthy();
  });

  it('시험과목·검정방법·합격기준을 보여준다', () => {
    render(<ExamInfo info={info()} />);

    expect(screen.getByText('시험과목')).toBeTruthy();
    expect(screen.getByText('검정방법')).toBeTruthy();
    expect(screen.getByText('합격기준')).toBeTruthy();
  });

  /** 관련학과와 응시자격은 다른 말이다. 큐넷 API 에 응시자격은 없다. */
  it('관련학과를 응시자격이라고 부르지 않는다', () => {
    render(<ExamInfo info={info()} />);

    expect(screen.getByText('관련학과')).toBeTruthy();
    expect(screen.queryByText('응시자격')).toBeNull();
  });

  it('빈 칸은 아예 만들지 않는다', () => {
    render(<ExamInfo info={info({ examMethod: null, passStandard: null, relatedMajor: null })} />);

    expect(screen.queryByText('검정방법')).toBeNull();
    expect(screen.queryByText('합격기준')).toBeNull();
    expect(screen.getByText('시험과목')).toBeTruthy();
  });

  /** 조각을 하나도 못 갈랐어도 있는 정보를 감추지 않는다. */
  it('조각을 못 갈랐으면 원문이라도 보여준다', () => {
    render(<ExamInfo info={info({
      feeWritten: null, feePractical: null, feeRaw: null,
      subjects: null, examMethod: null, passStandard: null, relatedMajor: null,
      acquisitionRaw: '시행처 홈페이지를 참고하세요.',
    })} />);

    expect(screen.getByText(/시행처 홈페이지를 참고하세요/)).toBeTruthy();
  });

  /** 아직 안 받은 시험이 많다(비큐넷은 조사 자체가 없다) — 그때 빈 칸을 만들면 안 된다. */
  it('정보가 없으면 아무것도 그리지 않는다', () => {
    const { container } = render(<ExamInfo info={null} />);

    expect(container.textContent).toBe('');
  });

  /** 오래된 응시료를 지금 값처럼 보여주면 안 된다. */
  it('언제 받아온 값인지 밝힌다', () => {
    render(<ExamInfo info={info()} />);

    expect(screen.getByText(/2026-09-22 확인/)).toBeTruthy();
  });
});

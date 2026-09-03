import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import ScheduleEditorModal from './ScheduleEditorModal';
import { examApi } from '../api/exams';
import type { AdminScheduleRow } from '../api/types';

/**
 * 일정 넣기 모달 — 쓰던 내용을 묻지 않고 버리지 않고, 보류(PENDING_REVIEW) 회차는 왜 보류인지 말한다.
 */
function row(over: Partial<AdminScheduleRow> = {}): AdminScheduleRow {
  return {
    id: 9, certificateId: 1, certificateName: '국가직 9급', year: 2026, round: 1, examType: 'WRITTEN',
    regStartAt: '2026-01-10T10:00', regEndAt: '2026-01-20T18:00', examStartDate: '2026-03-12',
    examEndDate: null, resultDate: null, status: 'ACTIVE', sourceUrl: null, ...over,
  };
}

function renderModal(onClose = vi.fn(), onSaved = vi.fn()) {
  render(<ScheduleEditorModal certificateId={1} name="국가직 9급" onClose={onClose} onSaved={onSaved} />);
  return { onClose, onSaved };
}

describe('ScheduleEditorModal', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(examApi, 'adminSchedules').mockResolvedValue([row()]);
  });

  it('열리면 첫 입력칸에 초점이 간다', async () => {
    renderModal();

    await waitFor(() => expect(document.activeElement).toBe(screen.getByLabelText('연도')));
  });

  it('접수 시각은 읽는 표기다', async () => {
    renderModal();

    expect(await screen.findByText(/2026-01-10 10:00/)).toBeTruthy();
    expect(document.body.textContent).not.toContain('T10:00');
  });

  it('보류 회차는 "보류" 배지와 푸는 법을 보여준다', async () => {
    vi.spyOn(examApi, 'adminSchedules').mockResolvedValue([row({ id: 10, round: 2, status: 'PENDING_REVIEW' })]);
    renderModal();

    const badge = await screen.findByText('보류');
    expect(badge.classList.contains('k-badge--warn')).toBe(true);
    expect(screen.getByText(/공고와 대조해 저장하면 풀립니다/)).toBeTruthy();
    expect(screen.queryByText('PENDING_REVIEW')).toBeNull();
  });

  it('건드리지 않았으면 묻지 않고 닫힌다', async () => {
    const confirm = vi.spyOn(window, 'confirm');
    const { onClose } = renderModal();
    await screen.findByLabelText('연도');

    // 머리의 × 와 발의 [닫기] 둘 다 이름이 '닫기'다 — 발의 것을 누른다
    fireEvent.click(screen.getAllByRole('button', { name: '닫기' }).at(-1)!);

    expect(confirm).not.toHaveBeenCalled();
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('입력 중이면 닫기 전에 묻고, 취소하면 남는다', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const { onClose } = renderModal();

    fireEvent.change(await screen.findByLabelText('시험일'), { target: { value: '2026-09-20' } });
    fireEvent.keyDown(window, { key: 'Escape' });

    expect(confirm).toHaveBeenCalledWith('입력 중인 내용이 있습니다. 닫을까요?');
    expect(onClose).not.toHaveBeenCalled();
  });
});

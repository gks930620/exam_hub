import { useEffect, useState } from 'react';
import { examApi } from '../api/exams';
import type { AdminScheduleRow } from '../api/types';
import Icon from './Icon';

/**
 * 일정 넣기 모달 — 할 일 목록의 [넣기] 를 누르면 그 자리에서 뜬다.
 *
 * 예전엔 '일정 넣기' 탭이 따로 있어서 목록 → 탭 → 검색 → 입력으로 흐름이 끊겼다
 * (사용자: "따로 탭이 있으면 헷갈린다"). 모달은 어느 시험을 다루는지가 제목에 박혀 있고,
 * 이미 들어간 회차가 폼 위에 보여서 "어디까지 넣었더라"를 다시 찾지 않는다.
 *
 * 폼은 마지막 회차에서 이어진다 — 같은 해면 회차+1, 해가 지났으면 새 해 1회. 구분(필기/실기)은
 * 마지막 것을 따른다. 매니저가 바꿀 건 날짜뿐인 경우가 대부분이다.
 */
const EMPTY = {
  year: new Date().getFullYear(),
  round: 1,
  examType: 'WRITTEN',
  regStartAt: '',
  regEndAt: '',
  examStartDate: '',
  examEndDate: '',
  resultDate: '',
  sourceUrl: '',
};

export default function ScheduleEditorModal({ certificateId, name, onClose, onSaved }: {
  certificateId: number;
  name: string;
  onClose: () => void;
  /** 저장·취소로 일정이 바뀌었을 때 — 부모가 목록을 다시 읽는다 */
  onSaved: () => void;
}) {
  const [rows, setRows] = useState<AdminScheduleRow[] | null>(null);
  const [form, setForm] = useState({ ...EMPTY });
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [dirty, setDirty] = useState(false);

  useEffect(() => {
    let alive = true;
    examApi.adminSchedules(certificateId)
      .then((list) => {
        if (!alive) return;
        setRows(list);
        setForm(nextRound(list));
      })
      .catch((e: Error) => alive && setErr(e.message));
    return () => { alive = false; };
  }, [certificateId]);

  // Esc 로 닫기 — 모달의 기본 예의
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') close(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  function close() {
    if (dirty) onSaved();
    onClose();
  }

  async function reload() {
    const list = await examApi.adminSchedules(certificateId);
    setRows(list);
    setForm(nextRound(list));
  }

  async function save() {
    setBusy(true); setErr(null); setMsg(null);
    try {
      await examApi.adminUpsertSchedule({
        certificateId,
        year: Number(form.year),
        round: Number(form.round),
        examType: form.examType,
        // 빈 문자열은 보내지 않는다 — 서버에서 null 로 들어가야 "미정"이 된다
        regStartAt: form.regStartAt || null,
        regEndAt: form.regEndAt || null,
        examStartDate: form.examStartDate || null,
        examEndDate: form.examEndDate || form.examStartDate || null,
        resultDate: form.resultDate || null,
        sourceUrl: form.sourceUrl || null,
      });
      setMsg('저장했습니다. 알림 예약도 다시 만들었습니다.');
      setDirty(true);
      await reload();
    } catch (e) {
      setErr(e instanceof Error ? e.message : '저장하지 못했습니다.');
    } finally {
      setBusy(false);
    }
  }

  async function cancel(scheduleId: number) {
    if (!confirm('이 회차를 취소 처리할까요? (삭제가 아니라 상태만 CANCELED 로 바뀝니다)')) return;
    try {
      await examApi.adminCancelSchedule(scheduleId);
      setDirty(true);
      await reload();
    } catch (e) {
      setErr(e instanceof Error ? e.message : '취소하지 못했습니다.');
    }
  }

  const set = (k: keyof typeof EMPTY) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  return (
    <div className="k-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) close(); }}>
      <div className="k-modal schedule-modal" role="dialog" aria-modal="true" aria-labelledby="sched-title">
        <div className="k-modal__head">
          <h2 id="sched-title" className="schedule-modal__title">{name} <span className="k-muted">일정 넣기</span></h2>
          <span className="k-spacer" />
          <button className="k-btn k-btn--ghost k-btn--icon" onClick={close} aria-label="닫기"><Icon name="plus" size={18} className="rotate45" /></button>
        </div>

        <div className="k-modal__body">
          {err && <div className="k-alert k-alert--err">{err}</div>}
          {msg && <div className="k-alert k-alert--ok">{msg}</div>}

          <div className="k-section" style={{ marginBottom: 16 }}>
            <h2>등록된 일정 {rows ? `${rows.length}건` : ''}</h2>
            {rows === null ? (
              <div className="k-skeleton" style={{ height: 40 }} />
            ) : rows.length === 0 ? (
              <p className="fineprint" style={{ margin: 0 }}>아직 없습니다. 아래에 첫 회차를 넣으세요.</p>
            ) : (
              <div className="k-tablewrap">
                <table className="k-table data-table compact">
                  <thead><tr><th>회차</th><th>접수</th><th>시험</th><th></th></tr></thead>
                  <tbody>
                    {rows.map((r) => (
                      <tr key={r.id}>
                        <td className="round">{r.year}년 {r.round}회 · {r.examType === 'WRITTEN' ? '필기' : '실기'}</td>
                        <td className="nowrap">{r.regStartAt ?? '-'} ~ {r.regEndAt ?? '-'}</td>
                        <td className="nowrap">{r.examStartDate ?? '-'}</td>
                        <td><button className="link-danger" onClick={() => cancel(r.id)}>취소</button></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="k-section" style={{ marginBottom: 0 }}>
            <h2>넣기 / 고치기</h2>
            <p className="fineprint" style={{ margin: '0 0 12px' }}>
              같은 <b>연도·회차·구분</b>이 이미 있으면 덮어씁니다. 시행처 공고를 보면서 옮겨 적으세요.
            </p>
            <div className="form-grid">
              <label className="field"><span>연도</span>
                <input className="k-input" type="number" value={form.year} onChange={set('year')} /></label>
              <label className="field"><span>회차</span>
                <input className="k-input" type="number" value={form.round} onChange={set('round')} /></label>
              <label className="field"><span>구분</span>
                <select className="k-select" value={form.examType} onChange={set('examType')}>
                  <option value="WRITTEN">필기 (구분 없으면 이것)</option>
                  <option value="PRACTICAL">실기</option>
                </select></label>
              <label className="field"><span>접수 시작</span>
                <input className="k-input" type="datetime-local" value={form.regStartAt} onChange={set('regStartAt')} /></label>
              <label className="field"><span>접수 마감</span>
                <input className="k-input" type="datetime-local" value={form.regEndAt} onChange={set('regEndAt')} /></label>
              <label className="field"><span>시험일</span>
                <input className="k-input" type="date" value={form.examStartDate} onChange={set('examStartDate')} /></label>
              <label className="field"><span>시험 종료일 (여러 날일 때만)</span>
                <input className="k-input" type="date" value={form.examEndDate} onChange={set('examEndDate')} /></label>
              <label className="field"><span>발표일</span>
                <input className="k-input" type="date" value={form.resultDate} onChange={set('resultDate')} /></label>
              <label className="field wide"><span>출처 URL (공고 주소)</span>
                <input className="k-input" placeholder="https://..." value={form.sourceUrl} onChange={set('sourceUrl')} /></label>
            </div>
          </div>
        </div>

        <div className="k-modal__foot">
          <button className="k-btn k-btn--secondary" onClick={close} type="button">닫기</button>
          <span className="k-spacer" />
          <button className="k-btn k-btn--primary" onClick={save} disabled={busy} type="button">
            {busy ? '저장 중…' : '저장'}
          </button>
        </div>
      </div>
    </div>
  );
}

/** 마지막 회차에서 이어지는 다음 회차를 미리 채운다. */
function nextRound(list: AdminScheduleRow[]): typeof EMPTY {
  if (list.length === 0) return { ...EMPTY };
  const last = [...list].sort((a, b) => (a.year - b.year) || (a.round - b.round))[list.length - 1];
  const thisYear = new Date().getFullYear();
  const sameYear = last.year >= thisYear;
  return {
    ...EMPTY,
    year: sameYear ? last.year : thisYear,
    round: sameYear ? last.round + 1 : 1,
    examType: last.examType,
  };
}

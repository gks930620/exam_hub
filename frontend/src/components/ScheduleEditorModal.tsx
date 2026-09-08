import { useEffect, useRef, useState } from 'react';
import { examApi } from '../api/exams';
import { fmtAt } from '../lib/format';
import { roundLabel } from '../lib/status';
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
 *
 * 보류(PENDING_REVIEW) 회차 = 수집된 일정이 30일 넘게 움직인 것. 같은 연도·회차·구분으로
 * 공고와 대조해 저장하면 풀린다.
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

const CLOSE_CONFIRM = '입력 중인 내용이 있습니다. 닫을까요?';

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
  // dirty = 서버 데이터가 바뀌었다(부모가 다시 읽어야 한다) / touched = 폼을 손댔다(닫기 전에 묻는다)
  const [dirty, setDirty] = useState(false);
  const [touched, setTouched] = useState(false);
  // Esc 리스너는 한 번만 달고, 최신 close 를 ref 로 본다 — 매 렌더마다 붙였다 떼지 않는다
  const closeRef = useRef<() => void>(() => {});

  useEffect(() => {
    let alive = true;
    examApi.adminSchedules(certificateId)
      .then((list) => {
        if (!alive) return;
        setRows(list);
        setForm(nextRound(list));
      })
      .catch((e: Error) => { if (alive) setErr(e.message); });
    return () => { alive = false; };
  }, [certificateId]);

  // Esc 로 닫기 — 모달의 기본 예의
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') closeRef.current(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  function close() {
    if (touched && !confirm(CLOSE_CONFIRM)) return;
    if (dirty) onSaved();
    onClose();
  }
  closeRef.current = close;

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
      setTouched(false);
      await reload();
    } catch (e) {
      setErr(e instanceof Error ? e.message : '저장하지 못했습니다.');
    } finally {
      setBusy(false);
    }
  }

  async function cancel(scheduleId: number) {
    if (!confirm('이 회차를 취소 처리할까요? (삭제가 아니라 상태만 취소로 바뀝니다)')) return;
    try {
      await examApi.adminCancelSchedule(scheduleId);
      setDirty(true);
      await reload();
    } catch (e) {
      setErr(e instanceof Error ? e.message : '취소하지 못했습니다.');
    }
  }

  const set = (k: keyof typeof EMPTY) => (e: { target: { value: string } }) => {
    setTouched(true);
    setForm((f) => ({ ...f, [k]: e.target.value }));
  };

  const pending = rows?.some((r) => r.status === 'PENDING_REVIEW') ?? false;
  const conflicted = rows?.some((r) => r.sourceConflict) ?? false;

  return (
    <div className="k-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) close(); }}>
      <div className="k-modal schedule-modal" role="dialog" aria-modal="true" aria-labelledby="sched-title">
        <div className="k-modal__head">
          <h2 id="sched-title" className="schedule-modal__title">{name} <span className="k-muted">일정 넣기</span></h2>
          <span className="k-spacer" />
          <button className="k-btn k-btn--ghost k-btn--icon" onClick={close} aria-label="닫기"><Icon name="plus" size={18} className="rotate45" /></button>
        </div>

        <div className="k-modal__body">
          {err && <div className="k-alert k-alert--err" role="alert">{err}</div>}
          {msg && <div className="k-alert k-alert--ok" role="status">{msg}</div>}

          <div className="k-section" style={{ marginBottom: 16 }}>
            <h2>등록된 일정 {rows ? `${rows.length}건` : ''}</h2>
            {rows === null ? (
              <div className="k-skeleton" style={{ height: 40 }} role="status" aria-label="불러오는 중" />
            ) : rows.length === 0 ? (
              <p className="fineprint" style={{ margin: 0 }}>아직 없습니다. 아래에 첫 회차를 넣으세요.</p>
            ) : (
              <>
                <div className="k-tablewrap">
                  <table className="k-table data-table compact">
                    <thead><tr><th>회차</th><th>접수</th><th>시험</th><th></th></tr></thead>
                    <tbody>
                      {rows.map((r) => (
                        <tr key={r.id} className={r.status === 'CANCELED' ? 'is-canceled' : undefined}>
                          <td className="round">
                            {roundLabel(r.year, r.round)} · {r.examType === 'WRITTEN' ? '필기' : '실기'}
                            {r.status === 'PENDING_REVIEW' && <> <span className="k-badge k-badge--warn">보류</span></>}
                            {r.status === 'CANCELED' && <> <span className="k-badge k-badge--err">취소됨</span></>}
                            {r.sourceConflict && <> <span className="k-badge k-badge--err">수집값 다름</span></>}
                          </td>
                          <td className="nowrap">{fmtAt(r.regStartAt)} ~ {fmtAt(r.regEndAt)}</td>
                          <td className="nowrap">
                            {r.examStartDate ?? '-'}
                            {/* 매니저 값은 그대로 두고, 시행처가 뭐라고 하는지만 알려 준다 */}
                            {r.sourceConflict && (
                              <div className="fineprint" style={{ margin: '2px 0 0' }}>
                                시행처: {r.sourceConflict}
                              </div>
                            )}
                          </td>
                          <td>
                            {r.status !== 'CANCELED' && (
                              <button className="link-danger" onClick={() => cancel(r.id)}>취소</button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                {conflicted && (
                  <p className="fineprint" style={{ margin: '10px 0 0' }}>
                    <b>수집값 다름</b>은 매니저가 넣은 값을 자동 수집이 덮지 않은 것입니다 — 사람이 넣은 값이 이깁니다.
                    공고를 보고 <b>맞는 쪽으로 다시 저장</b>하면 표시가 사라집니다(수집값이 맞다면 그 날짜로 저장하세요).
                  </p>
                )}
                {pending && (
                  <p className="fineprint" style={{ margin: '10px 0 0' }}>
                    <b>보류 회차</b>는 수집된 일정이 30일 넘게 움직인 것입니다. 공고와 대조해 저장하면 풀립니다
                    (같은 연도·회차·구분으로 넣으면 덮어씁니다).
                  </p>
                )}
              </>
            )}
          </div>

          <div className="k-section" style={{ marginBottom: 0 }}>
            <h2>넣기 / 고치기</h2>
            <p className="fineprint" style={{ margin: '0 0 12px' }}>
              같은 <b>연도·회차·구분</b>이 이미 있으면 덮어씁니다. 시행처 공고를 보면서 옮겨 적으세요.
            </p>
            <div className="form-grid">
              <label className="field"><span>연도</span>
                <input className="k-input" type="number" value={form.year} onChange={set('year')} autoFocus /></label>
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
function nextRound(rows: AdminScheduleRow[]): typeof EMPTY {
  // 취소한 회차는 "어디까지 넣었나"의 기준이 아니다 — 그걸 세면 다음 회차 번호를 건너뛰고,
  // 같은 번호를 제안하면 취소한 회차를 되살린다(2026-09-04).
  const list = rows.filter((r) => r.status !== 'CANCELED');
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

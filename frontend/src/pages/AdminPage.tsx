import { useEffect, useState } from 'react';
import { examApi } from '../api/exams';
import AdminDataMap from './AdminDataMap';
import AdminOverview from './AdminOverview';
import AdminLifecycle from './AdminLifecycle';
import { useAuth } from '../auth';
import type { AdminScheduleRow, CertItem } from '../api/types';

// 운영자 수기 일정 입력 (설계 07 §4-2 2단계).
// 공무원·JLPT·DELE 처럼 연 1~2회짜리는 스크래퍼보다 사람이 넣는 게 싸다.
// 사용법은 운영/03_일정_직접입력.md.
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

export default function AdminPage() {
  const { me } = useAuth();
  const [q, setQ] = useState('');
  const [results, setResults] = useState<CertItem[]>([]);
  const [selected, setSelected] = useState<CertItem | null>(null);
  const [rows, setRows] = useState<AdminScheduleRow[]>([]);
  const [form, setForm] = useState({ ...EMPTY });
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (q.trim().length < 2) { setResults([]); return; }
    const t = window.setTimeout(() => {
      examApi.browse({ query: q.trim(), size: 10 })
        .then((r) => setResults(r.items))
        .catch(() => setResults([]));
    }, 300);
    return () => window.clearTimeout(t);
  }, [q]);

  /**
   * 현황 목록에서 바로 고르기. 검색 결과와 달리 이름만 알면 되므로 최소 정보로 만든다.
   * 폼까지 스크롤해 주지 않으면 "눌렀는데 아무 일도 안 난다"고 느낀다.
   */
  function pickFromOverview(id: number, name: string) {
    setSelected({ id, name } as CertItem);
    setQ(name);
    setResults([]);
    void reload(id);
    window.setTimeout(
      () => document.getElementById('schedule-form')?.scrollIntoView({ behavior: 'smooth', block: 'start' }),
      50);
  }

  function pick(c: CertItem) {
    setSelected(c);
    setResults([]);
    setQ(c.name);
    void reload(c.id);
  }

  async function reload(certId: number) {
    try {
      setRows(await examApi.adminSchedules(certId));
    } catch (e) {
      setErr(e instanceof Error ? e.message : '일정을 불러오지 못했습니다.');
    }
  }

  async function save() {
    if (!selected) return;
    setBusy(true);
    setErr(null);
    setMsg(null);
    try {
      await examApi.adminUpsertSchedule({
        certificateId: selected.id,
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
      setForm({ ...EMPTY, year: form.year });
      await reload(selected.id);
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
      if (selected) await reload(selected.id);
    } catch (e) {
      setErr(e instanceof Error ? e.message : '취소하지 못했습니다.');
    }
  }

  if (me && me.role !== 'ADMIN') {
    return (
      <div className="state">
        <span className="big">운영자 전용 화면입니다</span>
        이 계정에는 권한이 없습니다.
      </div>
    );
  }

  const set = (k: keyof typeof EMPTY) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  return (
    <>
      <div className="page-header">
        <div className="page-avatar" aria-hidden="true">✎</div>
        <div>
          <h1>운영 화면</h1>
          <p>시험 일정을 확인하고, 자동으로 안 들어오는 것을 직접 넣습니다.</p>
        </div>
      </div>

      {/* 무엇을 넣어야 하는지부터 — 입력칸만 있으면 매니저는 뭘 할지 모른다 */}
      <div className="section-head"><h2>데이터 지도 — 뭐가 자동이고 뭐가 수기인가</h2></div>
      <AdminDataMap />

      {/* 무엇이 비어 있는지 — 이게 없으면 매니저는 기억나는 시험만 채우게 된다 */}
      <div className="section-head" style={{ marginTop: 34 }}>
        <h2>시험 일정 현황 — 무엇을 채워야 하나</h2>
      </div>
      <AdminOverview onPick={pickFromOverview} />

      {/* 사라진 시험이 어디로 갔는지 — 지우기만 하면 "왜 없냐"에 답할 수 없다 */}
      <div className="section-head" style={{ marginTop: 34 }}><h2>시험 변천사 — 폐지·개칭</h2></div>
      <AdminLifecycle />

      <div className="section-head" style={{ marginTop: 34 }} id="schedule-form"><h2>일정 직접 넣기</h2></div>
      <div className="notice info" style={{ marginBottom: 18 }}>
        <b>넣기 전에 확인하세요.</b> 위 지도에서 <b>자동 수집</b> 으로 표시된 시험은 손대지 마세요 —
        다음 수집 때 덮어써집니다. <b>수기 입력</b> 으로 표시된 것만 넣습니다.<br />
        아래 순서대로 하면 됩니다: ① 시험을 찾아 고르고 → ② 이미 등록된 회차를 확인하고 →
        ③ 원본 사이트의 공고를 보면서 날짜를 옮겨 적습니다.
      </div>

      {err && <div className="notice error">{err}</div>}
      {msg && <div className="notice ok">{msg}</div>}

      <div className="section-head"><h2>① 시험 고르기</h2></div>
      <div className="panel" style={{ padding: 20 }}>
        <div className="searchbar" style={{ marginBottom: 0 }}>
          <span className="ico" aria-hidden="true">⌕</span>
          <input className="input" placeholder="시험명 검색 (2자 이상)"
                 value={q} onChange={(e) => { setQ(e.target.value); setSelected(null); }} />
        </div>
        {results.length > 0 && (
          <div className="pick-list">
            {results.map((c) => (
              <button key={c.id} className="pick" onClick={() => pick(c)} type="button">
                <b>{c.name}</b>
                <span>{[c.category, c.agency].filter(Boolean).join(' · ')}</span>
              </button>
            ))}
          </div>
        )}
        {selected && (
          <p className="fineprint" style={{ marginTop: 12 }}>
            선택됨: <b>{selected.name}</b> ({selected.agency})
          </p>
        )}
      </div>

      {selected && (
        <>
          <div className="section-head">
            <h2>② 등록된 일정</h2>
            <span className="more">{rows.length}건</span>
          </div>
          {rows.length === 0 ? (
            <div className="state">아직 등록된 일정이 없습니다.</div>
          ) : (
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>회차</th><th>접수</th><th>시험</th><th>발표</th><th>출처</th><th></th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r) => (
                    <tr key={r.id}>
                      <td className="round">{r.year}년 {r.round}회 · {r.examType === 'WRITTEN' ? '필기' : '실기'}</td>
                      <td className="nowrap">{r.regStartAt ?? '-'} ~ {r.regEndAt ?? '-'}</td>
                      <td className="nowrap">{r.examStartDate ?? '-'}</td>
                      <td className="nowrap">{r.resultDate ?? '-'}</td>
                      <td className="nowrap">{r.sourceUrl ? '있음' : '-'}</td>
                      <td><button className="link-danger" onClick={() => cancel(r.id)}>취소</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="section-head"><h2>③ 일정 넣기 / 고치기</h2></div>
          <div className="panel" style={{ padding: 20 }}>
            <p className="fineprint" style={{ margin: '0 0 16px' }}>
              같은 <b>연도·회차·구분</b>이 이미 있으면 덮어씁니다 — 일정이 바뀌었을 때 그대로 다시 넣으면 됩니다.
            </p>

            <div className="form-grid">
              <label className="field"><span>연도</span>
                <input className="input" type="number" value={form.year} onChange={set('year')} /></label>
              <label className="field"><span>회차</span>
                <input className="input" type="number" value={form.round} onChange={set('round')} /></label>
              <label className="field"><span>구분</span>
                <select className="input" value={form.examType} onChange={set('examType')}>
                  <option value="WRITTEN">필기 (구분 없으면 이것)</option>
                  <option value="PRACTICAL">실기</option>
                </select></label>

              <label className="field"><span>접수 시작 (시각까지)</span>
                <input className="input" type="datetime-local" value={form.regStartAt} onChange={set('regStartAt')} /></label>
              <label className="field"><span>접수 마감 (시각까지)</span>
                <input className="input" type="datetime-local" value={form.regEndAt} onChange={set('regEndAt')} /></label>
              <label className="field"><span>시험일</span>
                <input className="input" type="date" value={form.examStartDate} onChange={set('examStartDate')} /></label>
              <label className="field"><span>시험 종료일 (여러 날일 때만)</span>
                <input className="input" type="date" value={form.examEndDate} onChange={set('examEndDate')} /></label>
              <label className="field"><span>발표일</span>
                <input className="input" type="date" value={form.resultDate} onChange={set('resultDate')} /></label>
              <label className="field wide"><span>출처 URL (공고 주소)</span>
                <input className="input" placeholder="https://..." value={form.sourceUrl} onChange={set('sourceUrl')} /></label>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 8 }}>
              <button className="btn primary" onClick={save} disabled={busy} type="button">
                {busy ? '저장 중…' : '저장'}
              </button>
            </div>
          </div>
        </>
      )}
    </>
  );
}

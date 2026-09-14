import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { examApi } from '../api/exams';
import { weekdayOf } from '../lib/format';
import type { CalendarEvent } from '../api/types';
import Icon from '../components/Icon';

// 월간 캘린더: 내 시험의 접수/시험/발표 이벤트를 날짜순으로.
export default function CalendarPage() {
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [events, setEvents] = useState<CalendarEvent[] | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let alive = true;
    setEvents(null);
    setErr(null);
    // 실패는 실패다 — events=[] 로 두면 "이 달 일정이 없습니다"로 둔갑해 사용자가 정말 없는 줄 안다
    examApi.calendar(year, month)
      .then((r) => { if (alive) setEvents(r.events); })
      .catch((e: Error) => { if (alive) setErr(e.message); });
    return () => { alive = false; };
  }, [year, month, attempt]);

  function move(delta: number) {
    let m = month + delta, y = year;
    if (m < 1) { m = 12; y--; } else if (m > 12) { m = 1; y++; }
    setMonth(m); setYear(y);
  }

  return (
    <>
      <div className="page-header">
        <div className="page-avatar" aria-hidden="true"><Icon name="calendar" size={22} /></div>
        <div className="page-header__text">
          <h1>캘린더</h1>
          <p>등록한 시험의 접수·시험·발표 일정입니다.</p>
        </div>
      </div>

      <div className="month-bar">
        <button className="k-btn k-btn--secondary" onClick={() => move(-1)}>‹ 이전</button>
        <strong>{year}년 {month}월</strong>
        <button className="k-btn k-btn--secondary" onClick={() => move(1)}>다음 ›</button>
      </div>

      {err ? (
        <div className="k-alert k-alert--err" role="alert">
          <span>{err}</span>
          <button className="k-btn k-btn--secondary k-btn--sm alert-action" onClick={() => setAttempt((a) => a + 1)}>
            다시 시도
          </button>
        </div>
      ) : events === null ? (
        // 첫 로딩은 스켈레톤 — 스피너 대신 자리를 미리 잡아 목록이 뜰 때 화면이 튀지 않게 한다(설계 05 §8)
        <div className="day-list" role="status" aria-label="일정 불러오는 중">
          {Array.from({ length: 4 }, (_, i) => <div className="k-skeleton row-skeleton" key={i} />)}
        </div>
      ) : events.length === 0 ? (
        <div className="k-empty state">
          <span className="big">이 달 일정이 없습니다</span>
          다른 달로 이동하거나 관심 시험을 더 등록해 보세요.
        </div>
      ) : (
        <>
          <div className="section-head">
            <h2>{month}월 일정</h2>
            <span className="more">{events.length}건</span>
          </div>
          <div className="day-list">
            {events.map((e, i) => (
              <Link to={`/cert/${e.certificateId}`} className="k-card day-row" key={`${e.date}-${i}`}>
                <span className="date">
                  <b>{Number(e.date.slice(8, 10))}</b>
                  <span>{weekdayOf(e.date)}</span>
                </span>
                <span className="body">
                  <h3>{e.name}</h3>
                  <span className="k-badge">{e.label}</span>
                </span>
              </Link>
            ))}
          </div>
        </>
      )}
    </>
  );
}

import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { examApi } from '../api/exams';
import type { FavoriteCard, FavoriteListResponse } from '../api/types';

// 홈: 내가 등록한 시험의 D-day. 백엔드가 배지 우선순위(접수중 → 접수 예정 → 시험 예정)로
// 정렬해 주므로 첫 카드가 가장 급한 건이다 → 그 하나만 히어로로 올린다(Halo 원칙 ②).
const dday = (n: number) => (n >= 0 ? `D-${n}` : `D+${-n}`);

export default function HomePage() {
  const [data, setData] = useState<FavoriteListResponse | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    examApi.favorites().then(setData).catch((e: Error) => setErr(e.message));
  }, []);

  if (err) return <div className="notice error">{err}</div>;
  if (!data) return <div className="state">불러오는 중…</div>;

  if (data.items.length === 0) {
    return (
      <>
        <div className="page-header">
          <div className="page-avatar" aria-hidden="true">◎</div>
          <div>
            <h1>내 시험</h1>
            <p>관심 시험을 등록하면 접수 시작·마감과 시험일을 챙겨 드립니다.</p>
          </div>
        </div>
        <div className="state">
          <span className="big">아직 등록한 시험이 없어요</span>
          시험을 등록하면 여기에 D-day가 표시됩니다.
          <div style={{ marginTop: 18 }}>
            <Link to="/search" className="btn primary lg">시험 찾으러 가기</Link>
          </div>
        </div>
      </>
    );
  }

  const [lead, ...rest] = data.items;

  return (
    <>
      <section className="hero">
        <div className="hero-dday">
          <span className="num">
            {lead.dday >= 0 ? lead.dday : `+${-lead.dday}`}
            <small>일</small>
          </span>
          <span className="what">
            <strong>{lead.name}</strong>
            <span>{lead.badgeLabel} · {lead.eventLabel} · {lead.eventAt}</span>
          </span>
        </div>
        <div className="hero-actions">
          <Link to={`/cert/${lead.certificateId}`} className="btn">일정 자세히 보기</Link>
        </div>
      </section>

      {rest.length > 0 && (
        <>
          <div className="section-head">
            <h2>등록한 다른 시험</h2>
            <span className="more">{data.items.length}개</span>
          </div>
          <div className="card-grid">
            {rest.map((c) => <ExamCard key={c.certificateId} card={c} />)}
          </div>
        </>
      )}

      <p className="fineprint">
        본 서비스는 공공데이터·각 시행처 정보를 모은 참고용입니다. 최종 일정은 각 시행처에서 확인하세요.
      </p>
    </>
  );
}

function ExamCard({ card }: { card: FavoriteCard }) {
  return (
    <Link to={`/cert/${card.certificateId}`} className="card exam-card">
      <div className="top">
        <h3>{card.name}</h3>
        <span className={`dday${card.dday <= 7 ? ' soon' : ''}`}>{dday(card.dday)}</span>
      </div>
      <div className="foot">
        <span className={`badge${card.badge === 'REG_OPEN' ? ' open' : ''}`}>{card.badgeLabel}</span>
        <span className="when">{card.eventLabel} · {card.eventAt}</span>
      </div>
    </Link>
  );
}

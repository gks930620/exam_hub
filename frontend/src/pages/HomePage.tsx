import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { examApi } from '../api/exams';
import type { FavoriteCard, FavoriteListResponse } from '../api/types';
import Icon from '../components/Icon';

// 홈: 내가 등록한 시험의 D-day. 백엔드가 배지 우선순위(접수중 → 접수 예정 → 시험 예정)로
// 정렬해 주므로 첫 카드가 가장 급한 건이다 → 그 하나만 히어로로 올린다(Halo 원칙 ②).
const dday = (n: number) => (n >= 0 ? `D-${n}` : `D+${-n}`);

export default function HomePage() {
  const [data, setData] = useState<FavoriteListResponse | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    examApi.favorites().then(setData).catch((e: Error) => setErr(e.message));
  }, []);

  if (err) return <div className="k-alert k-alert--err">{err}</div>;
  if (!data) return <div className="k-empty state">불러오는 중…</div>;

  if (data.items.length === 0) {
    return (
      <>
        <div className="page-header">
          <div className="page-avatar" aria-hidden="true"><Icon name="bookmark" size={22} /></div>
          <div>
            <h1>내 시험</h1>
            <p>관심 시험을 등록하면 접수 시작·마감과 시험일을 챙겨 드립니다.</p>
          </div>
        </div>
        <div className="k-empty state">
          <span className="big">아직 등록한 시험이 없어요</span>
          시험을 등록하면 여기에 D-day가 표시됩니다.
          <div style={{ marginTop: 18 }}>
            <Link to="/" className="k-btn k-btn--primary k-btn--lg">시험 찾으러 가기</Link>
          </div>
        </div>
      </>
    );
  }

  // 백엔드가 급한 순으로 준다 — 첫 항목이 히어로다. 지표는 서버를 더 부르지 않고 여기서 센다.
  const lead = data.items[0];
  // 접수 중·이번 주 같은 지표는 뺐다 — 지금은 헷갈린다(사용자 결정 2026-09-02). 익숙해지면 다시.

  return (
    <>
      {/* 킷 데모 구조: 히어로에 큰 제목 하나 + 가장 급한 일 + 행동 */}
      <section className="k-hero my-hero">
        <h1>{`등록한 시험 ${data.items.length}개`}</h1>
        <p>
          <strong>{lead.name}</strong> — {lead.eventLabel} {fmtAt(lead.eventAt)}
          {lead.dday >= 0 ? ` (D-${lead.dday})` : ` (D+${-lead.dday})`}
        </p>
        <div className="hero-actions">
          <Link to={`/cert/${lead.certificateId}`} className="k-btn k-btn--primary">일정 자세히 보기</Link>
          <Link to="/" className="k-btn k-btn--secondary">시험 더 찾기</Link>
        </div>
      </section>

      <section className="k-section">
        <h2>내 시험 현황</h2>
        <div className="k-stats">
          <div className="k-stat">
            <div className="k-stat__label">등록한 시험</div>
            <div className="k-stat__value">{data.items.length}</div>
          </div>
          <div className="k-stat k-stat--point">
            <div className="k-stat__label">가장 가까운 일정</div>
            <div className="k-stat__value">{dday(lead.dday)}</div>
          </div>
        </div>
      </section>

      <div className="k-section list-head">
        <h2>등록한 시험 <span className="more">{data.items.length}개</span></h2>
      </div>
      <div className="card-grid">
        {data.items.map((c) => <ExamCard key={c.certificateId} card={c} />)}
      </div>

      <p className="fineprint">
        본 서비스는 공공데이터·각 시행처 정보를 모은 참고용입니다. 최종 일정은 각 시행처에서 확인하세요.
      </p>
    </>
  );
}

function ExamCard({ card }: { card: FavoriteCard }) {
  return (
    <Link to={`/cert/${card.certificateId}`} className="k-card k-card--hover exam-card">
      <div className="top">
        <h3>{card.name}</h3>
        <span className={`dday${card.dday <= 7 ? ' soon' : ''}`}>{dday(card.dday)}</span>
      </div>
      <div className="foot">
        <span className={`k-badge${card.badge === 'REG_OPEN' ? ' k-badge--ok' : ''}`}>{card.badgeLabel}</span>
        <span className="when">{card.eventLabel} · {fmtAt(card.eventAt)}</span>
      </div>
    </Link>
  );
}

/** 2026-09-21T10:00 → 2026-09-21 10:00 — ISO 의 T 는 사람이 읽는 표기가 아니다 */
function fmtAt(iso: string): string {
  return iso.replace("T", " ").slice(0, 16);
}

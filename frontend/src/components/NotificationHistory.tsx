import { useEffect, useState } from 'react';
import { examApi } from '../api/exams';
import { fmtAt } from '../lib/format';
import type { NotificationHistoryItem } from '../api/types';

/**
 * <b>나한테 뭘 보냈다는 건지</b> — 최근 알림 목록.
 *
 * <p>발송 기록은 쌓이지만 매니저 집계에만 쓰였다. 사용자는 "왔다는데 나는 못 받았다"를
 * 확인할 길이 없었다. 위의 확인 메일 버튼과 짝이다 — 그 버튼은 "지금 보내면 오나",
 * 이 목록은 "그동안 뭘 보냈나"를 답한다. 둘이 어긋나면 <b>접수를 놓치기 전에</b> 알아챈다.
 *
 * <p>성공이 아니라 <b>도달</b>을 보여준다. 서버 로그로만 나간 건을 "보냈습니다"라고 하면
 * 사용자는 오지도 않은 메일을 기다린다.
 */
export default function NotificationHistory() {
  const [items, setItems] = useState<NotificationHistoryItem[] | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    examApi.notificationHistory()
      .then((r) => { if (alive) setItems(r.items); })
      .catch((e: unknown) => {
        if (alive) setErr(e instanceof Error ? e.message : '불러오지 못했습니다.');
      });
    return () => { alive = false; };
  }, []);

  if (err) {
    return (
      <section className="notif-history">
        <h2>최근 보낸 알림</h2>
        <p className="k-help k-help--err" role="status">{err}</p>
      </section>
    );
  }
  if (!items) return null;

  return (
    <section className="notif-history">
      <h2>최근 보낸 알림</h2>
      {items.length === 0 ? (
        <p className="k-help">
          아직 보낸 알림이 없습니다. 관심 등록한 시험의 접수가 다가오면 여기에 쌓입니다.
        </p>
      ) : (
        <ul className="notif-list">
          {items.map((n, i) => (
            <li key={i}>
              <span className="what">
                <b>{n.certificateName}</b>
                {n.round && <span className="k-dim"> {n.round}</span>}
                <span className="ev"> · {n.eventLabel}</span>
              </span>
              <span className="when">
                {fmtAt(n.sentAt)}
                {/* 도달하지 못한 건을 조용히 같은 모양으로 두면 사용자는 받은 줄 안다 */}
                {!n.delivered && <> · <span className="k-badge k-badge--warn">보내지 못함</span></>}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

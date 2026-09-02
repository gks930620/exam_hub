import { Outlet } from 'react-router-dom';
import { useAuth } from '../auth';
import AdminNav from './AdminNav';

/**
 * 운영 화면의 껍데기 — 제목과 갈래만 두고, 내용은 각 주소가 채운다.
 *
 * <p>예전에는 이 파일 하나에 수집 지도·일정 현황·변천사·입력 폼이 세로로 다 들어 있었다.
 * 각각은 말이 되는데 <b>한 화면에 분류 체계가 셋</b>이라(수집 방식 4갈래 · 일정 상태 5갈래 ·
 * 변천사) 어느 숫자가 무엇의 숫자인지 알 수 없었다. 한 번에 한 가지만 본다.
 */
export default function AdminPage() {
  const { me } = useAuth();

  if (me && me.role !== 'ADMIN') {
    return (
      <div className="k-empty state">
        <span className="big">운영자 전용 화면입니다</span>
        이 계정에는 권한이 없습니다.
      </div>
    );
  }

  return (
    <>
      <div className="page-header">
        <div className="page-avatar" aria-hidden="true">✎</div>
        <div>
          <h1>운영 화면</h1>
          <p>시험 일정을 확인하고, 자동으로 안 들어오는 것을 직접 넣습니다.</p>
        </div>
      </div>

      <AdminNav />
      <Outlet />
    </>
  );
}

import { NavLink } from 'react-router-dom';
import Icon from '../components/Icon';

/**
 * 운영 화면의 갈래.
 *
 * <p>예전에는 네 가지가 한 페이지에 세로로 쌓여 있었다 — 수집 지도(4갈래 아코디언),
 * 일정 현황(5갈래 탭), 변천사, 입력 폼. 각각은 말이 되는데 <b>한 화면에 분류 체계가 셋</b>이라
 * 어느 숫자가 무엇의 숫자인지 알 수 없었다("진짜 1도 눈에 안 들어와").
 *
 * <p>그래서 주소로 나눴다. 한 번에 한 가지만 본다.
 */
const ITEMS = [
  { to: '/admin', end: true, icon: 'list', label: '할 일', hint: '무엇을 채워야 하나' },
  { to: '/admin/sources', end: false, icon: 'layers', label: '수집 지도', hint: '뭐가 자동인가' },
  { to: '/admin/lifecycle', end: false, icon: 'clock', label: '변천사', hint: '폐지·개칭' },
];

export default function AdminNav() {
  return (
    <nav className="admin-nav" aria-label="운영 화면">
      {ITEMS.map((it) => (
        <NavLink key={it.to} to={it.to} end={it.end}
                 className={({ isActive }) => `admin-nav-item${isActive ? ' active' : ''}`}>
          <span className="ico" aria-hidden="true"><Icon name={it.icon} size={18} /></span>
          <span className="txt">
            <b>{it.label}</b>
            <small>{it.hint}</small>
          </span>
        </NavLink>
      ))}
    </nav>
  );
}

import { useEffect, useState, type ReactNode } from 'react';
import { NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import HomePage from './pages/HomePage';
import SearchPage from './pages/SearchPage';
import DetailPage from './pages/DetailPage';
import CalendarPage from './pages/CalendarPage';
import SettingsPage from './pages/SettingsPage';
import LoginPage from './pages/LoginPage';
import OAuthCallbackPage from './pages/OAuthCallbackPage';
import MePage from './pages/MePage';
import CommunityPage from './pages/CommunityPage';
import PostDetailPage from './pages/PostDetailPage';
import PostEditorPage from './pages/PostEditorPage';
import AdminPage from './pages/AdminPage';
import ManagerLoginPage from './pages/ManagerLoginPage';
import { useAuth } from './auth';
import { applyTheme, isDark, readTheme, type ThemeSetting } from './theme';
import Avatar from './components/Avatar';

// Halo 골격: 유리 헤더(원칙 ③ — 유리는 여기 한 곳만) + 불투명 사이드바(원칙 ①) + 본문.
const NAV = [
  { to: '/', label: '내 시험', icon: '◎', end: true },
  { to: '/calendar', label: '캘린더', icon: '▤', end: false },
  { to: '/search', label: '시험 찾기', icon: '⌕', end: false },
  { to: '/community', label: '커뮤니티', icon: '💬', end: false },
  { to: '/settings', label: '알림 설정', icon: '⚙', end: false },
];

/** 로그인이 필요한 화면 — 비로그인이면 로그인으로 보내고, 돌아올 곳을 기억한다. */
/**
 * 매니저 전용 구간. 일반 회원이 URL 을 직접 쳐서 들어오면 여기서 막는다.
 * 서버도 403 을 주지만, 화면이 먼저 막아야 "왜 안 되지" 하고 헤매지 않는다.
 */
function RequireAdmin({ children }: { children: ReactNode }) {
  const { me, loading } = useAuth();
  if (loading) return <div className="state">불러오는 중…</div>;
  if (!me) return <Navigate to="/manager/login" replace />;
  if (me.role !== 'ADMIN') {
    return (
      <div className="state">
        <span className="big">매니저만 볼 수 있는 화면입니다</span>
        운영자 계정으로 로그인해야 합니다. <a href="/manager/login">매니저 로그인</a>
      </div>
    );
  }
  return <>{children}</>;
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { me, loading } = useAuth();
  const location = useLocation();
  if (loading) return <div className="state">불러오는 중…</div>;
  if (!me) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <>{children}</>;
}

export default function App() {
  const [theme, setTheme] = useState<ThemeSetting>(readTheme);
  const [drawer, setDrawer] = useState(false);
  const location = useLocation();
  const { me, loading } = useAuth();

  useEffect(() => { applyTheme(theme); }, [theme]);
  useEffect(() => { setDrawer(false); }, [location.pathname]);

  const dark = isDark(theme);

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="header-container">
          <button className="icon-btn only-mobile" onClick={() => setDrawer((v) => !v)}
                  aria-label="메뉴 열기" aria-expanded={drawer}>☰</button>
          <NavLink to="/" className="header-logo">모든시험한번에보기</NavLink>
          <span className="hdr-badge only-desktop">시험일정 · D-day · 접수 알림</span>
          <div className="hdr-tools">
            <button className="icon-btn"
                    onClick={() => setTheme(dark ? 'light' : 'dark')}
                    aria-label={dark ? '라이트 모드로 전환' : '다크 모드로 전환'}
                    title={dark ? '라이트 모드로 전환' : '다크 모드로 전환'}>
              {dark ? '☀' : '☾'}
            </button>
            {loading ? null : me ? (
              <NavLink to="/me" className="who">
                <Avatar src={me.profileImage} nickname={me.nickname} />
                <span className="only-desktop">{me.nickname}</span>
              </NavLink>
            ) : (
              <NavLink to="/login" className="btn primary">로그인</NavLink>
            )}
          </div>
        </div>
      </header>

      <div className="app-body">
        <nav className={`sidebar${drawer ? ' open' : ''}`} aria-label="주요 화면">
          <div className="side-heading">시험 관리</div>
          {NAV.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end}
              className={({ isActive }) => `side-item${isActive ? ' active' : ''}`}>
              <span aria-hidden="true">{n.icon}</span>{n.label}
            </NavLink>
          ))}
          {me?.role === 'ADMIN' && (
            <>
              <div className="side-heading">운영</div>
              <NavLink to="/admin"
                className={({ isActive }) => `side-item${isActive ? ' active' : ''}`}>
                <span aria-hidden="true">✎</span>수기 일정 입력
              </NavLink>
            </>
          )}
        </nav>

        {drawer && <button className="scrim" onClick={() => setDrawer(false)} aria-label="메뉴 닫기" />}

        <main className="content-area">
          <Routes>
            {/* 공개 */}
            <Route path="/search" element={<SearchPage />} />
            <Route path="/cert/:id" element={<DetailPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
            <Route path="/community" element={<CommunityPage />} />
            <Route path="/community/posts/:id" element={<PostDetailPage />} />

            {/* 로그인 필요 */}
            <Route path="/" element={<RequireAuth><HomePage /></RequireAuth>} />
            <Route path="/calendar" element={<RequireAuth><CalendarPage /></RequireAuth>} />
            <Route path="/settings" element={<RequireAuth><SettingsPage /></RequireAuth>} />
            <Route path="/me" element={<RequireAuth><MePage /></RequireAuth>} />
            <Route path="/community/write" element={<RequireAuth><PostEditorPage /></RequireAuth>} />
            <Route path="/community/posts/:id/edit" element={<RequireAuth><PostEditorPage /></RequireAuth>} />
            <Route path="/manager/login" element={<ManagerLoginPage />} />
            <Route path="/admin" element={<RequireAdmin><AdminPage /></RequireAdmin>} />

            <Route path="*" element={
              <div className="state">
                <span className="big">페이지를 찾을 수 없습니다</span>
                주소를 확인하거나 왼쪽 메뉴에서 이동하세요.
              </div>
            } />
          </Routes>
        </main>
      </div>
    </div>
  );
}

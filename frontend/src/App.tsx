import { useEffect, useState, type ReactNode } from 'react';
import { Link, NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom';
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
import AdminTodo from './pages/AdminTodo';
import AdminSources from './pages/AdminSources';
import AdminLifecyclePage from './pages/AdminLifecyclePage';
import AdminCollectHealth from './pages/AdminCollectHealth';
import ManagerLoginPage from './pages/ManagerLoginPage';
import { useAuth } from './auth';
import { applyTheme, isDark, readTheme, type ThemeSetting } from './theme';
import AccountMenu from './components/AccountMenu';
import Icon from './components/Icon';

// Lets 골격: 상단바(.k-topnav) + 본문(.k-main). 메뉴는 헤더 안에 있다(사이드바 없음 — 사용자 지시).
//
// 홈(/)은 '시험 찾기'다. 처음 온 사람이 가장 먼저 할 일이 그것이고, 비로그인도 볼 수 있다.
// 예전엔 홈이 '내 시험'이라 로그인부터 요구했는데, 아직 관심 시험이 없는 사람에게는 빈 화면이었다.
const NAV = [
  { to: '/', label: '시험 찾기', end: true },
  { to: '/my', label: '내 시험', end: false },
  { to: '/calendar', label: '캘린더', end: false },
  { to: '/community', label: '커뮤니티', end: false },
  { to: '/settings', label: '알림 설정', end: false },
];

/**
 * 매니저 전용 구간. 일반 회원이 URL 을 직접 쳐서 들어오면 여기서 막는다.
 * 서버도 403 을 주지만, 화면이 먼저 막아야 "왜 안 되지" 하고 헤매지 않는다.
 */
function RequireAdmin({ children }: { children: ReactNode }) {
  const { me, loading } = useAuth();
  if (loading) return <div className="k-empty state" role="status">불러오는 중…</div>;
  if (!me) return <Navigate to="/manager/login" replace />;
  if (me.role !== 'ADMIN') {
    return (
      <div className="k-empty state">
        <span className="big">매니저만 볼 수 있는 화면입니다</span>
        운영자 계정으로 로그인해야 합니다. <Link to="/manager/login">매니저 로그인</Link>
      </div>
    );
  }
  return <>{children}</>;
}

/**
 * 로그인이 필요한 화면 — 비로그인이면 로그인으로 보내고, 돌아올 곳을 기억한다.
 * 돌아올 곳은 검색어까지(pathname + search) — useRequireLogin 과 같은 규칙.
 */
function RequireAuth({ children }: { children: ReactNode }) {
  const { me, loading } = useAuth();
  const location = useLocation();
  if (loading) return <div className="k-empty state" role="status">불러오는 중…</div>;
  if (!me) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <>{children}</>;
}

export default function App() {
  const [theme, setTheme] = useState<ThemeSetting>(readTheme);
  const { me, loading, logout } = useAuth();

  useEffect(() => { applyTheme(theme); }, [theme]);

  const dark = isDark(theme);

  return (
    <div className="k-shell">
      <header className="k-topnav app-header">
        <NavLink to="/" className="k-topnav__logo">모든시험한번에보기</NavLink>

        {/* 활성 표시는 aria-current — NavLink 가 자동으로 붙여 주고, 킷이 그걸로 밑줄을 긋는다 */}
        <nav className="top-nav" aria-label="주요 화면">
          {NAV.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end} className="nav-item">
              {n.label}
            </NavLink>
          ))}
          {/* 운영 화면은 할 일·수집 지도·변천사 세 갈래 — '수기 일정 입력'은 그중 하나를 부르던 옛 이름 */}
          {me?.role === 'ADMIN' && (
            <NavLink to="/admin" className="nav-item">운영</NavLink>
          )}
        </nav>
        <span className="k-spacer" />
        <div className="hdr-tools">
            <button className="k-btn k-btn--ghost k-btn--icon icon-btn"
                    onClick={() => setTheme(dark ? 'light' : 'dark')}
                    aria-label={dark ? '라이트 모드로 전환' : '다크 모드로 전환'}
                    title={dark ? '라이트 모드로 전환' : '다크 모드로 전환'}>
              <Icon name={dark ? 'sun' : 'moon'} size={18} />
            </button>
            {loading ? null : me ? (
              // 계정 자리는 사람 아이콘 하나 — 누르면 내 정보·로그아웃이 내려온다(AccountMenu)
              <AccountMenu me={me} onLogout={logout} />
            ) : (
              <NavLink to="/login" className="k-btn k-btn--primary k-btn--sm">로그인</NavLink>
            )}
          </div>
      </header>

      <main className="k-main">
          <Routes>
            {/* 공개 */}
            <Route path="/" element={<SearchPage />} />
            {/* 예전 주소 — 눌러 둔 링크·북마크가 죽지 않게 홈으로 보낸다 */}
            <Route path="/search" element={<Navigate to="/" replace />} />
            <Route path="/cert/:id" element={<DetailPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
            <Route path="/community" element={<CommunityPage />} />
            <Route path="/community/posts/:id" element={<PostDetailPage />} />

            {/* 로그인 필요 */}
            <Route path="/my" element={<RequireAuth><HomePage /></RequireAuth>} />
            <Route path="/calendar" element={<RequireAuth><CalendarPage /></RequireAuth>} />
            <Route path="/settings" element={<RequireAuth><SettingsPage /></RequireAuth>} />
            <Route path="/me" element={<RequireAuth><MePage /></RequireAuth>} />
            <Route path="/community/write" element={<RequireAuth><PostEditorPage /></RequireAuth>} />
            <Route path="/community/posts/:id/edit" element={<RequireAuth><PostEditorPage /></RequireAuth>} />
            <Route path="/manager/login" element={<ManagerLoginPage />} />
            {/* 운영 화면은 갈래마다 주소가 있다 — 한 페이지에 다 쌓으면 무엇을 보는지 알 수 없다 */}
            <Route path="/admin" element={<RequireAdmin><AdminPage /></RequireAdmin>}>
              <Route index element={<AdminTodo />} />
              <Route path="sources" element={<AdminSources />} />
              <Route path="health" element={<AdminCollectHealth />} />
              <Route path="lifecycle" element={<AdminLifecyclePage />} />
            </Route>

            <Route path="*" element={
              <div className="k-empty state">
                <span className="big">페이지를 찾을 수 없습니다</span>
                주소를 확인하거나 위 메뉴에서 이동하세요.
              </div>
            } />
          </Routes>
      </main>
    </div>
  );
}

// 로그인 상태 — JWT 를 localStorage 에 두고 모든 요청 헤더에 붙인다(설계 08 §3).
// 토큰은 OAuth 콜백에서 URL 프래그먼트(#token=)로 받는다. 쿼리스트링이 아닌 이유는
// 쿼리가 서버 접근로그·Referer 에 남기 때문이다.
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { examApi } from './api/exams';
import { clearToken, setToken } from './api/client';
import type { MeResponse } from './api/types';

interface AuthState {
  me: MeResponse | null;
  loading: boolean;
  /** returnTo 를 주면 로그인 뒤 그리로 돌아간다(안 주면 지금 있는 주소). */
  login: (provider: 'kakao' | 'google', returnTo?: string) => void;
  /** 매니저 폼 로그인 — 토큰을 직접 받아 저장한다(소셜 리다이렉트를 타지 않는다). */
  loginWithToken: (token: string) => Promise<void>;
  logout: () => void;
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<MeResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      setMe(await examApi.me());
    } catch {
      // 토큰이 없거나 만료 — 비로그인으로 취급한다(에러 화면을 띄울 일이 아니다)
      setMe(null);
      clearToken();
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const login = useCallback((provider: 'kakao' | 'google', returnTo?: string) => {
    // 돌아온 뒤 원래 있던 곳으로 보내기 위해 저장.
    // 로그인 화면을 거쳐 왔으면 지금 주소는 /login 이라, 원래 보던 곳을 따로 받는다.
    sessionStorage.setItem('afterLogin',
      returnTo ?? window.location.pathname + window.location.search);
    window.location.href = `/oauth2/authorization/${provider}`;
  }, []);

  const loginWithToken = useCallback(async (token: string) => {
    setToken(token);
    await refresh();
  }, [refresh]);

  const logout = useCallback(() => {
    clearToken();
    setMe(null);
  }, []);

  const value = useMemo(
    () => ({ me, loading, login, loginWithToken, logout, refresh }),
    [me, loading, login, loginWithToken, logout, refresh]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth 는 AuthProvider 안에서만 쓸 수 있습니다.');
  return ctx;
}

/** OAuth 콜백에서 프래그먼트의 토큰을 회수한다. 성공하면 true. */
export function consumeTokenFromHash(): boolean {
  const hash = window.location.hash;
  if (!hash.startsWith('#token=')) return false;
  const token = decodeURIComponent(hash.slice('#token='.length));
  if (!token) return false;
  setToken(token);
  // 주소창에서 토큰을 지운다 — 새로고침·공유로 새어 나가지 않게
  history.replaceState(null, '', window.location.pathname);
  return true;
}

/**
 * 로그인이 필요한 행동을 눌렀을 때 로그인 화면으로 보낸다.
 *
 * 그냥 API 를 불러 401 을 받으면 화면에는 "요청 실패 (401)" 만 남는다 — 무엇이 잘못됐는지도,
 * 어디로 가야 하는지도 알 수 없다. 보던 자리를 넘겨서 로그인 뒤 그리로 돌아오게 한다.
 */
export function useRequireLogin(): () => void {
  const navigate = useNavigate();
  const location = useLocation();
  return useCallback(() => {
    navigate('/login', { state: { from: location.pathname + location.search } });
  }, [navigate, location]);
}

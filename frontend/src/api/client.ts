// REST 클라이언트. 인증은 소셜 로그인으로 받은 JWT 를 Authorization 헤더에 붙인다(설계 08).
// 구 기기식별(X-Device-Id)은 폐기됐다.

const BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';
const TOKEN_KEY = 'exam-hub.token';

/**
 * 토큰이 죽었을 때(401) window 에 띄우는 이벤트. AuthProvider 가 듣고 me 를 비워
 * 화면 전체가 비로그인으로 바뀐다 — 예전엔 헤더에 닉네임이 남은 채 빨간 줄만 떴다.
 */
export const UNAUTHENTICATED_EVENT = 'exam-hub:unauthenticated';

/** 여기의 401 은 "비밀번호가 틀렸다"지 "네 토큰이 죽었다"가 아니다 — 멀쩡한 세션을 끊으면 안 된다. */
const LOGIN_PATHS = ['/api/manager/login'];

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }

  /** 로그인이 필요하다는 뜻 — 화면이 로그인 유도를 띄울 근거. */
  get isUnauthenticated(): boolean {
    return this.status === 401;
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (res.status === 204) {
    return undefined as T;
  }
  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;
  if (!res.ok) {
    const msg = (data && (data.message as string)) || `요청 실패 (${res.status})`;
    if (res.status === 401 && !LOGIN_PATHS.some((p) => path.startsWith(p))) {
      clearToken();
      window.dispatchEvent(new Event(UNAUTHENTICATED_EVENT));
    }
    throw new ApiError(res.status, msg);
  }
  return data as T;
}

/**
 * 파일을 받아 내려받기를 띄운다.
 *
 * <p>인증이 걸린 주소라 {@code <a href>} 로는 못 받는다 — 토큰이 안 실린다. 그래서 직접 받아
 * 브라우저에 넘긴다. 서버가 준 파일 이름을 그대로 쓰고, 다 쓴 임시 주소는 바로 놓아 준다.
 */
export async function downloadFile(path: string, filename: string): Promise<void> {
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(BASE + path, { headers });
  if (!res.ok) {
    if (res.status === 401) {
      clearToken();
      window.dispatchEvent(new Event(UNAUTHENTICATED_EVENT));
    }
    throw new ApiError(res.status, `내려받지 못했습니다 (${res.status})`);
  }

  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, body),
  del: <T>(path: string) => request<T>('DELETE', path),
};

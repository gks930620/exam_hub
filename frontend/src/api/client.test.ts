import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, clearToken, getToken, setToken, UNAUTHENTICATED_EVENT } from './client';

/**
 * REST 클라이언트 — 인증 헤더와 에러 변환.
 *
 * 여기가 틀리면 <b>모든 화면이 조용히 비로그인으로 동작한다</b>(토큰을 안 붙이므로).
 * 실제로 예전 기기식별 구현에서 헤더가 빠져 401 이 나던 적이 있어, 헤더 첨부를 테스트로 못 박는다.
 */
describe('api client', () => {
  beforeEach(() => {
    clearToken();
  });

  function mockFetch(status: number, body: unknown) {
    const spy = vi.fn().mockResolvedValue({
      status,
      ok: status >= 200 && status < 300,
      text: async () => (body === undefined ? '' : JSON.stringify(body)),
    });
    vi.stubGlobal('fetch', spy);
    return spy;
  }

  it('토큰이 없으면 Authorization 헤더를 붙이지 않는다', async () => {
    const spy = mockFetch(200, { ok: true });
    await api.get('/api/certificates/browse');

    const headers = spy.mock.calls[0][1].headers as Record<string, string>;
    expect(headers.Authorization).toBeUndefined();
  });

  it('토큰이 있으면 Bearer 로 붙인다', async () => {
    setToken('jwt-abc');
    const spy = mockFetch(200, { ok: true });
    await api.get('/api/me');

    const headers = spy.mock.calls[0][1].headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer jwt-abc');
  });

  it('204 는 본문 없이 통과한다', async () => {
    mockFetch(204, undefined);
    await expect(api.del('/api/me/favorites/1')).resolves.toBeUndefined();
  });

  it('실패하면 서버 메시지를 담은 ApiError 를 던진다', async () => {
    mockFetch(409, { message: '이미 관심 등록된 시험입니다.' });

    await expect(api.post('/api/me/favorites', { certificateId: 1 }))
      .rejects.toMatchObject({ status: 409, message: '이미 관심 등록된 시험입니다.' });
  });

  /** 매니저 로그인 5회 실패 → 429. 화면은 서버 문장을 그대로 보여주면 되므로 여기서 안 잘려야 한다. */
  it('429 도 서버 메시지를 그대로 전달한다', async () => {
    mockFetch(429, { message: '로그인 시도가 너무 많습니다. 15분 뒤 다시 시도하세요.' });

    await expect(api.post('/api/manager/login', { username: 'm', password: 'x' }))
      .rejects.toMatchObject({ status: 429, message: '로그인 시도가 너무 많습니다. 15분 뒤 다시 시도하세요.' });
  });

  /**
   * 토큰 만료 — 401 을 받으면 죽은 토큰을 지우고 이벤트를 띄운다. AuthProvider 가 이걸 듣고
   * me 를 비워 화면 전체가 비로그인으로 바뀐다. 예전엔 헤더에 닉네임이 남은 채 빨간 줄만 떴다.
   */
  it('401 이면 저장된 토큰을 지우고 exam-hub:unauthenticated 이벤트를 띄운다', async () => {
    setToken('expired');
    mockFetch(401, { message: '로그인이 필요합니다.' });
    const heard = vi.fn();
    window.addEventListener(UNAUTHENTICATED_EVENT, heard);
    try {
      await expect(api.get('/api/me/favorites')).rejects.toBeInstanceOf(ApiError);

      expect(getToken()).toBeNull();
      expect(heard).toHaveBeenCalledTimes(1);
    } finally {
      window.removeEventListener(UNAUTHENTICATED_EVENT, heard);
    }
  });

  /** 매니저 로그인의 401 은 "비밀번호가 틀렸다"지 "네 토큰이 죽었다"가 아니다 — 멀쩡한 세션을 끊으면 안 된다. */
  it('매니저 로그인의 401 은 토큰을 건드리지 않는다', async () => {
    setToken('social-token');
    mockFetch(401, { message: '아이디 또는 비밀번호가 올바르지 않습니다.' });
    const heard = vi.fn();
    window.addEventListener(UNAUTHENTICATED_EVENT, heard);
    try {
      await expect(api.post('/api/manager/login', { username: 'm', password: 'x' }))
        .rejects.toMatchObject({ status: 401 });

      expect(getToken()).toBe('social-token');
      expect(heard).not.toHaveBeenCalled();
    } finally {
      window.removeEventListener(UNAUTHENTICATED_EVENT, heard);
    }
  });

  it('401 은 isUnauthenticated 로 구분된다 — 화면이 로그인 유도를 띄울 근거', async () => {
    mockFetch(401, { message: '로그인이 필요합니다.' });

    try {
      await api.get('/api/me');
      throw new Error('던져야 한다');
    } catch (e) {
      expect(e).toBeInstanceOf(ApiError);
      expect((e as ApiError).isUnauthenticated).toBe(true);
    }
  });

  it('메시지가 없으면 상태코드로 대체한다', async () => {
    mockFetch(500, {});
    await expect(api.get('/api/x')).rejects.toThrow('요청 실패 (500)');
  });

  it('토큰 저장/삭제가 localStorage 를 쓴다', () => {
    expect(getToken()).toBeNull();
    setToken('t1');
    expect(getToken()).toBe('t1');
    clearToken();
    expect(getToken()).toBeNull();
  });
});

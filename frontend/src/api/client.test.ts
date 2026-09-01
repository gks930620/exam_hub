import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, clearToken, getToken, setToken } from './client';

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
    mockFetch(409, { message: '이미 관심 등록된 자격증입니다.' });

    await expect(api.post('/api/me/favorites', { certificateId: 1 }))
      .rejects.toMatchObject({ status: 409, message: '이미 관심 등록된 자격증입니다.' });
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

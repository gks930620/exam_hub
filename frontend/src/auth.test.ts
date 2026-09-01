import { describe, expect, it } from 'vitest';
import { consumeTokenFromHash } from './auth';
import { clearToken, getToken } from './api/client';

/**
 * OAuth 콜백에서 토큰을 회수하는 부분.
 *
 * <p>토큰을 <b>쿼리가 아니라 프래그먼트</b>로 받는 게 설계 의도다(설계 08 §3-1) —
 * 쿼리스트링은 서버 접근로그·Referer 에 남는다. 회수 후 주소창에서 지우는 것까지가 한 동작이다.
 */
describe('consumeTokenFromHash', () => {
  function withHash(hash: string, path = '/oauth/callback') {
    clearToken();
    history.replaceState(null, '', path + hash);
  }

  it('#token= 이 있으면 저장하고 true 를 준다', () => {
    withHash('#token=abc.def.ghi');

    expect(consumeTokenFromHash()).toBe(true);
    expect(getToken()).toBe('abc.def.ghi');
  });

  it('회수한 뒤 주소창에서 토큰을 지운다 — 새로고침·공유로 새지 않게', () => {
    withHash('#token=secret-token');

    consumeTokenFromHash();

    expect(window.location.hash).toBe('');
    expect(window.location.href).not.toContain('secret-token');
  });

  it('URL 인코딩된 토큰을 되돌린다', () => {
    withHash('#token=' + encodeURIComponent('a+b/c=='));

    consumeTokenFromHash();

    expect(getToken()).toBe('a+b/c==');
  });

  it('프래그먼트가 없으면 false 이고 아무것도 저장하지 않는다', () => {
    withHash('');

    expect(consumeTokenFromHash()).toBe(false);
    expect(getToken()).toBeNull();
  });

  it('다른 프래그먼트는 무시한다', () => {
    withHash('#error=access_denied');

    expect(consumeTokenFromHash()).toBe(false);
    expect(getToken()).toBeNull();
  });

  it('token 이 비어 있으면 저장하지 않는다', () => {
    withHash('#token=');

    expect(consumeTokenFromHash()).toBe(false);
    expect(getToken()).toBeNull();
  });
});

import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api, setToken, onUnauthorized, getToken } from './client'

function mockFetch(status: number, body?: unknown) {
  const fn = vi.fn().mockResolvedValue(new Response(body === undefined ? null : JSON.stringify(body), {
    status, headers: { 'Content-Type': 'application/json' },
  }))
  vi.stubGlobal('fetch', fn)
  return fn
}

afterEach(() => {
  vi.unstubAllGlobals()
  setToken(null)
})

describe('api client', () => {
  it('manda o token no cabeçalho e o corpo em JSON', async () => {
    const fetch = mockFetch(200, { ok: true })
    setToken('abc')
    await expect(api.post('/api/x', { a: 1 })).resolves.toEqual({ ok: true })
    const [url, init] = fetch.mock.calls[0]
    expect(url).toBe('/api/x')
    expect(init.method).toBe('POST')
    expect(init.headers.Authorization).toBe('Bearer abc')
    expect(init.body).toBe('{"a":1}')
  })

  it('204 devolve undefined; sem token não manda Authorization', async () => {
    const fetch = mockFetch(204)
    await expect(api.del('/api/x')).resolves.toBeUndefined()
    expect(fetch.mock.calls[0][1].headers.Authorization).toBeUndefined()
  })

  it('erro vira ApiError com status e mensagem traduzida', async () => {
    mockFetch(400, { status: 400, message: 'Account is archived' })
    const err = (await api.get('/api/x').catch((e: unknown) => e)) as ApiError
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(400)
    expect(err.message).toMatch(/arquivada/)
  })

  it('401 com token: limpa a sessão e avisa (sessão expirada ou revogada)', async () => {
    mockFetch(401, { status: 401, message: 'Authentication required' })
    const listener = vi.fn()
    const off = onUnauthorized(listener)
    setToken('velho')
    await api.get('/api/me').catch(() => {})
    expect(listener).toHaveBeenCalledOnce()
    expect(getToken()).toBeNull()
    off()
  })

  it('servidor fora do ar vira mensagem amigável', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    const err = (await api.get('/api/x').catch((e: unknown) => e)) as ApiError
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(0)
    expect(err.message).toMatch(/conexão/)
  })

  it('token persiste entre recargas (localStorage) e é removido no logout', () => {
    setToken('t1')
    expect(localStorage.getItem('bolso.token')).toBe('t1')
    setToken(null)
    expect(localStorage.getItem('bolso.token')).toBeNull()
  })
})

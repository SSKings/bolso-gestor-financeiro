import { vi } from 'vitest'

// Simulador da API para os testes de tela: "MÉTODO /caminho" → resposta. Registra cada chamada (com o corpo já
// decodificado) para o teste conferir exatamente o que o front mandou.

type Handler = unknown | ((body: unknown, url: URL) => unknown | [number, unknown?])
export type Call = { method: string; path: string; search: string; body: unknown; auth: string | undefined }

export function mockApi(routes: Record<string, Handler>) {
  const calls: Call[] = []
  const fetch = vi.fn(async (input: string, init: RequestInit = {}) => {
    const url = new URL(input, 'http://localhost')
    const method = init.method ?? 'GET'
    const body = init.body instanceof FormData ? init.body : init.body ? JSON.parse(String(init.body)) : undefined
    const headers = (init.headers ?? {}) as Record<string, string>
    calls.push({ method, path: url.pathname, search: url.search, body, auth: headers.Authorization })
    const key = Object.keys(routes).find((k) => {
      const [m, p] = k.split(' ')
      return m === method && new RegExp('^' + p.replace(/:[a-z]+/gi, '[^/]+') + '$').test(url.pathname)
    })
    if (!key) return new Response(JSON.stringify({ status: 404, message: 'Resource not found' }), { status: 404 })
    const h = routes[key]
    const out = typeof h === 'function' ? (h as (b: unknown, u: URL) => unknown)(body, url) : h
    const [status, payload] = Array.isArray(out) && typeof out[0] === 'number' ? (out as [number, unknown?]) : [200, out]
    return new Response(status === 204 || payload === undefined ? null : JSON.stringify(payload), {
      status, headers: { 'Content-Type': 'application/json' },
    })
  })
  vi.stubGlobal('fetch', fetch)
  return { calls, fetch, last: (method: string, path: string) => [...calls].reverse().find((c) => c.method === method && c.path === path) }
}

export const WS = '11111111-1111-1111-1111-111111111111'
export const ME = {
  id: 'u1', name: 'Ana Souza', email: 'ana@teste.com',
  workspaces: [
    { id: WS, name: 'Pessoal', role: 'OWNER' },
    { id: '22222222-2222-2222-2222-222222222222', name: 'Casa', role: 'MEMBER' },
  ],
}
export const ACCOUNTS = [
  { id: 'a1', name: 'Carteira', type: 'CASH', initialBalance: '0.00', balance: '150.00', archived: false },
  { id: 'a2', name: 'Nubank', type: 'CHECKING', initialBalance: '1000.00', balance: '2450.30', archived: false },
  { id: 'a3', name: 'Antiga', type: 'SAVINGS', initialBalance: '0.00', balance: '0.00', archived: true },
]
export const CATEGORIES = [
  { id: 'c1', name: 'Mercado', kind: 'EXPENSE', color: '#22c55e', archived: false },
  { id: 'c2', name: 'Alimentação', kind: 'EXPENSE', color: '#f97316', archived: false },
  { id: 'c3', name: 'Velha', kind: 'EXPENSE', color: '#6b7280', archived: true },
  { id: 'c4', name: 'Salário', kind: 'INCOME', color: '#16a34a', archived: false },
]

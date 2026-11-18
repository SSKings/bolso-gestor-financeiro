import { GENERIC_ERROR, translate } from '../lib/messages'

// Cliente HTTP da API: token no cabeçalho, erros como ApiError (mensagem já traduzida) e 401 → sessão encerrada.
//
// O token fica no localStorage para o app abrir já logado no celular. Risco conhecido: um XSS leria o token. Por
// isso o front nunca injeta HTML (sem dangerouslySetInnerHTML), e o nginx de produção serve com CSP script-src
// 'self' (sem script de terceiros nem inline).

const TOKEN_KEY = 'bolso.token'

let token: string | null = localStorage.getItem(TOKEN_KEY)
const unauthorizedListeners = new Set<() => void>()

export function getToken(): string | null {
  return token
}

export function setToken(value: string | null): void {
  token = value
  if (value) localStorage.setItem(TOKEN_KEY, value)
  else localStorage.removeItem(TOKEN_KEY)
}

/** Avisado quando a API recusa o token (expirado, revogado no logout de outro aparelho, troca de senha). */
export function onUnauthorized(listener: () => void): () => void {
  unauthorizedListeners.add(listener)
  return () => unauthorizedListeners.delete(listener)
}

export class ApiError extends Error {
  readonly status: number
  readonly errors: string[]

  constructor(status: number, message: string, errors: string[] = []) {
    super(message)
    this.status = status
    this.errors = errors
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const isForm = body instanceof FormData                              // arquivo: o navegador monta o multipart
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`
  let res: Response
  try {
    res = await fetch(path, { method, headers, body: body === undefined ? undefined : isForm ? body : JSON.stringify(body) })
  } catch {
    throw new ApiError(0, 'Sem conexão com o servidor. Confira a internet e tente de novo.')
  }
  if (res.status === 204) return undefined as T
  const data = await res.json().catch(() => null)
  if (!res.ok) {
    if (res.status === 401 && token) {
      setToken(null)
      unauthorizedListeners.forEach((l) => l())
    }
    throw new ApiError(res.status, data?.message ? translate(data.message) : GENERIC_ERROR, data?.errors ?? [])
  }
  return data as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  del: <T = void>(path: string) => request<T>('DELETE', path),
  upload: <T>(path: string, form: FormData) => request<T>('POST', path, form),
}

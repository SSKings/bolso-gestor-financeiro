import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ACCOUNTS, CATEGORIES, ME, WS, mockApi } from './test/mockApi'
import { renderApp } from './test/render'
import { getToken } from './api/client'

const CASA = '22222222-2222-2222-2222-222222222222'
const dash = (month = '2026-11') => ({ month, income: '0', expense: '0', net: '0', expensesByCategory: [], accounts: ACCOUNTS, totalBalance: '0' })
const routesFor = (ws: string) => ({
  [`GET /api/workspaces/${ws}/accounts`]: ACCOUNTS,
  [`GET /api/workspaces/${ws}/categories`]: CATEGORIES,
  [`GET /api/workspaces/${ws}/dashboard`]: dash(),
})
const members = [
  { userId: 'u1', name: 'Ana Souza', email: 'ana@teste.com', role: 'OWNER', joinedAt: '2026-11-01T00:00:00Z' },
  { userId: 'u2', name: 'Bruno Lima', email: 'bruno@teste.com', role: 'MEMBER', joinedAt: '2026-11-02T00:00:00Z' },
]

afterEach(() => vi.restoreAllMocks())

describe('espaços compartilhados', () => {
  it('troca de espaço: os dados passam a vir do outro espaço e a escolha fica salva', async () => {
    const api = mockApi({ 'GET /api/me': ME, ...routesFor(WS), ...routesFor(CASA) })
    renderApp('/')
    await screen.findByText('Saldo total')
    await userEvent.selectOptions(screen.getByLabelText('Espaço atual'), CASA)
    await waitFor(() => expect(api.calls.some((c) => c.path === `/api/workspaces/${CASA}/dashboard`)).toBe(true))
    expect(localStorage.getItem('bolso.workspace')).toBe(CASA)
  })

  it('espaço salvo que não é mais meu (fui removido) cai no primeiro da lista', async () => {
    localStorage.setItem('bolso.workspace', '99999999-9999-9999-9999-999999999999')
    const api = mockApi({ 'GET /api/me': ME, ...routesFor(WS) })
    renderApp('/')
    await screen.findByText('Saldo total')
    expect(api.calls.some((c) => c.path.includes('99999999'))).toBe(false)
  })

  it('dono gera convite: link com o código no FRAGMENTO (#), que o navegador não manda ao servidor', async () => {
    const api = mockApi({ 'GET /api/me': ME, ...routesFor(WS), [`GET /api/workspaces/${WS}/members`]: members,
      [`GET /api/workspaces/${WS}/invites`]: [], [`POST /api/workspaces/${WS}/invites`]: [201, { id: 'i1', code: 'C'.repeat(43), expiresAt: '2026-11-21T00:00:00Z' }] })
    renderApp('/espaco')
    expect(await screen.findByText('Bruno Lima')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Gerar convite' }))
    const link = await screen.findByLabelText('Link do convite')
    expect(link).toHaveValue(`${window.location.origin}/convite#codigo=${'C'.repeat(43)}`)
    expect(api.last('POST', `/api/workspaces/${WS}/invites`)).toBeDefined()
    // depois de gerar, a tela recarrega membros e convites: o link NÃO pode sumir com a recarga (bug pego pelo CI)
    await waitFor(() => expect(api.calls.filter((c) => c.method === 'GET' && c.path === `/api/workspaces/${WS}/invites`).length).toBeGreaterThan(1))
    await new Promise((r) => setTimeout(r, 50))
    expect(screen.getByLabelText('Link do convite')).toBeInTheDocument()
  })

  it('dono remove membro (com confirmação); membro comum não vê as ações de dono', async () => {
    const api = mockApi({ 'GET /api/me': ME, ...routesFor(WS), ...routesFor(CASA),
      [`GET /api/workspaces/${WS}/members`]: members, [`GET /api/workspaces/${WS}/invites`]: [],
      [`DELETE /api/workspaces/${WS}/members/u2`]: [204],
      [`GET /api/workspaces/${CASA}/members`]: members })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderApp('/espaco')
    await userEvent.click(await screen.findByRole('button', { name: 'Remover Bruno Lima' }))
    await waitFor(() => expect(api.last('DELETE', `/api/workspaces/${WS}/members/u2`)).toBeDefined())

    await userEvent.selectOptions(screen.getByLabelText('Espaço atual'), CASA)
    await screen.findByRole('heading', { name: 'Casa' })
    expect(screen.queryByRole('button', { name: 'Gerar convite' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Remover/ })).not.toBeInTheDocument()
  })

  it('sair do espaço: mensagem da API quando é o último dono', async () => {
    mockApi({ 'GET /api/me': ME, ...routesFor(WS), [`GET /api/workspaces/${WS}/members`]: members,
      [`GET /api/workspaces/${WS}/invites`]: [],
      [`POST /api/workspaces/${WS}/leave`]: [400, { status: 400, message: 'The last owner cannot leave the workspace' }] })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderApp('/espaco')
    await userEvent.click(await screen.findByRole('button', { name: 'Sair do espaço' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('outra pessoa como dona')
  })

  it('criar novo espaço', async () => {
    const api = mockApi({ 'GET /api/me': ME, ...routesFor(WS), [`GET /api/workspaces/${WS}/members`]: members,
      [`GET /api/workspaces/${WS}/invites`]: [], 'POST /api/workspaces': [201, { id: 'w3', name: 'Viagem', role: 'OWNER' }] })
    renderApp('/espaco')
    await userEvent.type(await screen.findByLabelText('Nome do novo espaço'), 'Viagem')
    await userEvent.click(screen.getByRole('button', { name: 'Criar espaço' }))
    await waitFor(() => expect(api.last('POST', '/api/workspaces')?.body).toEqual({ name: 'Viagem' }))
  })
})

describe('aceitar convite (/convite#codigo=...)', () => {
  const code = 'K'.repeat(43)

  it('logado: aceita, tira o código da barra de endereço e entra no espaço', async () => {
    window.history.replaceState(null, '', `/convite#codigo=${code}`)
    const api = mockApi({ 'GET /api/me': ME, ...routesFor(WS), ...routesFor(CASA),
      [`POST /api/invites/${code}/accept`]: { workspaceId: CASA, name: 'Casa' } })
    renderApp(`/convite#codigo=${code}`)
    expect(await screen.findByText('Você entrou no espaço Casa.')).toBeInTheDocument()
    expect(api.last('POST', `/api/invites/${code}/accept`)).toBeDefined()
    expect(window.location.hash).toBe('')
    expect(localStorage.getItem('bolso.workspace')).toBe(CASA)
  })

  it('sem login: guarda o convite, pede login e conclui depois', async () => {
    const api = mockApi({ 'POST /api/auth/login': { token: 'novo', user: ME }, 'GET /api/me': ME, ...routesFor(WS), ...routesFor(CASA),
      [`POST /api/invites/${code}/accept`]: { workspaceId: CASA, name: 'Casa' } })
    renderApp(`/convite#codigo=${code}`, { logged: false })
    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument()
    expect(screen.getByText(/convite/i)).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('E-mail'), 'bruno@teste.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'Vagalume4821')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByText('Você entrou no espaço Casa.')).toBeInTheDocument()
    expect(api.last('POST', `/api/invites/${code}/accept`)?.auth).toBe('Bearer novo')
    expect(getToken()).toBe('novo')
  })

  it('código inválido ou vencido: mensagem clara', async () => {
    mockApi({ 'GET /api/me': ME, ...routesFor(WS),
      [`POST /api/invites/${code}/accept`]: [404, { status: 404, message: 'Invalid or expired invite' }] })
    renderApp(`/convite#codigo=${code}`)
    expect(await screen.findByRole('alert')).toHaveTextContent('Convite inválido ou vencido')
  })

  it('link sem código', async () => {
    mockApi({ 'GET /api/me': ME, ...routesFor(WS) })
    renderApp('/convite')
    expect(await screen.findByRole('alert')).toHaveTextContent('Link de convite incompleto')
  })
})

describe('espaço: convites pendentes e saída', () => {
  it('dono vê convites pendentes e revoga', async () => {
    const api = mockApi({ 'GET /api/me': ME, ...routesFor(WS), [`GET /api/workspaces/${WS}/members`]: members,
      [`GET /api/workspaces/${WS}/invites`]: [{ id: 'i9', createdAt: '2026-11-18T10:00:00Z', expiresAt: '2026-11-21T10:00:00Z' }],
      [`DELETE /api/workspaces/${WS}/invites/i9`]: [204] })
    renderApp('/espaco')
    expect(await screen.findByText(/vence em 21\/11\/2026/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Revogar' }))
    await waitFor(() => expect(api.last('DELETE', `/api/workspaces/${WS}/invites/i9`)).toBeDefined())
  })

  it('sair do espaço: volta para o primeiro espaço que sobrou; cancelar não chama a API', async () => {
    const after = { ...ME, workspaces: [ME.workspaces[1]] }
    let left = false
    const api = mockApi({ 'GET /api/me': () => (left ? after : ME), ...routesFor(WS), ...routesFor(CASA),
      [`GET /api/workspaces/${WS}/members`]: members, [`GET /api/workspaces/${WS}/invites`]: [],
      [`GET /api/workspaces/${CASA}/members`]: members,
      [`POST /api/workspaces/${WS}/leave`]: () => { left = true; return [204] } })
    vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    renderApp('/espaco')
    await userEvent.click(await screen.findByRole('button', { name: 'Sair do espaço' }))
    expect(api.last('POST', `/api/workspaces/${WS}/leave`)).toBeUndefined()
    await userEvent.click(screen.getByRole('button', { name: 'Sair do espaço' }))
    expect(await screen.findByRole('heading', { name: 'Casa' })).toBeInTheDocument()
    expect(localStorage.getItem('bolso.workspace')).toBe(CASA)
  })

  it('remover membro: cancelar a confirmação não chama a API', async () => {
    const api = mockApi({ 'GET /api/me': ME, ...routesFor(WS), [`GET /api/workspaces/${WS}/members`]: members,
      [`GET /api/workspaces/${WS}/invites`]: [] })
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    renderApp('/espaco')
    await userEvent.click(await screen.findByRole('button', { name: 'Remover Bruno Lima' }))
    expect(api.calls.some((c) => c.method === 'DELETE')).toBe(false)
  })
})

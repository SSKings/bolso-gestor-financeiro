import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { ACCOUNTS, CATEGORIES, ME, WS, mockApi } from './test/mockApi'
import { renderApp } from './test/render'
import { getToken } from './api/client'

const dashboard = { month: '2026-11', income: '0', expense: '0', net: '0', expensesByCategory: [], accounts: ACCOUNTS, totalBalance: '2600.30' }
const appRoutes = {
  'GET /api/me': ME,
  [`GET /api/workspaces/${WS}/dashboard`]: dashboard,
  [`GET /api/workspaces/${WS}/accounts`]: ACCOUNTS,
  [`GET /api/workspaces/${WS}/categories`]: CATEGORIES,
}

describe('autenticação', () => {
  it('sem sessão, qualquer rota protegida leva ao login', async () => {
    mockApi({})
    renderApp('/lancamentos', { logged: false })
    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument()
  })

  it('login: manda e-mail e senha, guarda o token e abre o painel', async () => {
    const api = mockApi({ 'POST /api/auth/login': { token: 'novo', user: ME }, ...appRoutes })
    renderApp('/entrar', { logged: false })
    await userEvent.type(screen.getByLabelText('E-mail'), 'ana@teste.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'Vagalume4821')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByText('Saldo total')).toBeInTheDocument()
    expect(api.last('POST', '/api/auth/login')?.body).toEqual({ email: 'ana@teste.com', password: 'Vagalume4821' })
    expect(getToken()).toBe('novo')
  })

  it('login errado mostra a mensagem traduzida, sem detalhes técnicos', async () => {
    mockApi({ 'POST /api/auth/login': [401, { status: 401, message: 'Invalid email or password' }] })
    renderApp('/entrar', { logged: false })
    await userEvent.type(screen.getByLabelText('E-mail'), 'ana@teste.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'errada123')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail ou senha incorretos.')
    expect(getToken()).toBeNull()
  })

  it('cadastro: confere a confirmação da senha antes de enviar', async () => {
    const api = mockApi({ 'POST /api/auth/register': [201, { token: 'novo', user: ME }], ...appRoutes })
    renderApp('/cadastro', { logged: false })
    await userEvent.type(screen.getByLabelText('Nome'), 'Ana Souza')
    await userEvent.type(screen.getByLabelText('E-mail'), 'ana@teste.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'Vagalume4821')
    await userEvent.type(screen.getByLabelText('Confirme a senha'), 'Vagalume0000')
    await userEvent.click(screen.getByRole('button', { name: 'Criar conta' }))
    expect(screen.getByRole('alert')).toHaveTextContent('As senhas não conferem.')
    expect(api.calls).toHaveLength(0)

    await userEvent.clear(screen.getByLabelText('Confirme a senha'))
    await userEvent.type(screen.getByLabelText('Confirme a senha'), 'Vagalume4821')
    await userEvent.click(screen.getByRole('button', { name: 'Criar conta' }))
    expect(await screen.findByText('Saldo total')).toBeInTheDocument()
    expect(api.last('POST', '/api/auth/register')?.body).toEqual({ name: 'Ana Souza', email: 'ana@teste.com', password: 'Vagalume4821' })
  })

  it('erro de validação do cadastro aparece por campo', async () => {
    mockApi({ 'POST /api/auth/register': [400, { status: 400, message: 'Password is too common. Choose a less predictable one' }] })
    renderApp('/cadastro', { logged: false })
    await userEvent.type(screen.getByLabelText('Nome'), 'Ana')
    await userEvent.type(screen.getByLabelText('E-mail'), 'ana@teste.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'senha123')
    await userEvent.type(screen.getByLabelText('Confirme a senha'), 'senha123')
    await userEvent.click(screen.getByRole('button', { name: 'Criar conta' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Senha muito comum')
  })

  it('sessão revogada (401 no meio do uso) volta para o login', async () => {
    mockApi({ 'GET /api/me': ME, [`GET /api/workspaces/${WS}/dashboard`]: [401, { status: 401, message: 'Authentication required' }],
      [`GET /api/workspaces/${WS}/accounts`]: ACCOUNTS, [`GET /api/workspaces/${WS}/categories`]: CATEGORIES })
    renderApp('/')
    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument()
    expect(getToken()).toBeNull()
  })

  it('perfil: sair encerra a sessão no servidor e no aparelho', async () => {
    const api = mockApi({ ...appRoutes, 'POST /api/auth/logout': [204] })
    renderApp('/perfil')
    await userEvent.click(await screen.findByRole('button', { name: 'Sair' }))
    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument()
    expect(api.last('POST', '/api/auth/logout')).toBeDefined()
    expect(getToken()).toBeNull()
  })

  it('perfil: trocar a senha guarda o token novo (os antigos foram revogados)', async () => {
    const api = mockApi({ ...appRoutes, 'PUT /api/me/password': { token: 'token-novo', user: ME } })
    renderApp('/perfil')
    await userEvent.type(await screen.findByLabelText('Senha atual'), 'Vagalume4821')
    await userEvent.type(screen.getByLabelText('Nova senha'), 'Pirilampo7390')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar nova senha' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Senha alterada')
    expect(api.last('PUT', '/api/me/password')?.body).toEqual({ currentPassword: 'Vagalume4821', newPassword: 'Pirilampo7390' })
    await waitFor(() => expect(getToken()).toBe('token-novo'))
  })
})

describe('perfil e cadastro: erros', () => {
  it('senha atual errada', async () => {
    mockApi({ ...appRoutes, 'PUT /api/me/password': [400, { status: 400, message: 'Invalid password' }] })
    renderApp('/perfil')
    await userEvent.type(await screen.findByLabelText('Senha atual'), 'errada')
    await userEvent.type(screen.getByLabelText('Nova senha'), 'Pirilampo7390')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar nova senha' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Senha atual incorreta.')
    expect(getToken()).toBe('token-teste')
  })

  it('sair funciona mesmo se o servidor já tiver encerrado a sessão', async () => {
    mockApi({ ...appRoutes, 'POST /api/auth/logout': [500, {}] })
    renderApp('/perfil')
    await userEvent.click(await screen.findByRole('button', { name: 'Sair' }))
    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument()
    expect(getToken()).toBeNull()
  })

  it('sem conexão no carregamento do perfil: continua carregando sem quebrar', async () => {
    mockApi({ 'GET /api/me': [503, {}] })
    renderApp('/')
    expect(await screen.findByText('Carregando…')).toBeInTheDocument()
  })
})

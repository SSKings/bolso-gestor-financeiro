import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ACCOUNTS, CATEGORIES, ME, WS, mockApi } from './test/mockApi'
import { renderApp } from './test/render'
import { currentMonth, shiftMonth } from './lib/dates'
import type { Card } from './api/types'

// Caminhos de erro e ações secundárias que a análise de cobertura apontou sem teste.

const CARDS: Card[] = [
  { id: 'k1', name: 'Nubank', last4: '1234', closingDay: 10, dueDay: 17, creditLimit: 5000, available: 3800, archived: false },
  { id: 'k2', name: 'Velho', last4: null, closingDay: 5, dueDay: 12, creditLimit: 100, available: 100, archived: true },
]
const base = {
  'GET /api/me': ME,
  [`GET /api/workspaces/${WS}/accounts`]: ACCOUNTS,
  [`GET /api/workspaces/${WS}/categories`]: CATEGORIES,
  [`GET /api/workspaces/${WS}/cards`]: CARDS,
}

afterEach(() => vi.restoreAllMocks())

describe('cartões: arquivar, reativar e erros', () => {
  it('arquivar manda os dados atuais com archived=true (só o final, nunca outro número)', async () => {
    const api = mockApi({ ...base, [`PUT /api/workspaces/${WS}/cards/k1`]: CARDS[0] })
    renderApp('/cartoes')
    await userEvent.click(await screen.findByRole('button', { name: 'Arquivar Nubank' }))
    await waitFor(() => expect(api.last('PUT', `/api/workspaces/${WS}/cards/k1`)?.body).toEqual(
      { name: 'Nubank', last4: '1234', closingDay: 10, dueDay: 17, creditLimit: '5000.00', archived: true }))
  })

  it('reativar cartão sem final não inventa o campo last4', async () => {
    const api = mockApi({ ...base, [`PUT /api/workspaces/${WS}/cards/k2`]: CARDS[1] })
    renderApp('/cartoes')
    await userEvent.click(await screen.findByRole('button', { name: 'Reativar Velho' }))
    await waitFor(() => expect(api.last('PUT', `/api/workspaces/${WS}/cards/k2`)?.body).toEqual(
      { name: 'Velho', closingDay: 5, dueDay: 12, creditLimit: '100.00', archived: false }))
  })

  it('erro ao arquivar e ao criar aparecem traduzidos', async () => {
    mockApi({ ...base, [`PUT /api/workspaces/${WS}/cards/k1`]: [404, { message: 'Card not found' }],
      [`POST /api/workspaces/${WS}/cards`]: [409, { message: 'Card name already in use' }] })
    renderApp('/cartoes')
    await userEvent.click(await screen.findByRole('button', { name: 'Arquivar Nubank' }))
    expect(await screen.findByText('Cartão não encontrado.')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Nome do cartão'), 'Nubank')
    await userEvent.type(screen.getByLabelText('Limite'), '100000')
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar cartão' }))
    expect(await screen.findByText('Já existe um cartão com esse nome.')).toBeInTheDocument()
  })
})

describe('regras: erros', () => {
  it('falha ao carregar e ao remover aparecem na tela', async () => {
    mockApi({ ...base, [`GET /api/workspaces/${WS}/rules`]: [500, { message: 'Internal error' }] })
    renderApp('/regras')
    expect(await screen.findByText('Algo deu errado. Tente de novo.')).toBeInTheDocument()
  })

  it('remover cancelado não chama a API; erro ao remover é mostrado', async () => {
    const rules = [{ id: 'r1', pattern: 'uber', category: { id: 'c2', name: 'Alimentação', color: '#f97316' } }]
    const api = mockApi({ ...base, [`GET /api/workspaces/${WS}/rules`]: rules, [`DELETE /api/workspaces/${WS}/rules/r1`]: [404, { message: 'Rule not found' }] })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    renderApp('/regras')
    await userEvent.click(await screen.findByRole('button', { name: 'Remover regra uber' }))
    expect(api.last('DELETE', `/api/workspaces/${WS}/rules/r1`)).toBeUndefined()
    confirm.mockReturnValue(true)
    await userEvent.click(screen.getByRole('button', { name: 'Remover regra uber' }))
    expect(await screen.findByText('Regra não encontrada.')).toBeInTheDocument()
  })
})

describe('telegram: erros e atualizar', () => {
  const CODE = 'B'.repeat(43)

  it('erro ao desligar é mostrado e o vínculo continua na tela', async () => {
    mockApi({ ...base, 'GET /api/me/telegram': { available: true, botUsername: 'BolsoBot',
      link: { workspace: { id: WS, name: 'Pessoal' }, account: { id: 'a2', name: 'Nubank' }, linkedAt: '2026-11-10T12:00:00Z' } },
      'DELETE /api/me/telegram': [500, { message: 'Internal error' }] })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderApp('/perfil')
    const s = await screen.findByRole('region', { name: 'Telegram' })
    await userEvent.click(await within(s).findByRole('button', { name: 'Desligar Telegram' }))
    expect(await within(s).findByText('Algo deu errado. Tente de novo.')).toBeInTheDocument()
    expect(s).toHaveTextContent('Ligado ao espaço Pessoal')
  })

  it('"atualize" depois de ligar busca o estado de novo', async () => {
    let linked = false
    const api = mockApi({ ...base,
      'GET /api/me/telegram': () => ({ available: true, botUsername: 'BolsoBot',
        link: linked ? { workspace: { id: WS, name: 'Pessoal' }, account: { id: 'a2', name: 'Nubank' }, linkedAt: '2026-11-10T12:00:00Z' } : null }),
      'POST /api/me/telegram/code': { code: CODE, link: `https://t.me/BolsoBot?start=${CODE}`, expiresAt: '2026-11-10T12:10:00Z' } })
    renderApp('/perfil')
    const s = await screen.findByRole('region', { name: 'Telegram' })
    await waitFor(() => expect(within(s).getByRole('option', { name: 'Nubank' })).toBeInTheDocument())
    await userEvent.selectOptions(within(s).getByLabelText(/Conta dos lançamentos/), 'a2')
    await userEvent.click(within(s).getByRole('button', { name: 'Ligar o Telegram' }))
    linked = true
    await userEvent.click(await within(s).findByRole('button', { name: 'atualize' }))
    expect(await within(s).findByText(/Ligado ao espaço/)).toBeInTheDocument()
    expect(api.calls.filter((c) => c.path === '/api/me/telegram' && c.method === 'GET').length).toBeGreaterThanOrEqual(2)
  })
})

describe('navegação e falhas de carga', () => {
  it('mês anterior nos orçamentos pede o mês certo à API', async () => {
    const api = mockApi({ ...base, [`GET /api/workspaces/${WS}/budgets`]: [] })
    renderApp('/orcamentos')
    await userEvent.click(await screen.findByRole('button', { name: 'Mês anterior' }))
    await waitFor(() => expect(api.calls.some((c) => c.path.endsWith('/budgets') && c.search === `?month=${shiftMonth(currentMonth(), -1)}`)).toBe(true))
  })

  it('contas e categorias fora do ar: a tela abre mesmo assim (listas vazias, sem quebrar)', async () => {
    mockApi({ 'GET /api/me': ME, [`GET /api/workspaces/${WS}/accounts`]: [500, { message: 'Internal error' }],
      [`GET /api/workspaces/${WS}/categories`]: CATEGORIES, [`GET /api/workspaces/${WS}/rules`]: [] })
    renderApp('/regras')
    expect(await screen.findByRole('form', { name: 'Nova regra' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'Mercado' })).not.toBeInTheDocument()
  })
})

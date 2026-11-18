import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ACCOUNTS, CATEGORIES, ME, WS, mockApi } from './test/mockApi'
import { renderApp } from './test/render'
import { currentMonth, todayISO } from './lib/dates'
import { statementFor } from './lib/billing'

const CARDS = [
  { id: 'k1', name: 'Nubank', last4: '1234', closingDay: 10, dueDay: 17, creditLimit: '5000.00', available: '3800.00', archived: false },
  { id: 'k2', name: 'Velho', last4: null, closingDay: 5, dueDay: 12, creditLimit: '100.00', available: '100.00', archived: true },
]
const base = {
  'GET /api/me': ME,
  [`GET /api/workspaces/${WS}/accounts`]: ACCOUNTS,
  [`GET /api/workspaces/${WS}/categories`]: CATEGORIES,
  [`GET /api/workspaces/${WS}/cards`]: CARDS,
}
const statement = (month: string) => ({
  month, closingDate: `${month}-10`, dueDate: `${month}-17`, status: 'CLOSED', total: '233.34', paid: '100.00', remaining: '133.34',
  items: [
    { purchaseId: 'p1', description: 'Geladeira', number: 1, of: 3, amount: '33.34', purchaseDate: '2026-11-05', category: { id: 'c1', name: 'Mercado', color: '#22c55e' } },
    { purchaseId: 'p2', description: 'Mercado', number: 1, of: 1, amount: '200.00', purchaseDate: '2026-11-06', category: { id: 'c1', name: 'Mercado', color: '#22c55e' } },
  ],
  payments: [{ transactionId: 't1', date: '2026-11-15', amount: '100.00', account: { id: 'a2', name: 'Nubank' } }],
})

afterEach(() => vi.restoreAllMocks())

describe('cartões', () => {
  it('lista os cartões ativos com o final, o limite disponível e cadastra um novo (só 4 dígitos)', async () => {
    const api = mockApi({ ...base, [`POST /api/workspaces/${WS}/cards`]: [201, { ...CARDS[0], id: 'k3' }] })
    renderApp('/cartoes')
    expect(await screen.findByText('Nubank')).toBeInTheDocument()
    expect(screen.getByText('•••• 1234')).toBeInTheDocument()
    expect(screen.getByText('R$ 3.800,00 de R$ 5.000,00')).toBeInTheDocument()
    // arquivado: só na seção "Arquivados" (para reativar), não entre os ativos
    expect(screen.queryByRole('button', { name: 'Arquivar Velho' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reativar Velho' })).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Nome do cartão'), 'Inter')
    await userEvent.type(screen.getByLabelText('Final (4 últimos dígitos)'), '98765')
    expect(screen.getByLabelText('Final (4 últimos dígitos)')).toHaveValue('9876')
    await userEvent.clear(screen.getByLabelText('Fecha no dia'))
    await userEvent.type(screen.getByLabelText('Fecha no dia'), '3')
    await userEvent.clear(screen.getByLabelText('Vence no dia'))
    await userEvent.type(screen.getByLabelText('Vence no dia'), '10')
    await userEvent.type(screen.getByLabelText('Limite'), '200000')
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar cartão' }))
    await waitFor(() => expect(api.last('POST', `/api/workspaces/${WS}/cards`)?.body).toEqual(
      { name: 'Inter', last4: '9876', closingDay: 3, dueDay: 10, creditLimit: '2000.00' }))
  })

  it('fatura: abre na fatura atual, mostra parcelas, status, pagamentos e navega entre meses', async () => {
    const api = mockApi({ ...base, [`GET /api/workspaces/${WS}/cards/k1/statements/:m`]: (_b: unknown, url: URL) => statement(url.pathname.split('/').pop()!) })
    renderApp('/cartoes/k1')
    expect(await screen.findByText('Geladeira')).toBeInTheDocument()
    expect(api.calls.some((c) => c.path.endsWith(`/statements/${statementFor(todayISO(), 10)}`))).toBe(true)
    expect(screen.getByText('1/3')).toBeInTheDocument()
    expect(screen.getByText('Fechada')).toBeInTheDocument()
    expect(screen.getByText('R$ 233,34')).toBeInTheDocument()
    expect(screen.getByText(/Falta pagar/)).toHaveTextContent('R$ 133,34')
    expect(screen.getByText(/Pago em 15\/11\/2026/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Mês anterior' }))
    await waitFor(() => expect(new Set(api.calls.filter((c) => c.path.includes('/statements/')).map((c) => c.path)).size).toBe(2))
  })

  it('pagar a fatura: sugere o que falta, escolhe a conta e manda para a rota da fatura', async () => {
    const month = statementFor(todayISO(), 10)
    const api = mockApi({ ...base, [`GET /api/workspaces/${WS}/cards/k1/statements/:m`]: statement(month),
      [`POST /api/workspaces/${WS}/cards/k1/statements/${month}/payments`]: [201, { id: 'tp' }] })
    renderApp('/cartoes/k1')
    await screen.findByText('Geladeira')
    expect(screen.getByLabelText('Valor do pagamento')).toHaveValue('R$ 133,34')
    await userEvent.selectOptions(screen.getByLabelText('Pagar com a conta'), 'a2')
    await userEvent.click(screen.getByRole('button', { name: 'Pagar fatura' }))
    await waitFor(() => expect(api.last('POST', `/api/workspaces/${WS}/cards/k1/statements/${month}/payments`)?.body).toEqual(
      { accountId: 'a2', amount: '133.34', date: todayISO() }))
  })

  it('excluir compra da fatura pede confirmação; erro aparece na tela', async () => {
    const month = statementFor(todayISO(), 10)
    const api = mockApi({ ...base, [`GET /api/workspaces/${WS}/cards/k1/statements/:m`]: statement(month),
      [`DELETE /api/workspaces/${WS}/cards/k1/purchases/p1`]: [404, { status: 404, message: 'Purchase not found' }] })
    vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    renderApp('/cartoes/k1')
    await userEvent.click(await screen.findByRole('button', { name: 'Excluir compra Geladeira' }))
    expect(api.last('DELETE', `/api/workspaces/${WS}/cards/k1/purchases/p1`)).toBeUndefined()
    await userEvent.click(screen.getByRole('button', { name: 'Excluir compra Geladeira' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Compra não encontrada.')
  })

  it('cartão inexistente (ou de outro espaço) mostra aviso', async () => {
    mockApi({ ...base, [`GET /api/workspaces/${WS}/cards/zz/statements/:m`]: [404, { status: 404, message: 'Card not found' }] })
    renderApp('/cartoes/zz')
    expect(await screen.findByRole('alert')).toHaveTextContent('Cartão não encontrado.')
  })
})

describe('lançamento rápido no cartão', () => {
  it('aba Cartão: compra parcelada com prévia das parcelas, só cartões ativos', async () => {
    const api = mockApi({ ...base,
      [`GET /api/workspaces/${WS}/dashboard`]: { month: currentMonth(), income: '0', expense: '0', net: '0', expensesByCategory: [], accounts: ACCOUNTS, totalBalance: '0', cards: [] },
      [`POST /api/workspaces/${WS}/cards/k1/purchases`]: [201, { id: 'p9' }] })
    renderApp('/')
    await screen.findByText('Saldo total')
    await userEvent.click(screen.getByRole('button', { name: 'Novo lançamento' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cartão' }))
    expect(within(within(dialog).getByLabelText('Cartão de crédito')).queryByText('Velho')).not.toBeInTheDocument()
    await userEvent.type(within(dialog).getByLabelText('Valor'), '10000')
    await userEvent.type(within(dialog).getByLabelText('Descrição'), 'Geladeira')
    await userEvent.selectOptions(within(dialog).getByLabelText('Categoria'), 'c1')
    await userEvent.selectOptions(within(dialog).getByLabelText('Cartão de crédito'), 'k1')
    await userEvent.selectOptions(within(dialog).getByLabelText('Parcelas'), '3')
    expect(within(dialog).getByText('3× (1ª de R$ 33,34, demais de R$ 33,33)')).toBeInTheDocument()
    expect(within(dialog).queryByLabelText('Conta')).not.toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    await waitFor(() => expect(api.last('POST', `/api/workspaces/${WS}/cards/k1/purchases`)?.body).toEqual(
      { description: 'Geladeira', amount: '100.00', installments: 3, date: todayISO(), categoryId: 'c1' }))
  })

  it('aba Cartão sem cartão escolhido avisa sem chamar a API', async () => {
    const api = mockApi({ ...base,
      [`GET /api/workspaces/${WS}/dashboard`]: { month: currentMonth(), income: '0', expense: '0', net: '0', expensesByCategory: [], accounts: ACCOUNTS, totalBalance: '0', cards: [] } })
    renderApp('/')
    await screen.findByText('Saldo total')
    await userEvent.click(screen.getByRole('button', { name: 'Novo lançamento' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cartão' }))
    await userEvent.type(within(dialog).getByLabelText('Valor'), '100')
    await userEvent.type(within(dialog).getByLabelText('Descrição'), 'X')
    await userEvent.selectOptions(within(dialog).getByLabelText('Categoria'), 'c1')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Escolha o cartão.')
    expect(api.calls.some((c) => c.method === 'POST')).toBe(false)
  })
})

describe('painel e lançamentos com cartão', () => {
  it('painel lista as faturas do mês; pagamento de fatura aparece na lista de lançamentos', async () => {
    mockApi({ ...base,
      [`GET /api/workspaces/${WS}/dashboard`]: { month: currentMonth(), income: '0', expense: '290.00', net: '-290.00', expensesByCategory: [], accounts: ACCOUNTS, totalBalance: '0',
        cards: [{ cardId: 'k1', name: 'Nubank', statementTotal: '290.00', dueDate: '2026-11-17', status: 'OPEN' }] },
      [`GET /api/workspaces/${WS}/transactions`]: { items: [{ id: 't1', type: 'CARD_PAYMENT', amount: '100.00', date: '2026-11-15', description: 'Fatura Nubank 11/2026',
        account: { id: 'a2', name: 'Nubank' }, card: { id: 'k1', name: 'Nubank' }, statement: '2026-11', createdAt: '2026-11-15T00:00:00Z' }], total: 1, page: 0, size: 50 } })
    renderApp('/')
    const faturas = await screen.findByRole('list', { name: 'Faturas do mês' })
    expect(within(faturas).getByText('Nubank')).toBeInTheDocument()
    expect(within(faturas).getByText(/vence 17\/11/)).toBeInTheDocument()
    expect(within(faturas).getByRole('link', { name: /Nubank/ })).toHaveAttribute('href', '/cartoes/k1')

    await userEvent.click(screen.getByRole('link', { name: 'Lançamentos' }))
    expect(await screen.findByText('Fatura Nubank 11/2026')).toBeInTheDocument()
    expect(screen.getByText('-R$ 100,00')).toBeInTheDocument()
  })
})

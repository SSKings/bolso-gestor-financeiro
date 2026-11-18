import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ACCOUNTS, CATEGORIES, ME, WS, mockApi } from './test/mockApi'
import { renderApp } from './test/render'
import { currentMonth } from './lib/dates'
import type { Budget, Occurrence, Recurrence } from './api/types'

const M = currentMonth()
const base = {
  'GET /api/me': ME,
  [`GET /api/workspaces/${WS}/accounts`]: ACCOUNTS,
  [`GET /api/workspaces/${WS}/categories`]: CATEGORIES,
  [`GET /api/workspaces/${WS}/cards`]: [],
}
const cat = { id: 'c1', name: 'Mercado', color: '#22c55e' }
const acc = { id: 'a2', name: 'Nubank' }
const occ = (o: Partial<Occurrence> & Pick<Occurrence, 'recurrenceId' | 'description' | 'dueDate' | 'status'>): Occurrence => ({ type: 'EXPENSE', month: M, expectedAmount: 100, transactionId: null, paidAmount: null, category: cat, account: acc, ...o })
const OCCURRENCES: Occurrence[] = [
  occ({ recurrenceId: 'r1', description: 'Internet', dueDate: `${M}-05`, status: 'OVERDUE' }),
  occ({ recurrenceId: 'r2', description: 'Aluguel', dueDate: `${M}-15`, expectedAmount: 1500, status: 'DUE_SOON' }),
  occ({ recurrenceId: 'r3', description: 'Streaming', dueDate: `${M}-08`, expectedAmount: 40, status: 'PAID', transactionId: 't9', paidAmount: 39.9 }),
  occ({ recurrenceId: 'r4', type: 'INCOME', description: 'Salário', dueDate: `${M}-25`, expectedAmount: 3000, status: 'PENDING' }),
]
const RECURRENCES: Recurrence[] = [
  { id: 'r1', type: 'EXPENSE', description: 'Internet', amount: 100, dayOfMonth: 5, category: cat, account: acc, startMonth: '2026-01', endMonth: null, active: true },
  { id: 'r5', type: 'EXPENSE', description: 'Academia', amount: 120, dayOfMonth: 3, category: cat, account: acc, startMonth: '2026-01', endMonth: null, active: false },
]
const BUDGETS: Budget[] = [
  { category: { id: 'c2', name: 'Alimentação', color: '#f97316' }, amount: 100, spent: 120, remaining: -20, percent: 120, status: 'EXCEEDED' },
  { category: cat, amount: 500, spent: 450, remaining: 50, percent: 90, status: 'WARNING' },
  { category: { id: 'c9', name: 'Lazer', color: '#ec4899' }, amount: 300, spent: 30, remaining: 270, percent: 10, status: 'OK' },
]

afterEach(() => vi.restoreAllMocks())

describe('contas fixas', () => {
  it('mês com status de cada conta (atrasada, vence em breve, paga, a receber)', async () => {
    mockApi({ ...base, [`GET /api/workspaces/${WS}/recurrences/occurrences`]: OCCURRENCES, [`GET /api/workspaces/${WS}/recurrences`]: RECURRENCES })
    renderApp('/contas-fixas')
    const list = await screen.findByRole('list', { name: 'Contas do mês' })
    const row = (name: string) => within(list).getByText(name).closest('li')!
    expect(row('Internet')).toHaveTextContent('Atrasada')
    expect(row('Aluguel')).toHaveTextContent('Vence em breve')
    expect(row('Streaming')).toHaveTextContent('Paga')
    expect(row('Streaming')).toHaveTextContent('R$ 39,90')
    expect(row('Salário')).toHaveTextContent('A receber')
    expect(within(row('Streaming')).queryByRole('button', { name: /Confirmar/ })).not.toBeInTheDocument()
  })

  it('confirmar com o valor real: abre com o previsto e a conta da conta fixa, manda para a API', async () => {
    const api = mockApi({ ...base, [`GET /api/workspaces/${WS}/recurrences/occurrences`]: OCCURRENCES, [`GET /api/workspaces/${WS}/recurrences`]: RECURRENCES,
      [`POST /api/workspaces/${WS}/recurrences/r2/occurrences/${M}/confirm`]: [201, { id: 'tn' }] })
    renderApp('/contas-fixas')
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar Aluguel' }))
    const form = screen.getByRole('form', { name: 'Confirmar Aluguel' })
    expect(within(form).getByLabelText('Valor pago')).toHaveValue('R$ 1.500,00')
    await userEvent.clear(within(form).getByLabelText('Valor pago'))
    await userEvent.type(within(form).getByLabelText('Valor pago'), '152390')
    await userEvent.click(within(form).getByRole('button', { name: 'Confirmar pagamento' }))
    await waitFor(() => expect(api.last('POST', `/api/workspaces/${WS}/recurrences/r2/occurrences/${M}/confirm`)?.body)
      .toMatchObject({ amount: '1523.90', accountId: 'a2' }))
  })

  it('confirmar de novo no mesmo mês mostra a mensagem da API', async () => {
    mockApi({ ...base, [`GET /api/workspaces/${WS}/recurrences/occurrences`]: OCCURRENCES, [`GET /api/workspaces/${WS}/recurrences`]: RECURRENCES,
      [`POST /api/workspaces/${WS}/recurrences/r1/occurrences/${M}/confirm`]: [409, { status: 409, message: 'Already confirmed for this month' }] })
    renderApp('/contas-fixas')
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar Internet' }))
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar pagamento' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Esta conta já foi confirmada neste mês.')
  })

  it('cadastrar conta fixa e pausar/retomar', async () => {
    const api = mockApi({ ...base, [`GET /api/workspaces/${WS}/recurrences/occurrences`]: [], [`GET /api/workspaces/${WS}/recurrences`]: RECURRENCES,
      [`POST /api/workspaces/${WS}/recurrences`]: [201, { id: 'rn' }], [`PUT /api/workspaces/${WS}/recurrences/r1`]: RECURRENCES[0],
      [`PUT /api/workspaces/${WS}/recurrences/r5`]: RECURRENCES[1] })
    renderApp('/contas-fixas')
    await userEvent.type(await screen.findByLabelText('Descrição'), 'Luz')
    await userEvent.type(screen.getByLabelText('Valor previsto'), '18000')
    await userEvent.clear(screen.getByLabelText('Vence no dia'))
    await userEvent.type(screen.getByLabelText('Vence no dia'), '12')
    await userEvent.selectOptions(screen.getByLabelText('Categoria'), 'c1')
    await userEvent.selectOptions(screen.getByLabelText('Conta'), 'a2')
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar conta fixa' }))
    await waitFor(() => expect(api.last('POST', `/api/workspaces/${WS}/recurrences`)?.body).toEqual(
      { type: 'EXPENSE', description: 'Luz', amount: '180.00', dayOfMonth: 12, categoryId: 'c1', accountId: 'a2', startMonth: M }))

    await userEvent.click(screen.getByRole('button', { name: 'Pausar Internet' }))
    await waitFor(() => expect(api.last('PUT', `/api/workspaces/${WS}/recurrences/r1`)?.body).toMatchObject({ active: false, description: 'Internet' }))
    await userEvent.click(screen.getByRole('button', { name: 'Retomar Academia' }))
    await waitFor(() => expect(api.last('PUT', `/api/workspaces/${WS}/recurrences/r5`)?.body).toMatchObject({ active: true }))
  })
})

describe('orçamentos', () => {
  it('mostra gasto, limite e situação; define e remove orçamento', async () => {
    const api = mockApi({ ...base, [`GET /api/workspaces/${WS}/budgets`]: BUDGETS,
      [`PUT /api/workspaces/${WS}/budgets/c4`]: BUDGETS[2], [`DELETE /api/workspaces/${WS}/budgets/c9`]: [204] })
    renderApp('/orcamentos')
    const list = await screen.findByRole('list', { name: 'Orçamentos do mês' })
    const row = (name: string) => within(list).getByText(name).closest('li')!
    expect(row('Alimentação')).toHaveTextContent('Estourado')
    expect(row('Alimentação')).toHaveTextContent('R$ 120,00 de R$ 100,00')
    expect(row('Mercado')).toHaveTextContent('Atenção')
    expect(row('Lazer')).toHaveTextContent('Dentro do limite')
    expect(within(row('Mercado')).getByRole('progressbar')).toHaveAttribute('aria-valuenow', '90')

    expect(within(screen.getByLabelText('Categoria')).queryByText('Salário')).not.toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('Categoria'), 'c1')
    await userEvent.type(screen.getByLabelText('Limite mensal'), '60000')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar orçamento' }))
    await waitFor(() => expect(api.last('PUT', `/api/workspaces/${WS}/budgets/c1`)?.body).toEqual({ amount: '600.00' }))

    vi.spyOn(window, 'confirm').mockReturnValue(true)
    await userEvent.click(screen.getByRole('button', { name: 'Remover orçamento de Lazer' }))
    await waitFor(() => expect(api.last('DELETE', `/api/workspaces/${WS}/budgets/c9`)).toBeDefined())
  })
})

describe('painel: previsão, vencimentos e alertas', () => {
  it('mês atual: saldo previsto, o que vence/atrasou e orçamentos apertados', async () => {
    mockApi({ ...base,
      [`GET /api/workspaces/${WS}/dashboard`]: { month: M, income: '0', expense: '0', net: '0', expensesByCategory: [], accounts: ACCOUNTS, totalBalance: '3860.00', cards: [] },
      [`GET /api/workspaces/${WS}/forecast`]: { month: M, today: `${M}-10`, currentBalance: '3860.00', pendingIncome: '500.00', pendingExpense: '2000.00', projectedBalance: '2360.00', items: [] },
      [`GET /api/workspaces/${WS}/recurrences/occurrences`]: OCCURRENCES,
      [`GET /api/workspaces/${WS}/budgets`]: BUDGETS })
    renderApp('/')
    const forecast = await screen.findByRole('region', { name: 'Previsão do mês' })
    expect(within(forecast).getByText('R$ 2.360,00')).toBeInTheDocument()
    expect(forecast).toHaveTextContent('A receber R$ 500,00')
    expect(forecast).toHaveTextContent('A pagar R$ 2.000,00')
    const due = screen.getByRole('list', { name: 'Vencendo' })
    expect(within(due).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      expect.stringContaining('Internet'), expect.stringContaining('Aluguel')])
    const alerts = screen.getByRole('list', { name: 'Orçamentos apertados' })
    expect(within(alerts).getAllByRole('listitem')).toHaveLength(2)
    expect(within(alerts).queryByText('Lazer')).not.toBeInTheDocument()
  })

  it('mês passado: sem previsão (só vale para o mês atual)', async () => {
    const api = mockApi({ ...base,
      [`GET /api/workspaces/${WS}/dashboard`]: { month: M, income: '0', expense: '0', net: '0', expensesByCategory: [], accounts: ACCOUNTS, totalBalance: '0', cards: [] },
      [`GET /api/workspaces/${WS}/forecast`]: { month: M, today: `${M}-10`, currentBalance: '0', pendingIncome: '0', pendingExpense: '0', projectedBalance: '0', items: [] },
      [`GET /api/workspaces/${WS}/recurrences/occurrences`]: [], [`GET /api/workspaces/${WS}/budgets`]: [] })
    renderApp('/')
    await screen.findByRole('region', { name: 'Previsão do mês' })
    await userEvent.click(screen.getByRole('button', { name: 'Mês anterior' }))
    await waitFor(() => expect(screen.queryByRole('region', { name: 'Previsão do mês' })).not.toBeInTheDocument())
    expect(api.calls.filter((c) => c.path.endsWith('/forecast'))).toHaveLength(1)
  })
})

describe('planejamento: erros e exclusões', () => {
  it('orçamento: sem categoria avisa sem chamar a API; erro da API aparece; falha ao remover aparece', async () => {
    const api = mockApi({ ...base, [`GET /api/workspaces/${WS}/budgets`]: BUDGETS,
      [`PUT /api/workspaces/${WS}/budgets/c1`]: [400, { status: 400, message: 'Budgets are for expense categories' }],
      [`DELETE /api/workspaces/${WS}/budgets/c9`]: [404, { status: 404, message: 'Budget not found' }] })
    renderApp('/orcamentos')
    await screen.findByRole('list', { name: 'Orçamentos do mês' })
    await userEvent.click(screen.getByRole('button', { name: 'Salvar orçamento' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Escolha a categoria e o limite.')
    expect(api.calls.some((c) => c.method === 'PUT')).toBe(false)

    await userEvent.selectOptions(screen.getByLabelText('Categoria'), 'c1')
    await userEvent.type(screen.getByLabelText('Limite mensal'), '1000')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar orçamento' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Orçamento é só para categorias de despesa.')

    vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    await userEvent.click(screen.getByRole('button', { name: 'Remover orçamento de Lazer' }))
    expect(api.last('DELETE', `/api/workspaces/${WS}/budgets/c9`)).toBeUndefined()
    await userEvent.click(screen.getByRole('button', { name: 'Remover orçamento de Lazer' }))
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Orçamento não encontrado.'))
  })

  it('orçamentos: estado vazio e falha ao carregar', async () => {
    mockApi({ ...base, [`GET /api/workspaces/${WS}/budgets`]: [500, {}] })
    renderApp('/orcamentos')
    expect(await screen.findByRole('alert')).toHaveTextContent('Algo deu errado')
  })

  it('conta fixa: campos faltando avisam; excluir pede confirmação e mantém o histórico', async () => {
    const api = mockApi({ ...base, [`GET /api/workspaces/${WS}/recurrences/occurrences`]: [], [`GET /api/workspaces/${WS}/recurrences`]: RECURRENCES,
      [`DELETE /api/workspaces/${WS}/recurrences/r1`]: [204] })
    renderApp('/contas-fixas')
    expect(await screen.findByText('Nenhuma conta fixa neste mês.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar conta fixa' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Preencha descrição, valor, categoria e conta.')
    expect(api.calls.some((c) => c.method === 'POST')).toBe(false)

    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    await userEvent.click(screen.getByRole('button', { name: 'Excluir Internet' }))
    await waitFor(() => expect(api.last('DELETE', `/api/workspaces/${WS}/recurrences/r1`)).toBeDefined())
    expect(confirm.mock.calls[0][0]).toMatch(/continuam nos lançamentos/)
  })

  it('conta fixa de receita: rótulo "Valor recebido" e categorias de receita', async () => {
    mockApi({ ...base, [`GET /api/workspaces/${WS}/recurrences/occurrences`]: OCCURRENCES, [`GET /api/workspaces/${WS}/recurrences`]: [] })
    renderApp('/contas-fixas')
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar Salário' }))
    expect(screen.getByLabelText('Valor recebido')).toHaveValue('R$ 3.000,00')
    await userEvent.selectOptions(screen.getByLabelText('Tipo'), 'INCOME')
    expect(within(screen.getByLabelText('Categoria')).getAllByRole('option').map((o) => o.textContent)).toEqual(['Escolha…', 'Salário'])
  })
})

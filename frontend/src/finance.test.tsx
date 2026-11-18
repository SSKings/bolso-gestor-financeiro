import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ACCOUNTS, CATEGORIES, ME, WS, mockApi } from './test/mockApi'
import { renderApp } from './test/render'
import { todayISO } from './lib/dates'

const base = {
  'GET /api/me': ME,
  [`GET /api/workspaces/${WS}/accounts`]: ACCOUNTS,
  [`GET /api/workspaces/${WS}/categories`]: CATEGORIES,
}
const dashboard = {
  month: '2026-11', income: '3000.00', expense: '350.00', net: '2650.00',
  expensesByCategory: [
    { categoryId: 'c1', name: 'Mercado', color: '#22c55e', amount: '250.00' },
    { categoryId: 'c2', name: 'Alimentação', color: '#f97316', amount: '100.00' },
  ],
  accounts: ACCOUNTS, totalBalance: '2600.30',
}
const txs = {
  items: [
    { id: 't1', type: 'EXPENSE', amount: '35.90', date: '2026-11-05', description: 'Almoço', account: { id: 'a2', name: 'Nubank' },
      category: { id: 'c2', name: 'Alimentação', color: '#f97316' }, createdBy: { id: 'u2', name: 'Bruno Lima' }, createdAt: '2026-11-05T12:00:00Z' },
    { id: 't2', type: 'TRANSFER', amount: '200.00', date: '2026-11-04', description: 'Saque', account: { id: 'a2', name: 'Nubank' },
      toAccount: { id: 'a1', name: 'Carteira' }, createdBy: { id: 'u1', name: 'Ana Souza' }, createdAt: '2026-11-04T12:00:00Z' },
  ],
  total: 2, page: 0, size: 50,
}

afterEach(() => vi.restoreAllMocks())

describe('painel', () => {
  it('mostra entradas, saídas, resultado, gastos por categoria e saldo das contas ativas', async () => {
    mockApi({ ...base, [`GET /api/workspaces/${WS}/dashboard`]: dashboard })
    renderApp('/')
    expect(await screen.findByText('R$ 3.000,00')).toBeInTheDocument()
    expect(screen.getByText('R$ 350,00')).toBeInTheDocument()
    expect(screen.getByText('R$ 2.650,00')).toBeInTheDocument()
    const cats = screen.getByRole('list', { name: 'Gastos por categoria' })
    expect(within(cats).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      expect.stringContaining('Mercado'), expect.stringContaining('Alimentação')])
    expect(screen.getByText('R$ 2.600,30')).toBeInTheDocument()
    expect(screen.queryByText('Antiga')).not.toBeInTheDocument()
  })

  it('navega entre meses (consulta o mês certo na API)', async () => {
    const api = mockApi({ ...base, [`GET /api/workspaces/${WS}/dashboard`]: dashboard })
    renderApp('/')
    await screen.findByText('R$ 3.000,00')
    await userEvent.click(screen.getByRole('button', { name: 'Mês anterior' }))
    await waitFor(() => expect(api.last('GET', `/api/workspaces/${WS}/dashboard`)?.search).toMatch(/month=\d{4}-\d{2}/))
    const months = api.calls.filter((c) => c.path.endsWith('/dashboard')).map((c) => new URLSearchParams(c.search).get('month'))
    expect(new Set(months).size).toBe(2)
  })
})

describe('novo lançamento', () => {
  async function openForm() {
    const api = mockApi({ ...base, [`GET /api/workspaces/${WS}/dashboard`]: dashboard,
      [`POST /api/workspaces/${WS}/transactions`]: [201, { id: 'tn' }] })
    renderApp('/')
    await screen.findByText('R$ 3.000,00')
    await userEvent.click(screen.getByRole('button', { name: 'Novo lançamento' }))
    return { api, dialog: screen.getByRole('dialog', { name: 'Novo lançamento' }) }
  }

  it('despesa: valor digitado como app de banco; manda exatamente o que a API espera', async () => {
    const { api, dialog } = await openForm()
    await userEvent.type(within(dialog).getByLabelText('Valor'), '3590')
    expect(within(dialog).getByLabelText('Valor')).toHaveValue('R$ 35,90')
    await userEvent.type(within(dialog).getByLabelText('Descrição'), 'Almoço')
    await userEvent.selectOptions(within(dialog).getByLabelText('Categoria'), 'c2')
    await userEvent.selectOptions(within(dialog).getByLabelText('Conta'), 'a2')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(api.last('POST', `/api/workspaces/${WS}/transactions`)?.body).toEqual({
      type: 'EXPENSE', amount: '35.90', date: todayISO(), description: 'Almoço', accountId: 'a2', categoryId: 'c2' })
  })

  it('só oferece categorias do tipo escolhido e ativas; contas arquivadas não aparecem', async () => {
    const { dialog } = await openForm()
    const options = () => within(within(dialog).getByLabelText('Categoria')).getAllByRole('option').map((o) => o.textContent)
    expect(options()).toEqual(['Escolha…', 'Mercado', 'Alimentação'])
    await userEvent.click(within(dialog).getByRole('button', { name: 'Receita' }))
    expect(options()).toEqual(['Escolha…', 'Salário'])
    expect(within(within(dialog).getByLabelText('Conta')).queryByText('Antiga')).not.toBeInTheDocument()
  })

  it('transferência: sem categoria, com conta de destino diferente da origem', async () => {
    const { api, dialog } = await openForm()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Transferência' }))
    expect(within(dialog).queryByLabelText('Categoria')).not.toBeInTheDocument()
    await userEvent.type(within(dialog).getByLabelText('Valor'), '20000')
    await userEvent.type(within(dialog).getByLabelText('Descrição'), 'Saque')
    await userEvent.selectOptions(within(dialog).getByLabelText('Conta'), 'a2')
    expect(within(within(dialog).getByLabelText('Para a conta')).queryByText('Nubank')).not.toBeInTheDocument()
    await userEvent.selectOptions(within(dialog).getByLabelText('Para a conta'), 'a1')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    await waitFor(() => expect(api.last('POST', `/api/workspaces/${WS}/transactions`)?.body).toEqual({
      type: 'TRANSFER', amount: '200.00', date: todayISO(), description: 'Saque', accountId: 'a2', toAccountId: 'a1' }))
  })

  it('valor zero ou campos faltando: avisa no formulário, sem chamar a API', async () => {
    const { api, dialog } = await openForm()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Informe um valor maior que zero.')
    expect(api.calls.filter((c) => c.method === 'POST')).toHaveLength(0)
  })

  it('erro da API aparece no formulário, que continua aberto', async () => {
    mockApi({ ...base, [`GET /api/workspaces/${WS}/dashboard`]: dashboard,
      [`POST /api/workspaces/${WS}/transactions`]: [400, { status: 400, message: 'Account is archived' }] })
    renderApp('/')
    await screen.findByText('R$ 3.000,00')
    await userEvent.click(screen.getByRole('button', { name: 'Novo lançamento' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Valor'), '100')
    await userEvent.type(within(dialog).getByLabelText('Descrição'), 'Café')
    await userEvent.selectOptions(within(dialog).getByLabelText('Categoria'), 'c1')
    await userEvent.selectOptions(within(dialog).getByLabelText('Conta'), 'a1')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Esta conta está arquivada.')
  })
})

describe('lançamentos', () => {
  it('lista com quem lançou, filtra por texto e exclui com confirmação', async () => {
    const api = mockApi({ ...base, [`GET /api/workspaces/${WS}/transactions`]: txs,
      [`DELETE /api/workspaces/${WS}/transactions/t1`]: [204] })
    renderApp('/lancamentos')
    expect(await screen.findByText('Almoço')).toBeInTheDocument()
    expect(screen.getByText(/Bruno Lima/)).toBeInTheDocument()
    expect(screen.getByText('Nubank → Carteira')).toBeInTheDocument()
    expect(screen.getByText('-R$ 35,90')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Buscar'), 'alm')
    await waitFor(() => expect(new URLSearchParams(api.last('GET', `/api/workspaces/${WS}/transactions`)?.search).get('q')).toBe('alm'))

    vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    await userEvent.click(screen.getByRole('button', { name: 'Excluir Almoço' }))
    expect(api.last('DELETE', `/api/workspaces/${WS}/transactions/t1`)).toBeUndefined()
    await userEvent.click(screen.getByRole('button', { name: 'Excluir Almoço' }))
    await waitFor(() => expect(api.last('DELETE', `/api/workspaces/${WS}/transactions/t1`)).toBeDefined())
  })

  it('mês vazio mostra estado vazio', async () => {
    mockApi({ ...base, [`GET /api/workspaces/${WS}/transactions`]: { items: [], total: 0, page: 0, size: 50 } })
    renderApp('/lancamentos')
    expect(await screen.findByText('Nenhum lançamento neste mês.')).toBeInTheDocument()
  })
})

describe('contas e categorias', () => {
  it('contas: cria (saldo inicial em centavos), arquiva e mostra arquivadas à parte', async () => {
    const api = mockApi({ ...base, [`POST /api/workspaces/${WS}/accounts`]: [201, { id: 'n' }],
      [`PUT /api/workspaces/${WS}/accounts/a1`]: { ...ACCOUNTS[0], archived: true } })
    renderApp('/contas')
    expect(await screen.findByText('R$ 2.450,30')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Nome'), 'Inter')
    await userEvent.selectOptions(screen.getByLabelText('Tipo'), 'SAVINGS')
    await userEvent.type(screen.getByLabelText('Saldo inicial'), '150000')
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar conta' }))
    await waitFor(() => expect(api.last('POST', `/api/workspaces/${WS}/accounts`)?.body).toEqual(
      { name: 'Inter', type: 'SAVINGS', initialBalance: '1500.00' }))
    await userEvent.click(screen.getByRole('button', { name: 'Arquivar Carteira' }))
    await waitFor(() => expect(api.last('PUT', `/api/workspaces/${WS}/accounts/a1`)?.body).toEqual(
      { name: 'Carteira', type: 'CASH', initialBalance: '0.00', archived: true }))
    expect(screen.getByRole('button', { name: 'Reativar Antiga' })).toBeInTheDocument()
  })

  it('categorias: cria no tipo da aba e arquiva', async () => {
    const api = mockApi({ ...base, [`POST /api/workspaces/${WS}/categories`]: [201, { id: 'n' }],
      [`PUT /api/workspaces/${WS}/categories/c1`]: { ...CATEGORIES[0], archived: true } })
    renderApp('/categorias')
    expect(await screen.findByText('Mercado')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Receitas' }))
    expect(screen.queryByText('Mercado')).not.toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Nome'), 'Freela')
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar categoria' }))
    await waitFor(() => expect(api.last('POST', `/api/workspaces/${WS}/categories`)?.body).toMatchObject(
      { name: 'Freela', kind: 'INCOME' }))
    await userEvent.click(screen.getByRole('button', { name: 'Despesas' }))
    await userEvent.click(screen.getByRole('button', { name: 'Arquivar Mercado' }))
    await waitFor(() => expect(api.last('PUT', `/api/workspaces/${WS}/categories/c1`)?.body).toEqual(
      { name: 'Mercado', color: '#22c55e', archived: true }))
  })
})

describe('erros mostrados ao usuário', () => {
  it('conta com nome repetido', async () => {
    mockApi({ ...base, [`POST /api/workspaces/${WS}/accounts`]: [409, { status: 409, message: 'Account name already in use' }] })
    renderApp('/contas')
    await userEvent.type(await screen.findByLabelText('Nome'), 'Nubank')
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar conta' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Já existe uma conta com esse nome.')
  })

  it('falha inesperada ao arquivar conta vira mensagem genérica', async () => {
    mockApi({ ...base, [`PUT /api/workspaces/${WS}/accounts/a1`]: [500, { status: 500, message: 'Internal error' }],
    })
    renderApp('/contas')
    await userEvent.click(await screen.findByRole('button', { name: 'Arquivar Carteira' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Algo deu errado')
  })

  it('categorias: nome repetido e falha ao arquivar', async () => {
    mockApi({ ...base, [`POST /api/workspaces/${WS}/categories`]: [409, { status: 409, message: 'Category name already in use' }],
      [`PUT /api/workspaces/${WS}/categories/c1`]: [500, {}] })
    renderApp('/categorias')
    await userEvent.type(await screen.findByLabelText('Nome'), 'Mercado')
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar categoria' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Já existe uma categoria com esse nome.')
    await userEvent.click(screen.getByRole('button', { name: 'Arquivar Mercado' }))
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Algo deu errado'))
    expect(screen.getByRole('button', { name: 'Reativar Velha' })).toBeInTheDocument()
  })

  it('painel: falha ao carregar aparece na tela', async () => {
    mockApi({ ...base, [`GET /api/workspaces/${WS}/dashboard`]: [500, {}] })
    renderApp('/')
    expect(await screen.findByRole('alert')).toHaveTextContent('Algo deu errado')
  })

  it('lançamentos: falha ao excluir aparece na tela', async () => {
    mockApi({ ...base, [`GET /api/workspaces/${WS}/transactions`]: txs,
      [`DELETE /api/workspaces/${WS}/transactions/t1`]: [404, { status: 404, message: 'Transaction not found' }] })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderApp('/lancamentos')
    await userEvent.click(await screen.findByRole('button', { name: 'Excluir Almoço' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Lançamento não encontrado.')
  })

  it('receita aparece com sinal de mais', async () => {
    mockApi({ ...base, [`GET /api/workspaces/${WS}/transactions`]: { items: [{ ...txs.items[0], id: 't9', type: 'INCOME', description: 'Salário' }], total: 1, page: 0, size: 50 } })
    renderApp('/lancamentos')
    expect(await screen.findByText('+R$ 35,90')).toBeInTheDocument()
  })
})

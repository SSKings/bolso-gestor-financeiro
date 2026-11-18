import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ACCOUNTS, CATEGORIES, ME, WS, mockApi } from './test/mockApi'
import { renderApp } from './test/render'
import { safeTelegramLink } from './pages/TelegramSection'
import { translate } from './lib/messages'

const base = {
  'GET /api/me': ME,
  [`GET /api/workspaces/${WS}/accounts`]: ACCOUNTS,
  [`GET /api/workspaces/${WS}/categories`]: CATEGORIES,
  [`GET /api/workspaces/${WS}/cards`]: [],
}
const RULES = [
  { id: 'r1', pattern: 'uber', category: { id: 'c2', name: 'Alimentação', color: '#f97316' } },
  { id: 'r2', pattern: 'supermercado', category: { id: 'c1', name: 'Mercado', color: '#22c55e' } },
]
const entry = (o: Record<string, unknown>) => ({ type: 'EXPENSE', duplicate: false, similar: false, suggestedCategory: null, ...o })
const PREVIEW = {
  accountId: 'a2', total: 4, duplicates: 1,
  entries: [
    entry({ externalId: 'ofx:1', date: '2026-11-03', amount: '35.90', description: 'PADARIA', suggestedCategory: { id: 'c2', name: 'Alimentação', color: '#f97316' } }),
    entry({ externalId: 'ofx:2', date: '2026-11-05', amount: '3500.00', type: 'INCOME', description: 'SALARIO', suggestedCategory: { id: 'c4', name: 'Salário', color: '#16a34a' } }),
    entry({ externalId: 'ofx:3', date: '2026-11-06', amount: '23.10', description: 'UBER', duplicate: true }),
    entry({ externalId: 'ofx:4', date: '2026-11-07', amount: '10.00', description: 'CAFE', similar: true, suggestedCategory: { id: 'c2', name: 'Alimentação', color: '#f97316' } }),
  ],
}
const CODE = 'A'.repeat(43)

afterEach(() => vi.restoreAllMocks())

describe('regras', () => {
  it('lista, cria com o texto e a categoria, e remove', async () => {
    const api = mockApi({ ...base, [`GET /api/workspaces/${WS}/rules`]: RULES,
      [`POST /api/workspaces/${WS}/rules`]: [201, RULES[0]], [`DELETE /api/workspaces/${WS}/rules/r2`]: [204] })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderApp('/regras')
    const list = await screen.findByRole('list', { name: 'Regras' })
    expect(within(list).getByText('uber').closest('li')).toHaveTextContent('Alimentação')
    const form = screen.getByRole('form', { name: 'Nova regra' })
    await userEvent.type(within(form).getByLabelText('Se a descrição contém'), '  ifood ')
    await userEvent.selectOptions(within(form).getByLabelText('Categoria'), 'c2')
    expect(within(within(form).getByLabelText('Categoria')).queryByRole('option', { name: 'Velha' })).not.toBeInTheDocument()
    await userEvent.click(within(form).getByRole('button', { name: 'Adicionar regra' }))
    await waitFor(() => expect(api.last('POST', `/api/workspaces/${WS}/rules`)?.body).toEqual({ pattern: 'ifood', categoryId: 'c2' }))
    await userEvent.click(screen.getByRole('button', { name: 'Remover regra supermercado' }))
    await waitFor(() => expect(api.last('DELETE', `/api/workspaces/${WS}/rules/r2`)).toBeDefined())
  })

  it('sem texto ou categoria não chama a API; regra repetida mostra o erro traduzido', async () => {
    const api = mockApi({ ...base, [`GET /api/workspaces/${WS}/rules`]: [],
      [`POST /api/workspaces/${WS}/rules`]: [409, { message: 'Rule already exists' }] })
    renderApp('/regras')
    expect(await screen.findByText(/Nenhuma regra ainda/)).toBeInTheDocument()
    const form = screen.getByRole('form', { name: 'Nova regra' })
    await userEvent.type(within(form).getByLabelText('Se a descrição contém'), 'u')
    await userEvent.click(within(form).getByRole('button', { name: 'Adicionar regra' }))
    expect(await screen.findByText(/2 letras ou mais/)).toBeInTheDocument()
    expect(api.last('POST', `/api/workspaces/${WS}/rules`)).toBeUndefined()
    await userEvent.type(within(form).getByLabelText('Se a descrição contém'), 'ber')
    await userEvent.selectOptions(within(form).getByLabelText('Categoria'), 'c1')
    await userEvent.click(within(form).getByRole('button', { name: 'Adicionar regra' }))
    expect(await screen.findByText('Já existe uma regra com esse texto.')).toBeInTheDocument()
  })
})

describe('importar extrato', () => {
  async function choose(file: File) {
    renderApp('/importar')
    const form = await screen.findByRole('form', { name: 'Arquivo do extrato' })
    await waitFor(() => expect(within(form).getByRole('option', { name: 'Nubank' })).toBeInTheDocument())
    await userEvent.selectOptions(within(form).getByLabelText('Conta'), 'a2')
    await userEvent.upload(within(form).getByLabelText(/Arquivo/), file)
    await userEvent.click(within(form).getByRole('button', { name: 'Ver prévia' }))
  }

  it('prévia: envia conta e arquivo; já importado sem caixa, parecido desmarcado; importa só o marcado, com a categoria escolhida', async () => {
    const api = mockApi({ ...base, [`POST /api/workspaces/${WS}/imports/preview`]: PREVIEW,
      [`POST /api/workspaces/${WS}/imports/commit`]: { imported: 2, skipped: 0 } })
    const file = new File(['<OFX>'], 'extrato.ofx')
    await choose(file)
    const list = await screen.findByRole('list', { name: 'Linhas do extrato' })
    const sent = api.last('POST', `/api/workspaces/${WS}/imports/preview`)!.body as FormData
    expect(sent.get('accountId')).toBe('a2')
    expect((sent.get('file') as File).name).toBe('extrato.ofx')
    expect(screen.getByText('4 no arquivo · 1 já importados')).toBeInTheDocument()
    expect(within(list).getByText('UBER').closest('li')).toHaveTextContent('já importado')
    expect(within(list).queryByRole('checkbox', { name: 'Importar UBER' })).not.toBeInTheDocument()
    expect(within(list).getByRole('checkbox', { name: 'Importar CAFE' })).not.toBeChecked()
    expect(within(list).getByText('CAFE').closest('li')).toHaveTextContent('parecido com um lançamento já feito')
    expect(within(list).getByText('SALARIO').closest('li')).toHaveTextContent('+R$ 3.500,00')
    expect(within(list).getByLabelText('Categoria de SALARIO')).toHaveValue('c4')
    expect(within(within(list).getByLabelText('Categoria de SALARIO')).queryByRole('option', { name: 'Mercado' })).not.toBeInTheDocument()

    await userEvent.selectOptions(within(list).getByLabelText('Categoria de PADARIA'), 'c1')
    await userEvent.click(screen.getByRole('button', { name: 'Importar 2 lançamentos' }))
    await waitFor(() => expect(api.last('POST', `/api/workspaces/${WS}/imports/commit`)?.body).toEqual({
      accountId: 'a2',
      entries: [
        { externalId: 'ofx:1', date: '2026-11-03', amount: '35.90', type: 'EXPENSE', description: 'PADARIA', categoryId: 'c1' },
        { externalId: 'ofx:2', date: '2026-11-05', amount: '3500.00', type: 'INCOME', description: 'SALARIO', categoryId: 'c4' },
      ],
    }))
    expect(await screen.findByText('2 lançamentos importados.')).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Linhas do extrato' })).not.toBeInTheDocument()
  })

  it('linha marcada sem categoria não vai; desmarcar tudo desliga o botão; resultado com repetidas', async () => {
    const preview = { ...PREVIEW, entries: [entry({ externalId: 'x1', date: '2026-11-03', amount: '5.00', description: 'SEM REGRA' })] }
    const api = mockApi({ ...base, [`POST /api/workspaces/${WS}/imports/preview`]: preview,
      [`POST /api/workspaces/${WS}/imports/commit`]: { imported: 1, skipped: 1 } })
    await choose(new File(['x'], 'a.csv'))
    await userEvent.click(await screen.findByRole('button', { name: 'Importar 1 lançamento' }))
    expect(await screen.findByText('Escolha a categoria de todas as linhas marcadas.')).toBeInTheDocument()
    expect(api.last('POST', `/api/workspaces/${WS}/imports/commit`)).toBeUndefined()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Importar SEM REGRA' }))
    expect(screen.getByRole('button', { name: 'Nada marcado para importar' })).toBeDisabled()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Importar SEM REGRA' }))
    await userEvent.selectOptions(screen.getByLabelText('Categoria de SEM REGRA'), 'c1')
    await userEvent.click(screen.getByRole('button', { name: 'Importar 1 lançamento' }))
    expect(await screen.findByText('1 lançamento importado (1 já existiam).')).toBeInTheDocument()
  })

  it('arquivo acima de 1 MB é barrado antes de enviar; erro do servidor com número da linha vem traduzido', async () => {
    const api = mockApi({ ...base, [`POST /api/workspaces/${WS}/imports/preview`]: [400, { message: 'Invalid amount on line 12' }] })
    const big = new File(['x'], 'grande.csv')
    Object.defineProperty(big, 'size', { value: 1_048_577 })
    await choose(big)
    expect(await screen.findByText('Arquivo grande demais (máximo 1 MB).')).toBeInTheDocument()
    expect(api.last('POST', `/api/workspaces/${WS}/imports/preview`)).toBeUndefined()
    await userEvent.upload(screen.getByLabelText('Arquivo (OFX ou CSV, até 1 MB)'), new File(['x'], 'ok.csv'))
    await userEvent.click(screen.getByRole('button', { name: 'Ver prévia' }))
    expect(await screen.findByText('Valor inválido na linha 12 do arquivo.')).toBeInTheDocument()
  })

  it('sem conta ou arquivo: pede antes de enviar', async () => {
    const api = mockApi(base)
    renderApp('/importar')
    await userEvent.click(await screen.findByRole('button', { name: 'Ver prévia' }))
    expect(await screen.findByText('Escolha a conta e o arquivo do extrato.')).toBeInTheDocument()
    expect(api.calls.some((c) => c.path.endsWith('/imports/preview'))).toBe(false)
  })
})

describe('telegram no perfil', () => {
  const linked = { available: true, botUsername: 'BolsoBot', link: { workspace: { id: WS, name: 'Pessoal' }, account: { id: 'a2', name: 'Nubank' }, linkedAt: '2026-11-10T12:00:00Z' } }

  it('sem bot configurado: avisa e não oferece ligar', async () => {
    mockApi({ ...base, 'GET /api/me/telegram': { available: false, botUsername: null, link: null } })
    renderApp('/perfil')
    const s = await screen.findByRole('region', { name: 'Telegram' })
    expect(s).toHaveTextContent('não está configurado')
    expect(within(s).queryByRole('button', { name: 'Ligar o Telegram' })).not.toBeInTheDocument()
  })

  it('ligar: pede a conta, gera o código e mostra o link do bot e o comando', async () => {
    const api = mockApi({ ...base, 'GET /api/me/telegram': { available: true, botUsername: 'BolsoBot', link: null },
      'POST /api/me/telegram/code': { code: CODE, link: `https://t.me/BolsoBot?start=${CODE}`, expiresAt: '2026-11-10T12:10:00Z' } })
    renderApp('/perfil')
    const s = await screen.findByRole('region', { name: 'Telegram' })
    await userEvent.click(within(s).getByRole('button', { name: 'Ligar o Telegram' }))
    expect(await within(s).findByText('Escolha a conta onde os lançamentos vão cair.')).toBeInTheDocument()
    await waitFor(() => expect(within(s).getByRole('option', { name: 'Nubank' })).toBeInTheDocument())
    expect(within(s).queryByRole('option', { name: 'Antiga' })).not.toBeInTheDocument()
    await userEvent.selectOptions(within(s).getByLabelText(/Conta dos lançamentos/), 'a2')
    await userEvent.click(within(s).getByRole('button', { name: 'Ligar o Telegram' }))
    const link = await within(s).findByRole('link', { name: 'Abrir no Telegram' })
    expect(link).toHaveAttribute('href', `https://t.me/BolsoBot?start=${CODE}`)
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
    expect(s).toHaveTextContent(`/start ${CODE}`)
    expect(api.last('POST', '/api/me/telegram/code')?.body).toEqual({ workspaceId: WS, accountId: 'a2' })
  })

  it('link fora do Telegram não vira botão (só o comando aparece)', async () => {
    mockApi({ ...base, 'GET /api/me/telegram': { available: true, botUsername: 'BolsoBot', link: null },
      'POST /api/me/telegram/code': { code: CODE, link: `javascript:alert(1)//${CODE}`, expiresAt: '2026-11-10T12:10:00Z' } })
    renderApp('/perfil')
    const s = await screen.findByRole('region', { name: 'Telegram' })
    await waitFor(() => expect(within(s).getByRole('option', { name: 'Nubank' })).toBeInTheDocument())
    await userEvent.selectOptions(within(s).getByLabelText(/Conta dos lançamentos/), 'a2')
    await userEvent.click(within(s).getByRole('button', { name: 'Ligar o Telegram' }))
    expect(await within(s).findByText(new RegExp(`/start ${CODE}`))).toBeInTheDocument()
    expect(within(s).queryByRole('link', { name: 'Abrir no Telegram' })).not.toBeInTheDocument()
    expect(safeTelegramLink(`https://t.me.evil.com/BolsoBot?start=${CODE}`)).toBeNull()
    expect(safeTelegramLink(`https://t.me/BolsoBot?start=${CODE}&x=1`)).toBeNull()
  })

  it('ligado: mostra espaço e conta; desligar chama a API e volta ao estado inicial', async () => {
    let state: unknown = linked
    const api = mockApi({ ...base, 'GET /api/me/telegram': () => state,
      'DELETE /api/me/telegram': () => { state = { ...linked, link: null }; return [204] } })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderApp('/perfil')
    const s = await screen.findByRole('region', { name: 'Telegram' })
    await waitFor(() => expect(s).toHaveTextContent('Ligado ao espaço Pessoal, conta Nubank'))
    await userEvent.click(within(s).getByRole('button', { name: 'Desligar Telegram' }))
    await waitFor(() => expect(api.last('DELETE', '/api/me/telegram')).toBeDefined())
    expect(await within(s).findByRole('button', { name: 'Ligar o Telegram' })).toBeInTheDocument()
  })

  it('erro ao gerar o código é mostrado traduzido', async () => {
    mockApi({ ...base, 'GET /api/me/telegram': { available: true, botUsername: 'BolsoBot', link: null },
      'POST /api/me/telegram/code': [409, { message: 'Telegram is not configured' }] })
    renderApp('/perfil')
    const s = await screen.findByRole('region', { name: 'Telegram' })
    await waitFor(() => expect(within(s).getByRole('option', { name: 'Nubank' })).toBeInTheDocument())
    await userEvent.selectOptions(within(s).getByLabelText(/Conta dos lançamentos/), 'a2')
    await userEvent.click(within(s).getByRole('button', { name: 'Ligar o Telegram' }))
    expect(await within(s).findByText('O Telegram não está configurado neste servidor.')).toBeInTheDocument()
  })
})

describe('origem dos lançamentos', () => {
  it('marca os importados e os do Telegram', async () => {
    const tx = (id: string, description: string, source: string) => ({ id, type: 'EXPENSE', amount: '10.00', date: '2026-11-03', description,
      account: { id: 'a2', name: 'Nubank' }, category: { id: 'c1', name: 'Mercado', color: '#22c55e' }, createdAt: '2026-11-03T10:00:00Z', source })
    mockApi({ ...base, [`GET /api/workspaces/${WS}/transactions`]: { items: [tx('t1', 'Pão', 'IMPORT'), tx('t2', 'Café', 'TELEGRAM'), tx('t3', 'Feira', 'MANUAL')], total: 3, page: 0, size: 100 } })
    renderApp('/lancamentos')
    expect((await screen.findByText('Pão')).closest('li')).toHaveTextContent('importado')
    expect(screen.getByText('Café').closest('li')).toHaveTextContent('via Telegram')
    expect(screen.getByText('Feira').closest('li')).not.toHaveTextContent(/importado|Telegram/)
  })
})

describe('mensagens com número', () => {
  it('traduz as mensagens do arquivo com a linha; padrão desconhecido vira genérico', () => {
    expect(translate('Invalid date on line 7')).toBe('Data inválida na linha 7 do arquivo.')
    expect(translate('Invalid date in transaction 3')).toBe('Data inválida no lançamento 3 do arquivo.')
    expect(translate('Invalid amount in transaction 2')).toBe('Valor inválido no lançamento 2 do arquivo.')
    expect(translate('Too many lines (maximum 2000)')).toBe('O arquivo tem lançamentos demais (máximo 2000). Exporte um período menor.')
    expect(translate('Invalid date on line x')).toBe('Algo deu errado. Tente de novo.')
    expect(translate(undefined)).toBe('Algo deu errado. Tente de novo.')
  })
})

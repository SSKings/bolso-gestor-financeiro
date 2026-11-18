import { api } from './client'
import type {
  AcceptedInvite, Account, AccountInput, AuthResponse, Budget, Card, CardInput, Category, CategoryCreate, CategoryUpdate, ConfirmInput,
  Dashboard, Forecast, ImportEntry, ImportPreview, ImportResult, Invite, InviteCreated, Me, Member, Occurrence, PaymentInput,
  PurchaseInput, Recurrence, RecurrenceInput, Rule, RuleInput, Statement, TelegramCode, TelegramStatus, Transaction, TransactionInput,
  TransactionPage, TransactionType, WorkspaceSummary,
} from './types'

// Endpoints da API do Bolso. Todo recurso de espaço vai em /api/workspaces/{id}/...: o servidor confere a
// participação a cada chamada (o id do usuário nunca é enviado: vem do token).

const ws = (id: string, path = '') => `/api/workspaces/${encodeURIComponent(id)}${path}`

export const auth = {
  login: (email: string, password: string) => api.post<AuthResponse>('/api/auth/login', { email, password }),
  register: (name: string, email: string, password: string) => api.post<AuthResponse>('/api/auth/register', { name, email, password }),
  logout: () => api.post<void>('/api/auth/logout'),
  me: () => api.get<Me>('/api/me'),
  changePassword: (currentPassword: string, newPassword: string) =>
    api.put<AuthResponse>('/api/me/password', { currentPassword, newPassword }),
}

export const workspaces = {
  create: (name: string) => api.post<WorkspaceSummary>('/api/workspaces', { name }),
  members: (id: string) => api.get<Member[]>(ws(id, '/members')),
  removeMember: (id: string, userId: string) => api.del(ws(id, `/members/${encodeURIComponent(userId)}`)),
  leave: (id: string) => api.post<void>(ws(id, '/leave')),
  invites: (id: string) => api.get<Invite[]>(ws(id, '/invites')),
  createInvite: (id: string) => api.post<InviteCreated>(ws(id, '/invites')),
  revokeInvite: (id: string, inviteId: string) => api.del(ws(id, `/invites/${encodeURIComponent(inviteId)}`)),
  accept: (code: string) => api.post<AcceptedInvite>(`/api/invites/${encodeURIComponent(code)}/accept`),
}

export const finance = {
  dashboard: (id: string, month: string) => api.get<Dashboard>(ws(id, `/dashboard?month=${month}`)),
  accounts: (id: string) => api.get<Account[]>(ws(id, '/accounts')),
  createAccount: (id: string, body: AccountInput) => api.post<Account>(ws(id, '/accounts'), body),
  updateAccount: (id: string, accountId: string, body: AccountInput) =>
    api.put<Account>(ws(id, `/accounts/${encodeURIComponent(accountId)}`), body),
  categories: (id: string) => api.get<Category[]>(ws(id, '/categories')),
  createCategory: (id: string, body: CategoryCreate) => api.post<Category>(ws(id, '/categories'), body),
  updateCategory: (id: string, categoryId: string, body: CategoryUpdate) =>
    api.put<Category>(ws(id, `/categories/${encodeURIComponent(categoryId)}`), body),
  transactions: (id: string, f: { month: string; q?: string; type?: TransactionType }) => {
    const params = new URLSearchParams({ month: f.month, size: '100' })
    if (f.q?.trim()) params.set('q', f.q.trim())
    if (f.type) params.set('type', f.type)
    return api.get<TransactionPage>(ws(id, `/transactions?${params}`))
  },
  createTransaction: (id: string, body: TransactionInput) => api.post<Transaction>(ws(id, '/transactions'), body),
  deleteTransaction: (id: string, txId: string) => api.del(ws(id, `/transactions/${encodeURIComponent(txId)}`)),
}

const card = (id: string, cardId: string, path = '') => ws(id, `/cards/${encodeURIComponent(cardId)}${path}`)

export const cards = {
  list: (id: string) => api.get<Card[]>(ws(id, '/cards')),
  create: (id: string, body: CardInput) => api.post<Card>(ws(id, '/cards'), body),
  update: (id: string, cardId: string, body: CardInput) => api.put<Card>(card(id, cardId), body),
  buy: (id: string, cardId: string, body: PurchaseInput) => api.post(card(id, cardId, '/purchases'), body),
  deletePurchase: (id: string, cardId: string, purchaseId: string) => api.del(card(id, cardId, `/purchases/${encodeURIComponent(purchaseId)}`)),
  statement: (id: string, cardId: string, month: string) => api.get<Statement>(card(id, cardId, `/statements/${month}`)),
  pay: (id: string, cardId: string, month: string, body: PaymentInput) =>
    api.post(card(id, cardId, `/statements/${month}/payments`), body),
}

export const planning = {
  recurrences: (id: string) => api.get<Recurrence[]>(ws(id, '/recurrences')),
  createRecurrence: (id: string, body: RecurrenceInput) => api.post<Recurrence>(ws(id, '/recurrences'), body),
  updateRecurrence: (id: string, recId: string, body: RecurrenceInput) => api.put<Recurrence>(ws(id, `/recurrences/${encodeURIComponent(recId)}`), body),
  deleteRecurrence: (id: string, recId: string) => api.del(ws(id, `/recurrences/${encodeURIComponent(recId)}`)),
  occurrences: (id: string, month: string) => api.get<Occurrence[]>(ws(id, `/recurrences/occurrences?month=${month}`)),
  confirm: (id: string, recId: string, month: string, body: ConfirmInput) =>
    api.post(ws(id, `/recurrences/${encodeURIComponent(recId)}/occurrences/${month}/confirm`), body),
  budgets: (id: string, month: string) => api.get<Budget[]>(ws(id, `/budgets?month=${month}`)),
  setBudget: (id: string, categoryId: string, amount: string) => api.put<Budget>(ws(id, `/budgets/${encodeURIComponent(categoryId)}`), { amount }),
  deleteBudget: (id: string, categoryId: string) => api.del(ws(id, `/budgets/${encodeURIComponent(categoryId)}`)),
  forecast: (id: string) => api.get<Forecast>(ws(id, '/forecast')),
}

export const automation = {
  rules: (id: string) => api.get<Rule[]>(ws(id, '/rules')),
  createRule: (id: string, body: RuleInput) => api.post<Rule>(ws(id, '/rules'), body),
  deleteRule: (id: string, ruleId: string) => api.del(ws(id, `/rules/${encodeURIComponent(ruleId)}`)),
  previewImport: (id: string, accountId: string, file: File) => {
    const form = new FormData()
    form.append('accountId', accountId)
    form.append('file', file)
    return api.upload<ImportPreview>(ws(id, '/imports/preview'), form)
  },
  commitImport: (id: string, accountId: string, entries: ImportEntry[]) =>
    api.post<ImportResult>(ws(id, '/imports/commit'), { accountId, entries }),
}

export const telegram = {
  status: () => api.get<TelegramStatus>('/api/me/telegram'),
  code: (workspaceId: string, accountId: string) => api.post<TelegramCode>('/api/me/telegram/code', { workspaceId, accountId }),
  unlink: () => api.del('/api/me/telegram'),
}

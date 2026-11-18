import { api } from './client'
import type {
  Account, AccountType, AuthResponse, Card, CardInput, Category, CategoryKind, Dashboard, Invite, InviteCreated, Me, Member,
  PurchaseInput, Statement,
  TransactionInput, TransactionPage, TransactionType, WorkspaceSummary,
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
  accept: (code: string) => api.post<{ workspaceId: string; name: string }>(`/api/invites/${encodeURIComponent(code)}/accept`),
}

export const finance = {
  dashboard: (id: string, month: string) => api.get<Dashboard>(ws(id, `/dashboard?month=${month}`)),
  accounts: (id: string) => api.get<Account[]>(ws(id, '/accounts')),
  createAccount: (id: string, body: { name: string; type: AccountType; initialBalance: string }) => api.post<Account>(ws(id, '/accounts'), body),
  updateAccount: (id: string, accountId: string, body: { name: string; type: AccountType; initialBalance: string; archived: boolean }) =>
    api.put<Account>(ws(id, `/accounts/${encodeURIComponent(accountId)}`), body),
  categories: (id: string) => api.get<Category[]>(ws(id, '/categories')),
  createCategory: (id: string, body: { name: string; kind: CategoryKind; color: string }) => api.post<Category>(ws(id, '/categories'), body),
  updateCategory: (id: string, categoryId: string, body: { name: string; color: string; archived: boolean }) =>
    api.put<Category>(ws(id, `/categories/${encodeURIComponent(categoryId)}`), body),
  transactions: (id: string, f: { month: string; q?: string; type?: TransactionType }) => {
    const params = new URLSearchParams({ month: f.month, size: '100' })
    if (f.q?.trim()) params.set('q', f.q.trim())
    if (f.type) params.set('type', f.type)
    return api.get<TransactionPage>(ws(id, `/transactions?${params}`))
  },
  createTransaction: (id: string, body: TransactionInput) => api.post(ws(id, '/transactions'), body),
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
  pay: (id: string, cardId: string, month: string, body: { accountId: string; amount: string; date: string }) =>
    api.post(card(id, cardId, `/statements/${month}/payments`), body),
}

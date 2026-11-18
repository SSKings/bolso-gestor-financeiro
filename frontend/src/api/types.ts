export type Role = 'OWNER' | 'MEMBER'
export type AccountType = 'CHECKING' | 'SAVINGS' | 'CASH' | 'INVESTMENT'
export type CategoryKind = 'INCOME' | 'EXPENSE'
export type TransactionType = 'INCOME' | 'EXPENSE' | 'TRANSFER' | 'CARD_PAYMENT'

export interface User { id: string; name: string; email: string }
export interface WorkspaceSummary { id: string; name: string; role: Role }
export interface Me extends User { workspaces: WorkspaceSummary[] }
export interface AuthResponse { token: string; user: User }

export interface Account { id: string; name: string; type: AccountType; initialBalance: string; balance: string; archived: boolean }
export interface Category { id: string; name: string; kind: CategoryKind; color: string; archived: boolean }

export interface Ref { id: string; name: string }
export interface Transaction {
  id: string; type: TransactionType; amount: string; date: string; description: string
  account: Ref; toAccount?: Ref; category?: Ref & { color: string }; card?: Ref; statement?: string; createdBy?: Ref; createdAt: string
}
export interface TransactionPage { items: Transaction[]; total: number; page: number; size: number }
export interface TransactionInput {
  type: TransactionType; amount: string; date: string; description: string
  accountId: string; categoryId?: string; toAccountId?: string
}

export interface Dashboard {
  month: string; income: string; expense: string; net: string
  expensesByCategory: { categoryId: string; name: string; color: string; amount: string }[]
  accounts: Account[]; totalBalance: string
  cards: StatementSummary[]
}

export interface Member { userId: string; name: string; email: string; role: Role; joinedAt: string }
export interface InviteCreated { id: string; code: string; expiresAt: string }
export interface Invite { id: string; createdAt: string; expiresAt: string }

export type StatementStatus = 'OPEN' | 'CLOSED' | 'PAID'
export interface Card {
  id: string; name: string; last4: string | null; closingDay: number; dueDay: number
  creditLimit: string; available: string; archived: boolean
}
export interface CardInput { name: string; last4?: string; closingDay: number; dueDay: number; creditLimit: string; archived?: boolean }
export interface PurchaseInput { description: string; amount: string; installments: number; date: string; categoryId: string }
export interface StatementItem {
  purchaseId: string; description: string; number: number; of: number; amount: string; purchaseDate: string
  category: Ref & { color: string }
}
export interface Statement {
  month: string; closingDate: string; dueDate: string; status: StatementStatus
  total: string; paid: string; remaining: string
  items: StatementItem[]
  payments: { transactionId: string; date: string; amount: string; account: Ref }[]
}
export interface StatementSummary { cardId: string; name: string; statementTotal: string; dueDate: string; status: StatementStatus }

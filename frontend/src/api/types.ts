import type { components } from './schema'

// Tipos da API derivados do contrato (docs/openapi.json → schema.d.ts, gerado por `npm run contract`).
// Não escreva tipos de resposta à mão: se o backend mudar um campo, o tsc aponta aqui e em quem usa.
//
// Exceção deliberada: nos corpos de requisição, valores em dinheiro vão como texto ("35.90"), nunca como número de
// ponto flutuante do JavaScript; o backend (BigDecimal) aceita os dois. `Money` troca esses campos por string.

type S = components['schemas']
type Money<T, K extends keyof T> = Omit<T, K> & { [P in K]: undefined extends T[P] ? string | undefined : string }

export type Role = S['WorkspaceSummary']['role']
export type AccountType = S['AccountView']['type']
export type CategoryKind = S['CategoryView']['kind']
export type TransactionType = S['TransactionView']['type']
export type TransactionSource = S['TransactionView']['source']
export type FlowType = Extract<TransactionType, 'INCOME' | 'EXPENSE'>

export type User = S['UserView']
export type WorkspaceSummary = S['WorkspaceSummary']
export type Me = S['MeResponse']
export type AuthResponse = S['AuthResponse']
export type Ref = S['Ref']
export type CategoryRef = S['CategoryRef']

export type Account = S['AccountView']
export type AccountInput = Money<S['AccountRequest'], 'initialBalance'>
export type Category = S['CategoryView']
export type CategoryCreate = S['CreateCategory']
export type CategoryUpdate = S['UpdateCategory']

export type Transaction = S['TransactionView']
export type TransactionPage = S['Page']
export type TransactionInput = Money<S['TransactionRequest'], 'amount'>

export type Rule = S['RuleView']
export type RuleInput = S['RuleRequest']
export type ImportPreviewEntry = S['PreviewEntry']
export type ImportPreview = S['Preview']
export type ImportEntry = Money<S['ImportEntry'], 'amount'>
export type ImportResult = S['CommitResult']

export type TelegramStatus = S['Status']
export type TelegramCode = S['CodeResponse']

export type Dashboard = S['Dashboard']
export type Member = S['MemberView']
export type InviteCreated = S['InviteCreated']
export type Invite = S['InviteView']
export type AcceptedInvite = S['AcceptedInvite']

export type StatementStatus = S['StatementView']['status']
export type Card = S['CardView']
export type CardInput = Money<S['CardRequest'], 'creditLimit'>
export type PurchaseInput = Money<S['PurchaseRequest'], 'amount'>
export type StatementItem = S['StatementItem']
export type Statement = S['StatementView']
export type StatementSummary = S['StatementSummary']
export type PaymentInput = Money<S['PaymentRequest'], 'amount'>

export type OccurrenceStatus = S['Occurrence']['status']
export type Recurrence = S['RecurrenceView']
export type RecurrenceInput = Money<S['RecurrenceRequest'], 'amount'>
export type ConfirmInput = Money<S['ConfirmRequest'], 'amount'>
export type Occurrence = S['Occurrence']
export type BudgetStatus = S['BudgetView']['status']
export type Budget = S['BudgetView']
export type Forecast = S['Forecast']

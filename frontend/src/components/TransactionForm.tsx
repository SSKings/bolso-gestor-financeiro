import { useState, type FormEvent } from 'react'
import { X } from 'lucide-react'
import { cards as cardsApi, finance } from '../api/bolso'
import type { Account, Card, Category, TransactionInput } from '../api/types'
import { todayISO } from '../lib/dates'
import { formatBRL, toApiAmount } from '../lib/money'
import { installmentPreview } from '../lib/billing'
import { AmountInput } from './AmountInput'
import { messageOf } from '../lib/errors'
import { ErrorAlert } from './Alert'
import { SelectField, TextField, primaryButton } from './fields'

type Mode = 'EXPENSE' | 'INCOME' | 'TRANSFER' | 'CARD'

const TYPES: { value: Mode; label: string }[] = [
  { value: 'EXPENSE', label: 'Despesa' },
  { value: 'INCOME', label: 'Receita' },
  { value: 'TRANSFER', label: 'Transferência' },
  { value: 'CARD', label: 'Cartão' },
]

/**
 * Lançamento rápido: despesa, receita, transferência ou compra no cartão (à vista ou parcelada). Data padrão: hoje.
 * Compra no cartão não sai de conta nenhuma: entra na fatura (o pagamento da fatura é que sai da conta).
 */
export function TransactionForm({ workspaceId, accounts, categories, cards, onSaved, onClose }: {
  workspaceId: string; accounts: Account[]; categories: Category[]; cards: Card[]; onSaved: () => void; onClose: () => void
}) {
  const [type, setType] = useState<Mode>('EXPENSE')
  const [cardId, setCardId] = useState('')
  const [installments, setInstallments] = useState(1)
  const [cents, setCents] = useState(0)
  const [description, setDescription] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [accountId, setAccountId] = useState('')
  const [toAccountId, setToAccountId] = useState('')
  const [date, setDate] = useState(todayISO())
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const active = accounts.filter((a) => !a.archived)
  const kind = type === 'INCOME' ? 'INCOME' : 'EXPENSE'
  const usableCategories = categories.filter((c) => !c.archived && c.kind === kind)

  const activeCards = cards.filter((c) => !c.archived)
  const preview = installmentPreview(cents, installments)

  function changeType(t: Mode) {
    setType(t)
    setCategoryId('')
    setToAccountId('')
  }

  function problem(): string {
    if (cents <= 0) return 'Informe um valor maior que zero.'
    if (!description.trim()) return 'Escreva uma descrição.'
    if (type !== 'TRANSFER' && !categoryId) return 'Escolha uma categoria.'
    if (type === 'CARD') return cardId ? '' : 'Escolha o cartão.'
    if (!accountId) return 'Escolha a conta.'
    if (type === 'TRANSFER' && !toAccountId) return 'Escolha a conta de destino.'
    return ''
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    const p = problem()
    if (p) { setError(p); return }
    setSaving(true)
    setError('')
    try {
      if (type === 'CARD') {
        await cardsApi.buy(workspaceId, cardId, { description: description.trim(), amount: toApiAmount(cents), installments, date, categoryId })
      } else {
        const body: TransactionInput = { type, amount: toApiAmount(cents), date, description: description.trim(), accountId }
        if (type === 'TRANSFER') body.toAccountId = toAccountId
        else body.categoryId = categoryId
        await finance.createTransaction(workspaceId, body)
      }
      onSaved()
    } catch (err) {
      setError(messageOf(err, 'Não foi possível salvar.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-30 flex items-end justify-center bg-black/40 sm:items-center">
      <form role="dialog" aria-modal="true" aria-labelledby="tx-title" onSubmit={submit} noValidate
        className="max-h-[92vh] w-full max-w-md space-y-4 overflow-y-auto rounded-t-2xl bg-white p-5 sm:rounded-2xl">
        <div className="flex items-center justify-between">
          <h2 id="tx-title" className="text-lg font-semibold">Novo lançamento</h2>
          <button type="button" aria-label="Fechar" onClick={onClose} className="rounded-full p-1 hover:bg-slate-100"><X size={20} /></button>
        </div>
        <div className="grid grid-cols-4 gap-1 rounded-xl bg-slate-100 p-1">
          {TYPES.map((t) => (
            <button key={t.value} type="button" aria-pressed={type === t.value} onClick={() => changeType(t.value)}
              className={`rounded-lg py-2 text-sm font-medium ${type === t.value ? 'bg-white shadow' : 'text-slate-600'}`}>{t.label}</button>
          ))}
        </div>
        <AmountInput id="tx-amount" label="Valor" cents={cents} onChange={setCents} />
        <TextField id="tx-description" label="Descrição" maxLength={140} value={description} onChange={(e) => setDescription(e.target.value)} />
        {type !== 'TRANSFER' && (
          <SelectField id="tx-category" label="Categoria" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
            <option value="">Escolha…</option>
            {usableCategories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </SelectField>
        )}
        {type === 'CARD' ? (
          <>
            <SelectField id="tx-card" label="Cartão de crédito" value={cardId} onChange={(e) => setCardId(e.target.value)}>
              <option value="">Escolha…</option>
              {activeCards.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </SelectField>
            <SelectField id="tx-installments" label="Parcelas" value={installments} onChange={(e) => setInstallments(Number(e.target.value))}>
              {Array.from({ length: 48 }, (_, i) => i + 1).map((n) => <option key={n} value={n}>{n === 1 ? 'À vista' : `${n}×`}</option>)}
            </SelectField>
            {installments > 1 && cents > 0 && (
              <p className="text-sm text-slate-600">{installments}× (1ª de {formatBRL(toApiAmount(preview.first))}, demais de {formatBRL(toApiAmount(preview.others))})</p>
            )}
          </>
        ) : (
          <SelectField id="tx-account" label="Conta" value={accountId} onChange={(e) => setAccountId(e.target.value)}>
            <option value="">Escolha…</option>
            {active.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
          </SelectField>
        )}
        {type === 'TRANSFER' && (
          <SelectField id="tx-to" label="Para a conta" value={toAccountId} onChange={(e) => setToAccountId(e.target.value)}>
            <option value="">Escolha…</option>
            {active.filter((a) => a.id !== accountId).map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
          </SelectField>
        )}
        <TextField id="tx-date" label="Data" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        <ErrorAlert message={error} />
        <button type="submit" disabled={saving} className={primaryButton}>{saving ? 'Salvando…' : 'Salvar'}</button>
      </form>
    </div>
  )
}

import { useEffect, useState, type FormEvent } from 'react'
import { planning } from '../api/bolso'
import type { Budget, BudgetStatus } from '../api/types'
import { AmountInput } from '../components/AmountInput'
import { ErrorAlert } from '../components/Alert'
import { MonthNav } from '../components/MonthNav'
import { SelectField, primaryButton } from '../components/fields'
import { currentMonth } from '../lib/dates'
import { messageOf } from '../lib/errors'
import { formatBRL, toApiAmount } from '../lib/money'
import { useWorkspace } from '../workspace/WorkspaceContext'
import { useWorkspaceData } from '../workspace/useWorkspaceData'

export const BUDGET_STATUS: Record<BudgetStatus, { label: string; bar: string; text: string }> = {
  OK: { label: 'Dentro do limite', bar: 'bg-emerald-500', text: 'text-emerald-700' },
  WARNING: { label: 'Atenção', bar: 'bg-amber-500', text: 'text-amber-700' },
  EXCEEDED: { label: 'Estourado', bar: 'bg-rose-500', text: 'text-rose-700' },
}

export function BudgetBar({ budget }: { budget: Budget }) {
  return (
    <div role="progressbar" aria-label={`Orçamento de ${budget.category.name}`} aria-valuemin={0} aria-valuemax={100}
      aria-valuenow={budget.percent} className="mt-1 h-2 rounded-full bg-slate-100">
      <div className={`h-2 rounded-full ${BUDGET_STATUS[budget.status].bar}`} style={{ width: `${Math.min(budget.percent, 100)}%` }} />
    </div>
  )
}

export function BudgetsPage() {
  const { current, version, changed } = useWorkspace()
  const { categories } = useWorkspaceData()
  const [month, setMonth] = useState(currentMonth())
  const [budgets, setBudgets] = useState<Budget[]>([])
  const [categoryId, setCategoryId] = useState('')
  const [cents, setCents] = useState(0)
  const [error, setError] = useState('')
  const id = current!.id

  useEffect(() => {
    let alive = true
    planning.budgets(id, month)
      .then((b) => { if (alive) setBudgets(b) })
      .catch((e) => { if (alive) setError(messageOf(e, 'Não foi possível carregar.')) })
    return () => { alive = false }
  }, [id, month, version])

  async function save(e: FormEvent) {
    e.preventDefault()
    if (!categoryId || cents <= 0) { setError('Escolha a categoria e o limite.'); return }
    setError('')
    try {
      await planning.setBudget(id, categoryId, toApiAmount(cents))
      setCents(0)
      changed()
    } catch (err) {
      setError(messageOf(err, 'Não foi possível salvar.'))
    }
  }

  async function remove(b: Budget) {
    if (!window.confirm(`Remover o orçamento de ${b.category.name}?`)) return
    try {
      await planning.deleteBudget(id, b.category.id)
      changed()
    } catch (err) {
      setError(messageOf(err, 'Não foi possível remover.'))
    }
  }

  return (
    <div className="space-y-5">
      <h1 className="text-lg font-semibold">Orçamentos</h1>
      <MonthNav month={month} onChange={setMonth} />
      <ErrorAlert message={error} />
      {budgets.length === 0
        ? <p className="py-4 text-center text-slate-500">Nenhum orçamento definido. Comece pelas categorias em que mais gasta.</p>
        : (
          <ul aria-label="Orçamentos do mês" className="space-y-3">
            {budgets.map((b) => (
              <li key={b.category.id} className="rounded-2xl bg-white p-4 shadow-sm">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-medium">{b.category.name}</span>
                  <span className={`text-xs font-medium ${BUDGET_STATUS[b.status].text}`}>{BUDGET_STATUS[b.status].label}</span>
                </div>
                <BudgetBar budget={b} />
                <div className="mt-1 flex justify-between text-sm text-slate-600">
                  <span className="tabular-nums">{formatBRL(b.spent)} de {formatBRL(b.amount)}</span>
                  <button type="button" aria-label={`Remover orçamento de ${b.category.name}`} onClick={() => remove(b)} className="text-xs text-rose-700">Remover</button>
                </div>
              </li>
            ))}
          </ul>
        )}
      <form onSubmit={save} noValidate className="space-y-3 rounded-2xl bg-white p-4 shadow-sm">
        <h2 className="font-semibold">Definir orçamento</h2>
        <SelectField id="budget-category" label="Categoria" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
          <option value="">Escolha…</option>
          {categories.filter((c) => c.kind === 'EXPENSE' && !c.archived).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </SelectField>
        <AmountInput id="budget-amount" label="Limite mensal" cents={cents} onChange={setCents} />
        <button type="submit" className={primaryButton}>Salvar orçamento</button>
      </form>
    </div>
  )
}

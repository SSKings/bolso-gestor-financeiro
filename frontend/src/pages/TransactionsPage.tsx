import { useEffect, useState } from 'react'
import { Trash2 } from 'lucide-react'
import { finance } from '../api/bolso'
import type { Transaction } from '../api/types'
import { messageOf } from '../lib/errors'
import { ErrorAlert } from '../components/Alert'
import { MonthNav } from '../components/MonthNav'
import { currentMonth, formatDay } from '../lib/dates'
import { formatBRL } from '../lib/money'
import { useWorkspace } from '../workspace/WorkspaceContext'

function signed(t: Transaction): string {
  if (t.type === 'EXPENSE') return `-${formatBRL(t.amount)}`
  if (t.type === 'INCOME') return `+${formatBRL(t.amount)}`
  if (t.type === 'CARD_PAYMENT') return `-${formatBRL(t.amount)}`
  return formatBRL(t.amount)
}

export function TransactionsPage() {
  const { current, version, changed } = useWorkspace()
  const [month, setMonth] = useState(currentMonth())
  const [q, setQ] = useState('')
  const [items, setItems] = useState<Transaction[] | null>(null)
  const [error, setError] = useState('')
  const id = current!.id

  useEffect(() => {
    let alive = true
    const timer = setTimeout(() => {
      finance.transactions(id, { month, q })
        .then((p) => { if (alive) { setItems(p.items); setError('') } })
        .catch((e) => { if (alive) setError(messageOf(e, 'Não foi possível carregar.')) })
    }, q ? 250 : 0)
    return () => { alive = false; clearTimeout(timer) }
  }, [id, month, q, version])

  async function remove(t: Transaction) {
    if (!window.confirm(`Excluir “${t.description}”?`)) return
    try {
      await finance.deleteTransaction(id, t.id)
      changed()
    } catch (e) {
      setError(messageOf(e, 'Não foi possível excluir.'))
    }
  }

  return (
    <div className="space-y-4">
      <h1 className="sr-only">Lançamentos</h1>
      <MonthNav month={month} onChange={setMonth} />
      <label htmlFor="tx-search" className="sr-only">Buscar</label>
      <input id="tx-search" type="search" placeholder="Buscar pela descrição" maxLength={100} value={q} onChange={(e) => setQ(e.target.value)}
        className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5" />
      <ErrorAlert message={error} />
      {items?.length === 0 && <p className="py-10 text-center text-slate-500">Nenhum lançamento neste mês.</p>}
      <ul className="divide-y divide-slate-100 rounded-2xl bg-white shadow-sm">
        {items?.map((t) => (
          <li key={t.id} className="flex items-center gap-3 px-4 py-3">
            <span aria-hidden="true" className="h-3 w-3 shrink-0 rounded-full" style={{ backgroundColor: t.category?.color ?? '#94a3b8' }} />
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium">{t.description}</p>
              <p className="truncate text-xs text-slate-500">
                {formatDay(t.date)} · <span>{t.type === 'TRANSFER' ? `${t.account.name} → ${t.toAccount?.name}`
                  : t.type === 'CARD_PAYMENT' ? `Pagamento de fatura · ${t.account.name}` : `${t.category?.name} · ${t.account.name}`}</span>
                {t.createdBy && ` · por ${t.createdBy.name}`}
              </p>
            </div>
            <span className={`tabular-nums font-semibold ${t.type === 'EXPENSE' || t.type === 'CARD_PAYMENT' ? 'text-rose-700' : t.type === 'INCOME' ? 'text-emerald-700' : 'text-slate-700'}`}>{signed(t)}</span>
            <button type="button" aria-label={`Excluir ${t.description}`} onClick={() => remove(t)} className="rounded-full p-1.5 text-slate-400 hover:bg-slate-100 hover:text-rose-600">
              <Trash2 size={16} />
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

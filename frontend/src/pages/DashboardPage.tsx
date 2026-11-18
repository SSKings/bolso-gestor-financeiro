import { useEffect, useState } from 'react'
import { finance } from '../api/bolso'
import type { Dashboard } from '../api/types'
import { messageOf } from '../lib/errors'
import { ErrorAlert } from '../components/Alert'
import { MonthNav } from '../components/MonthNav'
import { currentMonth, formatDay } from '../lib/dates'
import { Link } from 'react-router-dom'
import { formatBRL } from '../lib/money'
import { useWorkspace } from '../workspace/WorkspaceContext'

function Stat({ label, value, tone }: { label: string; value: string; tone: string }) {
  return (
    <div className="rounded-2xl bg-white p-4 shadow-sm">
      <p className="text-xs text-slate-500">{label}</p>
      <p className={`mt-1 text-lg font-semibold tabular-nums ${tone}`}>{formatBRL(value)}</p>
    </div>
  )
}

export function DashboardPage() {
  const { current, version } = useWorkspace()
  const [month, setMonth] = useState(currentMonth())
  const [data, setData] = useState<Dashboard | null>(null)
  const [error, setError] = useState('')
  const id = current!.id

  useEffect(() => {
    let alive = true
    finance.dashboard(id, month)
      .then((d) => { if (alive) { setData(d); setError('') } })
      .catch((e) => { if (alive) setError(messageOf(e, 'Não foi possível carregar.')) })
    return () => { alive = false }
  }, [id, month, version])

  const max = Math.max(...(data?.expensesByCategory.map((c) => Number(c.amount)) ?? [0]), 1)

  return (
    <div className="space-y-5">
      <MonthNav month={month} onChange={setMonth} />
      <ErrorAlert message={error} />
      {data && (
        <>
          <div className="grid grid-cols-3 gap-3">
            <Stat label="Entradas" value={data.income} tone="text-emerald-700" />
            <Stat label="Saídas" value={data.expense} tone="text-rose-700" />
            <Stat label="Resultado" value={data.net} tone={Number(data.net) < 0 ? 'text-rose-700' : 'text-slate-900'} />
          </div>

          <section className="rounded-2xl bg-white p-4 shadow-sm">
            <h2 className="mb-3 font-semibold">Para onde foi o dinheiro</h2>
            {data.expensesByCategory.length === 0
              ? <p className="text-sm text-slate-500">Nenhuma despesa neste mês.</p>
              : (
                <ul aria-label="Gastos por categoria" className="space-y-3">
                  {data.expensesByCategory.map((c) => (
                    <li key={c.categoryId}>
                      <div className="flex justify-between text-sm"><span>{c.name}</span><span className="tabular-nums">{formatBRL(c.amount)}</span></div>
                      <div className="mt-1 h-2 rounded-full bg-slate-100">
                        <div className="h-2 rounded-full" style={{ width: `${(Number(c.amount) / max) * 100}%`, backgroundColor: c.color }} />
                      </div>
                    </li>
                  ))}
                </ul>
              )}
          </section>

          {data.cards?.length > 0 && (
            <section className="rounded-2xl bg-white p-4 shadow-sm">
              <h2 className="mb-2 font-semibold">Faturas do mês</h2>
              <ul aria-label="Faturas do mês" className="divide-y divide-slate-100">
                {data.cards.map((c) => (
                  <li key={c.cardId}>
                    <Link to={`/cartoes/${c.cardId}`} className="flex justify-between py-2 text-sm">
                      <span><span>{c.name}</span> <span className="text-slate-500">· vence {formatDay(c.dueDate).slice(0, 5)}</span></span>
                      <span className="tabular-nums">{formatBRL(c.statementTotal)}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          )}

          <section className="rounded-2xl bg-white p-4 shadow-sm">
            <div className="mb-3 flex items-baseline justify-between">
              <h2 className="font-semibold">Saldo total</h2>
              <span className="text-lg font-semibold tabular-nums">{formatBRL(data.totalBalance)}</span>
            </div>
            <ul className="divide-y divide-slate-100">
              {data.accounts.filter((a) => !a.archived).map((a) => (
                <li key={a.id} className="flex justify-between py-2 text-sm">
                  <span>{a.name}</span>
                  <span className={`tabular-nums ${Number(a.balance) < 0 ? 'text-rose-700' : ''}`}>{formatBRL(a.balance)}</span>
                </li>
              ))}
            </ul>
          </section>
        </>
      )}
    </div>
  )
}

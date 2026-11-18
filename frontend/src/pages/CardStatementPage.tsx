import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Trash2 } from 'lucide-react'
import { cards as cardsApi } from '../api/bolso'
import type { Statement, StatementStatus } from '../api/types'
import { AmountInput } from '../components/AmountInput'
import { ErrorAlert } from '../components/Alert'
import { MonthNav } from '../components/MonthNav'
import { SelectField, TextField, primaryButton } from '../components/fields'
import { statementFor } from '../lib/billing'
import { formatDay, todayISO } from '../lib/dates'
import { messageOf } from '../lib/errors'
import { formatBRL, fromApiAmount, toApiAmount } from '../lib/money'
import { useWorkspace } from '../workspace/WorkspaceContext'
import { useWorkspaceData } from '../workspace/useWorkspaceData'

const STATUS: Record<StatementStatus, { label: string; tone: string }> = {
  OPEN: { label: 'Aberta', tone: 'bg-sky-100 text-sky-800' },
  CLOSED: { label: 'Fechada', tone: 'bg-amber-100 text-amber-800' },
  PAID: { label: 'Paga', tone: 'bg-emerald-100 text-emerald-800' },
}

export function CardStatementPage() {
  const { cardId = '' } = useParams()
  const { current, version, changed } = useWorkspace()
  const { accounts, cards } = useWorkspaceData()
  const card = cards.find((c) => c.id === cardId)
  const [month, setMonth] = useState<string | null>(null)
  const [st, setSt] = useState<Statement | null>(null)
  const [error, setError] = useState('')
  const [payCents, setPayCents] = useState(0)
  const [payAccount, setPayAccount] = useState('')
  const [payDate, setPayDate] = useState(todayISO())
  const id = current!.id
  // abre na fatura em que uma compra de hoje entraria; sem o cartão carregado ainda, no mês atual
  const shown = month ?? statementFor(todayISO(), card?.closingDay ?? 31)

  useEffect(() => {
    let alive = true
    cardsApi.statement(id, cardId, shown)
      .then((s) => { if (alive) { setSt(s); setError(''); setPayCents(fromApiAmount(s.remaining)) } })
      .catch((e) => { if (alive) { setSt(null); setError(messageOf(e, 'Não foi possível carregar a fatura.')) } })
    return () => { alive = false }
  }, [id, cardId, shown, version])

  async function removePurchase(purchaseId: string, description: string) {
    if (!window.confirm(`Excluir a compra “${description}” (todas as parcelas)?`)) return
    try {
      await cardsApi.deletePurchase(id, cardId, purchaseId)
      changed()
    } catch (e) {
      setError(messageOf(e, 'Não foi possível excluir a compra.'))
    }
  }

  async function pay(e: FormEvent) {
    e.preventDefault()
    if (payCents <= 0) { setError('Informe um valor maior que zero.'); return }
    if (!payAccount) { setError('Escolha a conta.'); return }
    try {
      await cardsApi.pay(id, cardId, shown, { accountId: payAccount, amount: toApiAmount(payCents), date: payDate })
      changed()
    } catch (err) {
      setError(messageOf(err, 'Não foi possível pagar a fatura.'))
    }
  }

  return (
    <div className="space-y-4">
      <Link to="/cartoes" className="text-sm text-emerald-700">← Cartões</Link>
      <h1 className="text-lg font-semibold">{card?.name ?? 'Fatura'}</h1>
      <MonthNav month={shown} onChange={setMonth} />
      <ErrorAlert message={error} />
      {st && (
        <>
          <section className="space-y-1 rounded-2xl bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between">
              <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS[st.status].tone}`}>{STATUS[st.status].label}</span>
              <span className="text-xl font-semibold tabular-nums">{formatBRL(st.total)}</span>
            </div>
            <p className="text-sm text-slate-600">Fecha em {formatDay(st.closingDate)} · vence em {formatDay(st.dueDate)}</p>
            {Number(st.paid) > 0 && <p className="text-sm text-slate-600">Pago: {formatBRL(st.paid)} · <span>Falta pagar: {formatBRL(st.remaining)}</span></p>}
          </section>

          {st.items.length === 0
            ? <p className="py-6 text-center text-slate-500">Nenhuma compra nesta fatura.</p>
            : (
              <ul className="divide-y divide-slate-100 rounded-2xl bg-white shadow-sm">
                {st.items.map((i) => (
                  <li key={`${i.purchaseId}-${i.number}`} className="flex items-center gap-3 px-4 py-3">
                    <span aria-hidden="true" className="h-3 w-3 shrink-0 rounded-full" style={{ backgroundColor: i.category.color }} />
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium">{i.description}</p>
                      <p className="text-xs text-slate-500">{formatDay(i.purchaseDate)} · {i.category.name}{i.of > 1 && <> · parcela <span>{i.number}/{i.of}</span></>}</p>
                    </div>
                    <span className="tabular-nums font-semibold">{formatBRL(i.amount)}</span>
                    <button type="button" aria-label={`Excluir compra ${i.description}`} onClick={() => removePurchase(i.purchaseId, i.description)}
                      className="rounded-full p-1.5 text-slate-400 hover:bg-slate-100 hover:text-rose-600"><Trash2 size={16} /></button>
                  </li>
                ))}
              </ul>
            )}

          {st.payments.length > 0 && (
            <ul className="space-y-1 text-sm text-slate-600">
              {st.payments.map((p) => <li key={p.transactionId}>Pago em {formatDay(p.date)}: {formatBRL(p.amount)} ({p.account.name})</li>)}
            </ul>
          )}

          {st.status !== 'PAID' && Number(st.total) > 0 && (
            <form onSubmit={pay} className="space-y-3 rounded-2xl bg-white p-4 shadow-sm">
              <h2 className="font-semibold">Pagar fatura</h2>
              <AmountInput id="pay-amount" label="Valor do pagamento" cents={payCents} onChange={setPayCents} />
              <SelectField id="pay-account" label="Pagar com a conta" value={payAccount} onChange={(e) => setPayAccount(e.target.value)}>
                <option value="">Escolha…</option>
                {accounts.filter((a) => !a.archived).map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
              </SelectField>
              <TextField id="pay-date" label="Data do pagamento" type="date" value={payDate} onChange={(e) => setPayDate(e.target.value)} />
              <button type="submit" className={primaryButton}>Pagar fatura</button>
            </form>
          )}
        </>
      )}
    </div>
  )
}

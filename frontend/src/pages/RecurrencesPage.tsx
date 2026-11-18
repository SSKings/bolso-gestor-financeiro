import { useEffect, useState, type FormEvent } from 'react'
import { planning } from '../api/bolso'
import type { FlowType, Occurrence, Recurrence } from '../api/types'
import { AmountInput } from '../components/AmountInput'
import { ErrorAlert } from '../components/Alert'
import { MonthNav } from '../components/MonthNav'
import { SelectField, TextField, primaryButton } from '../components/fields'
import { currentMonth, formatDay, todayISO } from '../lib/dates'
import { messageOf } from '../lib/errors'
import { formatBRL, fromApiAmount, toApiAmount } from '../lib/money'
import { useWorkspace } from '../workspace/WorkspaceContext'
import { useWorkspaceData } from '../workspace/useWorkspaceData'

export function statusLabel(o: Pick<Occurrence, 'status' | 'type'>): string {
  switch (o.status) {
    case 'PAID': return o.type === 'INCOME' ? 'Recebida' : 'Paga'
    case 'OVERDUE': return 'Atrasada'
    case 'DUE_SOON': return 'Vence em breve'
    default: return o.type === 'INCOME' ? 'A receber' : 'Pendente'
  }
}

const TONE: Record<Occurrence['status'], string> = {
  PAID: 'bg-emerald-100 text-emerald-800', OVERDUE: 'bg-rose-100 text-rose-800',
  DUE_SOON: 'bg-amber-100 text-amber-800', PENDING: 'bg-slate-100 text-slate-700',
}

/** Confirmação com o valor real (a conta de luz muda todo mês). Data: o vencimento, se já passou; senão, hoje. */
function ConfirmForm({ occurrence, workspaceId, onDone }: { occurrence: Occurrence; workspaceId: string; onDone: () => void }) {
  const { accounts } = useWorkspaceData()
  const [cents, setCents] = useState(fromApiAmount(occurrence.expectedAmount))
  const [accountId, setAccountId] = useState(occurrence.account.id)
  const [date, setDate] = useState(occurrence.dueDate < todayISO() ? occurrence.dueDate : todayISO())
  const [error, setError] = useState('')

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (cents <= 0) { setError('Informe um valor maior que zero.'); return }
    try {
      await planning.confirm(workspaceId, occurrence.recurrenceId, occurrence.month, { amount: toApiAmount(cents), date, accountId })
      onDone()
    } catch (err) {
      setError(messageOf(err, 'Não foi possível confirmar.'))
    }
  }

  const label = occurrence.type === 'INCOME' ? 'Valor recebido' : 'Valor pago'
  return (
    <form aria-label={`Confirmar ${occurrence.description}`} onSubmit={submit} className="mt-3 space-y-3 rounded-xl bg-slate-50 p-3">
      <AmountInput id={`conf-amount-${occurrence.recurrenceId}`} label={label} cents={cents} onChange={setCents} />
      <SelectField id={`conf-account-${occurrence.recurrenceId}`} label="Conta" value={accountId} onChange={(e) => setAccountId(e.target.value)}>
        {accounts.filter((a) => !a.archived || a.id === accountId).map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
      </SelectField>
      <TextField id={`conf-date-${occurrence.recurrenceId}`} label="Data" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
      <ErrorAlert message={error} />
      <button type="submit" className={primaryButton}>Confirmar pagamento</button>
    </form>
  )
}

export function RecurrencesPage() {
  const { current, version, changed } = useWorkspace()
  const { accounts, categories } = useWorkspaceData()
  const [month, setMonth] = useState(currentMonth())
  const [occurrences, setOccurrences] = useState<Occurrence[]>([])
  const [recurrences, setRecurrences] = useState<Recurrence[]>([])
  const [confirming, setConfirming] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [type, setType] = useState<FlowType>('EXPENSE')
  const [description, setDescription] = useState('')
  const [cents, setCents] = useState(0)
  const [day, setDay] = useState('10')
  const [categoryId, setCategoryId] = useState('')
  const [accountId, setAccountId] = useState('')
  const id = current!.id

  useEffect(() => {
    let alive = true
    Promise.all([planning.occurrences(id, month), planning.recurrences(id)])
      .then(([o, r]) => { if (alive) { setOccurrences(o); setRecurrences(r) } })
      .catch((e) => { if (alive) setError(messageOf(e, 'Não foi possível carregar.')) })
    return () => { alive = false }
  }, [id, month, version])

  async function create(e: FormEvent) {
    e.preventDefault()
    setError('')
    if (cents <= 0 || !description.trim() || !categoryId || !accountId) { setError('Preencha descrição, valor, categoria e conta.'); return }
    try {
      await planning.createRecurrence(id, { type, description: description.trim(), amount: toApiAmount(cents), dayOfMonth: Number(day),
        categoryId, accountId, startMonth: currentMonth() })
      setDescription('')
      setCents(0)
      changed()
    } catch (err) {
      setError(messageOf(err, 'Não foi possível cadastrar.'))
    }
  }

  async function toggle(r: Recurrence) {
    try {
      await planning.updateRecurrence(id, r.id, { type: r.type, description: r.description, amount: toApiAmount(fromApiAmount(r.amount)),
        dayOfMonth: r.dayOfMonth, categoryId: r.category.id, accountId: r.account.id, startMonth: r.startMonth,
        ...(r.endMonth ? { endMonth: r.endMonth } : {}), active: !r.active })
      changed()
    } catch (err) {
      setError(messageOf(err, 'Não foi possível atualizar.'))
    }
  }

  async function remove(r: Recurrence) {
    if (!window.confirm(`Excluir a conta fixa “${r.description}”? Os pagamentos já confirmados continuam nos lançamentos.`)) return
    try {
      await planning.deleteRecurrence(id, r.id)
      changed()
    } catch (err) {
      setError(messageOf(err, 'Não foi possível excluir.'))
    }
  }

  const kind = type === 'INCOME' ? 'INCOME' : 'EXPENSE'
  return (
    <div className="space-y-5">
      <h1 className="text-lg font-semibold">Contas fixas</h1>
      <MonthNav month={month} onChange={setMonth} />
      <ErrorAlert message={error} />
      {occurrences.length === 0
        ? <p className="py-4 text-center text-slate-500">Nenhuma conta fixa neste mês.</p>
        : (
          <ul aria-label="Contas do mês" className="divide-y divide-slate-100 rounded-2xl bg-white shadow-sm">
            {occurrences.map((o) => (
              <li key={o.recurrenceId} className="px-4 py-3">
                <div className="flex items-center gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">{o.description}</p>
                    <p className="text-xs text-slate-500">vence {formatDay(o.dueDate)} · {o.category.name}</p>
                  </div>
                  <span className="tabular-nums font-semibold">{formatBRL(o.paidAmount ?? o.expectedAmount)}</span>
                  <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${TONE[o.status]}`}>{statusLabel(o)}</span>
                </div>
                {o.status !== 'PAID' && (
                  confirming === o.recurrenceId
                    ? <ConfirmForm occurrence={o} workspaceId={id} onDone={() => { setConfirming(null); changed() }} />
                    : <button type="button" aria-label={`Confirmar ${o.description}`} onClick={() => setConfirming(o.recurrenceId)}
                        className="mt-2 rounded-lg border border-emerald-600 px-3 py-1 text-sm text-emerald-700">Confirmar</button>
                )}
              </li>
            ))}
          </ul>
        )}

      {recurrences.length > 0 && (
        <section>
          <h2 className="mb-2 text-sm font-semibold text-slate-500">Cadastradas</h2>
          <ul className="divide-y divide-slate-100 rounded-2xl bg-white shadow-sm">
            {recurrences.map((r) => (
              <li key={r.id} className={`flex items-center justify-between gap-2 px-4 py-3 ${r.active ? '' : 'opacity-60'}`}>
                <span className="min-w-0 truncate">{r.description} · dia {r.dayOfMonth} · {formatBRL(r.amount)}</span>
                <span className="flex gap-2">
                  <button type="button" aria-label={`${r.active ? 'Pausar' : 'Retomar'} ${r.description}`} onClick={() => toggle(r)}
                    className="rounded-lg border border-slate-300 px-2 py-1 text-xs">{r.active ? 'Pausar' : 'Retomar'}</button>
                  <button type="button" aria-label={`Excluir ${r.description}`} onClick={() => remove(r)}
                    className="rounded-lg border border-slate-300 px-2 py-1 text-xs text-rose-700">Excluir</button>
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <form onSubmit={create} noValidate className="space-y-3 rounded-2xl bg-white p-4 shadow-sm">
        <h2 className="font-semibold">Nova conta fixa</h2>
        <SelectField id="rec-type" label="Tipo" value={type} onChange={(e) => { setType(e.target.value as FlowType); setCategoryId('') }}>
          <option value="EXPENSE">Despesa (aluguel, internet, assinatura)</option>
          <option value="INCOME">Receita (salário, aluguel recebido)</option>
        </SelectField>
        <TextField id="rec-description" label="Descrição" maxLength={140} value={description} onChange={(e) => setDescription(e.target.value)} />
        <AmountInput id="rec-amount" label="Valor previsto" cents={cents} onChange={setCents} />
        <TextField id="rec-day" label="Vence no dia" type="number" min={1} max={28} value={day} onChange={(e) => setDay(e.target.value)} />
        <SelectField id="rec-category" label="Categoria" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
          <option value="">Escolha…</option>
          {categories.filter((c) => !c.archived && c.kind === kind).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </SelectField>
        <SelectField id="rec-account" label="Conta" value={accountId} onChange={(e) => setAccountId(e.target.value)}>
          <option value="">Escolha…</option>
          {accounts.filter((a) => !a.archived).map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
        </SelectField>
        <button type="submit" className={primaryButton}>Adicionar conta fixa</button>
      </form>
    </div>
  )
}

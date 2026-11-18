import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { cards as cardsApi } from '../api/bolso'
import type { Card } from '../api/types'
import { AmountInput } from '../components/AmountInput'
import { ErrorAlert } from '../components/Alert'
import { TextField, primaryButton } from '../components/fields'
import { messageOf } from '../lib/errors'
import { formatBRL, fromApiAmount, toApiAmount } from '../lib/money'
import { useWorkspace } from '../workspace/WorkspaceContext'
import { useWorkspaceData } from '../workspace/useWorkspaceData'

/** Cartões: só os 4 últimos dígitos — o número completo nunca é pedido nem guardado. */
export function CardsPage() {
  const { current, changed } = useWorkspace()
  const { cards } = useWorkspaceData()
  const [name, setName] = useState('')
  const [last4, setLast4] = useState('')
  const [closingDay, setClosingDay] = useState('10')
  const [dueDay, setDueDay] = useState('17')
  const [limit, setLimit] = useState(0)
  const [error, setError] = useState('')
  const id = current!.id

  async function create(e: FormEvent) {
    e.preventDefault()
    setError('')
    try {
      await cardsApi.create(id, {
        name: name.trim(), ...(last4 ? { last4 } : {}), closingDay: Number(closingDay), dueDay: Number(dueDay), creditLimit: toApiAmount(limit),
      })
      setName('')
      setLast4('')
      setLimit(0)
      changed()
    } catch (err) {
      setError(messageOf(err, 'Não foi possível criar o cartão.'))
    }
  }

  async function setArchived(c: Card, archived: boolean) {
    try {
      await cardsApi.update(id, c.id, { name: c.name, ...(c.last4 ? { last4: c.last4 } : {}), closingDay: c.closingDay, dueDay: c.dueDay,
        creditLimit: toApiAmount(fromApiAmount(c.creditLimit)), archived })
      changed()
    } catch (err) {
      setError(messageOf(err, 'Não foi possível atualizar o cartão.'))
    }
  }

  return (
    <div className="space-y-5">
      <h1 className="text-lg font-semibold">Cartões</h1>
      <ErrorAlert message={error} />
      <ul className="space-y-3">
        {cards.filter((c) => !c.archived).map((c) => (
          <li key={c.id} className="rounded-2xl bg-white p-4 shadow-sm">
            <div className="flex items-start justify-between gap-3">
              <Link to={`/cartoes/${c.id}`} className="min-w-0">
                <p className="font-semibold">{c.name}</p>
                {c.last4 && <p className="text-xs text-slate-500">•••• {c.last4}</p>}
                <p className="mt-1 text-sm text-slate-600">Disponível: <span className="tabular-nums">{formatBRL(c.available)} de {formatBRL(c.creditLimit)}</span></p>
                <p className="text-xs text-slate-500">Fecha dia {c.closingDay} · vence dia {c.dueDay}</p>
              </Link>
              <button type="button" aria-label={`Arquivar ${c.name}`} onClick={() => setArchived(c, true)}
                className="rounded-lg border border-slate-300 px-2 py-1 text-xs">Arquivar</button>
            </div>
          </li>
        ))}
      </ul>
      {cards.some((c) => c.archived) && (
        <section>
          <h2 className="mb-2 text-sm font-semibold text-slate-500">Arquivados</h2>
          <ul className="divide-y divide-slate-100 rounded-2xl bg-white opacity-70 shadow-sm">
            {cards.filter((c) => c.archived).map((c) => (
              <li key={c.id} className="flex items-center justify-between px-4 py-3">
                <span>{c.name}</span>
                <button type="button" aria-label={`Reativar ${c.name}`} onClick={() => setArchived(c, false)}
                  className="rounded-lg border border-slate-300 px-2 py-1 text-xs">Reativar</button>
              </li>
            ))}
          </ul>
        </section>
      )}
      <form onSubmit={create} className="space-y-3 rounded-2xl bg-white p-4 shadow-sm">
        <h2 className="font-semibold">Novo cartão</h2>
        <TextField id="card-name" label="Nome do cartão" required maxLength={60} value={name} onChange={(e) => setName(e.target.value)} />
        <TextField id="card-last4" label="Final (4 últimos dígitos)" inputMode="numeric" autoComplete="off" value={last4}
          onChange={(e) => setLast4(e.target.value.replace(/\D/g, '').slice(0, 4))} />
        <div className="grid grid-cols-2 gap-3">
          <TextField id="card-closing" label="Fecha no dia" type="number" min={1} max={28} required value={closingDay} onChange={(e) => setClosingDay(e.target.value)} />
          <TextField id="card-due" label="Vence no dia" type="number" min={1} max={28} required value={dueDay} onChange={(e) => setDueDay(e.target.value)} />
        </div>
        <AmountInput id="card-limit" label="Limite" cents={limit} onChange={setLimit} />
        <p className="text-xs text-slate-500">Nunca digite o número completo do cartão: o Bolso só precisa do final, para você reconhecer qual é.</p>
        <button type="submit" className={primaryButton}>Adicionar cartão</button>
      </form>
    </div>
  )
}

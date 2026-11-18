import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { finance } from '../api/bolso'
import type { Account, AccountType } from '../api/types'
import { AmountInput } from '../components/AmountInput'
import { messageOf } from '../lib/errors'
import { ErrorAlert } from '../components/Alert'
import { SelectField, TextField, primaryButton } from '../components/fields'
import { formatBRL, fromApiAmount, toApiAmount } from '../lib/money'
import { useWorkspace } from '../workspace/WorkspaceContext'
import { useWorkspaceData } from '../workspace/useWorkspaceData'

export const ACCOUNT_TYPES: Record<AccountType, string> = {
  CHECKING: 'Conta corrente', SAVINGS: 'Poupança', CASH: 'Dinheiro', INVESTMENT: 'Investimento',
}

export function AccountsPage() {
  const { current, changed } = useWorkspace()
  const { accounts } = useWorkspaceData()
  const [name, setName] = useState('')
  const [type, setType] = useState<AccountType>('CHECKING')
  const [cents, setCents] = useState(0)
  const [error, setError] = useState('')
  const id = current!.id

  async function create(e: FormEvent) {
    e.preventDefault()
    setError('')
    try {
      await finance.createAccount(id, { name: name.trim(), type, initialBalance: toApiAmount(cents) })
      setName('')
      setCents(0)
      changed()
    } catch (err) {
      setError(messageOf(err, 'Não foi possível criar a conta.'))
    }
  }

  async function setArchived(a: Account, archived: boolean) {
    try {
      await finance.updateAccount(id, a.id, { name: a.name, type: a.type, initialBalance: toApiAmount(fromApiAmount(a.initialBalance)), archived })
      changed()
    } catch (err) {
      setError(messageOf(err, 'Não foi possível atualizar a conta.'))
    }
  }

  const row = (a: Account) => (
    <li key={a.id} className="flex items-center justify-between gap-3 px-4 py-3">
      <div>
        <p className="font-medium">{a.name}</p>
        <p className="text-xs text-slate-500">{ACCOUNT_TYPES[a.type]}</p>
      </div>
      <div className="flex items-center gap-3">
        <span className={`tabular-nums font-semibold ${Number(a.balance) < 0 ? 'text-rose-700' : ''}`}>{formatBRL(a.balance)}</span>
        <button type="button" aria-label={`${a.archived ? 'Reativar' : 'Arquivar'} ${a.name}`} onClick={() => setArchived(a, !a.archived)}
          className="rounded-lg border border-slate-300 px-2 py-1 text-xs">{a.archived ? 'Reativar' : 'Arquivar'}</button>
      </div>
    </li>
  )

  return (
    <div className="space-y-5">
      <div className="flex items-baseline justify-between">
        <h1 className="text-lg font-semibold">Contas</h1>
        <Link to="/categorias" className="text-sm font-medium text-emerald-700">Categorias</Link>
      </div>
      <ul className="divide-y divide-slate-100 rounded-2xl bg-white shadow-sm">{accounts.filter((a) => !a.archived).map(row)}</ul>
      {accounts.some((a) => a.archived) && (
        <section>
          <h2 className="mb-2 text-sm font-semibold text-slate-500">Arquivadas</h2>
          <ul className="divide-y divide-slate-100 rounded-2xl bg-white opacity-70 shadow-sm">{accounts.filter((a) => a.archived).map(row)}</ul>
        </section>
      )}
      <form onSubmit={create} className="space-y-3 rounded-2xl bg-white p-4 shadow-sm">
        <h2 className="font-semibold">Nova conta</h2>
        <TextField id="acc-name" label="Nome" required maxLength={60} value={name} onChange={(e) => setName(e.target.value)} />
        <SelectField id="acc-type" label="Tipo" value={type} onChange={(e) => setType(e.target.value as AccountType)}>
          {Object.entries(ACCOUNT_TYPES).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </SelectField>
        <AmountInput id="acc-initial" label="Saldo inicial" cents={cents} onChange={setCents} />
        <ErrorAlert message={error} />
        <button type="submit" className={primaryButton}>Adicionar conta</button>
      </form>
    </div>
  )
}

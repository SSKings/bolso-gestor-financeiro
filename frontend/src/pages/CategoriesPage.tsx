import { useState, type FormEvent } from 'react'
import { finance } from '../api/bolso'
import type { Category, CategoryKind } from '../api/types'
import { messageOf } from '../lib/errors'
import { ErrorAlert } from '../components/Alert'
import { TextField, primaryButton } from '../components/fields'
import { useWorkspace } from '../workspace/WorkspaceContext'
import { useWorkspaceData } from '../workspace/useWorkspaceData'

export function CategoriesPage() {
  const { current, changed } = useWorkspace()
  const { categories } = useWorkspaceData()
  const [kind, setKind] = useState<CategoryKind>('EXPENSE')
  const [name, setName] = useState('')
  const [color, setColor] = useState('#10b981')
  const [error, setError] = useState('')
  const id = current!.id
  const list = categories.filter((c) => c.kind === kind)

  async function create(e: FormEvent) {
    e.preventDefault()
    setError('')
    try {
      await finance.createCategory(id, { name: name.trim(), kind, color })
      setName('')
      changed()
    } catch (err) {
      setError(messageOf(err, 'Não foi possível criar a categoria.'))
    }
  }

  async function setArchived(c: Category, archived: boolean) {
    try {
      await finance.updateCategory(id, c.id, { name: c.name, color: c.color, archived })
      changed()
    } catch (err) {
      setError(messageOf(err, 'Não foi possível atualizar a categoria.'))
    }
  }

  return (
    <div className="space-y-5">
      <h1 className="text-lg font-semibold">Categorias</h1>
      <div className="grid grid-cols-2 gap-1 rounded-xl bg-slate-100 p-1">
        {(['EXPENSE', 'INCOME'] as const).map((k) => (
          <button key={k} type="button" aria-pressed={kind === k} onClick={() => setKind(k)}
            className={`rounded-lg py-2 text-sm font-medium ${kind === k ? 'bg-white shadow' : 'text-slate-600'}`}>{k === 'EXPENSE' ? 'Despesas' : 'Receitas'}</button>
        ))}
      </div>
      <ul className="divide-y divide-slate-100 rounded-2xl bg-white shadow-sm">
        {list.map((c) => (
          <li key={c.id} className={`flex items-center justify-between px-4 py-3 ${c.archived ? 'opacity-60' : ''}`}>
            <span className="flex items-center gap-2">
              <span aria-hidden="true" className="h-3 w-3 rounded-full" style={{ backgroundColor: c.color }} />{c.name}
            </span>
            <button type="button" aria-label={`${c.archived ? 'Reativar' : 'Arquivar'} ${c.name}`} onClick={() => setArchived(c, !c.archived)}
              className="rounded-lg border border-slate-300 px-2 py-1 text-xs">{c.archived ? 'Reativar' : 'Arquivar'}</button>
          </li>
        ))}
      </ul>
      <form onSubmit={create} className="space-y-3 rounded-2xl bg-white p-4 shadow-sm">
        <h2 className="font-semibold">Nova categoria de {kind === 'EXPENSE' ? 'despesa' : 'receita'}</h2>
        <TextField id="cat-name" label="Nome" required maxLength={40} value={name} onChange={(e) => setName(e.target.value)} />
        <TextField id="cat-color" label="Cor" type="color" value={color} onChange={(e) => setColor(e.target.value)} />
        <ErrorAlert message={error} />
        <button type="submit" className={primaryButton}>Adicionar categoria</button>
      </form>
    </div>
  )
}

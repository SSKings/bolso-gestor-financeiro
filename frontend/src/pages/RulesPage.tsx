import { useEffect, useState, type FormEvent } from 'react'
import { automation } from '../api/bolso'
import type { Rule } from '../api/types'
import { ErrorAlert } from '../components/Alert'
import { SelectField, TextField, primaryButton } from '../components/fields'
import { messageOf } from '../lib/errors'
import { useWorkspace } from '../workspace/WorkspaceContext'
import { useWorkspaceData } from '../workspace/useWorkspaceData'

// Regras de categorização: "descrição contém X → categoria". Usadas na importação de extrato e no Telegram.
export function RulesPage() {
  const { current, version, changed } = useWorkspace()
  const { categories } = useWorkspaceData()
  const [rules, setRules] = useState<Rule[]>([])
  const [pattern, setPattern] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [error, setError] = useState('')
  const id = current!.id

  useEffect(() => {
    let alive = true
    automation.rules(id)
      .then((r) => { if (alive) setRules(r) })
      .catch((e) => { if (alive) setError(messageOf(e, 'Não foi possível carregar.')) })
    return () => { alive = false }
  }, [id, version])

  async function save(e: FormEvent) {
    e.preventDefault()
    if (pattern.trim().length < 2 || !categoryId) { setError('Escreva o texto (2 letras ou mais) e escolha a categoria.'); return }
    setError('')
    try {
      await automation.createRule(id, { pattern: pattern.trim(), categoryId })
      setPattern('')
      changed()
    } catch (err) {
      setError(messageOf(err, 'Não foi possível salvar.'))
    }
  }

  async function remove(r: Rule) {
    if (!window.confirm(`Remover a regra “${r.pattern}”?`)) return
    try {
      await automation.deleteRule(id, r.id)
      changed()
    } catch (err) {
      setError(messageOf(err, 'Não foi possível remover.'))
    }
  }

  const active = categories.filter((c) => !c.archived)
  return (
    <div className="space-y-5">
      <h1 className="text-lg font-semibold">Regras de categoria</h1>
      <p className="text-sm text-slate-600">
        Quando a descrição contém o texto, a categoria é sugerida sozinha — na importação de extrato e no Telegram.
        Maiúsculas e acentos não importam; se duas regras servirem, vale a mais específica.
      </p>
      <ErrorAlert message={error} />
      {rules.length === 0
        ? <p className="py-4 text-center text-slate-500">Nenhuma regra ainda. Exemplo: “uber” → Transporte.</p>
        : (
          <ul aria-label="Regras" className="divide-y divide-slate-100 rounded-2xl bg-white shadow-sm">
            {rules.map((r) => (
              <li key={r.id} className="flex items-center gap-3 px-4 py-3">
                <span className="min-w-0 flex-1 truncate">contém <strong>{r.pattern}</strong></span>
                <span className="flex items-center gap-1.5 text-sm text-slate-600">
                  <span aria-hidden="true" className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: r.category.color }} />
                  {r.category.name}
                </span>
                <button type="button" aria-label={`Remover regra ${r.pattern}`} onClick={() => remove(r)}
                  className="rounded-full p-1.5 text-slate-400 hover:bg-slate-100 hover:text-rose-600">✕</button>
              </li>
            ))}
          </ul>
        )}
      <form onSubmit={save} aria-label="Nova regra" className="space-y-3 rounded-2xl bg-white p-4 shadow-sm">
        <h2 className="font-semibold">Nova regra</h2>
        <TextField id="rule-pattern" label="Se a descrição contém" maxLength={60} value={pattern} onChange={(e) => setPattern(e.target.value)} />
        <SelectField id="rule-category" label="Categoria" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
          <option value="">Escolha…</option>
          <optgroup label="Despesas">{active.filter((c) => c.kind === 'EXPENSE').map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</optgroup>
          <optgroup label="Receitas">{active.filter((c) => c.kind === 'INCOME').map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</optgroup>
        </SelectField>
        <button type="submit" className={primaryButton}>Adicionar regra</button>
      </form>
    </div>
  )
}

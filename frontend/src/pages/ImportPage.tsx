import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { automation } from '../api/bolso'
import type { ImportPreview, ImportPreviewEntry } from '../api/types'
import { ErrorAlert, SuccessStatus } from '../components/Alert'
import { SelectField, primaryButton } from '../components/fields'
import { formatDay } from '../lib/dates'
import { messageOf } from '../lib/errors'
import { formatBRL } from '../lib/money'
import { useWorkspace } from '../workspace/WorkspaceContext'
import { useWorkspaceData } from '../workspace/useWorkspaceData'

export const MAX_FILE_BYTES = 1_048_576

type Row = ImportPreviewEntry & { include: boolean; categoryId: string }

// Importação de extrato: escolhe a conta e o arquivo → prévia (nada gravado) → revisa categorias → importa.
// Linhas já importadas não aparecem para marcar; as parecidas com um lançamento manual vêm desmarcadas.
export function ImportPage() {
  const { current, changed } = useWorkspace()
  const { accounts, categories } = useWorkspaceData()
  const [accountId, setAccountId] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [rows, setRows] = useState<Row[]>([])
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)
  const id = current!.id

  async function load(e: FormEvent) {
    e.preventDefault()
    setError('')
    setOk('')
    if (!accountId || !file) { setError('Escolha a conta e o arquivo do extrato.'); return }
    if (file.size > MAX_FILE_BYTES) { setError('Arquivo grande demais (máximo 1 MB).'); return }
    setBusy(true)
    try {
      const p = await automation.previewImport(id, accountId, file)
      setPreview(p)
      setRows(p.entries.map((en) => ({ ...en, include: !en.duplicate && !en.similar, categoryId: en.suggestedCategory?.id ?? '' })))
    } catch (err) {
      setPreview(null)
      setError(messageOf(err, 'Não foi possível ler o extrato.'))
    } finally {
      setBusy(false)
    }
  }

  function update(i: number, patch: Partial<Row>) {
    setRows((rs) => rs.map((r, j) => (j === i ? { ...r, ...patch } : r)))
  }

  const chosen = rows.filter((r) => r.include && !r.duplicate)

  async function commit() {
    setError('')
    if (chosen.some((r) => !r.categoryId)) { setError('Escolha a categoria de todas as linhas marcadas.'); return }
    setBusy(true)
    try {
      const r = await automation.commitImport(id, accountId, chosen.map((c) => ({
        externalId: c.externalId, date: c.date, amount: c.amount, type: c.type, description: c.description, categoryId: c.categoryId,
      })))
      setOk(`${r.imported} ${r.imported === 1 ? 'lançamento importado' : 'lançamentos importados'}${r.skipped ? ` (${r.skipped} já existiam)` : ''}.`)
      setPreview(null)
      setRows([])
      setFile(null)
      changed()
    } catch (err) {
      setError(messageOf(err, 'Não foi possível importar.'))
    } finally {
      setBusy(false)
    }
  }

  const options = (kind: 'INCOME' | 'EXPENSE') => categories.filter((c) => !c.archived && c.kind === kind)
  return (
    <div className="space-y-5">
      <h1 className="text-lg font-semibold">Importar extrato</h1>
      <p className="text-sm text-slate-600">
        Exporte o extrato no app do banco (OFX ou CSV) e envie aqui. Nada é gravado antes da sua revisão, e o que já foi
        importado não entra de novo. As categorias vêm das <Link to="/regras" className="font-medium text-emerald-700">regras</Link>.
      </p>
      <form onSubmit={load} aria-label="Arquivo do extrato" className="space-y-3 rounded-2xl bg-white p-4 shadow-sm">
        <SelectField id="imp-account" label="Conta" value={accountId} onChange={(e) => { setAccountId(e.target.value); setPreview(null) }}>
          <option value="">Escolha…</option>
          {accounts.filter((a) => !a.archived).map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
        </SelectField>
        <label htmlFor="imp-file" className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700">Arquivo (OFX ou CSV, até 1 MB)</span>
          <input id="imp-file" type="file" accept=".ofx,.csv,.txt,text/csv,application/x-ofx"
            onChange={(e) => { setFile(e.target.files?.[0] ?? null); setPreview(null) }} className="block w-full text-sm" />
        </label>
        <button type="submit" disabled={busy} className={primaryButton}>Ver prévia</button>
      </form>
      <ErrorAlert message={error} />
      <SuccessStatus message={ok} />
      {preview && (
        <section aria-label="Prévia" className="space-y-3">
          <p className="text-sm text-slate-600">
            {preview.total} no arquivo{preview.duplicates > 0 && ` · ${preview.duplicates} já importados`}
          </p>
          <ul aria-label="Linhas do extrato" className="divide-y divide-slate-100 rounded-2xl bg-white shadow-sm">
            {rows.map((r, i) => (
              <li key={r.externalId} className={`space-y-2 px-4 py-3 ${r.duplicate ? 'opacity-50' : ''}`}>
                <div className="flex items-center gap-3">
                  {r.duplicate
                    ? <span className="w-5 text-xs text-slate-500" aria-hidden="true">✓</span>
                    : <input type="checkbox" aria-label={`Importar ${r.description}`} checked={r.include}
                        onChange={(e) => update(i, { include: e.target.checked })} className="h-5 w-5" />}
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">{r.description}</p>
                    <p className="text-xs text-slate-500">
                      {formatDay(r.date)}
                      {r.duplicate && ' · já importado'}
                      {r.similar && !r.duplicate && ' · parecido com um lançamento já feito (mesmo dia e valor)'}
                    </p>
                  </div>
                  <span className={`tabular-nums font-semibold ${r.type === 'EXPENSE' ? 'text-rose-700' : 'text-emerald-700'}`}>
                    {r.type === 'EXPENSE' ? '−' : '+'}{formatBRL(r.amount)}
                  </span>
                </div>
                {!r.duplicate && r.include && (
                  <SelectField id={`imp-cat-${i}`} label={`Categoria de ${r.description}`} value={r.categoryId}
                    onChange={(e) => update(i, { categoryId: e.target.value })}>
                    <option value="">Escolha…</option>
                    {options(r.type).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                  </SelectField>
                )}
              </li>
            ))}
          </ul>
          <button type="button" onClick={commit} disabled={busy || chosen.length === 0} className={primaryButton}>
            {chosen.length === 0 ? 'Nada marcado para importar' : `Importar ${chosen.length} ${chosen.length === 1 ? 'lançamento' : 'lançamentos'}`}
          </button>
        </section>
      )}
    </div>
  )
}

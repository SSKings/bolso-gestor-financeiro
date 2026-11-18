import { useEffect, useState, type FormEvent } from 'react'
import { workspaces } from '../api/bolso'
import type { Invite, Member } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { messageOf } from '../lib/errors'
import { ErrorAlert } from '../components/Alert'
import { TextField, primaryButton } from '../components/fields'
import { formatDay } from '../lib/dates'
import { useWorkspace } from '../workspace/WorkspaceContext'

export function WorkspacePage() {
  const { me, refreshMe } = useAuth()
  const { current, select, version, changed } = useWorkspace()
  const [members, setMembers] = useState<Member[]>([])
  const [invites, setInvites] = useState<Invite[]>([])
  const [link, setLink] = useState('')
  const [newName, setNewName] = useState('')
  const [error, setError] = useState('')
  const ws = current!
  const owner = ws.role === 'OWNER'

  // o link gerado some só ao trocar de espaço — não a cada recarga (gerar o convite já recarrega a lista)
  useEffect(() => setLink(''), [ws.id])

  useEffect(() => {
    let alive = true
    workspaces.members(ws.id).then((m) => alive && setMembers(m)).catch(() => {})
    if (owner) workspaces.invites(ws.id).then((i) => alive && setInvites(i)).catch(() => {})
    return () => { alive = false }
  }, [ws.id, owner, version])

  const run = async (action: () => Promise<unknown>) => {
    setError('')
    try { await action() } catch (e) { setError(messageOf(e, 'Não foi possível concluir.')) }
  }

  const invite = () => run(async () => {
    const created = await workspaces.createInvite(ws.id)
    setLink(`${window.location.origin}/convite#codigo=${created.code}`)
    changed()
  })

  const remove = (m: Member) => run(async () => {
    if (!window.confirm(`Remover ${m.name} do espaço?`)) return
    await workspaces.removeMember(ws.id, m.userId)
    changed()
  })

  const revoke = (i: Invite) => run(async () => {
    await workspaces.revokeInvite(ws.id, i.id)
    changed()
  })

  const leave = () => run(async () => {
    if (!window.confirm(`Sair do espaço ${ws.name}?`)) return
    await workspaces.leave(ws.id)
    const m = await refreshMe()
    if (m?.workspaces[0]) select(m.workspaces[0].id)
  })

  const create = (e: FormEvent) => {
    e.preventDefault()
    void run(async () => {
      const created = await workspaces.create(newName.trim())
      setNewName('')
      await refreshMe()
      select(created.id)
    })
  }

  return (
    <div className="space-y-5">
      <h1 className="text-lg font-semibold">{ws.name}</h1>
      <ErrorAlert message={error} />

      <section className="rounded-2xl bg-white p-4 shadow-sm">
        <h2 className="mb-2 font-semibold">Pessoas</h2>
        <ul className="divide-y divide-slate-100">
          {members.map((m) => (
            <li key={m.userId} className="flex items-center justify-between py-2">
              <div>
                <p className="font-medium">{m.name}</p>
                <p className="text-xs text-slate-500">{m.email} · {m.role === 'OWNER' ? 'Dono' : 'Membro'}</p>
              </div>
              {owner && m.userId !== me?.id && (
                <button type="button" aria-label={`Remover ${m.name}`} onClick={() => remove(m)}
                  className="rounded-lg border border-slate-300 px-2 py-1 text-xs text-rose-700">Remover</button>
              )}
            </li>
          ))}
        </ul>
      </section>

      {owner && (
        <section className="space-y-3 rounded-2xl bg-white p-4 shadow-sm">
          <h2 className="font-semibold">Convidar</h2>
          <p className="text-sm text-slate-600">O link vale por 3 dias e para uma pessoa só. Mande por um canal de confiança.</p>
          <button type="button" onClick={invite} className={primaryButton}>Gerar convite</button>
          {link && (
            <div className="flex gap-2">
              <label htmlFor="invite-link" className="sr-only">Link do convite</label>
              <input id="invite-link" readOnly value={link} onFocus={(e) => e.target.select()} className="min-w-0 flex-1 rounded-lg border border-slate-300 px-2 text-sm" />
              <button type="button" onClick={() => navigator.clipboard?.writeText(link)} className="rounded-lg border border-slate-300 px-3 text-sm">Copiar</button>
            </div>
          )}
          {invites.length > 0 && (
            <ul className="text-sm text-slate-600">
              {invites.map((i) => (
                <li key={i.id} className="flex items-center justify-between py-1">
                  <span>Convite pendente, vence em {formatDay(i.expiresAt.slice(0, 10))}</span>
                  <button type="button" onClick={() => revoke(i)} className="text-xs text-rose-700">Revogar</button>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      <button type="button" onClick={leave} className="w-full rounded-xl border border-rose-300 px-4 py-3 font-medium text-rose-700">Sair do espaço</button>

      <form onSubmit={create} className="space-y-3 rounded-2xl bg-white p-4 shadow-sm">
        <h2 className="font-semibold">Novo espaço</h2>
        <p className="text-sm text-slate-600">Para separar a casa, uma viagem ou um negócio — cada espaço tem contas e categorias próprias.</p>
        <TextField id="ws-new" label="Nome do novo espaço" required maxLength={60} value={newName} onChange={(e) => setNewName(e.target.value)} />
        <button type="submit" className={primaryButton}>Criar espaço</button>
      </form>
    </div>
  )
}

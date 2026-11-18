import { useEffect, useState } from 'react'
import { telegram } from '../api/bolso'
import type { TelegramCode, TelegramStatus } from '../api/types'
import { ErrorAlert } from '../components/Alert'
import { SelectField, primaryButton } from '../components/fields'
import { messageOf } from '../lib/errors'
import { useWorkspace } from '../workspace/WorkspaceContext'
import { useWorkspaceData } from '../workspace/useWorkspaceData'

/** Só abre link do próprio Telegram: o endereço vem da API, mas o front confere antes de virar um href. */
export function safeTelegramLink(link: string): string | null {
  return /^https:\/\/t\.me\/[A-Za-z0-9_]{5,32}\?start=[A-Za-z0-9_-]{43}$/.test(link) ? link : null
}

// Ligar o Telegram: código de uso único (10 minutos) para o espaço e a conta escolhidos.
export function TelegramSection() {
  const { current } = useWorkspace()
  const { accounts } = useWorkspaceData()
  const [status, setStatus] = useState<TelegramStatus | null>(null)
  const [accountId, setAccountId] = useState('')
  const [code, setCode] = useState<TelegramCode | null>(null)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let alive = true
    telegram.status().then((s) => { if (alive) setStatus(s) }).catch(() => { if (alive) setStatus(null) })
    return () => { alive = false }
  }, [reload])

  async function generate() {
    setError('')
    if (!accountId) { setError('Escolha a conta onde os lançamentos vão cair.'); return }
    try {
      setCode(await telegram.code(current!.id, accountId))
    } catch (err) {
      setError(messageOf(err, 'Não foi possível gerar o código.'))
    }
  }

  async function unlink() {
    if (!window.confirm('Desligar o Telegram?')) return
    try {
      await telegram.unlink()
      setCode(null)
      setReload((n) => n + 1)
    } catch (err) {
      setError(messageOf(err, 'Não foi possível desligar.'))
    }
  }

  if (!status) return null
  const href = code ? safeTelegramLink(code.link) : null
  return (
    <section aria-label="Telegram" className="space-y-3 rounded-2xl bg-white p-4 shadow-sm">
      <h2 className="font-semibold">Telegram</h2>
      {!status.available && <p className="text-sm text-slate-600">O Telegram não está configurado neste servidor.</p>}
      {status.available && status.link && (
        <>
          <p className="text-sm text-slate-700">
            Ligado ao espaço <strong>{status.link.workspace.name}</strong>, conta <strong>{status.link.account.name}</strong>.
            Mande <code>32,50 almoço</code> para @{status.botUsername} e o gasto entra na hora; os lembretes das contas fixas chegam por lá.
          </p>
          <button type="button" onClick={unlink} className="w-full rounded-xl border border-slate-300 px-4 py-3 font-medium">Desligar Telegram</button>
        </>
      )}
      {status.available && !status.link && (
        <>
          <p className="text-sm text-slate-600">Lance gastos por mensagem e receba os lembretes das contas fixas.</p>
          <SelectField id="tg-account" label={`Conta dos lançamentos (espaço ${current?.name})`} value={accountId}
            onChange={(e) => { setAccountId(e.target.value); setCode(null) }}>
            <option value="">Escolha…</option>
            {accounts.filter((a) => !a.archived).map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
          </SelectField>
          {!code && <button type="button" onClick={generate} className={primaryButton}>Ligar o Telegram</button>}
          {code && (
            <div className="space-y-2 rounded-xl bg-emerald-50 p-3 text-sm">
              {href && <a href={href} target="_blank" rel="noopener noreferrer" className={`${primaryButton} block text-center`}>Abrir no Telegram</a>}
              <p>Ou mande ao @{status.botUsername}: <code className="break-all">/start {code.code}</code></p>
              <p className="text-slate-600">Vale por 10 minutos e uma vez só. Depois de ligar, <button type="button" className="font-medium text-emerald-700 underline" onClick={() => { setCode(null); setReload((n) => n + 1) }}>atualize</button>.</p>
            </div>
          )}
        </>
      )}
      <ErrorAlert message={error} />
    </section>
  )
}

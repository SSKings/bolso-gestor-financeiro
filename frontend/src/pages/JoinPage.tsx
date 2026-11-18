import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useWorkspace } from '../workspace/WorkspaceContext'
import { workspaces } from '../api/bolso'
import { messageOf } from '../lib/errors'
import { ErrorAlert, SuccessStatus } from '../components/Alert'
import { AuthCard } from './AuthCard'
import { pendingInvite } from './pendingInvite'

/**
 * /convite#codigo=... — o código vem no FRAGMENTO, que o navegador não manda ao servidor (não fica em log de
 * acesso). Sai da barra de endereço na hora; sem login, espera na sessão do navegador até a pessoa entrar.
 */
function codeFromHash(hash: string): string | null {
  return new URLSearchParams(hash.replace(/^#/, '')).get('codigo')
}

export function JoinPage() {
  const { token, refreshMe } = useAuth()
  const { select } = useWorkspace()
  const location = useLocation()
  const [code] = useState(() => codeFromHash(location.hash) ?? pendingInvite.get())
  const [done, setDone] = useState('')
  const [error, setError] = useState('')
  const started = useRef(false)

  useEffect(() => {
    if (window.location.hash.includes('codigo=')) {
      window.history.replaceState(null, '', window.location.pathname + window.location.search)
    }
  }, [])

  useEffect(() => {
    if (!token || !code || started.current) return
    started.current = true
    pendingInvite.clear()
    workspaces.accept(code)
      .then(async (r) => {
        await refreshMe()
        select(r.workspaceId)
        setDone(`Você entrou no espaço ${r.name}.`)
      })
      .catch((err) => setError(messageOf(err, 'Não foi possível aceitar o convite.')))
  }, [token, code, refreshMe, select])

  if (!code) return <AuthCard title="Convite"><ErrorAlert message="Link de convite incompleto. Peça o link de novo." /></AuthCard>
  if (!token) {
    pendingInvite.set(code)
    return <Navigate to="/entrar" replace />
  }
  return (
    <AuthCard title="Convite">
      {!done && !error && <p className="text-center text-slate-600">Aceitando o convite…</p>}
      <SuccessStatus message={done} />
      <ErrorAlert message={error} />
      {(done || error) && <Link to="/" className="block text-center font-medium text-emerald-700">Ir para o painel</Link>}
    </AuthCard>
  )
}

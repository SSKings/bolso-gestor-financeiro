import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { messageOf } from '../lib/errors'
import { ErrorAlert } from '../components/Alert'
import { TextField, primaryButton } from '../components/fields'
import { AuthCard } from './AuthCard'
import { pendingInvite } from './pendingInvite'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const from = (location.state as { from?: string } | null)?.from

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await login(email, password)
      navigate(pendingInvite.get() ? '/convite' : from ?? '/', { replace: true })
    } catch (err) {
      setError(messageOf(err, 'Não foi possível entrar.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthCard title="Entrar">
      {pendingInvite.get() && <p className="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-800">Entre (ou crie sua conta) para aceitar o convite.</p>}
      <form onSubmit={submit} className="space-y-4">
        <TextField id="login-email" label="E-mail" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <TextField id="login-password" label="Senha" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
        <ErrorAlert message={error} />
        <button type="submit" disabled={busy} className={primaryButton}>Entrar</button>
      </form>
      <p className="text-center text-sm text-slate-600">Não tem conta? <Link to="/cadastro" className="font-medium text-emerald-700">Criar conta</Link></p>
    </AuthCard>
  )
}

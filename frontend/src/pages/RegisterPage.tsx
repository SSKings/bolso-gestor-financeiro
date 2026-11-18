import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { messageOf } from '../lib/errors'
import { ErrorAlert } from '../components/Alert'
import { TextField, primaryButton } from '../components/fields'
import { AuthCard } from './AuthCard'
import { pendingInvite } from './pendingInvite'

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (password !== confirm) { setError('As senhas não conferem.'); return }
    setBusy(true)
    setError('')
    try {
      await register(name, email, password)
      navigate(pendingInvite.get() ? '/convite' : '/', { replace: true })
    } catch (err) {
      setError(messageOf(err, 'Não foi possível criar a conta.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthCard title="Criar conta">
      <form onSubmit={submit} className="space-y-4">
        <TextField id="reg-name" label="Nome" autoComplete="name" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
        <TextField id="reg-email" label="E-mail" type="email" autoComplete="email" required maxLength={150} value={email} onChange={(e) => setEmail(e.target.value)} />
        <TextField id="reg-password" label="Senha" type="password" autoComplete="new-password" required maxLength={72} value={password} onChange={(e) => setPassword(e.target.value)} />
        <TextField id="reg-confirm" label="Confirme a senha" type="password" autoComplete="new-password" required maxLength={72} value={confirm} onChange={(e) => setConfirm(e.target.value)} />
        <p className="text-xs text-slate-500">Mínimo de 8 caracteres, com letras e números. Evite senhas comuns.</p>
        <ErrorAlert message={error} />
        <button type="submit" disabled={busy} className={primaryButton}>Criar conta</button>
      </form>
      <p className="text-center text-sm text-slate-600">Já tem conta? <Link to="/entrar" className="font-medium text-emerald-700">Entrar</Link></p>
    </AuthCard>
  )
}

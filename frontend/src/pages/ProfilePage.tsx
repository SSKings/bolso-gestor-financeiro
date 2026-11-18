import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { auth } from '../api/bolso'
import { useAuth } from '../auth/AuthContext'
import { messageOf } from '../lib/errors'
import { ErrorAlert, SuccessStatus } from '../components/Alert'
import { TextField, primaryButton } from '../components/fields'
import { TelegramSection } from './TelegramSection'

export function ProfilePage() {
  const { me, logout, replaceToken } = useAuth()
  const navigate = useNavigate()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')

  async function change(e: FormEvent) {
    e.preventDefault()
    setError('')
    setOk('')
    try {
      const r = await auth.changePassword(current, next)
      replaceToken(r.token)
      setCurrent('')
      setNext('')
      setOk('Senha alterada. Os outros aparelhos foram desconectados.')
    } catch (err) {
      setError(messageOf(err, 'Não foi possível alterar a senha.'))
    }
  }

  async function exit() {
    await logout()
    navigate('/entrar', { replace: true })
  }

  return (
    <div className="space-y-5">
      <h1 className="text-lg font-semibold">Perfil</h1>
      <section className="rounded-2xl bg-white p-4 shadow-sm">
        <p className="font-medium">{me?.name}</p>
        <p className="text-sm text-slate-500">{me?.email}</p>
      </section>
      <TelegramSection />
      <form onSubmit={change} className="space-y-3 rounded-2xl bg-white p-4 shadow-sm">
        <h2 className="font-semibold">Trocar senha</h2>
        <TextField id="pw-current" label="Senha atual" type="password" autoComplete="current-password" required value={current} onChange={(e) => setCurrent(e.target.value)} />
        <TextField id="pw-new" label="Nova senha" type="password" autoComplete="new-password" required maxLength={72} value={next} onChange={(e) => setNext(e.target.value)} />
        <ErrorAlert message={error} />
        <SuccessStatus message={ok} />
        <button type="submit" className={primaryButton}>Salvar nova senha</button>
      </form>
      <button type="button" onClick={exit} className="w-full rounded-xl border border-slate-300 px-4 py-3 font-medium">Sair</button>
      <p className="text-center text-xs text-slate-500">"Sair" encerra a sessão em todos os aparelhos.</p>
    </div>
  )
}

import { useState } from 'react'
import { NavLink, Outlet, Link } from 'react-router-dom'
import { Home, List, Wallet, CreditCard, Users, Plus, UserRound } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { useWorkspace } from '../workspace/WorkspaceContext'
import { useWorkspaceData } from '../workspace/useWorkspaceData'
import { TransactionForm } from './TransactionForm'

const NAV = [
  { to: '/', label: 'Painel', icon: Home, end: true },
  { to: '/lancamentos', label: 'Lançamentos', icon: List },
  { to: '/contas', label: 'Contas', icon: Wallet },
  { to: '/cartoes', label: 'Cartões', icon: CreditCard },
  { to: '/espaco', label: 'Espaço', icon: Users },
]

export function Layout() {
  const { me } = useAuth()
  const { current, select, changed } = useWorkspace()
  const { accounts, categories, cards } = useWorkspaceData()
  const [adding, setAdding] = useState(false)

  return (
    <div className="min-h-screen bg-slate-50 pb-24 text-slate-900">
      <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-3xl items-center gap-3 px-4 py-3">
          <Link to="/" className="text-xl font-bold text-emerald-700">Bolso</Link>
          <label htmlFor="ws-select" className="sr-only">Espaço atual</label>
          <select id="ws-select" value={current?.id ?? ''} onChange={(e) => select(e.target.value)}
            className="min-w-0 flex-1 rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-sm">
            {me?.workspaces.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
          </select>
          <Link to="/perfil" aria-label="Perfil" className="rounded-full p-2 hover:bg-slate-100"><UserRound size={20} /></Link>
        </div>
      </header>

      <main className="mx-auto max-w-3xl px-4 py-5">
        <Outlet />
      </main>

      <button type="button" aria-label="Novo lançamento" onClick={() => setAdding(true)}
        className="fixed bottom-20 right-5 z-20 rounded-full bg-emerald-600 p-4 text-white shadow-lg hover:bg-emerald-700 sm:bottom-8">
        <Plus size={26} />
      </button>

      <nav aria-label="Principal" className="fixed inset-x-0 bottom-0 z-10 border-t border-slate-200 bg-white">
        <ul className="mx-auto grid max-w-3xl grid-cols-5">
          {NAV.map(({ to, label, icon: Icon, end }) => (
            <li key={to}>
              <NavLink to={to} end={end} className={({ isActive }) =>
                `flex flex-col items-center gap-0.5 py-2 text-xs ${isActive ? 'text-emerald-700' : 'text-slate-500'}`}>
                <Icon size={20} aria-hidden="true" />{label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      {adding && current && (
        <TransactionForm workspaceId={current.id} accounts={accounts} categories={categories} cards={cards}
          onClose={() => setAdding(false)} onSaved={() => { setAdding(false); changed() }} />
      )}
    </div>
  )
}

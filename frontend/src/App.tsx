import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { WorkspaceProvider, useWorkspace } from './workspace/WorkspaceContext'
import { Layout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { DashboardPage } from './pages/DashboardPage'
import { TransactionsPage } from './pages/TransactionsPage'
import { AccountsPage } from './pages/AccountsPage'
import { CategoriesPage } from './pages/CategoriesPage'
import { WorkspacePage } from './pages/WorkspacePage'
import { JoinPage } from './pages/JoinPage'
import { ProfilePage } from './pages/ProfilePage'
import { CardsPage } from './pages/CardsPage'
import { CardStatementPage } from './pages/CardStatementPage'
import { RecurrencesPage } from './pages/RecurrencesPage'
import { BudgetsPage } from './pages/BudgetsPage'

function RequireAuth({ children }: { children: ReactNode }) {
  const { token, me } = useAuth()
  const { current } = useWorkspace()
  const location = useLocation()
  if (!token) return <Navigate to="/entrar" replace state={{ from: location.pathname }} />
  if (!me || !current) return <p className="p-6 text-center text-slate-500">Carregando…</p>
  return <>{children}</>
}

export function App() {
  return (
    <AuthProvider>
      <WorkspaceProvider>
        <Routes>
          <Route path="/entrar" element={<LoginPage />} />
          <Route path="/cadastro" element={<RegisterPage />} />
          <Route path="/convite" element={<JoinPage />} />
          <Route element={<RequireAuth><Layout /></RequireAuth>}>
            <Route index element={<DashboardPage />} />
            <Route path="lancamentos" element={<TransactionsPage />} />
            <Route path="contas" element={<AccountsPage />} />
            <Route path="categorias" element={<CategoriesPage />} />
            <Route path="cartoes" element={<CardsPage />} />
            <Route path="cartoes/:cardId" element={<CardStatementPage />} />
            <Route path="contas-fixas" element={<RecurrencesPage />} />
            <Route path="orcamentos" element={<BudgetsPage />} />
            <Route path="espaco" element={<WorkspacePage />} />
            <Route path="perfil" element={<ProfilePage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </WorkspaceProvider>
    </AuthProvider>
  )
}

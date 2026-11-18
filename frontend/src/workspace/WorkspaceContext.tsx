import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import type { WorkspaceSummary } from '../api/types'

// Espaço atual: o escolhido por último (localStorage), se a pessoa ainda participa dele; senão, o primeiro.

const KEY = 'bolso.workspace'

interface WorkspaceState {
  current: WorkspaceSummary | null
  select: (id: string) => void
  /** Muda a cada gravação: telas que dependem dos dados recarregam. */
  version: number
  changed: () => void
}

const WorkspaceContext = createContext<WorkspaceState | null>(null)

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const { me } = useAuth()
  const [selected, setSelected] = useState<string | null>(() => localStorage.getItem(KEY))
  const [version, setVersion] = useState(0)

  const current = useMemo(() => {
    const list = me?.workspaces ?? []
    return list.find((w) => w.id === selected) ?? list[0] ?? null
  }, [me, selected])

  const select = useCallback((id: string) => {
    localStorage.setItem(KEY, id)
    setSelected(id)
  }, [])

  const changed = useCallback(() => setVersion((v) => v + 1), [])

  const value = useMemo(() => ({ current, select, version, changed }), [current, select, version, changed])
  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>
}

export function useWorkspace(): WorkspaceState {
  const ctx = useContext(WorkspaceContext)
  if (!ctx) throw new Error('useWorkspace fora do WorkspaceProvider')
  return ctx
}

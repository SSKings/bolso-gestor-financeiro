import { useEffect, useState } from 'react'
import { cards as cardsApi, finance } from '../api/bolso'
import type { Account, Card, Category } from '../api/types'
import { useWorkspace } from './WorkspaceContext'

/** Contas, categorias e cartões do espaço atual, recarregados a cada gravação. */
export function useWorkspaceData() {
  const { current, version } = useWorkspace()
  const [accounts, setAccounts] = useState<Account[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [cards, setCards] = useState<Card[]>([])
  const id = current?.id

  useEffect(() => {
    if (!id) return
    let alive = true
    Promise.all([finance.accounts(id), finance.categories(id), cardsApi.list(id).catch(() => [] as Card[])])
      .then(([a, c, k]) => { if (alive) { setAccounts(a); setCategories(c); setCards(k) } })
      .catch(() => {})
    return () => { alive = false }
  }, [id, version])

  return { accounts, categories, cards }
}

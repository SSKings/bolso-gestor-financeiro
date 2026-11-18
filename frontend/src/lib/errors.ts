import { ApiError } from '../api/client'

/** Mensagem para a tela: a da API (já traduzida) ou um texto padrão do contexto. */
export function messageOf(err: unknown, fallback: string): string {
  return err instanceof ApiError ? err.message : fallback
}

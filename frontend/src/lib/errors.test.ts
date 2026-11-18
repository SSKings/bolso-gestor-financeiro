import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/client'
import { messageOf } from './errors'

describe('messageOf', () => {
  it('usa a mensagem da API; qualquer outro erro vira o texto padrão (nada técnico na tela)', () => {
    expect(messageOf(new ApiError(400, 'Esta conta está arquivada.'), 'x')).toBe('Esta conta está arquivada.')
    expect(messageOf(new TypeError('undefined is not a function'), 'Não foi possível salvar.')).toBe('Não foi possível salvar.')
  })
})

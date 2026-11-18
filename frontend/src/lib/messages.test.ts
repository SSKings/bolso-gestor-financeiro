import { describe, expect, it } from 'vitest'
import { translate } from './messages'

describe('translate', () => {
  it('traduz mensagens conhecidas da API', () => {
    expect(translate('Invalid email or password')).toBe('E-mail ou senha incorretos.')
    expect(translate('Too many failed attempts. Try again later')).toMatch(/bloqueada/)
    expect(translate('Account is archived')).toMatch(/arquivada/)
    expect(translate('Invalid or expired invite')).toMatch(/convite/i)
  })

  it('mensagem desconhecida vira texto genérico (nada técnico na tela)', () => {
    expect(translate('NullPointerException at com.x')).toBe('Algo deu errado. Tente de novo.')
    expect(translate(undefined)).toBe('Algo deu errado. Tente de novo.')
  })
})

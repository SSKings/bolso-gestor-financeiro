import { describe, expect, it } from 'vitest'
import { centsFromDigits, formatBRL, toApiAmount, fromApiAmount } from './money'

describe('money', () => {
  it('formata em reais (pt-BR)', () => {
    expect(formatBRL('1234.5')).toBe('R$ 1.234,50')
    expect(formatBRL(-20)).toBe('-R$ 20,00')
    expect(formatBRL('0')).toBe('R$ 0,00')
  })

  it('digitação estilo app de banco: os dígitos são centavos', () => {
    expect(centsFromDigits('')).toBe(0)
    expect(centsFromDigits('5')).toBe(5)
    expect(centsFromDigits('R$ 12,34')).toBe(1234)
    expect(centsFromDigits('1.234,567')).toBe(1234567)
    expect(centsFromDigits('9'.repeat(20))).toBe(99999999999)   // teto: R$ 999.999.999,99
  })

  it('converte centavos para o formato da API e de volta, sem erro de ponto flutuante', () => {
    expect(toApiAmount(1)).toBe('0.01')
    expect(toApiAmount(1050)).toBe('10.50')
    expect(toApiAmount(99999999999)).toBe('999999999.99')
    expect(fromApiAmount('0.29')).toBe(29)
    expect(fromApiAmount('1234.5')).toBe(123450)
    expect(fromApiAmount(-15.1)).toBe(-1510)
  })
})

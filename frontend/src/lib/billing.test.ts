import { describe, expect, it } from 'vitest'
import { installmentPreview, statementFor } from './billing'

describe('billing (igual à regra da API)', () => {
  it('compra até o dia do fechamento entra na fatura do mês; depois, na seguinte', () => {
    expect(statementFor('2026-11-10', 10)).toBe('2026-11')
    expect(statementFor('2026-11-11', 10)).toBe('2026-12')
    expect(statementFor('2026-12-20', 10)).toBe('2027-01')
  })

  it('prévia das parcelas: o resto dos centavos na primeira', () => {
    expect(installmentPreview(10000, 3)).toEqual({ first: 3334, others: 3333 })
    expect(installmentPreview(1200, 1)).toEqual({ first: 1200, others: 1200 })
  })
})

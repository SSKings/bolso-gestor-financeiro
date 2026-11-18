import { describe, expect, it } from 'vitest'
import { currentMonth, monthLabel, shiftMonth, todayISO, formatDay } from './dates'

describe('dates', () => {
  it('mês atual e hoje no formato da API', () => {
    expect(currentMonth(new Date(2026, 10, 18))).toBe('2026-11')
    expect(todayISO(new Date(2026, 0, 5))).toBe('2026-01-05')
  })

  it('avança e volta meses, virando o ano', () => {
    expect(shiftMonth('2026-12', 1)).toBe('2027-01')
    expect(shiftMonth('2026-01', -1)).toBe('2025-12')
  })

  it('rótulos em português', () => {
    expect(monthLabel('2026-11')).toBe('novembro de 2026')
    expect(formatDay('2026-11-05')).toBe('05/11/2026')
  })
})

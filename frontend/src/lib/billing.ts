// Mesma regra da API (Billing.java): compra até o dia do fechamento entra na fatura do mês; depois, na seguinte.

export function statementFor(dateISO: string, closingDay: number): string {
  const [y, m, d] = dateISO.split('-').map(Number)
  const next = new Date(y, m - 1 + (d <= closingDay ? 0 : 1), 1)
  return `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}`
}

/** Valores (em centavos) da primeira parcela e das demais: o resto da divisão vai na primeira. */
export function installmentPreview(cents: number, installments: number): { first: number; others: number } {
  const base = Math.floor(cents / installments)
  return { first: base + (cents % installments), others: base }
}

// Dinheiro no front: sempre em CENTAVOS (inteiro) — nada de 0.1 + 0.2. A API recebe e devolve string decimal.

const MAX_CENTS = 99_999_999_999 // R$ 999.999.999,99, o teto da API

const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

/** "1234.5" → "R$ 1.234,50" (com espaço comum, não o espaço fixo do Intl). */
export function formatBRL(value: string | number): string {
  return brl.format(Number(value)).replace(/ /g, ' ')
}

/** Digitação de app de banco: só os dígitos contam e são centavos ("1234" → R$ 12,34). */
export function centsFromDigits(text: string): number {
  const digits = text.replace(/\D/g, '').replace(/^0+/, '').slice(0, 11)
  return Math.min(Number(digits || '0'), MAX_CENTS)
}

export function toApiAmount(cents: number): string {
  const abs = Math.abs(cents)
  return `${cents < 0 ? '-' : ''}${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, '0')}`
}

export function fromApiAmount(value: string | number): number {
  const [int, frac = ''] = String(value).split('.')
  const negative = int.startsWith('-')
  const cents = Math.abs(Number(int)) * 100 + Number((frac + '00').slice(0, 2))
  return negative ? -cents : cents
}

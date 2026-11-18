import { centsFromDigits, formatBRL, toApiAmount } from '../lib/money'

/** Valor como em app de banco: cada dígito entra pela direita (3590 → R$ 35,90). Guarda centavos (inteiro). */
export function AmountInput({ id, label, cents, onChange }: { id: string; label: string; cents: number; onChange: (c: number) => void }) {
  return (
    <label htmlFor={id} className="block">
      <span className="mb-1 block text-sm font-medium text-slate-700">{label}</span>
      <input
        id={id}
        inputMode="numeric"
        autoComplete="off"
        className="w-full rounded-xl border border-slate-300 px-4 py-3 text-2xl font-semibold tabular-nums focus:outline-none focus:ring-2 focus:ring-emerald-500"
        value={formatBRL(toApiAmount(cents))}
        onChange={(e) => onChange(centsFromDigits(e.target.value))}
      />
    </label>
  )
}

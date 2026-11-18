import { ChevronLeft, ChevronRight } from 'lucide-react'
import { monthLabel, shiftMonth } from '../lib/dates'

export function MonthNav({ month, onChange }: { month: string; onChange: (m: string) => void }) {
  return (
    <div className="flex items-center justify-between">
      <button type="button" aria-label="Mês anterior" onClick={() => onChange(shiftMonth(month, -1))}
        className="rounded-full p-2 hover:bg-slate-100"><ChevronLeft size={20} /></button>
      <h1 className="text-lg font-semibold capitalize">{monthLabel(month)}</h1>
      <button type="button" aria-label="Próximo mês" onClick={() => onChange(shiftMonth(month, 1))}
        className="rounded-full p-2 hover:bg-slate-100"><ChevronRight size={20} /></button>
    </div>
  )
}

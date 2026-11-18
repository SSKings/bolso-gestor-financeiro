import type { ReactNode } from 'react'

export function AuthCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm space-y-5 rounded-2xl bg-white p-6 shadow-sm">
        <p className="text-center text-2xl font-bold text-emerald-700">Bolso</p>
        <h1 className="text-center text-xl font-semibold">{title}</h1>
        {children}
      </div>
    </div>
  )
}

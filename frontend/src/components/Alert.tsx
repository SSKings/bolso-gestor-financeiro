export function ErrorAlert({ message }: { message: string }) {
  if (!message) return null
  return <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{message}</p>
}

export function SuccessStatus({ message }: { message: string }) {
  if (!message) return null
  return <p role="status" className="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-800">{message}</p>
}

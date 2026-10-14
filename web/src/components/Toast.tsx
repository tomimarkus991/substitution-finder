import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'

type ToastCtx = { showError: (message: string) => void }
const ToastContext = createContext<ToastCtx | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState<string | null>(null)
  const timer = useRef<number | undefined>(undefined)

  const showError = useCallback((m: string) => {
    setMessage(m)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setMessage(null), 4000)
  }, [])

  useEffect(() => () => window.clearTimeout(timer.current), [])

  return (
    <ToastContext value={{ showError }}>
      {children}
      {message && (
        <div role="alert" className="no-print fixed bottom-4 left-1/2 -translate-x-1/2 rounded bg-red-600 px-4 py-2 text-white shadow-lg">
          {message}
        </div>
      )}
    </ToastContext>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast outside ToastProvider')
  return ctx
}

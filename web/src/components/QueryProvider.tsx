import { useState, type ReactNode } from 'react'
import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { useToast } from './Toast'

export default function QueryProvider({ children }: { children: ReactNode }) {
  const { showError } = useToast()
  const [client] = useState(() => {
    // A 401 redirects to the landing page, which explains itself; no toast.
    const onError = (e: Error) => {
      if (e instanceof ApiError && e.status === 401) return
      showError(e.message)
    }
    return new QueryClient({
      defaultOptions: {
        queries: { retry: (n, e) => !(e instanceof ApiError && e.status > 0 && e.status < 500) && n < 2 },
        mutations: { retry: false },
      },
      mutationCache: new MutationCache({ onError }),
      queryCache: new QueryCache({ onError }),
    })
  })
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

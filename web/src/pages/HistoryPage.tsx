import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { Card } from '../components/ui/Card'
import { PageTitle } from '../components/ui/PageTitle'
import { longDate } from '../lib/dates'

export default function HistoryPage() {
  const days = useQuery({ queryKey: ['days'], queryFn: () => api<string[]>('/days') })

  return (
    <div className="space-y-5">
      <PageTitle hint="Varasemad päevad, kus keegi puudus.">Ajalugu</PageTitle>
      {days.isLoading && <p className="text-text-caption">Laen…</p>}
      {days.isError && <p className="font-semibold text-casual-red-darker">Ajaloo laadimine ebaõnnestus</p>}
      {days.data && days.data.length === 0 && (
        <Card className="flex items-center gap-4 text-lg">
          <span aria-hidden className="text-3xl">🗓️</span>
          <p>Asendusi pole veel tehtud</p>
        </Card>
      )}
      {days.data && days.data.length > 0 && (
        <Card className="p-2">
          <ul className="divide-y divide-slate-100">
            {days.data.map(d => (
              <li key={d}>
                <Link to={`/paev/${d}`} className="flex min-h-12 items-center justify-between px-4 py-3 font-semibold hover:underline">
                  {longDate(d)}
                  <span aria-hidden className="text-xl text-text-caption">›</span>
                </Link>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  )
}

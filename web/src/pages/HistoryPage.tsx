import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { longDate } from '../lib/dates'

export default function HistoryPage() {
  const days = useQuery({ queryKey: ['days'], queryFn: () => api<string[]>('/days') })

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold">Ajalugu</h1>
      {days.isLoading && <p className="text-sm text-gray-600">Laen…</p>}
      {days.isError && <p className="text-red-700">Ajaloo laadimine ebaõnnestus</p>}
      {days.data && days.data.length === 0 && <p className="text-gray-600">Asendusi pole veel tehtud</p>}
      {days.data && days.data.length > 0 && (
        <ul className="divide-y divide-gray-200 rounded-lg border border-gray-200 bg-white">
          {days.data.map(d => (
            <li key={d}>
              <Link to={`/paev/${d}`} className="block min-h-11 px-4 py-3 text-blue-700 hover:bg-gray-50 hover:underline">
                {longDate(d)}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

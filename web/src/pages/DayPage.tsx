import { useParams } from 'react-router'

export default function DayPage() {
  const { date } = useParams()
  return <h1 className="text-2xl font-semibold">Päev {date}</h1>
}

const PERIODS = [1, 2, 3, 4, 5, 6, 7]

export default function PeriodSelect({ label, value, onChange }: { label: string; value: number; onChange: (v: number) => void }) {
  return (
    <label className="flex flex-1 flex-col text-sm font-semibold text-text-caption sm:flex-none">
      {label}
      <select value={value} onChange={e => onChange(Number(e.target.value))} className="field mt-1">
        {PERIODS.map(p => <option key={p} value={p}>{p}. tund</option>)}
      </select>
    </label>
  )
}

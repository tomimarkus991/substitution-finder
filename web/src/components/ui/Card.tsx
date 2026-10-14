import { cn } from '../../lib/cn'

export function Card({ className, children }: { className?: string; children: React.ReactNode }) {
  return <section className={cn('rounded-3xl bg-white p-5 shadow-sm', className)}>{children}</section>
}

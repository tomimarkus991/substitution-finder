export function PageTitle({ children, hint }: { children: React.ReactNode; hint?: string }) {
  return (
    <header>
      <h1 className="text-4xl leading-none font-extrabold tracking-tight">{children}</h1>
      {hint && <p className="mt-2 text-text-caption">{hint}</p>}
    </header>
  )
}

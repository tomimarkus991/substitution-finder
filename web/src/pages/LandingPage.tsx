import { GoogleLogin } from '@react-oauth/google'
import { Navigate, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/Toast'
import { RealButton } from '../components/ui/RealButton'
import { WaveBackground } from '../components/ui/WaveBackground'
import { cn } from '../lib/cn'
import { todayIso } from '../lib/dates'

const STEPS = [
  { title: 'Lae tunniplaan', text: 'Sama PDF, mis koolil juba on. Midagi ümber teha pole vaja.', color: 'bg-casual-sky-blue' },
  { title: 'Märgi puudujad', text: 'Iga tunni kohta näed kohe, kes on vaba ja keda tasub enne küsida.', color: 'bg-casual-green' },
  { title: 'Kopeeri tabel', text: 'Päeva asenduste tabel on valmis. Kleebi Wordi või prindi välja.', color: 'bg-casual-orange' },
]

export default function LandingPage() {
  const { loggedIn, login } = useAuth()
  const { showError } = useToast()
  const navigate = useNavigate()
  if (loggedIn) return <Navigate to={`/paev/${todayIso()}`} replace />

  const signIn = (token: string) =>
    login(token).then(() => navigate(`/paev/${todayIso()}`)).catch(e => showError((e as Error).message))

  return (
    <div className="flex min-h-screen flex-col">
      <header className="mx-auto flex w-full max-w-6xl items-center gap-3 px-5 py-5">
        <img src="/favicon.svg" alt="" className="h-9 w-9" />
        <span className="text-xl font-bold tracking-tight">Asendused</span>
      </header>

      <main className="mx-auto grid w-full max-w-6xl flex-1 items-center gap-12 px-5 py-8 md:grid-cols-2 md:py-16">
        <section>
          <span className="inline-block rounded-full bg-white px-3 py-1 text-sm font-semibold text-casual-sky-blue-darker shadow-sm">
            Kooli asenduste tööriist
          </span>
          <h1 className="mt-5 text-4xl leading-tight font-extrabold tracking-tight sm:text-5xl lg:text-6xl">
            Leia asendaja{' '}
            <span className="bg-gradient-to-r from-casual-sky-blue-darker to-cyan-500 bg-clip-text text-transparent">
              minutiga
            </span>
          </h1>
          <p className="mt-5 max-w-lg text-lg text-text-caption">
            Märgi puuduvad õpetajad ja näed kohe, kes saab nende tunde anda. Tööpäeva algus ja lõpp on arvestatud,
            tabel on hommikuks valmis.
          </p>

          <div className="mt-8 flex max-w-sm flex-col gap-3 rounded-3xl bg-white p-5 shadow-sky">
            <p className="text-sm font-semibold text-text-caption">Logi sisse kooli Google'i kontoga</p>
            <GoogleLogin
              onSuccess={({ credential }) => credential && signIn(credential)}
              onError={() => showError('Google sisselogimine ebaõnnestus')}
              text="signin_with"
              shape="pill"
              size="large"
              width="300"
            />
            {import.meta.env.DEV && (
              <RealButton variant="light" size="xs" className="self-start" onClick={() => signIn('dev')}>
                Dev login
              </RealButton>
            )}
          </div>
        </section>

        <PreviewCard />
      </main>

      <section className="mx-auto grid w-full max-w-6xl gap-5 px-5 pb-16 sm:grid-cols-3">
        {STEPS.map((s, i) => (
          <div key={s.title} className="rounded-3xl bg-white p-6 shadow-sm">
            <span className={cn('flex h-10 w-10 items-center justify-center rounded-2xl text-lg font-bold text-white', s.color)}>
              {i + 1}
            </span>
            <h2 className="mt-4 text-lg font-bold">{s.title}</h2>
            <p className="mt-1 text-text-caption">{s.text}</p>
          </div>
        ))}
      </section>

      <footer className="relative">
        <WaveBackground />
        <p className="bg-[#1e3a8a] pb-6 text-center text-sm text-blue-100">Asendused · tehtud ühe kooli hommikute jaoks</p>
      </footer>
    </div>
  )
}

/** A static picture of the real picker, so a first-time visitor sees what they get. */
function PreviewCard() {
  return (
    <div aria-hidden className="relative mx-auto w-full max-w-md rotate-1 rounded-3xl bg-white p-6 shadow-sky">
      <p className="text-sm font-semibold text-text-caption">Reede · Kati Kask puudub</p>
      <p className="mt-2 text-lg font-bold">
        2. tund <span className="font-normal text-text-caption">· 4R · eesti keel</span>
      </p>

      <Group dot="bg-casual-green" label="Vaba">
        <Chip className="border-casual-green-outline bg-green-50">Mari Tamm</Chip>
        <Chip className="border-casual-green-outline bg-green-50">Jaan Lepp</Chip>
        <Chip className="border-casual-green-outline bg-green-50">Liis Saar</Chip>
      </Group>
      <Group dot="bg-casual-orange" label="Küsi enne">
        <Chip className="border-casual-orange-outline bg-orange-50">Peeter Põld</Chip>
      </Group>

      <div className="mt-5 flex gap-2">
        <RealButton variant="green" size="xs" tabIndex={-1}>✓ Mari Tamm</RealButton>
        <RealButton variant="light" size="xs" tabIndex={-1}>Koju</RealButton>
      </div>
    </div>
  )
}

function Group({ dot, label, children }: { dot: string; label: string; children: React.ReactNode }) {
  return (
    <div className="mt-4">
      <p className="flex items-center gap-2 text-sm font-semibold">
        <span className={cn('h-2.5 w-2.5 rounded-full', dot)} />
        {label}
      </p>
      <div className="mt-2 flex flex-wrap gap-2">{children}</div>
    </div>
  )
}

function Chip({ className, children }: { className: string; children: React.ReactNode }) {
  return <span className={cn('rounded-xl border px-3 py-1.5 text-sm font-semibold', className)}>{children}</span>
}

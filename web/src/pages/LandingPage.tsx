import { GoogleLogin } from '@react-oauth/google'
import { motion, useReducedMotion } from 'motion/react'
import { Navigate, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/Toast'
import { ShaderCanvas } from '../components/shader/ShaderCanvas'
import contours from '../components/shader/contours.frag?raw'
import { LetterDecryptor } from '../components/ui/LetterDecryptor'
import { RealButton } from '../components/ui/RealButton'
import { WaveBackground } from '../components/ui/WaveBackground'
import { todayIso } from '../lib/dates'

// Estonian letters and period numbers instead of the default kanji
const GLYPHS = 'ÄÕÖÜŠŽ123456789'

// Shown under the shader, and instead of it when WebGL is missing
const HERO_FALLBACK =
  'radial-gradient(120% 90% at 20% 10%, color-mix(in srgb, var(--color-primary) 45%, transparent), transparent 60%),' +
  'radial-gradient(90% 80% at 85% 30%, color-mix(in srgb, var(--color-secondary-soft) 45%, transparent), transparent 60%),' +
  'var(--color-secondary-dark)'

export default function LandingPage() {
  const { loggedIn, login } = useAuth()
  const { showError } = useToast()
  const navigate = useNavigate()
  if (loggedIn) return <Navigate to={`/paev/${todayIso()}`} replace />

  const signIn = (token: string) =>
    login(token).then(() => navigate(`/paev/${todayIso()}`)).catch(e => showError((e as Error).message))

  return (
    <div className="relative flex min-h-svh flex-col overflow-hidden text-white" style={{ background: HERO_FALLBACK }}>
      {/* Contour lines on navy; the light lines need a dark base, so it's overridden here */}
      <ShaderCanvas fragment={contours} colors={{ bg: '#14285f' }} className="absolute inset-0 h-full w-full" />

      <header className="relative z-10 mx-auto flex w-full max-w-6xl items-center gap-3 px-5 py-5">
        <img src="/favicon.svg" alt="" className="h-10 w-10" />
        <span className="font-display text-2xl font-extrabold tracking-tight">Asendused</span>
      </header>

      <main className="relative z-10 mx-auto flex w-full max-w-6xl flex-1 flex-col justify-center px-5 pt-6 pb-16">
        <h1 className="font-display text-5xl leading-[0.95] font-extrabold tracking-tight sm:text-7xl lg:text-8xl">
          <LetterDecryptor time={700} glyphs={GLYPHS}>Keegi puudub?</LetterDecryptor>
          <br />
          <LetterDecryptor time={700} delay={500} glyphs={GLYPHS}>Asendaja on</LetterDecryptor>{' '}
          <span className="animate-text bg-hot bg-clip-text text-transparent">
            <LetterDecryptor time={600} delay={1100} glyphs={GLYPHS}>olemas.</LetterDecryptor>
          </span>
        </h1>
        <p className="mt-6 max-w-xl text-xl text-blue-100 sm:text-2xl">
          Märgi puuduvad õpetajad ja näed iga tunni kohta, kes saab asendada. Tabel on hommikuks valmis.
        </p>

        <div className="mt-10 flex w-full max-w-sm flex-col gap-3 rounded-3xl bg-surface p-5 text-text shadow-lg">
          <p className="font-semibold text-text-muted">Logi sisse kooli Google'i kontoga</p>
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
      </main>

      <Steps />

      <footer className="relative z-10">
        <WaveBackground />
        <p className="bg-[#1e3a8a] pb-6 text-center text-sm text-blue-100">Asendused · tehtud ühe kooli hommikute jaoks</p>
      </footer>
    </div>
  )
}

const icon = 'h-6 w-6 fill-none stroke-current stroke-[2.5] [stroke-linecap:round] [stroke-linejoin:round]'

const STEPS = [
  {
    title: 'Lae tunniplaan',
    text: 'Sama PDF, mis koolil juba on.',
    tile: 'bg-casual-sky-blue border-casual-sky-blue-darker',
    icon: <svg viewBox="0 0 24 24" className={icon}><path d="M12 15V4M7 9l5-5 5 5M5 15v4h14v-4" /></svg>,
  },
  {
    title: 'Märgi puudujad',
    text: 'Iga tunni kohta näed kohe, kes on vaba.',
    tile: 'bg-casual-green border-casual-green-darker',
    icon: <svg viewBox="0 0 24 24" className={icon}><circle cx="10" cy="8" r="4" /><path d="M3 20c0-4 3-6 7-6M16 15l5 5M21 15l-5 5" /></svg>,
  },
  {
    title: 'Kopeeri tabel',
    text: 'Kleebi Wordi või prindi välja.',
    tile: 'bg-casual-orange border-casual-orange-darker',
    icon: <svg viewBox="0 0 24 24" className={icon}><rect x="8" y="8" width="12" height="12" rx="2" /><path d="M16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3" /></svg>,
  },
]

/** The three steps as one row along the bottom of the hero, sliding in after the headline. */
function Steps() {
  const reduced = useReducedMotion()
  return (
    <div className="relative z-10 mx-auto w-full max-w-6xl px-5 pb-10">
      {/* Frosted panel so the contour lines don't run through the text */}
      <ol className="grid gap-6 rounded-3xl bg-secondary-dark/75 p-5 shadow-lg ring-1 ring-white/10 backdrop-blur-md sm:grid-cols-3 sm:p-6">
        {STEPS.map((s, i) => (
          <motion.li
            key={s.title}
            className="flex items-start gap-4"
            initial={reduced ? false : { opacity: 0, x: -100 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.5, ease: 'easeInOut', delay: 1.4 + i * 0.15 }}
          >
            <span className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl border-b-4 text-white ${s.tile}`}>
              {s.icon}
            </span>
            <span>
              <span className="block text-lg font-bold">{s.title}</span>
              <span className="block text-blue-50">{s.text}</span>
            </span>
          </motion.li>
        ))}
      </ol>
    </div>
  )
}

// The guide's text lives here once; the Õpetus page and the help panel both render these sections.
import { Link } from 'react-router'
import { Card } from '../components/ui/Card'
import { cn } from '../lib/cn'
import { todayIso } from '../lib/dates'

/** Step 1: uploading and activating a timetable. */
export function TimetableHelp() {
  return (
    <Step n={1} color="bg-casual-sky-blue" title="Lae tunniplaan üles" when="kui tunniplaan muutub">
      <p>
        Ava <Link to="/tunniplaan" className="font-semibold text-casual-sky-blue-darker hover:underline">Tunniplaan</Link>{' '}
        ja vali kooli õpetajate tunniplaani PDF.
      </p>
      <p>
        Eelvaates näed õpetajate ja tundide arvu ning kes on uued või eemaldatud. Kui midagi jäi lugemata, on all
        oranž hoiatus. Kui kõik on korras, vajuta <b>Aktiveeri</b>.
      </p>
      <Tip>
        Märgi linnuke <b>Pole päris õpetaja</b> ridadele nagu „Puutöö“ või „Inglise keel“. Neid saab puudujaks
        märkida, aga asendajana neid ei pakuta. Märge jääb alles ka järgmise tunniplaani juures.
      </Tip>
    </Step>
  )
}

/** Step 2: marking absent teachers. */
export function AbsenceHelp() {
  return (
    <Step n={2} color="bg-casual-red" title="Märgi puudujad" when="iga hommik">
      <p>
        Ava <Link to={`/paev/${todayIso()}`} className="font-semibold text-casual-sky-blue-darker hover:underline">Täna</Link>.
        Teise päeva saad valida kuupäevast või noolte ‹ › abil.
      </p>
      <p>
        Kirjuta otsingusse õpetaja nimi (täpitähti pole vaja, „parn“ leiab Pärnpuu), vali ta ja vajuta <b>Lisa puuduja</b>.
        Iga tema tunni kohta tekib rida.
      </p>
      <Tip>
        Kui õpetaja puudub ainult osa päevast, vali <b>Alates tunnist</b> ja <b>Kuni tunnini</b>. Näiteks kui ta tuleb
        3. tunniks, märgi 1.–2. tund. Ülejäänud päeval võib ta ise asendada.
      </Tip>
    </Step>
  )
}

/** Step 3: picking substitutes, and what Vaba / Küsi enne mean. */
export function SubstituteHelp() {
  return (
    <Step n={3} color="bg-casual-green" title="Vali asendajad" when="iga tunni kohta">
      <p>Vajuta tunni real <b>Vali asendaja</b>. Rakendus näitab ainult neid, kes saavad tundi anda:</p>
      <div className="grid gap-3 @lg:grid-cols-2">
        <Legend dot="bg-casual-green" title="Vaba">
          Õpetaja on sel päeval koolis ja tal on just siis auk, või on see tund kohe pärast tema viimast tundi.
        </Legend>
        <Legend dot="bg-casual-orange" title="Küsi enne">
          Tund on kohe enne tema esimest tundi, ehk ta peaks tulema tund varem. Lepi temaga enne kokku.
        </Legend>
      </div>
      <p>
        Puudu on need, kes sel päeval tööl pole, annavad ise tundi, juba asendavad samal ajal, ise puuduvad, või kelle
        tööpäev on liiga kaugel (näiteks 4 tundi õpetanud õpetaja ei saa enam 6. tundi anda).
      </p>
      <p>
        Kõrval on õpetaja ained ja <b>täna N(arv) asendust</b>, et koormus jaguks ühtlaselt. Valida võib mitu õpetajat. Kui
        asendajat pole, vali märkus: <b>Koju</b>, <b>Tulevad hiljem</b>, <b>Klass kokku</b>, <b>Iseseisev töö</b> või kirjuta
        oma tekst.
      </p>
      <Tip>Kõik salvestub kohe, eraldi salvestamise nuppu pole. Rida läheb roheliseks, kui tund on kaetud.</Tip>
    </Step>
  )
}

/** Step 4: copying or printing the day table. */
export function TableHelp() {
  return (
    <Step n={4} color="bg-casual-orange" title="Kopeeri tabel" when="kui kõik tunnid on kaetud">
      <p>
        Ülal olev riba näitab, mitu tundi on kaetud. Lehe all on päeva <b>asenduste tabel</b> samade veergudega nagu
        Wordi tabelis.
      </p>
      <p>
        <b>Kopeeri</b> paneb tabeli lõikelauale, kleebi see Wordi või OneDrive'i ja see jääb tabeliks. <b>Prindi</b>{' '}
        prindib ainult tabeli.
      </p>
    </Step>
  )
}

/** Rules that surprise people. */
export function GoodToKnow() {
  return (
    <Card>
      <h2 className="text-lg font-bold">Hea teada</h2>
      <ul className="mt-3 list-disc space-y-2 pl-5 text-text-caption marker:text-casual-sky-blue">
        <li>Uus tunniplaan ei muuda juba tehtud asendusi, need jäävad nii nagu olid.</li>
        <li>Tehtud asendus ei pikenda õpetaja tööpäeva: kui ta asendab 4. tundi, ei pakuta teda selle pärast 5. tunniks.</li>
        <li>
          Vanad päevad leiad <Link to="/ajalugu" className="font-semibold text-casual-sky-blue-darker hover:underline">Ajaloost</Link>.
        </li>
      </ul>
    </Card>
  )
}

type StepProps = { n: number; color: string; title: string; when: string; children: React.ReactNode }

export function Step({ n, color, title, when, children }: StepProps) {
  return (
    <Card className="flex gap-4">
      <span className={cn('flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl text-lg font-bold text-white', color)}>
        {n}
      </span>
      <div className="min-w-0 flex-1 space-y-3">
        <div>
          <h2 className="text-lg font-bold">{title}</h2>
          <p className="text-sm text-text-caption">{when}</p>
        </div>
        {children}
      </div>
    </Card>
  )
}

export function Legend({ dot, title, children }: { dot: string; title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-2xl bg-surface-bg p-4">
      <p className="flex items-center gap-2 font-bold">
        <span className={cn('h-3 w-3 rounded-full', dot)} />
        {title}
      </p>
      <p className="mt-1 text-sm text-text-caption">{children}</p>
    </div>
  )
}

export function Tip({ children }: { children: React.ReactNode }) {
  return <p className="rounded-2xl bg-sky-50 px-4 py-3 text-sm">💡 {children}</p>
}

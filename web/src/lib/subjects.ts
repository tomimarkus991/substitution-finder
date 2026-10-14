// Codes seen in the school's aSc export. Unknown codes are shown as they are.
export const SUBJECTS: Record<string, string> = {
  Ek: 'eesti keel', Kirjandus: 'kirjandus', Ink: 'inglise keel', Sk: 'saksa keel', E2: 'eesti keel teise keelena',
  Ma: 'matemaatika', 'matem/digi': 'matemaatika/digi', Lo: 'loodus', InÕ: 'inimeseõpetus', Aj: 'ajalugu', Üh: 'ühiskonnaõpetus',
  Ku: 'kunst', 'Kunst/digi': 'kunst/digi', Mu: 'muusika', TÕ: 'tehnoloogiaõpetus', 'Tehnol/digi': 'tehnoloogia/digi',
  Kk: 'kehaline kasvatus', KKp: 'kehaline kasvatus (poisid)', KKt: 'kehaline kasvatus (tüdrukud)', ujumine: 'ujumine',
  Digikirjaoskus: 'digikirjaoskus', Ettevõtlusõpe: 'ettevõtlusõpe', 'Ettevõtlus/digi': 'ettevõtlus/digi',
}
export const subjectName = (code: string) => SUBJECTS[code] ?? code

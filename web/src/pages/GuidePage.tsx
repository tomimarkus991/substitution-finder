import { PageTitle } from '../components/ui/PageTitle'
import { AbsenceHelp, DutyHelp, GoodToKnow, SubstituteHelp, TableHelp, TimetableHelp } from '../help/guideSections'

export default function GuidePage() {
  return (
    <div className="@container space-y-5">
      <PageTitle hint="Kuidas rakendusega asendused hommikul kiiresti tehtud saada.">Õpetus</PageTitle>
      <TimetableHelp />
      <AbsenceHelp />
      <SubstituteHelp />
      <TableHelp />
      <DutyHelp />
      <GoodToKnow />
    </div>
  )
}

<!-- generated from 8b44d9dcceaa, 2026-10-15 -->
<!-- c4-map config abedddd81c0a -->

# API components: timetables

Uploading, activating and renaming in the `timetable` package. `timetable` and `duty` import each other: `DutyService` matches duty names against the active timetable's teachers, and `TeacherRenameService` renames a teacher in both. See also [days and duties](c4-components-days-duties.md) and [auth](c4-components-auth.md).

```mermaid
C4Component
  title Component diagram for the Asendus API: timetables

  Container_Boundary(api, "API (Spring Boot)") {
    Component(timetableController, "TimetableController", "/timetables, /teachers", "Upload, activate, rename")
    Component(renameService, "TeacherRenameService", "Spring service", "Name overrides")
    Component(timetableService, "TimetableService", "Spring service", "Timetable versions")
    Component(dutyService, "DutyService", "Spring service", "Duty versions")
    Component(pdfParser, "TimetablePdfParser", "PDFBox", "Reads aSc PDF")
  }

  Rel(timetableController, timetableService, "Uploads via")
  Rel(timetableController, renameService, "Renames via")
  Rel(renameService, timetableService, "Reads teachers")
  Rel(renameService, dutyService, "Renames in")
  Rel(timetableService, pdfParser, "Parses with")
  Rel(dutyService, timetableService, "Matches teachers")

  UpdateRelStyle(timetableController, renameService, $offsetX="-55", $offsetY="-30")
  UpdateRelStyle(dutyService, timetableService, $offsetX="-70", $offsetY="-30")
  UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="1")
```

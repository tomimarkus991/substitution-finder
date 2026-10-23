<!-- generated from 8b44d9dcceaa, 2026-10-15 -->
<!-- c4-map config abedddd81c0a -->

# API components: days and duties

The daily cover work in `substitution` and the duty import in `duty`. Each service reads and writes the database through its own Spring Data JPA repositories (not drawn). Both parsers report anything they can't read safely as a warning and never guess. See also [timetables](c4-components-timetable.md) and [auth](c4-components-auth.md).

```mermaid
C4Component
  title Component diagram for the Asendus API: days and duties

  Container_Boundary(api, "API (Spring Boot)") {
    Component(dayController, "DayController", "/days, /substitutions", "Day view, cover picking")
    Component(dutyController, "DutyController", "/duties", "Duty CRUD, Excel import")
    Component(substitutionService, "SubstitutionService", "Spring service", "Absences, candidates")
    Component(dutyService, "DutyService", "Spring service", "Duty versions")
    Component(timetableService, "TimetableService", "Spring service", "Timetable versions")
    Component(excelParser, "DutyExcelParser", "Apache POI", "Reads duty .xlsx")
  }

  Rel(dayController, substitutionService, "Delegates to")
  Rel(dutyController, dutyService, "Manages via")
  Rel(substitutionService, dutyService, "Reads duties")
  Rel(substitutionService, timetableService, "Reads lessons")
  Rel(dutyService, timetableService, "Matches teachers")
  Rel(dutyService, excelParser, "Parses with")

  UpdateRelStyle(substitutionService, dutyService, $offsetX="-55", $offsetY="-30")
  UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="1")
```

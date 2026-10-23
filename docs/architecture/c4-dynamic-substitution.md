# Substitution flow

The core daily task. Marking a teacher absent saves one open substitution row for each lesson and break duty they have in the chosen periods. After that, the coordinator fills each row: the Day page calls `GET /days/{date}/candidates?period=…` for the eligible teachers, then `PUT /substitutions/{id}` with the chosen one. Both go through `DayController` → `SubstitutionService`.

```mermaid
C4Dynamic
  title Dynamic diagram: marking a teacher absent

  Person(coordinator, "Substitution coordinator", "")
  Container(spa, "Web app", "React", "Day page")
  ContainerDb(db, "Database", "PostgreSQL", "")

  Container_Boundary(api, "API") {
    Component(dayController, "DayController", "REST", "")
    Component(substitutionService, "SubstitutionService", "Spring service", "")
    Component(timetableService, "TimetableService", "Spring service", "")
    Component(dutyService, "DutyService", "Spring service", "")
  }

  Rel(coordinator, spa, "Adds absent teacher")
  Rel(spa, dayController, "POST /days/{date}/absences", "JSON/HTTPS")
  Rel(dayController, substitutionService, "createAbsence")
  Rel(substitutionService, timetableService, "Gets their lessons")
  Rel(substitutionService, dutyService, "Gets their duties")
  Rel(substitutionService, db, "Saves absence and rows", "JPA")

  UpdateRelStyle(coordinator, spa, $offsetX="-50", $offsetY="-35")
  UpdateRelStyle(dayController, substitutionService, $offsetX="-45", $offsetY="-30")
  UpdateRelStyle(substitutionService, db, $offsetX="-250", $offsetY="-55")
  UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="1")
```

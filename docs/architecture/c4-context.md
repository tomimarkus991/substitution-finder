<!-- generated from 953ce3a65125, 2026-10-23 -->
<!-- c4-map config abedddd81c0a -->

# System context

Asendus has one kind of user: the school's substitution coordinator. Sign-in goes through Google and only allowlisted emails get in. The timetable comes from aSc as a PDF export that the coordinator uploads by hand. Asendus never calls aSc.

```mermaid
C4Context
  title System Context diagram for Asendus

  System_Ext(asc, "aSc Timetables", "School timetable software")
  Person(coordinator, "Substitution coordinator", "Plans cover for absent teachers")
  System(asendus, "Asendus", "Finds substitutes, builds day table")
  System_Ext(google, "Google Identity", "Sign-in and token keys")

  Rel(coordinator, asc, "Exports timetable PDF from")
  Rel(coordinator, asendus, "Plans substitutions in", "HTTPS")
  Rel(asendus, google, "Signs users in with", "OIDC")

  UpdateLayoutConfig($c4ShapeInRow="1")
```

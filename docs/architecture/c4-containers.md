<!-- generated from 8b44d9dcceaa, 2026-10-15 -->
<!-- c4-map config abedddd81c0a -->

# Containers

The browser loads a React SPA from Cloudflare Pages. The SPA gets a Google ID token, exchanges it for an Asendus JWT at `POST /api/v1/auth/google`, and sends that JWT on every later call. Flyway owns the schema (`api/src/main/resources/db/migration/`) and Hibernate only validates it.

```mermaid
C4Container
  title Container diagram for Asendus

  Person(coordinator, "Substitution coordinator", "Plans daily cover")

  System_Boundary(asendus, "Asendus") {
    Container(spa, "Web app", "React 19, Vite, TypeScript", "Estonian UI")
    Container(api, "API", "Spring Boot 4, Java 25", "REST at /api/v1, parses PDF/Excel")
    ContainerDb(db, "Database", "PostgreSQL 17", "Timetables, absences, duties")
  }

  Boundary(external, "External", "") {
    System_Ext(google, "Google Identity", "Sign-in and JWKS")
  }

  Rel(coordinator, spa, "Uses", "HTTPS")
  Rel(spa, api, "Calls", "JSON, Bearer JWT")
  Rel(api, db, "Reads and writes", "JPA")
  Rel(spa, google, "Gets ID token from")
  Rel(api, google, "Verifies tokens with", "JWKS")

  UpdateRelStyle(coordinator, spa, $offsetY="-60")
  UpdateRelStyle(spa, google, $offsetX="-70", $offsetY="-35")
  UpdateRelStyle(api, google, $offsetY="20")
  UpdateLayoutConfig($c4ShapeInRow="1", $c4BoundaryInRow="2")
```

<!-- generated from 8b44d9dcceaa, 2026-10-15 -->
<!-- c4-map config abedddd81c0a -->

> **Agent context map.** Dense on purpose and not laid out for viewing; labels may overlap when rendered. Diagrams for people: [docs/architecture/](../architecture/).

# Level 3: Components

Generated from source imports. A component is a directory (or package) at the configured depth; an arrow means code in one component imports the other, labelled with what it imports. Container context: [level-2.md](level-2.md).

## API (Spring Boot)

Root `api/src/main/java`, depth 1.

```mermaid
C4Component
  title Components of API (Spring Boot)

  Container_Boundary(api_b, "API (Spring Boot)") {
    Component(api__root, "Application", "Spring Boot", "Entry point and dev data seeder (2 files)")
    Component(api_config, "Config", "Spring Security, springdoc", "Security chain, JWT, CORS, email allowlist (4 files)")
    Component(api_controller, "Auth and user API", "Spring Web", "Google sign-in and current user endpoints (3 files)")
    Component(api_dto, "Auth DTOs", "Jakarta Validation", "Sign-in request and response shapes (3 files)")
    Component(api_duty, "Duties", "Spring, Apache POI", "Imports and stores break-duty schedules (12 files)")
    Component(api_model, "User model", "JPA", "App user entity (1 files)")
    Component(api_repository, "User repository", "Spring Data JPA", "App user persistence (1 files)")
    Component(api_service, "Auth services", "Spring Security", "Verifies Google tokens, issues app JWTs (4 files)")
    Component(api_substitution, "Substitutions", "Spring, JPA", "Absences, substitution rows, substitute candidates (12 files)")
    Component(api_timetable, "Timetables", "Spring, JPA, PDFBox", "Parses aSc PDFs, versions teachers and lessons (19 files)")
    Component(api_util, "JWT utils", "Spring Security", "Reads the current user from the JWT (1 files)")
  }

  Rel(api__root, api_model, "uses", "AppUser")
  Rel(api__root, api_service, "uses", "AuthService")
  Rel(api_config, api_service, "uses", "TokenService")
  Rel(api_controller, api_dto, "uses", "AuthRequestDto, AuthResponseDto, UserDto")
  Rel(api_controller, api_service, "uses", "AuthService, UserService")
  Rel(api_controller, api_util, "uses", "JwtUtils")
  Rel(api_duty, api_timetable, "uses", "Teacher, TeacherKey, TimetableService")
  Rel(api_duty, api_util, "uses", "JwtUtils")
  Rel(api_repository, api_model, "uses", "AppUser")
  Rel(api_service, api_config, "uses", "EmailAllowlist")
  Rel(api_service, api_dto, "uses", "AuthRequestDto, AuthResponseDto, UserDto")
  Rel(api_service, api_model, "uses", "AppUser")
  Rel(api_service, api_repository, "uses", "AppUserRepository")
  Rel(api_substitution, api_duty, "uses", "Duty, DutyLabels, DutyService")
  Rel(api_substitution, api_timetable, "uses", "Lesson, Teacher, TeacherKey +1")
  Rel(api_substitution, api_util, "uses", "JwtUtils")
  Rel(api_timetable, api_duty, "uses", "DutyService")
  Rel(api_timetable, api_util, "uses", "JwtUtils")
```

## Web app (React)

Root `web/src`, depth 1.

```mermaid
C4Component
  title Components of Web app (React)

  Container_Boundary(web_b, "Web app (React)") {
    Component(web__root, "App shell", "React, React Router", "Entry point and routes (2 files)")
    Component(web_api, "API client", "fetch, TanStack Query", "HTTP client, queries and API types (3 files)")
    Component(web_auth, "Auth", "React context", "Session state and route guard (2 files)")
    Component(web_components, "Shared UI", "React, Tailwind", "Layout, toasts and UI primitives (7 files)")
    Component(web_help, "Help", "React", "In-app guide and info tips (3 files)")
    Component(web_lib, "Lib", "TypeScript", "Dates, day table and subject helpers (4 files)")
    Component(web_pages, "Pages", "React, TanStack Query", "Day, timetable, history, guide, landing pages (14 files)")
  }

  Rel(web__root, web_auth, "uses", "auth/AuthContext, auth/RequireAuth")
  Rel(web__root, web_components, "uses", "components/Layout, components/QueryProvider, components/Toast")
  Rel(web__root, web_pages, "uses", "pages/DayPage, pages/GuidePage, pages/HistoryPage +2")
  Rel(web_auth, web_api, "uses", "api/client, api/types")
  Rel(web_components, web_api, "uses", "api/client")
  Rel(web_components, web_auth, "uses", "auth/AuthContext")
  Rel(web_components, web_help, "uses", "help/HelpButton")
  Rel(web_components, web_lib, "uses", "lib/cn, lib/dates")
  Rel(web_help, web_components, "uses", "components/ui/Card, components/ui/RealButton")
  Rel(web_help, web_lib, "uses", "lib/cn, lib/dates")
  Rel(web_lib, web_api, "uses", "api/types")
  Rel(web_pages, web_api, "uses", "api/client, api/queries, api/types")
  Rel(web_pages, web_auth, "uses", "auth/AuthContext")
  Rel(web_pages, web_components, "uses", "components/Toast, components/ui/Card, components/ui/PageTitle +2")
  Rel(web_pages, web_help, "uses", "help/InfoTip, help/guideSections")
  Rel(web_pages, web_lib, "uses", "lib/cn, lib/dates, lib/dayTable +1")
```

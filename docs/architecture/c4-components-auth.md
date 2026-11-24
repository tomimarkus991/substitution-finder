<!-- generated from 8b44d9dcceaa, 2026-10-15 -->
<!-- c4-map config abedddd81c0a -->

# API components: auth

How sign-in works and how requests are authenticated. The web app sends a Google ID token to `POST /auth/google`. The API verifies it against Google's keys, checks the email allowlist, and returns its own 30-day HS256 JWT. All later requests carry that JWT, and Spring Security's resource-server filter validates it. The feature modules are in [timetables](c4-components-timetable.md) and [days and duties](c4-components-days-duties.md).

```mermaid
C4Component
  title Component diagram for the Asendus API: auth

  ContainerDb(db, "Database", "PostgreSQL", "app_user table")
  Container(spa, "Web app", "React", "Calls the API")

  Container_Boundary(api, "API (Spring Boot)") {
    Component(security, "Security filter", "Spring Security", "Validates Bearer JWT")
    Component(authController, "AuthController", "POST /auth/google", "Sign-in endpoint")
    Component(userController, "UserController", "GET /me", "Current user")
    Component(authService, "AuthService", "Spring service", "Allowlist, find or create user")
    Component(tokenService, "TokenService", "HS256 JWT", "Issues 30-day app tokens")
    Component(googleVerifier, "GoogleTokenVerifier", "JwtDecoder", "Verifies Google ID tokens")
  }

  Boundary(outside, "External", "") {
    System_Ext(google, "Google Identity", "JWKS")
  }

  Rel(spa, security, "Sends requests through", "JSON/HTTPS")
  Rel(spa, authController, "Signs in at", "JSON/HTTPS")
  Rel(security, userController, "Passes requests to")
  Rel(authController, authService, "Delegates to")
  Rel(authService, googleVerifier, "Verifies token with")
  Rel(authService, tokenService, "Issues JWT with")
  Rel(authService, db, "Saves users to", "JPA")
  Rel(googleVerifier, google, "Fetches keys from", "HTTPS")

  UpdateRelStyle(spa, authController, $offsetY="-50")
  UpdateRelStyle(spa, security, $offsetY="-50")
  UpdateRelStyle(googleVerifier, google, $offsetY="-40")
  UpdateRelStyle(authService, db, $offsetX="-25", $offsetY="-30")
  UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="1")
```

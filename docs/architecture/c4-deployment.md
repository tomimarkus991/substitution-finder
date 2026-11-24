# Deployment

Pushing to `main` deploys both halves. `deploy-web.yml` tests and builds the SPA, then uploads it to Cloudflare Pages. `deploy-api.yml` builds the API image, pushes it to Docker Hub and runs `docker compose up -d` on the VPS with `docker-compose.prod.yml`. API tests run in `verify-api.yml` on pull requests, not on deploy.

```mermaid
C4Deployment
  title Deployment diagram for Asendus (production)

  Deployment_Node(cloudflare, "Cloudflare Pages", "Project asendus") {
    Container(static, "Static assets", "HTML/JS/CSS", "asendus.alber.ee")
  }

  Deployment_Node(browser, "Coordinator's browser", "Chrome / Firefox / Safari") {
    Container(spa, "Web app", "React SPA", "Runs in the browser")
  }

  Deployment_Node(vps, "VPS", "Docker Compose") {
    Deployment_Node(dbNode, "asendus-db", "postgres:17 + volume") {
      ContainerDb(db, "Database", "PostgreSQL 17", "Internal network only")
    }
    Deployment_Node(traefik, "Traefik", "TLS via Let's Encrypt") {
      Container(api, "asendus-api", "Spring Boot, Java 25", "api.asendus.alber.ee")
    }
  }

  Deployment_Node(googleCloud, "Google", "") {
    System_Ext(google, "Google Identity", "Sign-in and JWKS")
  }

  Rel(spa, static, "Loaded from", "HTTPS")
  Rel(spa, api, "Calls", "JSON/HTTPS")
  Rel(api, db, "Reads and writes", "JDBC")
  Rel(spa, google, "Signs in with", "HTTPS")
  Rel(api, google, "Fetches JWKS from", "HTTPS")

  UpdateRelStyle(spa, static, $offsetY="-35")
  UpdateRelStyle(api, db, $offsetY="-35")
  UpdateRelStyle(api, google, $offsetX="-50", $offsetY="-35")
  UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="2")
```

# Architecture

C4 diagrams for Asendus, in Mermaid. Start at the top and go down a level only when you need more detail.

1. [System context](c4-context.md): who uses Asendus and what it talks to
2. [Containers](c4-containers.md): the SPA, the API and the database
3. API components: [auth](c4-components-auth.md), [timetables](c4-components-timetable.md), [days and duties](c4-components-days-duties.md)
4. [Deployment](c4-deployment.md): Cloudflare Pages, the VPS and Traefik
5. [Substitution flow](c4-dynamic-substitution.md): marking an absence and picking a substitute

These diagrams are drawn for people from the agent maps in [`docs/c4/`](../c4/), and carry the stamp of the map they came from. Regenerate both with the c4-map skill.

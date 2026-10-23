## Agent skills

### Issue tracker

GitHub Issues on tomimarkus991/substitution-finder, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Architecture maps

@docs/c4/level-1.md
@docs/c4/level-2.md

Before changing module boundaries or adding a dependency between components, read `docs/c4/level-3.md`. Before writing a migration or a query, read `docs/c4/database.md`. Refresh the maps with the c4-map skill when they are stale.

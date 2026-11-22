# AGENTS.md — Agent guidance for this repository

Purpose
- Short, focused instructions to help AI coding agents work productively in this workspace.

Quick commands
- Backend dev: `cd backend && mvn spring-boot:run`
- Backend tests: `cd backend && mvn test`
- Frontend dev: `cd frontend && npm install && npm run dev`
- Frontend build/preview: `cd frontend && npm run build` / `npm run preview`

Important files & links
- Overview/intent: [README.md](README.md)
- Backend project: [backend/pom.xml](backend/pom.xml)
- Frontend project: [frontend/package.json](frontend/package.json)
- Backend sources: [backend/src/main/java](backend/src/main/java)
- Backend tests: [backend/src/test/java](backend/src/test/java)
- Flyway migrations: [backend/src/main/resources/db/migration](backend/src/main/resources/db/migration)

Conventions & guardrails for agents
- Prefer updating `AGENTS.md` over creating a `.github/copilot-instructions.md` unless repository already uses that file.
- Always run `cd backend && mvn test` before making or proposing backend changes. Tests are the canonical safety net.
- The backend uses an H2 in-memory DB seeded by Flyway migrations. Do NOT change entity mappings without updating the corresponding migration; `spring.jpa.hibernate.ddl-auto` is `validate`.
- Keep changes small and well-scoped. When touching public APIs (controllers, DTOs), update tests and mention migration/schema impact.
- Link to existing documentation rather than copy it: use README.md and migration files as the source-of-truth.

Typical agent tasks and where to start
- Small bugfix in API: start at [backend/src/main/java](backend/src/main/java) and run `mvn test`.
- Add a UI tweak: modify [frontend/src](frontend/src) and run `npm run dev` to verify in the browser.
- DB/schema work: inspect [backend/src/main/resources/db/migration](backend/src/main/resources/db/migration) first.

If you add or update this file
- Keep it minimal. Add links to new top-level docs (CONTRIBUTING.md, ARCHITECTURE.md) rather than duplicating them.

Contact / follow-up
- If you want more granular agent instructions (separate frontend/backend instructions, or automated hooks), ask to `/create-agent` with the desired scope.

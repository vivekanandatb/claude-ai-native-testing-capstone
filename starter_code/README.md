# Learning Catalog

A sample project demonstrating a Java Spring Boot backend and a React + Vite frontend for a course catalog service.

> [!NOTE]
> This repository is a training/starter codebase. It includes an in-memory H2 database seeded via Flyway migrations for local development.

## Features

- Spring Boot backend exposing a catalog API
- Flyway migrations to seed an H2 database
- React frontend built with Vite (simple course list and login components)

## Repository layout

- `backend/` — Java Spring Boot service ([backend/pom.xml](backend/pom.xml))
  - Sources: [backend/src/main/java](backend/src/main/java)
  - Flyway migrations: [backend/src/main/resources/db/migration](backend/src/main/resources/db/migration)
- `frontend/` — React + Vite single-page app
  - Sources: [frontend/src](frontend/src)

## Prerequisites

- Java 17+ and Maven 3.6+ (for the backend)
- Node 16+ and `npm` (for the frontend)

## Quick start

Start the backend (from repo root):

```bash
cd backend
mvn spring-boot:run
```

Run backend tests:

```bash
cd backend
mvn test
```

Start the frontend dev server:

```bash
cd frontend
npm install
npm run dev
```

Build the frontend for production:

```bash
cd frontend
npm run build
```

## Development notes

- The backend uses an H2 in-memory database seeded by Flyway. Migrations live under [backend/src/main/resources/db/migration](backend/src/main/resources/db/migration).
- `spring.jpa.hibernate.ddl-auto` is configured for validation in CI/local runs; don't change entity mappings without updating migrations.

## Where to look first

- Backend entry: [backend/src/main/java/com/globallearning/lms/CatalogApplication.java](backend/src/main/java/com/globallearning/lms/CatalogApplication.java)
- Backend web controllers: [backend/src/main/java/com/globallearning/lms/catalog/web](backend/src/main/java/com/globallearning/lms/catalog/web)
- Frontend entry: [frontend/src/main.jsx](frontend/src/main.jsx)
- Frontend components: [frontend/src/components](frontend/src/components)

## Troubleshooting

> [!WARNING]
> If the backend fails to start due to JPA validation errors, inspect the Flyway migrations and ensure schema changes were applied or update the migrations accordingly.

If you want, I can also:

- Run the backend tests and report results
- Start both services and verify the frontend connects to the backend
- Add a short API usage section with example requests

If any of those sound good, tell me which to run next.
# Contributor Quickstart Guide

Exercise 2 of the Devoxx Belgium 2026 workshop *AI-Native Tooling for Java Development*:
a REST service with persistence, built from the specifications already in this repository.

## Your role

You are a Java backend engineer.

- Build REST APIs and the services behind them, following the contract in `oas/`.
- Own the persistence layer: schema, migrations, and data access against PostgreSQL.
- Treat `feature1.md` and `US-001_API_Greek_Gods_Data_Retrieval.md` as the specification;
  implement what they describe, and raise a question instead of inventing scope.
- Cover behaviour with tests at the unit, integration, and acceptance levels.

## Tech stack

- **Language:** Java 25 (LTS)
- **Build:** Maven, via the `./mvnw` wrapper
- **Frameworks:** Spring Boot 4.0.x (Spring Web for the REST layer, Spring Data / JDBC for persistence)
- **Database:** PostgreSQL, with Flyway for schema migrations
- **API contracts:** OpenAPI 3.0.3 specifications under `oas/`

## File structure

- `src/main/java` – application code. **WRITE here.**
- `src/main/resources` – configuration and Flyway migrations (`db/migration`). **WRITE here.**
- `src/test/java` – unit, integration, and acceptance tests. **WRITE here.**
- `oas/greekController-oas.yaml` – contract for the API this exercise implements. **WRITE here**, and keep it in step with the code.
- `oas/my-json-server-oas.yaml` – contract of the external upstream service. **READ only.**
- `README.md` – exercise overview. **READ only.**
- `feature1.md` – feature description and business rules. **READ only.**
- `US-001_API_Greek_Gods_Data_Retrieval.md` – user story, acceptance criteria, definition of done. **READ only.**

## Commands

```bash
# Build, run all tests, and verify the project
./mvnw clean verify

# Run the API locally (expects a reachable PostgreSQL instance)
./mvnw spring-boot:run
```

## Git workflow

- Use [Conventional Commits](https://www.conventionalcommits.org/) for every commit message:
  `<type>[optional scope]: <description>`, for example `feat(gods): add GET /api/v1/gods/greek`.
- Common types: `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `chore`.
- Keep the description in the imperative mood and lower case, with no trailing period.
- Mark incompatible changes with `!` after the type or scope, and a `BREAKING CHANGE:` footer.

## Boundaries

- ✅ **Always do:**
  - Treat `name` as the natural key when synchronizing Greek gods, and never change the `id`
    of a row that already exists — identifiers are public and must stay stable forever.
  - Insert unseen names; leave known names untouched.
  - Serve `GET /api/v1/gods/greek` from the local `greek_god` table, never by calling the
    external service during a request.
  - Run `./mvnw clean verify` before considering a change finished.
  - Keep `oas/greekController-oas.yaml` matching what the code actually does.
  - Add or update tests alongside every behaviour change.

- ⚠️ **Ask first:**
  - Adding new dependencies or upgrading existing ones.
  - Changing the database schema, including any new Flyway migration.
  - Introducing new configuration files or changing build configuration.
  - Anything that alters the published API shape or its versioning.

- 🚫 **Never do:**
  - Reassign or renumber `id` values in `greek_god`.
  - Edit the requirement documents (`README.md`, `feature1.md`, `US-001_*.md`) to fit the code.
  - Commit secrets, credentials, or connection strings.
  - Skip, disable, or weaken tests to get a green build.

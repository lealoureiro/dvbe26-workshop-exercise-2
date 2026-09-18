# Exercise 2: REST Service + Persistence
## Devoxx Belgium 2026 - AI-Native Tooling for Java Development Workshop
Repository hosting the content for Exercise 2 of the [Technical Workshop on AI-Native Tooling for Java Development](https://m.devoxx.com/events/dvbe26/talks/8190/technical-workshop-on-ai-native-tooling-for-java-development).

## Running locally

### Prerequisites

- **Java 25** and **Docker**. Nothing else — the Maven wrapper (`./mvnw`) fetches Maven itself.

### Run the tests

```bash
./mvnw clean verify
```

No database setup needed. Integration and acceptance tests start a real PostgreSQL
through Testcontainers. If Docker is not running they fail rather than silently
falling back to an in-memory database — the guarantees under test are identity
generation and a case-insensitive unique index, and both are engine-specific.

### Run the API

Start the database, then the application:

```bash
docker compose up -d --wait
./mvnw spring-boot:run
```

`--wait` returns only once PostgreSQL is accepting connections, so Flyway cannot race
it on start-up. The application's defaults already point at this database, so no extra
configuration is needed. Flyway creates the schema on first run.

```bash
curl -s http://localhost:8080/api/v1/gods/greek
```

```json
[{"id":1,"name":"Zeus"},{"id":2,"name":"Hera"},{"id":3,"name":"Poseidon"}]
```

The catalogue is populated by a reconciliation run at start-up, and again on a
recurring schedule. Before the first run completes the endpoint returns `[]` — an
empty catalogue is a valid state, not an error.

### Configuration

Every setting has a working local default; override only what you need.

| Variable | Default | Purpose |
| --- | --- | --- |
| `GREEK_GODS_SYNC_FIXED_DELAY` | `PT1H` | Time between reconciliation runs. Set `PT15S` to watch it work without waiting an hour |
| `GREEK_GODS_SYNC_ENABLED` | `true` | The operational kill switch. `false` stops reconciliation; reads keep working |
| `GREEK_GODS_SYNC_UPSTREAM_BASE_URL` | the public mock | Point reconciliation at your own stub |
| `GREEK_GODS_DB_URL` | `jdbc:postgresql://localhost:5432/greek_gods` | Database location |
| `GREEK_GODS_DB_USERNAME` / `GREEK_GODS_DB_PASSWORD` | `greek_gods` | Database credentials |
| `GREEK_GODS_DB_PORT` | `5432` | Host port Compose publishes. Override only if 5432 is already in use on your machine |

If 5432 is taken, both sides need the same port:

```bash
GREEK_GODS_DB_PORT=55432 docker compose up -d --wait
GREEK_GODS_DB_URL=jdbc:postgresql://localhost:55432/greek_gods ./mvnw spring-boot:run
```

### Seeing the behaviour that matters

Identifiers are published to consumers, so one must always denote the same god. Run
with a short cadence to watch reconciliation converge:

```bash
GREEK_GODS_SYNC_FIXED_DELAY=PT15S ./mvnw spring-boot:run
```

```
event=reconciliation.run_completed offered=20 takenUp=20 alreadyHeld=0  skippedBlank=0 failed=0
event=reconciliation.run_completed offered=20 takenUp=0  alreadyHeld=20 skippedBlank=0 failed=0
```

The first run takes up every name; later runs take up nothing and leave every
identifier untouched. The data lives in a named volume, so identifiers survive a
restart too — `docker compose down` then `up` and Zeus still holds the same id.

Other things worth trying by hand:

- `GREEK_GODS_SYNC_ENABLED=false` — the endpoint still answers, and nothing calls the
  third-party service.
- `docker compose stop` while the app is running — a read eventually returns `500`
  with a problem detail, rather than pretending the catalogue is empty. Expect it to
  take about 30 seconds: the connection pool waits for a connection before giving up,
  so give `curl` a generous timeout or it will look like a hang.

### Shutting down

```bash
docker compose down      # stop, keep the data
docker compose down -v   # stop and discard the data
```

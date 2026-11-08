# SingleStore + Spring Boot (JDBC) + Datadog tracing demo

A small CRUD service (Spring Boot 3.5, Java 21, Gradle Kotlin DSL) that talks to a
self-managed SingleStore instance over JDBC. It's built to check how `dd-java-agent`
traces the SingleStore JDBC driver.

```
.
├── docker-compose.yml        # singlestore + app (+ optional datadog-agent)
├── .env.example
├── singlestore/
│   ├── Dockerfile            # FROM official Dev Image + init.sql
│   └── init.sql              # database, tables, indexes, app user, seed data
├── app/
│   ├── Dockerfile            # multi-stage Gradle build, dd-java-agent included
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── src/main/...          # JdbcTemplate repository + REST controller
└── scripts/smoke-test.sh     # hits every endpoint once
```

## Quick start

```bash
cp .env.example .env                       # set DD_API_KEY if you want traces
docker compose --profile datadog up --build -d   # or without --profile datadog
./scripts/smoke-test.sh
```

| What                | Where                          |
|---------------------|--------------------------------|
| REST API            | http://localhost:8080/api/...  |
| Health              | http://localhost:8080/actuator/health |
| SingleStore (JDBC)  | `localhost:3306` (root / `S2_ROOT_PASSWORD`, or app / app_password) |
| SingleStore Studio  | http://localhost:8091          |

The `singlestore` healthcheck only goes green once `init.sql` has created
`demo.products`, so the app always starts against a ready schema.

Reset everything (including re-running `init.sql`): `docker compose down -v`.

### Apple silicon

The Dev Image is amd64-only (`platform: linux/amd64` is already set). Enable
*Use Rosetta for x86_64/amd64 emulation* in Docker Desktop. The latest image needs
macOS 26 on Apple silicon; see the Dev Image README for details.

## SingleStore: how the init script works

No custom database image is strictly needed. The official
[SingleStore Dev Image](https://github.com/singlestore-labs/singlestoredb-dev-image)
runs an SQL file on **first** start (path given by `INIT_SQL`, default `/init.sql`)
as soon as the engine is ready, and marks it done in `/data/.init.sql.done`.

`singlestore/Dockerfile` just bakes `init.sql` into that image. The equivalent with
the stock image and a bind mount is:

```bash
docker run -d --name singlestoredb-dev \
  --platform linux/amd64 \
  -e ROOT_PASSWORD=rootpass \
  -p 3306:3306 -p 8091:8080 -p 9000:9000 \
  -v "$PWD/singlestore/init.sql:/init.sql:ro" \
  ghcr.io/singlestore-labs/singlestoredb-dev:latest
```

Schema notes (SingleStore-specific):
- `categories` is a **reference table** (replicated on every leaf, cheap joins).
- `products` is a columnstore table with `SHARD KEY (id)`, `SORT KEY (created_at)`
  and two hash indexes (`sku`, `category_id`).
- SingleStore has no foreign keys, so the app validates `categoryId` itself.
- The Dev Image is for prototyping and functional testing only, not benchmarks.

## REST endpoints

| Method | Path                     | SQL / JDBC path                          |
|--------|--------------------------|------------------------------------------|
| GET    | `/api/products?categoryId=&limit=` | SELECT + JOIN (PreparedStatement) |
| GET    | `/api/products/{id}`     | SELECT by PK (PreparedStatement)         |
| GET    | `/api/products/count`    | SELECT COUNT(*) (**plain Statement**)    |
| GET    | `/api/categories`        | SELECT (PreparedStatement)               |
| POST   | `/api/products`          | INSERT + generated key (PreparedStatement) |
| POST   | `/api/products/batch`    | INSERT (**executeBatch**)                |
| PUT    | `/api/products/{id}`     | UPDATE (PreparedStatement)               |
| DELETE | `/api/products/{id}`     | DELETE (PreparedStatement)               |

Example:

```bash
curl -X POST localhost:8080/api/products -H 'Content-Type: application/json' \
  -d '{"sku":"NEW-1","name":"Thing","categoryId":5,"price":9.99,"stock":3}'
```

## Tracing test plan

The app container always loads the tracer through
`JAVA_TOOL_OPTIONS=-javaagent:/opt/datadog/dd-java-agent.jar`. By default compose
also sets:

```
DD_TRACE_JDBC_CONNECTION_CLASS_NAME=com.singlestore.jdbc.Connection
DD_TRACE_JDBC_PREPARED_STATEMENT_CLASS_NAME=com.singlestore.jdbc.ClientPreparedStatement
```

Suggested runs (edit `.env`, then `docker compose --profile datadog up -d --force-recreate app`,
then `./scripts/smoke-test.sh`):

1. **Baseline:** uncomment the two empty `DD_TRACE_JDBC_*` lines. Expect web spans
   but no SingleStore query spans.
2. **Opt-in (default):** comment them out again. Expect query spans under each request.
   Check `count` (plain Statement) and `batch` (executeBatch) explicitly.
3. **MariaDB Connector/J comparison:** uncomment `DB_URL`/`DB_DRIVER` for MariaDB.
   Expect query spans with no opt-in, tagged as mariadb.

Set `DD_TRACE_DEBUG=true` to see in the app logs which classes the agent instruments.

## Building / running without Docker

The Gradle wrapper isn't committed. Generate it once with a local Gradle
(`gradle wrapper --gradle-version 8.14`) or just use your installed `gradle`:

```bash
cd app
gradle bootRun          # expects SingleStore on localhost:3306 (e.g. compose's singlestore service)
```

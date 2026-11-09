# SingleStore + Spring Boot (JDBC) + Datadog tracing demo

A small CRUD service (Spring Boot 3.5, Java 21, Gradle Kotlin DSL) that talks to a
self-managed SingleStore instance over JDBC. It's built to check how `dd-java-agent`
traces the SingleStore JDBC driver.

It runs the same way on **Linux** and **macOS**: everything runs in Docker, and the
only platform-specific part is how you get a Docker engine.

```
.
├── docker-compose.yml        # singlestore + app (+ optional datadog-agent)
├── .env.example              # copy to .env
├── singlestore/
│   ├── Dockerfile            # FROM official Dev Image + init.sql
│   └── init.sql              # database, tables, indexes, app user, seed data
├── app/
│   ├── Dockerfile            # tracer download, Gradle build, runtime image
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradlew, gradle/      # Gradle wrapper (8.14.3)
│   └── src/main/...          # JdbcTemplate repository + REST controller
└── scripts/smoke-test.sh     # hits every endpoint once
```

## Prerequisites

You need a Docker engine with the Compose plugin and BuildKit (both default on
current Docker versions).

**Linux (e.g. Ubuntu)**
- Docker Engine and the `docker-compose-plugin` from Docker's apt repository:
  https://docs.docker.com/engine/install/ubuntu/
- The SingleStore Dev Image is **amd64-only** and needs a CPU with x86-64-v3 support
  (AVX2). Most x86 cloud VMs qualify. On an arm64 VM you'd need emulation, so prefer
  an x86 machine type.

**macOS**
- Docker Desktop, or [Colima](https://github.com/abiosoft/colima) for a CLI-only setup:
  ```bash
  brew install colima docker docker-compose docker-buildx
  colima start --vm-type=vz --vz-rosetta --cpu 4 --memory 8 --disk 60
  ```
- On Apple silicon the image runs under Rosetta. In Docker Desktop, enable
  *Use Virtualization framework* and *Use Rosetta for x86_64/amd64 emulation*. The
  latest Dev Image needs macOS 26 on Apple silicon (see the Dev Image README).

Give the Docker engine at least 4 CPUs and 8 GB RAM; SingleStore is the heavy part.

## Quick start (docker compose)

```bash
cp .env.example .env                              # set DD_API_KEY if you want traces
docker compose --profile datadog up --build -d    # or without --profile datadog
./scripts/smoke-test.sh
```

| What                | Where                                   |
|---------------------|-----------------------------------------|
| REST API            | http://localhost:8080/api/...           |
| Health              | http://localhost:8080/actuator/health   |
| SingleStore (JDBC)  | `localhost:3306` — root / `S2_ROOT_PASSWORD`, or app / app_password |
| SingleStore Studio  | http://localhost:8091                   |

The `singlestore` healthcheck only goes green once `init.sql` has created
`demo.products`, so the app always starts against a ready schema.

- After changing a Dockerfile or the code: `docker compose up -d --build --force-recreate app`
  (a plain `up` reuses the existing `s2-demo-app:local` image).
- Reset everything, including re-running `init.sql`: `docker compose down -v`.

On a remote VM, either use `localhost` from the VM itself, or open the ports
(firewall rules) / use SSH port forwarding to reach them from your laptop, e.g.
`ssh -L 8080:localhost:8080 -L 8091:localhost:8091 <vm>`.

## SingleStore: how the init script works

The official [SingleStore Dev Image](https://github.com/singlestore-labs/singlestoredb-dev-image)
runs an SQL file on **first** start (path given by `INIT_SQL`, default `/init.sql`)
as soon as the engine is ready, and marks it done in `/data/.init.sql.done`.
`singlestore/Dockerfile` just bakes `init.sql` into that image.

`init.sql` creates, in order:
1. the `demo` database;
2. `categories`, a **reference table** (replicated on every leaf, cheap joins);
3. `products`, a columnstore table with `SHARD KEY (id)`, `SORT KEY (created_at)` and
   hash indexes on `sku` and `category_id`;
4. the `app` / `app_password` user with SELECT/INSERT/UPDATE/DELETE on `demo.*`;
5. seed data (5 categories, 24 products).

SingleStore has no foreign keys, so the app validates `categoryId` itself.
The Dev Image is for prototyping and functional testing only, not for benchmarks.

## Running without docker compose

**SingleStore** (stock image, `init.sql` bind-mounted; run from the repo root):

```bash
docker run -d --name singlestoredb-dev \
  --platform linux/amd64 \
  -e ROOT_PASSWORD=rootpass \
  -p 3306:3306 -p 8091:8080 -p 9000:9000 \
  -v "$PWD/singlestore/init.sql:/init.sql:ro" \
  -v s2-data:/data \
  ghcr.io/singlestore-labs/singlestoredb-dev:latest

# wait until init.sql has run
until docker exec singlestoredb-dev singlestore -uroot -prootpass \
      -e "SELECT COUNT(*) AS products FROM demo.products" 2>/dev/null; do
  echo "waiting for init.sql..."; sleep 3
done
```

To re-run `init.sql`: `docker rm -f singlestoredb-dev && docker volume rm s2-data`,
then start it again. Init output shows in `docker logs singlestoredb-dev`.

**App container** (connecting to the SingleStore container above):

```bash
docker build -t s2-demo-app:local app
docker run --rm -p 8080:8080 \
  --add-host=host.docker.internal:host-gateway \
  -e DB_URL=jdbc:singlestore://host.docker.internal:3306/demo \
  s2-demo-app:local
```

`--add-host=...:host-gateway` is needed on Linux; Docker Desktop and Colima on macOS
provide `host.docker.internal` already (the flag is harmless there). To attach the
tracer, add `-e JAVA_TOOL_OPTIONS=-javaagent:/opt/datadog/dd-java-agent.jar` plus the
`DD_*` variables from `docker-compose.yml`.

## Building and running the app outside Docker

The Gradle wrapper is committed, so Gradle itself doesn't need to be installed:
`./gradlew` downloads Gradle 8.14.3 on first use. It does need a **JDK** to start
(JDK 21 recommended, 17+ minimum). If only another JDK version is installed, the
Foojay toolchain plugin downloads JDK 21 for compiling.

**Linux:**
```bash
sudo apt install -y openjdk-21-jdk
cd app
./gradlew bootRun            # expects SingleStore on localhost:3306
```

**macOS / IntelliJ:** open the `app` folder as a Gradle project; IntelliJ uses the
wrapper. If no JDK 21 is installed, either let Gradle download one, or add it via
*File → Project Structure → SDKs → Download JDK*. Run `S2DemoApplication`.

The defaults in `application.yml` (`jdbc:singlestore://localhost:3306/demo`,
`app` / `app_password`) match the SingleStore container, so no extra configuration
is needed. Then try `curl localhost:8080/api/products?limit=3`.

To regenerate the wrapper without a local Gradle install:
```bash
docker run --rm -v "$PWD/app":/workspace -w /workspace \
  gradle:8.14-jdk21 gradle wrapper --gradle-version 8.14.3
```

## REST endpoints

| Method | Path                                | SQL / JDBC path                            |
|--------|-------------------------------------|--------------------------------------------|
| GET    | `/api/products?categoryId=&limit=`  | SELECT + JOIN (PreparedStatement)          |
| GET    | `/api/products/{id}`                | SELECT by PK (PreparedStatement)           |
| GET    | `/api/products/count`               | SELECT COUNT(*) (**plain Statement**)      |
| GET    | `/api/categories`                   | SELECT (PreparedStatement)                 |
| POST   | `/api/products`                     | INSERT + generated key (PreparedStatement) |
| POST   | `/api/products/batch`               | INSERT (**executeBatch**)                  |
| PUT    | `/api/products/{id}`                | UPDATE (PreparedStatement)                 |
| DELETE | `/api/products/{id}`                | DELETE (PreparedStatement)                 |

Example:

```bash
curl -X POST localhost:8080/api/products -H 'Content-Type: application/json' \
  -d '{"sku":"NEW-1","name":"Thing","categoryId":5,"price":9.99,"stock":3}'
```

## Tracing test plan

In compose, the app container loads the tracer through
`JAVA_TOOL_OPTIONS=-javaagent:/opt/datadog/dd-java-agent.jar` and opts the SingleStore
driver classes into the JDBC instrumentation:

```
DD_TRACE_JDBC_CONNECTION_CLASS_NAME=com.singlestore.jdbc.Connection
DD_TRACE_JDBC_PREPARED_STATEMENT_CLASS_NAME=com.singlestore.jdbc.ClientPreparedStatement
```

With that, query spans appear as `singlestore.query` with the SQL as the resource,
nested under the controller spans.

Suggested runs (edit `.env`, then `docker compose --profile datadog up -d --force-recreate app`,
then `./scripts/smoke-test.sh`):

1. **Baseline:** uncomment the two empty `DD_TRACE_JDBC_*` lines. Expect web spans
   but no SingleStore query spans.
2. **Opt-in (default):** comment them out again. Expect query spans under each request.
   Check `count` (plain Statement) and `batch` (executeBatch) explicitly.
3. **MariaDB Connector/J comparison:** uncomment `DB_URL`/`DB_DRIVER` for MariaDB.
   Expect query spans with no opt-in, tagged as mariadb.

Set `DD_TRACE_DEBUG=true` to see in the app logs which classes the agent instruments.

**DBM propagation:** Database Monitoring doesn't support SingleStore, so leave
`DD_DBM_PROPAGATION_MODE` off for this traffic. If the same app also uses a
DBM-supported database and enables it, the comments would also reach SingleStore
through the MariaDB/MySQL drivers; validate that with a load test or canary first.

## Troubleshooting

**`Error opening zip file or JAR manifest missing : /opt/datadog/dd-java-agent.jar`**
The container can't read the tracer JAR. Inspect the image without starting the app
(`JAVA_TOOL_OPTIONS` is only set by compose, so the shell starts normally):

```bash
docker run --rm --entrypoint sh s2-demo-app:local -c '
  ls -la /opt/datadog/
  DD_TRACE_ENABLED=false java -javaagent:/opt/datadog/dd-java-agent.jar -version'
```

The Dockerfile already guards against the two known causes: the download stage fails
on HTTP errors or non-JAR responses, and the build checks that the agent loads as the
non-root `app` user. (Using `COPY --chmod=644` there used to make `/opt/datadog`
untraversable, since the mode was also applied to the created directory.)

**App restarting / can't connect to SingleStore:** check `docker compose ps` (is
`singlestore` healthy?) and `docker compose logs singlestore` for errors from `init.sql`.

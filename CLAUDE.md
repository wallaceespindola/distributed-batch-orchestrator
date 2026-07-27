# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
mvn test                                # all tests (unit + single-node e2e on port 18080)
mvn -q package -DskipTests              # build target/distributed-batch-orchestrator-1.0.0.jar
./scripts/start-local.sh [--build] [--with-frontend]   # 4 instances on 8080-8083 (+frontend :3000)
./scripts/stop-local.sh                 # stop everything (ps1 variants exist for Windows)
make help                               # all Makefile targets
docker compose up --build               # kubernetes-mode topology locally (Kafka + H2 server + 4 apps)
kubectl apply -k k8s/                   # deploy to Kubernetes
```

Run a single test: `mvn test -Dtest=RoundRobinTest`.

## Architecture

Distributed batch app: Java 21, Spring Boot 3.4, Spring Batch, H2. Four **identical** instances; the first to receive `POST /api/batch/start` wins a ShedLock JDBC lock and is Master for that run (lock released by the job-completion listener → role rotates). Concurrent starts → 409.

Two execution modes, selected by Spring profile — never fork code per mode:

- **`local` (default profile):** job = `localDispatchStep` tasklet (`batch/local/LocalDispatchTasklet`). Master splits account ids round-robin (`batch/RoundRobin`) across healthy peers (probed via `/api/info`, config `app.peers`) and POSTs each partition to `/internal/partitions/execute`. All processes share one H2 file DB via `AUTO_SERVER=TRUE` (`./data/`).
- **`kubernetes`:** Kafka remote partitioning (`config/KafkaBatchConfig`, `@EnableBatchIntegration`): manager step sends `StepExecutionRequest`s to topic `batch-partition-requests` (Java serialization — `batch/kafka/JavaSerializer`), 4 pods consume in group `batch-workers`, manager polls the shared job repository (H2 TCP server pod, `DB_URL` env) for completion. Every pod carries both manager and worker beans.

Both modes converge on `service/ReportService.processPartition(...)` — it inserts the worker's own `PARTITION_ASSIGNMENTS` row (worker attribution is a first-class requirement) and one `ACCOUNT_REPORTS` row per account, stamped with `app.instance-id`.

Key invariants:

- **Schema is owned by `src/main/resources/schema.sql`** (idempotent `IF NOT EXISTS`, includes Spring Batch 5.2 metadata + ShedLock tables). `ddl-auto=none`, `spring.batch.jdbc.initialize-schema=never`. If the Boot/Batch version changes, regenerate the batch DDL from `spring-batch-core` jar's `schema-h2.sql`.
- Job name `accountReportJob` is shared by both profile-specific `Job` beans and referenced by `BatchStatusService`.
- The `JobLauncher` is async (`@Primary` bean in `BatchConfig`) so `/api/batch/start` returns 202 immediately.
- Startup data seeding races across 4 instances — guarded by ShedLock (`data-init`) + empty check in `DataGeneratorService`.
- Status/history come from Spring Batch metadata (`JobExplorer`) joined with `PARTITION_ASSIGNMENTS`/`ACCOUNT_REPORTS`, so any instance can answer.

## Frontend

`frontend/` is a deliberately separate NPM project (plain HTML/CSS/JS, no framework, no build). `npm start` → http-server on 3000, polls the REST API (base URL configurable in the UI). Keep it dependency-free.

## Conventions

- REST responses always include `timestamp`; errors go through `web/GlobalExceptionHandler`.
- `specs.md` is the original requirements document; README.md documents the implemented architecture.
- All 4 instances must stay byte-identical: never add instance-specific config, images or code paths.

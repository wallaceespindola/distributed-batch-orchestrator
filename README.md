![Java](https://cdn.icon-icons.com/icons2/2699/PNG/512/java_logo_icon_168609.png)

# distributed-batch-orchestrator

![Apache 2.0 License](https://img.shields.io/badge/License-Apache2.0-orange)
![Java](https://img.shields.io/badge/Built_with-Java21-blue)
![Spring](https://img.shields.io/badge/Structured_by-SpringBoot-lemon)
![Spring Batch](https://img.shields.io/badge/Processed_by-SpringBatch-green)
![Maven](https://img.shields.io/badge/Powered_by-Maven-pink)
[![CI](https://github.com/wallaceespindola/distributed-batch-orchestrator/actions/workflows/ci.yml/badge.svg)](https://github.com/wallaceespindola/distributed-batch-orchestrator/actions/workflows/ci.yml)

Distributed batch processing application built with **Java 21, Maven, Spring Boot 3.4 and Spring Batch**, backed by **H2**. Six identical instances process banking reports together: one instance is dynamically elected **Master** per run, the others act as **Workers**. A separate HTML/CSS/JavaScript frontend visualizes the whole thing live.

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Quick start (local mode)](#quick-start-local-mode)
- [Configuration](#configuration)
- [REST API](#rest-api)
- [Dashboard](#dashboard)
- [Kubernetes / OpenShift-style deployment](#kubernetes--openshift-style-deployment)
- [Testing](#testing)
- [CI](#ci)
- [Project layout](#project-layout)
- [Author](#author)
- [License](#license)

## Features

- **Dynamic master election** with a ShedLock JDBC lock: whichever instance receives `POST /api/batch/start` leads that run; concurrent starts get HTTP 409
- **Master is also a worker**: it takes a partition like every other instance
- **Round-robin partitioning** of account ids across healthy instances (bucket sizes differ by at most 1)
- **Two distribution modes, one codebase**: HTTP dispatch between peers locally, Kafka remote partitioning on Kubernetes, selected by Spring profile
- **Worker attribution**: every partition and every account report records which instance produced it
- **Shared job repository in H2**, so status and history can be read from any instance
- **Live dashboard** (plain HTML/CSS/JS) with status, run metrics, partition distribution, cluster health and history
- **One-command local cluster**: start/stop scripts for Bash and PowerShell
- **Container and cluster ready**: multi-stage Dockerfile (non-root, `HEALTHCHECK`), Docker Compose topology with Kafka + H2 server + 6 app containers, and kustomize manifests
- All REST responses carry a `timestamp` field

## Tech Stack

| Area            | Technology                                                                 |
|-----------------|----------------------------------------------------------------------------|
| Language        | Java 21                                                                    |
| Framework       | Spring Boot 3.4.5 (Web, Data JPA, Batch, Actuator, Validation)             |
| Batch           | Spring Batch, `spring-batch-integration` remote partitioning               |
| Messaging       | Spring Kafka + Spring Integration Kafka; Apache Kafka 3.9.0 (KRaft) in Compose/k8s |
| Locking         | ShedLock 6.3.0 (`shedlock-provider-jdbc-template`)                         |
| Database        | H2 (file + `AUTO_SERVER` locally, TCP server in Compose/k8s)               |
| Boilerplate     | Lombok                                                                     |
| Frontend        | Plain HTML/CSS/JavaScript served by `http-server` on port 3000             |
| Testing         | JUnit 5, Spring Boot Test, `spring-batch-test`                             |
| Build / runtime | Maven, Docker (Temurin 21 JRE), Docker Compose, Kubernetes (kustomize)     |
| CI              | GitHub Actions, Dependabot                                                 |

## Architecture

```
                        POST /api/batch/start
                                 │
                     first receiver wins ShedLock
                                 ▼
 ┌────────────┐   ┌──────────────────────────────────┐
 │  Frontend  │──▶│  Master (elected for this run)   │
 │ (port 3000)│   │  - reads account ids from H2     │
 └────────────┘   │  - round-robin split into N parts│
                  └───────┬───────┬───────┬──────────┘
                          ▼       ▼       ▼
                     Worker    Worker    Worker   (+ master itself)
                          │       │       │
                          └───────┴───────┴──▶  H2: one report per account,
                                                partition → worker attribution
```

- **6 identical instances** — same artifact, same config. No instance-specific images. The elected Master is also a Worker: it takes a partition like everyone else.
- **Dynamic master election** — the first instance to receive `POST /api/batch/start` acquires a **ShedLock** JDBC lock and becomes Master for that run. The lock guarantees a single active master (concurrent starts get HTTP 409) and is released when the job ends, so the role rotates between runs.
- **Round-robin partitioning** — account ids are split evenly (`i % workers`) into one partition per healthy worker; bucket sizes differ by at most 1.
- **Worker attribution** — each worker records its own `PARTITION_ASSIGNMENTS` row (partition key, worker id, master id, timing, status) and stamps every `ACCOUNT_REPORTS` row with its worker id. The status API surfaces both.
- **Shared state** — all instances share one H2 database (file + `AUTO_SERVER` locally, H2 TCP server on Kubernetes), which also holds the Spring Batch job repository — so status/history can be queried from **any** instance.

### Execution modes

| | Local mode (default) | Kubernetes mode (`kubernetes` profile) |
|---|---|---|
| Topology | 6 Spring Boot processes, ports 8080–8085 | 6 identical pods |
| Distribution | Master dispatches partitions to peers over **HTTP** (no Kafka) | **Kafka remote partitioning** (`spring-batch-integration`), consumer group `batch-workers` |
| Batch job | `accountReportJob` → dispatch tasklet | `accountReportJob` → manager step + remote worker steps |
| Completion tracking | HTTP responses | Manager polls the shared job repository |

Same codebase, mode selected purely by Spring profile.

### Batch flow

1. Random banking data (25 accounts by default via `APP_DATA_ACCOUNTS`, 1–10,000 on demand; 5–50 transactions each) is generated into H2 — seeded on startup, regenerable on demand.
2. Master reads all account ids and splits them round-robin.
3. Each partition is executed by a worker (HTTP call in local mode, Kafka `StepExecutionRequest` in Kubernetes mode). All workers run the same `ReportService`.
4. One report per account: transaction count, total credits/debits, ending balance, plus the worker that produced it.
5. Job status, partition distribution and history are exposed via REST and shown live in the frontend.

## Prerequisites

- Java 21+ (the start scripts check for it)
- Maven
- Node.js + npm (frontend only)
- `curl` (the start script uses it for health checks)
- Docker / Docker Compose, `kubectl` (container and cluster modes only)

## Quick start (local mode)

```bash
# one command: builds if needed, resets ./data, starts 6 instances + frontend
./scripts/start-local.sh --with-frontend      # Linux/macOS
scripts\start-local.ps1 -WithFrontend         # Windows PowerShell

# stop everything
./scripts/stop-local.sh                       # or scripts\stop-local.ps1
```

Then open **http://localhost:3000** (dashboard) or hit the API directly:

```bash
curl -X POST localhost:8080/api/data/generate -H 'Content-Type: application/json' -d '{"accounts":40}'
curl -X POST localhost:8082/api/batch/start        # 8082 becomes master for this run
curl localhost:8081/api/batch/status               # readable from any instance
```

The script builds `target/distributed-batch-orchestrator-1.0.0.jar` if it is missing (`--build` forces a rebuild),
wipes `./data`, starts `instance-8080` first and waits for it to be healthy (it owns schema init), then starts
8081–8085 and prints an UP/DOWN table. Logs go to `logs/instance-<port>.log`, PIDs to `.pids/`.

Makefile shortcuts:

| Target                  | What it does                                               |
|-------------------------|------------------------------------------------------------|
| `make build`            | `mvn -q package -DskipTests`                               |
| `make test`             | `mvn -q test`                                              |
| `make run`              | `scripts/start-local.sh` (6 instances)                     |
| `make run-all`          | `scripts/start-local.sh --with-frontend`                   |
| `make stop`             | `scripts/stop-local.sh`                                    |
| `make clean`            | `mvn -q clean` and remove `data/`, `logs/`, `.pids/`       |
| `make docker-build`     | `docker build -t distributed-batch-orchestrator:1.0.0 .`   |
| `make frontend-install` | `npm install` in `frontend/`                               |
| `make frontend-run`     | `npm start` in `frontend/` (port 3000)                     |
| `make help`             | List targets                                               |

## Configuration

Defaults live in [`src/main/resources/application.yml`](src/main/resources/application.yml); every value below can be
overridden with an environment variable. See [`.env.example`](.env.example) for a commented template.

| Variable                 | Default                                              | Purpose                                              |
|--------------------------|------------------------------------------------------|------------------------------------------------------|
| `SERVER_PORT`            | `8080`                                               | HTTP port of this instance                           |
| `APP_INSTANCE_ID`        | `instance-${server.port}` (k8s: pod `HOSTNAME`)      | Identity used for master election and attribution   |
| `APP_PEERS`              | `http://localhost:8080` … `http://localhost:8085`    | Peer base URLs for local-mode HTTP dispatch          |
| `APP_DATA_ACCOUNTS`      | `25`                                                 | Accounts generated on startup / when no body is sent |
| `APP_INTERNAL_TOKEN`     | `local-dev-internal-token`                           | Shared secret (`X-Internal-Token`) for peer dispatch; override outside local dev |
| `SPRING_PROFILES_ACTIVE` | `local`                                              | `kubernetes` switches to Kafka remote partitioning   |
| `DB_URL`                 | `jdbc:h2:file:./data/orchestrator;AUTO_SERVER=TRUE`  | JDBC URL of the shared H2 database                   |
| `KAFKA_BOOTSTRAP`        | `kafka:9092`                                         | Kafka bootstrap servers (`kubernetes` profile only)  |



## REST API

| Method | Path | Description |
|---|---|---|
| POST | `/api/data/generate` | Generate random banking data (`{"accounts": 40}`, optional) |
| GET | `/api/data/summary` | Current account/transaction counts |
| POST | `/api/batch/start` | Trigger a run — caller instance becomes Master (409 if a run is active) |
| GET | `/api/batch/status` | Latest run: status, master, partitions + worker attribution |
| GET | `/api/batch/status/{id}` | Same for a specific job execution |
| GET | `/api/batch/history` | Past runs with master, partition and report counts |
| GET | `/api/reports?jobExecutionId=N` | The generated per-account reports |
| GET | `/api/cluster` | Live view of all 6 instances |
| GET | `/api/info`, `/api/health` | Instance metadata / simple health (also `/actuator/health`) |
| POST | `/internal/partitions/execute` | Peer-to-peer partition dispatch (local mode); requires the `X-Internal-Token` header, else 403 |

All responses include a `timestamp` field. `GET /api/batch/history` accepts `?limit=` (default 50); `GET /api/batch/status`
returns 404 until the first run. Actuator also exposes `/actuator/info` and `/actuator/metrics`.

## Dashboard

The [`frontend/`](frontend/) folder is a static dashboard with no framework and no build step
(details in [`frontend/README.md`](frontend/README.md)):

```bash
cd frontend
npm install
npm start          # http://localhost:3000
```

It shows banking data counts, a start button, live status (job id, master, reports), run metrics (duration,
partitions, workers engaged, accounts per worker, throughput), the partition distribution table, a card per
instance (ports 8080–8085) and run history. It polls status and cluster every 2s and history every 5s; the API base
URL (default `http://localhost:8080`) can be changed in the top bar at runtime.

## Kubernetes / OpenShift-style deployment

```bash
# build the image
docker build -t distributed-batch-orchestrator:1.0.0 .

# or try the same topology locally first (Kafka + H2 server + 6 app containers):
docker compose up --build

# deploy: namespace, H2 server, single-node Kafka (KRaft), app deployment with 6 identical replicas
kubectl apply -k k8s/
```

The Compose file maps the six app containers to host ports 8080–8085, Kafka to `9094` and the H2 TCP server to `9092`,
so the same `curl` commands and dashboard work against it.

In this mode partitions travel over Kafka (`batch-partition-requests` topic, 6 partitions — one per pod) and the 6 pods form the `batch-workers` consumer group. Pods are truly identical — instance identity comes from the pod name via the Downward API.

## Testing

```bash
mvn test
```

22 tests: round-robin partitioning, data generation, report calculation + worker attribution (including failure marking), master election (lock won/lost/released), Kafka Java serializer/deserializer round-trips, and a full end-to-end local-mode run through the REST API.

## CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on pushes and pull requests to `main`:

- **Build & Test (Java)**: `mvn -B verify` on Temurin JDK 21
- **Frontend checks**: `npm ci` and `node --check` on every file in `frontend/js/`
- **Docker build (no push)**: builds the image after the Java job passes

Dependabot config: [`.github/dependabot.yml`](.github/dependabot.yml).

## Project layout

```
src/main/java/com/wallaceespindola/orchestrator/
  config/      Batch jobs (local + Kafka profiles), ShedLock, CORS, properties
  batch/       Round-robin split, local HTTP dispatch tasklet, Kafka worker tasklet
  domain/      Account, BankTransaction, AccountReport, PartitionAssignment
  repository/  Spring Data JPA repositories
  service/     Data generator, report processing, master election, cluster probe, status
  web/         REST controllers + error handling
src/main/resources/
  application.yml   local defaults + kubernetes profile
  schema.sql        idempotent DDL (domain tables, ShedLock, Spring Batch metadata)
src/test/java/      unit, election, serde and end-to-end tests
frontend/      Separate NPM project (plain HTML/CSS/JS dashboard)
scripts/       start/stop for sh and PowerShell
k8s/           Kubernetes manifests (kustomize): namespace, h2, kafka, app
Dockerfile, docker-compose.yml, Makefile, .env.example, specs.md
```

## Author

**Wallace Espindola** — Senior software engineer & solution architect (Java/Spring, Python, distributed systems).

- GitHub: [github.com/wallaceespindola](https://github.com/wallaceespindola/)
- LinkedIn: [linkedin.com/in/wallaceespindola](https://www.linkedin.com/in/wallaceespindola/)
- E-mail: wallace.espindola@gmail.com

## License

[Apache 2.0](LICENSE)

# distributed-batch-orchestrator

Distributed batch processing application built with **Java 21, Maven, Spring Boot 3.4 and Spring Batch**, backed by **H2**. Four identical instances process banking reports together: one instance is dynamically elected **Master** per run, the others act as **Workers**. A separate HTML/CSS/JavaScript frontend visualizes the whole thing live.

![CI](https://github.com/wallaceespindola/distributed-batch-orchestrator/actions/workflows/ci.yml/badge.svg)

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

- **4 identical instances** — same artifact, same config. No instance-specific images.
- **Dynamic master election** — the first instance to receive `POST /api/batch/start` acquires a **ShedLock** JDBC lock and becomes Master for that run. The lock guarantees a single active master (concurrent starts get HTTP 409) and is released when the job ends, so the role rotates between runs.
- **Round-robin partitioning** — account ids are split evenly (`i % workers`) into one partition per healthy worker; bucket sizes differ by at most 1.
- **Worker attribution** — each worker records its own `PARTITION_ASSIGNMENTS` row (partition key, worker id, master id, timing, status) and stamps every `ACCOUNT_REPORTS` row with its worker id. The status API surfaces both.
- **Shared state** — all instances share one H2 database (file + `AUTO_SERVER` locally, H2 TCP server on Kubernetes), which also holds the Spring Batch job repository — so status/history can be queried from **any** instance.

### Execution modes

| | Local mode (default) | Kubernetes mode (`kubernetes` profile) |
|---|---|---|
| Topology | 4 Spring Boot processes, ports 8080–8083 | 4 identical pods |
| Distribution | Master dispatches partitions to peers over **HTTP** (no Kafka) | **Kafka remote partitioning** (`spring-batch-integration`), consumer group `batch-workers` |
| Batch job | `accountReportJob` → dispatch tasklet | `accountReportJob` → manager step + remote worker steps |
| Completion tracking | HTTP responses | Manager polls the shared job repository |

Same codebase, mode selected purely by Spring profile.

### Batch flow

1. Random banking data (10–100+ accounts, 5–50 transactions each) is generated into H2 — reset on startup, regenerable on demand.
2. Master reads all account ids and splits them round-robin.
3. Each partition is executed by a worker (HTTP call in local mode, Kafka `StepExecutionRequest` in Kubernetes mode). All workers run the same `ReportService`.
4. One report per account: transaction count, total credits/debits, ending balance, plus the worker that produced it.
5. Job status, partition distribution and history are exposed via REST and shown live in the frontend.

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
| GET | `/api/cluster` | Live view of all 4 instances |
| GET | `/api/info`, `/api/health` | Instance metadata / simple health (also `/actuator/health`) |

All responses include a `timestamp` field.

## Quick start (local mode)

Prerequisites: Java 21+, Maven, Node.js (frontend only).

```bash
# one command: builds if needed, resets ./data, starts 4 instances + frontend
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

Makefile shortcuts: `make build`, `make test`, `make run`, `make run-all`, `make stop`, `make help`.

## Kubernetes / OpenShift-style deployment

```bash
# build the image
docker build -t distributed-batch-orchestrator:1.0.0 .

# or try the same topology locally first (Kafka + H2 server + 4 app containers):
docker compose up --build

# deploy: namespace, H2 server, single-node Kafka (KRaft), app deployment with 4 identical replicas
kubectl apply -k k8s/
```

In this mode partitions travel over Kafka (`batch-partition-requests` topic, 8 partitions) and the 4 pods form the `batch-workers` consumer group. Pods are truly identical — instance identity comes from the pod name via the Downward API.

## Testing

```bash
mvn test
```

18 tests: round-robin partitioning, data generation, report calculation + worker attribution (including failure marking), master election (lock won/lost/released), and a full end-to-end local-mode run through the REST API.

## Project layout

```
src/main/java/com/wallaceespindola/orchestrator/
  config/      Batch jobs (local + Kafka profiles), ShedLock, CORS, properties
  batch/       Round-robin split, local HTTP dispatch tasklet, Kafka worker tasklet
  domain/      Account, BankTransaction, AccountReport, PartitionAssignment
  repository/  Spring Data JPA repositories
  service/     Data generator, report processing, master election, cluster probe, status
  web/         REST controllers + error handling
frontend/      Separate NPM project (plain HTML/CSS/JS dashboard)
scripts/       start/stop for sh and PowerShell
k8s/           Kubernetes manifests (kustomize)
```

## Author

**Wallace Espindola** — Senior software engineer & solution architect (Java/Spring, Python, distributed systems).

- GitHub: [github.com/wallaceespindola](https://github.com/wallaceespindola/)
- LinkedIn: [linkedin.com/in/wallaceespindola](https://www.linkedin.com/in/wallaceespindola/)
- E-mail: wallace.espindola@gmail.com

## License

[Apache 2.0](LICENSE)

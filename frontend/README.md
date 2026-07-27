# Distributed Batch Orchestrator — Frontend

Static dashboard (plain HTML/CSS/JS, no framework, no build step) for the
distributed Spring Batch banking-report orchestrator. Talks directly to the
backend REST API over `fetch`.

## Run

```bash
pnpm install   # or npm install
pnpm start     # or npm start
```

Serves on http://localhost:3000 via `http-server`.

## Configuration

Default API base is `http://localhost:8080`; change it from the input field
in the top bar at runtime (no rebuild needed). The cluster panel always
probes ports 8080-8083 for the 4 backend instances.

## Polling

- `/api/batch/status` and `/api/cluster` — every 2s
- `/api/batch/history` — every 5s

Backend outages are handled silently (offline state shown, no console spam).

## Author

Wallace Espindola — wallace.espindola@gmail.com
[GitHub](https://github.com/wallaceespindola/) · [LinkedIn](https://www.linkedin.com/in/wallaceespindola/)

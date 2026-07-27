#!/usr/bin/env bash
# Starts 4 identical instances of distributed-batch-orchestrator in local mode.
# Usage: scripts/start-local.sh [--build] [--with-frontend]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
JAR_PATH="${ROOT_DIR}/target/distributed-batch-orchestrator-1.0.0.jar"
PORTS=(8080 8081 8082 8083)
PID_DIR="${ROOT_DIR}/.pids"
LOG_DIR="${ROOT_DIR}/logs"
DATA_DIR="${ROOT_DIR}/data"

BUILD=false
WITH_FRONTEND=false
for arg in "$@"; do
  case "$arg" in
    --build) BUILD=true ;;
    --with-frontend) WITH_FRONTEND=true ;;
    *) echo "Unknown option: $arg" >&2; exit 1 ;;
  esac
done

log()  { echo "[start-local] $*"; }
fail() { echo "[start-local] ERROR: $*" >&2; exit 1; }

# --- check Java 21+ ---
command -v java >/dev/null 2>&1 || fail "java not found on PATH. Install Java 21+."
JAVA_MAJOR="$(java -version 2>&1 | head -1 | awk -F '"' '{print $2}' | cut -d. -f1)"
[ "${JAVA_MAJOR}" -ge 21 ] 2>/dev/null || fail "Java 21+ required, found major version '${JAVA_MAJOR}'."
log "Java ${JAVA_MAJOR} detected."

# --- build jar if needed ---
if [ "${BUILD}" = true ] || [ ! -f "${JAR_PATH}" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not found on PATH."
  log "Building jar (mvn -q package -DskipTests)..."
  (cd "${ROOT_DIR}" && mvn -q package -DskipTests)
  [ -f "${JAR_PATH}" ] || fail "Build finished but jar not found at ${JAR_PATH}"
else
  log "Jar already present at ${JAR_PATH} (use --build to force rebuild)."
fi

# --- fresh state ---
log "Resetting ./data ..."
rm -rf "${DATA_DIR}"
mkdir -p "${DATA_DIR}" "${LOG_DIR}" "${PID_DIR}"

wait_healthy() {
  local port="$1" timeout="$2" waited=0
  while [ "${waited}" -lt "${timeout}" ]; do
    if curl -fsS "http://localhost:${port}/actuator/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done
  return 1
}

start_instance() {
  local port="$1" batch_init="$2"
  local logfile="${LOG_DIR}/instance-${port}.log"
  local pidfile="${PID_DIR}/instance-${port}.pid"
  log "Starting instance-${port} (BATCH_INIT=${batch_init}) -> ${logfile}"
  (
    cd "${ROOT_DIR}"
    SERVER_PORT="${port}" \
    APP_INSTANCE_ID="instance-${port}" \
    BATCH_INIT="${batch_init}" \
    nohup java -jar "${JAR_PATH}" >"${logfile}" 2>&1 &
    echo $! > "${pidfile}"
  )
}

# instance 1 first (schema init owner), then wait for it healthy
start_instance "${PORTS[0]}" "always"
log "Waiting for instance-${PORTS[0]} to become healthy (up to 60s)..."
wait_healthy "${PORTS[0]}" 60 || fail "instance-${PORTS[0]} did not become healthy in time. Check ${LOG_DIR}/instance-${PORTS[0]}.log"
log "instance-${PORTS[0]} healthy."

sleep 3

# remaining instances can start together
for port in "${PORTS[@]:1}"; do
  start_instance "${port}" "never"
done

log "Waiting for all instances to become healthy (up to 90s)..."
ALL_HEALTHY=true
for port in "${PORTS[@]:1}"; do
  if ! wait_healthy "${port}" 90; then
    log "instance-${port} did NOT become healthy. Check ${LOG_DIR}/instance-${port}.log"
    ALL_HEALTHY=false
  fi
done

if [ "${WITH_FRONTEND}" = true ]; then
  FRONTEND_DIR="${ROOT_DIR}/frontend"
  if [ -d "${FRONTEND_DIR}" ]; then
    command -v npm >/dev/null 2>&1 || fail "npm not found on PATH."
    if [ ! -d "${FRONTEND_DIR}/node_modules" ]; then
      log "Installing frontend dependencies..."
      (cd "${FRONTEND_DIR}" && npm install)
    fi
    log "Starting frontend (npm start) -> ${LOG_DIR}/frontend.log"
    (
      cd "${FRONTEND_DIR}"
      nohup npm start >"${LOG_DIR}/frontend.log" 2>&1 &
      echo $! > "${PID_DIR}/frontend.pid"
    )
  else
    log "WARNING: --with-frontend requested but ${FRONTEND_DIR} not found; skipping."
  fi
fi

echo
printf "%-14s %-8s %-38s %-8s\n" "INSTANCE" "PORT" "URL" "STATUS"
for port in "${PORTS[@]}"; do
  status="DOWN"
  curl -fsS "http://localhost:${port}/actuator/health" >/dev/null 2>&1 && status="UP"
  printf "%-14s %-8s %-38s %-8s\n" "instance-${port}" "${port}" "http://localhost:${port}" "${status}"
done
if [ "${WITH_FRONTEND}" = true ] && [ -d "${ROOT_DIR}/frontend" ]; then
  printf "%-14s %-8s %-38s %-8s\n" "frontend" "3000" "http://localhost:3000" "STARTED"
fi
echo

[ "${ALL_HEALTHY}" = true ] || fail "One or more instances failed health check. See logs in ${LOG_DIR}."
log "All instances healthy."

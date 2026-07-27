#!/usr/bin/env bash
# Stops all instances (and frontend, if running) started by start-local.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PID_DIR="${ROOT_DIR}/.pids"

log() { echo "[stop-local] $*"; }

if [ ! -d "${PID_DIR}" ]; then
  log "No ${PID_DIR} directory found; nothing to stop."
  exit 0
fi

shopt -s nullglob
PID_FILES=("${PID_DIR}"/*.pid)
shopt -u nullglob

if [ ${#PID_FILES[@]} -eq 0 ]; then
  log "No pid files found; nothing to stop."
  exit 0
fi

for pidfile in "${PID_FILES[@]}"; do
  name="$(basename "${pidfile}" .pid)"
  pid="$(cat "${pidfile}" 2>/dev/null || true)"

  if [ -z "${pid}" ] || ! kill -0 "${pid}" 2>/dev/null; then
    log "${name}: stale pid file (process not running), removing."
    rm -f "${pidfile}"
    continue
  fi

  log "${name}: stopping pid ${pid} (SIGTERM)..."
  kill "${pid}" 2>/dev/null || true

  waited=0
  while kill -0 "${pid}" 2>/dev/null && [ "${waited}" -lt 10 ]; do
    sleep 1
    waited=$((waited + 1))
  done

  if kill -0 "${pid}" 2>/dev/null; then
    log "${name}: still running after 10s, sending SIGKILL."
    kill -9 "${pid}" 2>/dev/null || true
  fi

  rm -f "${pidfile}"
  log "${name}: stopped."
done

log "All done."

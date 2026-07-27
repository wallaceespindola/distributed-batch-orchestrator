// Distributed Batch Orchestrator - dashboard frontend (no framework, no build step)

const DEFAULT_API_BASE = 'http://localhost:8080';
const CLUSTER_PORTS = [8080, 8081, 8082, 8083];
const STATUS_POLL_MS = 2000;
const HISTORY_POLL_MS = 5000;

let apiBase = DEFAULT_API_BASE;
let lastMasterId = null;

function el(id) {
  return document.getElementById(id);
}

async function fetchJson(url, options) {
  const res = await fetch(url, options);
  if (!res.ok && res.status !== 404 && res.status !== 409) {
    throw new Error(`HTTP ${res.status}`);
  }
  const body = await res.json().catch(() => null);
  return { status: res.status, body };
}

function setBadge(status) {
  const inner = el('statusBadgeInner');
  const s = (status || 'none').toLowerCase();
  inner.textContent = status || 'NONE';
  inner.className = `badge ${s}`;
}

function setMsg(text, kind) {
  const msg = el('actionMsg');
  msg.textContent = text || '';
  msg.className = `msg ${kind || ''}`.trim();
}

function fmtTime(v) {
  if (!v) return '-';
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? String(v) : d.toLocaleTimeString();
}

// ponytail: escape backend-sourced strings before innerHTML interpolation - no sanitizer lib, this is enough for plain text fields
function esc(v) {
  return String(v ?? '-').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  }[c]));
}

// ---- actions ----

async function generateData() {
  const accounts = Math.min(100, Math.max(10, Number(el('accountsInput').value) || 25));
  el('generateBtn').disabled = true;
  setMsg('Generating data...', '');
  try {
    const { body } = await fetchJson(`${apiBase}/api/data/generate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ accounts }),
    });
    if (body) {
      el('statAccounts').textContent = body.accounts ?? '-';
      el('statTransactions').textContent = body.transactions ?? '-';
    }
    setMsg('Data generated.', 'ok');
  } catch (e) {
    setMsg('Failed to generate data (backend unreachable?)', 'error');
  } finally {
    el('generateBtn').disabled = false;
  }
}

async function loadDataSummary() {
  try {
    const { body } = await fetchJson(`${apiBase}/api/data/summary`);
    if (body) {
      el('statAccounts').textContent = body.accounts ?? '-';
      el('statTransactions').textContent = body.transactions ?? '-';
    }
  } catch (e) {
    // ponytail: silent - summary is best-effort, status polling already reports offline state
  }
}

async function startProcessing() {
  el('startBtn').disabled = true;
  setMsg('Starting...', '');
  try {
    const { status, body } = await fetchJson(`${apiBase}/api/batch/start`, { method: 'POST' });
    if (status === 409) {
      setMsg(body?.error || 'A run is already active.', 'error');
    } else {
      setMsg(`Started job ${body?.jobExecutionId ?? ''}`.trim(), 'ok');
    }
  } catch (e) {
    setMsg('Failed to start processing (backend unreachable?)', 'error');
  } finally {
    el('startBtn').disabled = false;
  }
}

// ---- polling ----

function renderPartitions(partitions) {
  const tbody = el('partitionsBody');
  if (!partitions || partitions.length === 0) {
    tbody.innerHTML = '<tr><td colspan="7" class="empty">No run yet</td></tr>';
    return;
  }
  tbody.innerHTML = partitions
    .map(
      (p) => `
    <tr>
      <td class="mono">${esc(p.partitionKey)}</td>
      <td class="mono">${esc(p.workerId)}</td>
      <td class="mono">${esc(p.masterId)}</td>
      <td class="mono">${esc(p.accountCount)}</td>
      <td><span class="badge ${esc((p.status || '').toLowerCase())}">${esc(p.status)}</span></td>
      <td class="mono">${esc(fmtTime(p.startedAt))}</td>
      <td class="mono">${esc(fmtTime(p.finishedAt))}</td>
    </tr>`
    )
    .join('');
}

async function pollStatus() {
  try {
    const { status, body } = await fetchJson(`${apiBase}/api/batch/status`);
    if (status === 404 || !body) {
      el('statusJobId').textContent = '-';
      setBadge('NONE');
      el('statusMaster').textContent = '-';
      el('statusReports').textContent = '-';
      el('statusStart').textContent = '-';
      el('statusEnd').textContent = '-';
      renderPartitions([]);
      lastMasterId = null;
      return;
    }
    el('statusJobId').textContent = body.jobExecutionId ?? '-';
    setBadge(body.status);
    el('statusMaster').textContent = body.masterId ?? '-';
    el('statusReports').textContent = body.reportsGenerated ?? '-';
    el('statusStart').textContent = fmtTime(body.startTime);
    el('statusEnd').textContent = fmtTime(body.endTime);
    renderPartitions(body.partitions);
    lastMasterId = body.masterId ?? null;
  } catch (e) {
    setBadge('NONE');
    lastMasterId = null;
  }
}

function renderCluster(instances) {
  const cards = el('clusterCards');
  cards.innerHTML = CLUSTER_PORTS.map((port) => {
    const found = instances.find((i) => i.port === port || (i.url || '').includes(String(port)));
    const up = !!found?.up;
    const instanceId = found?.instanceId || '-';
    const isMaster = !!found && lastMasterId && found.instanceId === lastMasterId;
    return `
      <div class="cluster-card${isMaster ? ' master' : ''}">
        <div class="cluster-card-head">
          <span class="mono">:${port}</span>
          <span class="dot${up ? ' up' : ''}" title="${up ? 'UP' : 'DOWN'}"></span>
        </div>
        <span class="mono">${esc(instanceId)}</span>
        ${isMaster ? '<span class="badge started">MASTER</span>' : ''}
      </div>`;
  }).join('');
}

async function pollCluster() {
  try {
    const { body } = await fetchJson(`${apiBase}/api/cluster`);
    renderCluster(body?.instances || []);
  } catch (e) {
    renderCluster([]);
  }
}

function renderHistory(runs) {
  const tbody = el('historyBody');
  if (!runs || runs.length === 0) {
    tbody.innerHTML = '<tr><td colspan="5" class="empty">No history yet</td></tr>';
    return;
  }
  tbody.innerHTML = runs
    .map(
      (r) => `
    <tr>
      <td class="mono">${esc(r.jobExecutionId)}</td>
      <td><span class="badge ${esc((r.status || '').toLowerCase())}">${esc(r.status)}</span></td>
      <td class="mono">${esc(r.masterId)}</td>
      <td class="mono">${esc(r.partitionCount)}</td>
      <td class="mono">${esc(r.reportCount)}</td>
    </tr>`
    )
    .join('');
}

async function pollHistory() {
  try {
    const { body } = await fetchJson(`${apiBase}/api/batch/history`);
    renderHistory(body?.runs || []);
  } catch (e) {
    // ponytail: silent - status/cluster polling already surfaces offline state
  }
}

// ---- setup ----

function applyApiBase() {
  const val = el('apiBaseInput').value.trim().replace(/\/+$/, '');
  apiBase = val || DEFAULT_API_BASE;
  loadDataSummary();
  pollStatus();
  pollCluster();
  pollHistory();
}

function init() {
  el('apiBaseInput').value = apiBase;
  el('apiBaseSave').addEventListener('click', applyApiBase);
  el('generateBtn').addEventListener('click', generateData);
  el('startBtn').addEventListener('click', startProcessing);

  loadDataSummary();
  pollStatus();
  pollCluster();
  pollHistory();

  setInterval(pollStatus, STATUS_POLL_MS);
  setInterval(pollCluster, STATUS_POLL_MS);
  setInterval(pollHistory, HISTORY_POLL_MS);
}

document.addEventListener('DOMContentLoaded', init);

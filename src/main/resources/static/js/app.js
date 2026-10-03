// CDR console: streams feeds (optionally gzip-compressed in the browser), tracks async jobs, queries usage.
const API = '/api/v1';
const $ = (s) => document.querySelector(s);
let pollTimer = null;

$('#feed').addEventListener('change', () => { $('#send').disabled = !$('#feed').files.length; });
$('#send').addEventListener('click', sendFeed);
$('#refresh').addEventListener('click', loadJobs);
$('#usage').addEventListener('click', loadUsage);

async function sendFeed() {
  const file = $('#feed').files[0];
  const source = $('#source').value.trim();
  if (!source) { status('#send-status', 'Enter a source name, for example MSC-01.', 'warn'); return; }
  const mode = document.querySelector('input[name="mode"]:checked').value;
  const alreadyGzip = file.name.endsWith('.gz');
  let body = file;
  let gzip = alreadyGzip;

  if (!alreadyGzip && $('#compress').checked && 'CompressionStream' in window) {
    status('#send-status', 'Compressing…', '');
    body = await new Response(file.stream().pipeThrough(new CompressionStream('gzip'))).blob();
    gzip = true;
  }

  const xhr = new XMLHttpRequest();
  xhr.open('POST', `${API}/cdrs/${mode}?source=${encodeURIComponent(source)}`);
  xhr.setRequestHeader('Content-Type', file.name.includes('.json') && !file.name.includes('.ndjson') ? 'application/json' : 'application/x-ndjson');
  if (gzip) xhr.setRequestHeader('Content-Encoding', 'gzip');

  $('#meter').hidden = false; $('#meter-bar').style.width = '0';
  $('#summary').hidden = true; $('#errors').innerHTML = '';
  $('#send').disabled = true;
  status('#send-status', `Sending ${size(body.size)}${gzip ? ' (gzip)' : ''}…`, '');

  xhr.upload.onprogress = (e) => { if (e.lengthComputable) $('#meter-bar').style.width = `${(e.loaded / e.total) * 100}%`; };
  xhr.upload.onload = () => { if (mode === 'stream') status('#send-status', 'Uploaded. Waiting for the server to finish ingesting…', ''); };
  xhr.onload = () => {
    $('#send').disabled = false; $('#meter').hidden = true;
    let data = {};
    try { data = JSON.parse(xhr.responseText); } catch { /* not JSON */ }
    if (xhr.status === 200) {
      showSummary(data);
      status('#send-status', data.rejected ? 'Done. Some records were rejected; see below.' : 'Done. Every record was processed.', data.rejected ? 'warn' : 'ok');
    } else if (xhr.status === 202) {
      status('#send-status', 'Accepted. The job is running in the background; its progress appears on the right.', 'ok');
      loadJobs(); startPolling();
    } else {
      status('#send-status', data.detail || `The server answered ${xhr.status}.`, xhr.status >= 500 ? 'err' : 'warn');
      if (data.partialResult) showSummary(data.partialResult);
    }
  };
  xhr.onerror = () => { $('#send').disabled = false; $('#meter').hidden = true;
    status('#send-status', 'The feed did not reach the server. Check the connection and try again.', 'err'); };
  xhr.send(body);
}

function showSummary(s) {
  const rows = [['Received', s.received], ['Accepted', s.accepted], ['Duplicates', s.duplicates],
    ['Rejected', s.rejected], ['Batch', s.batchId], ['Time', `${s.durationMs} ms`]];
  $('#summary').innerHTML = rows.map(([k, v]) => `<dt>${k}</dt><dd>${esc(fmt(v))}</dd>`).join('');
  $('#summary').hidden = false;
  $('#errors').innerHTML = (s.errors || []).slice(0, 10)
    .map((e) => `<li>Record ${e.recordIndex}${e.recordId ? ` (${esc(e.recordId)})` : ''}: ${esc(e.message)}</li>`).join('');
}

async function loadJobs() {
  const res = await fetch(`${API}/ingestion-jobs?size=10`);
  if (!res.ok) return;
  const page = await res.json();
  $('#jobs').innerHTML = page.content.length ? page.content.map((j) => `
      <tr>
        <td>${new Date(j.createdAt).toLocaleTimeString()}</td>
        <td>${esc(j.source)}</td>
        <td><span class="badge ${j.status}">${j.status.toLowerCase()}</span></td>
        <td class="num">${fmt(j.accepted)}</td>
        <td class="num">${fmt(j.rejected)}</td>
      </tr>`).join('') : '<tr><td colspan="5" class="muted">No jobs yet.</td></tr>';
  return page.content.some((j) => j.status === 'PENDING' || j.status === 'RUNNING');
}

function startPolling() {
  clearInterval(pollTimer);
  pollTimer = setInterval(async () => { if (!(await loadJobs())) clearInterval(pollTimer); }, 1500);
}

async function loadUsage() {
  const msisdn = $('#msisdn').value.trim();
  if (!/^[6-9]\d{9}$/.test(msisdn)) { status('#usage-status', 'Enter a 10-digit mobile number starting with 6–9.', 'warn'); return; }
  const from = new Date(`${$('#from').value}T00:00:00Z`);
  const to = new Date(`${$('#to').value}T00:00:00Z`); to.setUTCDate(to.getUTCDate() + 1);   // inclusive end day
  const res = await fetch(`${API}/subscribers/${msisdn}/usage?from=${from.toISOString()}&to=${to.toISOString()}`);
  const data = await res.json();
  if (!res.ok) { status('#usage-status', data.detail || 'Could not load usage.', 'warn'); $('#usage-result').hidden = true; return; }
  status('#usage-status', '', '');
  $('#u-voice').textContent = fmt(data.voiceCalls);
  $('#u-minutes').textContent = fmt(Math.round(data.voiceSeconds / 60));
  $('#u-sms').textContent = fmt(data.smsCount);
  $('#u-data').textContent = size(data.dataBytes);
  $('#u-charge').textContent = Number(data.totalCharge).toFixed(2);
  $('#usage-result').hidden = false;
}

function status(sel, msg, kind) { const el = $(sel); el.textContent = msg; el.className = `status ${kind}`; }
function fmt(v) { return typeof v === 'number' ? v.toLocaleString() : (v ?? ''); }
function size(b) { const u = ['B', 'KB', 'MB', 'GB', 'TB']; let i = 0; while (b >= 1024 && i < u.length - 1) { b /= 1024; i++; } return `${b.toFixed(i ? 1 : 0)} ${u[i]}`; }
function esc(s) { return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }

// sensible defaults: last 30 days
const today = new Date();
$('#to').value = today.toISOString().slice(0, 10);
$('#from').value = new Date(today.getTime() - 30 * 86400000).toISOString().slice(0, 10);
loadJobs();

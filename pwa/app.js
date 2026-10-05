'use strict';
/* Rubato Guru PWA v1 — downloader: discover/enqueue/jobs su guru-api. */

const $ = (id) => document.getElementById(id);
const store = {
  get url() { return (localStorage.getItem('guru_url') || '').replace(/\/$/, ''); },
  get token() { return localStorage.getItem('guru_token') || ''; },
};

let searchType = 'track';
let jobsTimer = null;

async function api(path, opts) {
  opts = opts || {};
  opts.headers = Object.assign({ Authorization: 'Bearer ' + store.token }, opts.headers || {});
  const res = await fetch(store.url + path, opts);
  if (!res.ok) {
    const txt = await res.text().catch(() => '');
    throw new Error(res.status + ' ' + txt.slice(0, 120));
  }
  return res.json();
}

function needCfg() {
  return !store.url || !store.token;
}

function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"]/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
}

/* ---------- config ---------- */
function loadCfg() {
  $('cfgUrl').value = localStorage.getItem('guru_url') || '';
  $('cfgToken').value = store.token;
  $('cfg').hidden = true;
  updateCfgState();
}

async function updateCfgState() {
  const el = $('cfgState');
  if (needCfg()) {
    el.textContent = 'Inserisci URL e token per iniziare.';
    return;
  }
  el.textContent = 'Verifica…';
  try {
    const h = await api('/health');
    el.textContent = 'OK · dry-run: ' + (h.dry_run ? 'sì' : 'no');
  } catch (e) {
    el.textContent = 'Errore: ' + e.message;
  }
}

/* ---------- discover ---------- */
function rowHtml(item) {
  const cover = item.cover
    ? '<img loading="lazy" src="' + esc(item.cover) + '" alt="">'
    : '<img alt="">';
  const sub = esc(item.artist || '') + (item.album ? ' • ' + esc(item.album) : '');
  const dur = item.duration
    ? ' · ' + Math.floor(item.duration / 60) + ':' + String(item.duration % 60).padStart(2, '0') : '';
  return '<li class="row" data-id="' + item.id + '">' + cover +
    '<div class="meta"><div class="t">' + esc(item.title) + '</div>' +
    '<div class="a">' + sub + dur + '</div></div>' +
    '<button class="dl" aria-label="Scarica">⬇</button></li>';
}

function bindDownloads(container, items, kind) {
  container.querySelectorAll('button.dl').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const li = btn.closest('li.row');
      const item = items.find((t) => String(t.id) === li.dataset.id);
      if (!item) return;
      btn.disabled = true;
      try {
        let jobId = null;
        if (kind === 'album') {
          const al = await api('/deezer/album/' + item.id);
          const titles = (al.tracks || []).map((t) => t.title).filter(Boolean);
          if (!titles.length) throw new Error('tracklist vuota');
          const acc = await api('/slskd/enqueue-album', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ artist: item.artist, album: item.title, tracks: titles }),
          });
          jobId = acc.job_id;
        } else {
          const acc = await api('/slskd/enqueue', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ artist: item.artist, title: item.title, album: item.album || null }),
          });
          jobId = acc.job_id;
        }
        if (jobId) watch(jobId, 'fetch');
        $('searchInfo').textContent = 'Accodato — ti avviso qui sotto quando è pronto.';
        loadJobs();
      } catch (e) {
        $('searchInfo').textContent = 'Errore: ' + e.message;
        btn.disabled = false;
      }
    });
  });
}

/* ---------- auto-import chain (come il Worker su Android) ---------- */
function getWatched() {
  try {
    return JSON.parse(localStorage.getItem('guru_watch') || '[]');
  } catch (e) {
    return [];
  }
}

function setWatched(list) {
  localStorage.setItem('guru_watch', JSON.stringify(list.slice(-20)));
}

function watch(id, phase) {
  const w = getWatched().filter((x) => x.id !== id);
  w.push({ id: id, phase: phase || 'fetch' });
  setWatched(w);
}

function unwatch(id) {
  setWatched(getWatched().filter((x) => x.id !== id));
}

async function checkWatched() {
  const watched = getWatched();
  if (!watched.length || needCfg()) return;
  for (const w of watched) {
    try {
      const st = await api('/slskd/status/' + w.id);
      if (st.status === 'failed') {
        unwatch(w.id);
        continue;
      }
      if (st.status !== 'done') continue;
      if (w.phase === 'fetch') {
        const files = st.files || [];
        if (!files.length) {
          // niente file: riprova il fetch (server: max tentativi)
          try {
            await api('/slskd/retry/' + w.id, { method: 'POST' });
          } catch (e) {
            if (String(e.message).indexOf('410') >= 0) unwatch(w.id);
          }
          continue;
        }
        const imp = await api('/import', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ job_id: w.id }),
        });
        if (imp.job_id) watch(imp.job_id, 'import');
        unwatch(w.id);
      } else {
        unwatch(w.id); // import done/failed: la lista job mostra l'esito
      }
    } catch (e) {
      /* riprova al prossimo giro */
    }
  }
  loadJobs();
}

function startChainPoll() {
  if (window._chainTimer) clearInterval(window._chainTimer);
  checkWatched();
  window._chainTimer = setInterval(checkWatched, 20000);
}

/* ---------- jobs ---------- */
function jobStateLabel(st) {
  return { queued: 'in coda…', running: 'in corso…', done: 'pronto ✓', failed: 'fallito ✗' }[st] || st;
}

async function loadJobs() {
  const ul = $('jobs');
  try {
    const data = await api('/slskd/jobs?limit=15');
    ul.innerHTML = (data.jobs || []).map((j) => {
      const what = esc(j.artist || '') +
        (j.album ? ' — ' + esc(j.album) : (j.title ? ' — ' + esc(j.title) : ''));
      const extra = j.moved ? ' · ' + j.moved + ' file' : '';
      const scan = j.scan ? ' · scan ' + esc(j.scan) : '';
      return '<li class="row"><div class="meta"><div class="t">' + what + '</div>' +
        '<div class="a">' + esc(j.kind || '') + ' · ' + esc(j.job_id.slice(0, 6)) + extra + scan + '</div></div>' +
        '<div class="st ' + esc(j.status) + '">' + jobStateLabel(j.status) + '</div></li>';
    }).join('') || '<li class="row"><div class="meta"><div class="a">Nessun download.</div></div></li>';
  } catch (e) {
    ul.innerHTML = '<li class="row"><div class="meta"><div class="a">Errore: ' +
      esc(e.message) + '</div></div></li>';
  }
}

function startJobsPoll() {
  if (jobsTimer) clearInterval(jobsTimer);
  loadJobs();
  jobsTimer = setInterval(loadJobs, 15000);
  startChainPoll();
}

/* ---------- wire ---------- */
document.querySelectorAll('#typeRow button').forEach((b) => {
  b.addEventListener('click', () => {
    searchType = b.dataset.type;
    document.querySelectorAll('#typeRow button').forEach((x) => x.classList.toggle('on', x === b));
    $('results').innerHTML = '';
    $('searchInfo').textContent = '';
  });
});

$('searchForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const q = $('q').value.trim();
  if (q.length < 2 || needCfg()) {
    $('searchInfo').textContent = needCfg() ? 'Configura prima URL e token (⚙).' : '';
    return;
  }
  $('searchInfo').textContent = 'Cerco…';
  $('results').innerHTML = '';
  try {
    const data = await api('/discover?q=' + encodeURIComponent(q) + '&type=' + searchType);
    const key = searchType === 'album' ? 'albums' : 'tracks';
    const items = data[key] || [];
    $('searchInfo').textContent = items.length ? items.length + ' risultati.' : 'Niente trovato.';
    const ul = $('results');
    ul.innerHTML = items.map(rowHtml).join('');
    bindDownloads(ul, items, searchType);
  } catch (err) {
    $('searchInfo').textContent = 'Errore: ' + err.message;
  }
});

$('cfgToggle').addEventListener('click', () => {
  $('cfg').hidden = !$('cfg').hidden;
});
$('cfgSave').addEventListener('click', async () => {
  localStorage.setItem('guru_url', $('cfgUrl').value.trim().replace(/\/$/, ''));
  localStorage.setItem('guru_token', $('cfgToken').value.trim());
  $('cfg').hidden = true;
  await updateCfgState();
  startJobsPoll();
});
$('jobsRefresh').addEventListener('click', loadJobs);

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}

loadCfg();
if (!needCfg()) startJobsPoll();

const input = document.getElementById('trackInput');
const searchBtn = document.getElementById('searchBtn');
const resultsEl = document.getElementById('results');
const emptyEl = document.getElementById('empty');
const toastEl = document.getElementById('toast');

let debounceTimer = null;
let activeCtrl = null;
const DEBOUNCE_MS = 300;

input.addEventListener('input', () => {
  clearTimeout(debounceTimer);
  const q = input.value.trim();
  if (!q) { resultsEl.innerHTML = ''; emptyEl.hidden = false; return; }
  debounceTimer = setTimeout(() => searchTrack(), DEBOUNCE_MS);
});

input.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') { e.preventDefault(); searchTrack(); }
});

function setLoading(isLoading) {
  const btn = document.getElementById('searchBtn');
  if (btn) btn.disabled = !!isLoading;

  document.body.classList.toggle('is-loading', !!isLoading);
  const spinner = document.getElementById('spinner');
  if (spinner) spinner.hidden = !isLoading;
}

function qp(name){ 
  const u = new URL(window.location.href); 
  return u.searchParams.get(name) || ""; 
}

async function searchTrack() {
  const query = input.value.trim();
  if (!query) { resultsEl.innerHTML=''; emptyEl.hidden=false; return; }

  if (activeCtrl) activeCtrl.abort();
  activeCtrl = new AbortController();

  setLoading(true);
  try {
    const res = await fetch(`/search?track=${encodeURIComponent(query)}&limit=25`, {
      headers: { 'Accept': 'application/json' },
      signal: activeCtrl.signal
    });
    if (!res.ok) {
      console.error('search failed', res.status, await res.text());
      showToast('Search failed. Try again.', true);
      return;
    }
    const data = await res.json();

    resultsEl.innerHTML = '';
    if (!Array.isArray(data) || data.length === 0) {
      emptyEl.hidden = false;
      emptyEl.textContent = 'No results. Try another search.';
      return;
    }
    emptyEl.hidden = true;

    data.forEach(t => {
      const row = document.createElement('div');
      row.className = 'row';

      let thumbEl;
      if (t.imageUrl) {
        thumbEl = document.createElement('img');
        thumbEl.className = 'thumb';
        thumbEl.src = t.imageUrl;
        thumbEl.alt = `${t.name ?? 'Track'} album cover`;
        thumbEl.width = 44; thumbEl.height = 44;
        thumbEl.decoding = 'async';
        thumbEl.loading = 'lazy';
      } else {
        thumbEl = document.createElement('div');
        thumbEl.className = 'thumb';
      }

      const meta = document.createElement('div');
      meta.className = 'meta';
      const title = document.createElement('div');
      title.className = 'title';
      title.textContent = t.name ?? 'Unknown title';
      const artist = document.createElement('div');
      artist.className = 'artist';
      artist.textContent = t.artist ?? 'Unknown artist';
      meta.appendChild(title);
      meta.appendChild(artist);

      const btn = document.createElement('button');
      btn.className = 'reqbtn';
      btn.textContent = 'Request';
      btn.onclick = () => submitRequest(t.uri);

      row.appendChild(thumbEl);
      row.appendChild(meta);
      row.appendChild(btn);
      resultsEl.appendChild(row);
    });
  } catch (err) {
    if (err.name !== 'AbortError') {
      console.error(err);
      showToast('Something went wrong searching. Please try again.', true);
    }
  } finally {
    setLoading(false);
  }
}


async function submitRequest(trackUri) {
  if (!trackUri) {
    showToast('Could not request this track (missing URI).', true);
    return;
  }

  const owner = qp("owner");
  const sig = qp("sig") || "";

  if (!owner) {
    showToast('This page was opened without a valid business QR. Please scan the QR again.', true);
    return;
  }

  const qs = new URLSearchParams({ owner, sig }).toString();

  try {
    const res = await fetch(`/request?${qs}`, {
      method: 'POST',
      headers: { 'Content-Type':'application/json' },
      body: JSON.stringify({ uri: trackUri })
    });
    const text = await res.text();
    if (!res.ok) {
      console.error(text);
      showToast('Request failed. Try again.', true);
    } else {
      showToast('Requested! Thanks.');
    }
  } catch (e) {
    console.error(e);
    showToast('Network error. Try again.', true);
  }
}

let toastTimer;
function showToast(msg, isError=false) {
  clearTimeout(toastTimer);
  toastEl.textContent = msg;
  toastEl.className = 'toast show ' + (isError ? 'err' : 'ok');
  toastTimer = setTimeout(() => toastEl.className = 'toast', 2200);
}

(async function loadPreferredGenres() {
  const wrap = document.getElementById('genres');
  if (!wrap) return;

  function render(list) {
    const unique = Array.from(new Set(
      (Array.isArray(list) ? list : [])
        .map(g => (g || '').trim().toLowerCase())
        .filter(Boolean)
    )).sort((a, b) => a.localeCompare(b));

    wrap.innerHTML = '';
    unique.forEach(g => {
      const chip = document.createElement('span');
      chip.className = 'chip';
      chip.textContent = g;
      wrap.appendChild(chip);
    });
  }

  try {
    const allowedRes = await fetch('/allowedGenres', { headers: { 'Accept': 'application/json' } });
    const allowed = allowedRes.ok ? await allowedRes.json() : [];

    if (Array.isArray(allowed) && allowed.length > 0) {
      render(allowed);
      return;
    }

    const allRes = await fetch('/genres', { headers: { 'Accept': 'application/json' } });
    const all = allRes.ok ? await allRes.json() : [];
    render(all);
  } catch (e) {
    console.error('Failed to load genres', e);
  }
})();



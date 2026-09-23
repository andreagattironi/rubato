"""guru-api — thin FastAPI wrapper sopra ~/music-guru/music_guru.py.

Non reimplementa nessuna regola di matching/scan: delega tutto alla CLI
via subprocess (fetch / compare / consolida / queue) e a Deezer per /discover.
Stati job persistiti in MG_JOBS_DIR/jobs.json (sopravvivono al restart).
"""
from __future__ import annotations

import asyncio
import hashlib
import json
import os
import secrets
import time
import urllib.parse
import urllib.request
from pathlib import Path

import httpx
from fastapi import Depends, FastAPI, Header, HTTPException, Query
from pydantic import BaseModel

# --- config (env, con default sensati per il Pi) -----------------------------
TOKEN = os.environ.get("GURU_API_TOKEN", "")
DRY_RUN = os.environ.get("GURU_DRY_RUN", "1") == "1"
MG_ROOT = Path(os.environ.get("MG_ROOT", "/home/micho/music-guru"))
MG_PY = MG_ROOT / "music_guru.py"
JOBS_DIR = Path(os.environ.get("MG_JOBS_DIR", "/home/micho/guru-api/jobs"))
JOBS_FILE = JOBS_DIR / "jobs.json"
ND_USER = os.environ.get("ND_USER", "")
ND_PASS = os.environ.get("ND_PASS", "")
ND_BASE = os.environ.get("ND_BASE_URL", "http://localhost:4533").rstrip("/")

JOBS_DIR.mkdir(parents=True, exist_ok=True)

app = FastAPI(title="guru-api", version="0.1.0")


# --- auth --------------------------------------------------------------------
def check_auth(authorization: str = Header(default="")) -> None:
    if not TOKEN:
        raise HTTPException(500, "GURU_API_TOKEN non configurato sul server")
    scheme, _, cred = authorization.partition(" ")
    if scheme.lower() != "bearer" or not secrets.compare_digest(cred, TOKEN):
        raise HTTPException(401, "token mancante o errato")


# --- jobs store (JSON, lock via asyncio) --------------------------------------
_lock = asyncio.Lock()


def _load_jobs() -> dict:
    if JOBS_FILE.exists():
        try:
            return json.loads(JOBS_FILE.read_text())
        except json.JSONDecodeError:
            return {}
    return {}


def _save_jobs(jobs: dict) -> None:
    tmp = JOBS_FILE.with_suffix(".tmp")
    tmp.write_text(json.dumps(jobs, indent=1, ensure_ascii=False))
    tmp.replace(JOBS_FILE)


# --- helpers ------------------------------------------------------------------
async def run_cli(*args: str, timeout: int = 600) -> dict:
    """Esegue `music_guru.py ...`, ritorna {rc, stdout, stderr}."""
    proc = await asyncio.create_subprocess_exec(
        "python3", str(MG_PY), *args,
        stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE,
    )
    try:
        out, err = await asyncio.wait_for(proc.communicate(), timeout)
    except asyncio.TimeoutError:
        proc.kill()
        return {"rc": -1, "stdout": "", "stderr": "timeout CLI"}
    return {
        "rc": proc.returncode,
        "stdout": out.decode(errors="replace")[-6000:],
        "stderr": err.decode(errors="replace")[-2000:],
    }


def nd_auth_params() -> dict:
    salt = secrets.token_hex(8)
    token = hashlib.md5(f"{ND_PASS}{salt}".encode()).hexdigest()
    return {"u": ND_USER, "t": token, "s": salt, "v": "1.16.1",
            "c": "guru-api", "f": "json"}


async def nd_get(path: str, extra: dict | None = None) -> dict:
    params = nd_auth_params()
    if extra:
        params.update(extra)
    url = f"{ND_BASE}/rest/{path}?" + urllib.parse.urlencode(params)
    async with httpx.AsyncClient(timeout=30) as cli:
        r = await cli.get(url)
        r.raise_for_status()
        resp = r.json().get("subsonic-response", {})
    if resp.get("status") != "ok":  # Navidrome: es. code 50 senza admin
        err = resp.get("error", {})
        raise RuntimeError(f"Navidrome {path}: {err.get('code')} {err.get('message')}")
    return resp


# --- background workers --------------------------------------------------------
async def _fetch_worker(job_id: str, json_path: str) -> None:
    async with _lock:
        jobs = _load_jobs()
        jobs[job_id]["status"] = "running"
        jobs[job_id]["started_at"] = time.strftime("%Y-%m-%dT%H:%M:%S")
        _save_jobs(jobs)
    args = [json_path, "--strategy", "track"]
    if not DRY_RUN:
        args.append("--commit")
    res = await run_cli("fetch", *args, timeout=1800)
    async with _lock:
        jobs = _load_jobs()
        jobs[job_id]["status"] = "done" if res["rc"] == 0 else "failed"
        jobs[job_id]["finished_at"] = time.strftime("%Y-%m-%dT%H:%M:%S")
        jobs[job_id]["cli_rc"] = res["rc"]
        jobs[job_id]["cli_tail"] = res["stdout"][-1500:]
        if res["rc"] != 0:
            jobs[job_id]["cli_stderr"] = res["stderr"][-500:]
        _save_jobs(jobs)


LIBRARY_PATH = os.environ.get("LIBRARY_PATH", "/mnt/music/library")
DOWNLOADS_DIR = Path(os.environ.get("DOWNLOADS_DIR", "/mnt/music/downloads/slskd"))
STAGING_ROOT = Path(os.environ.get("STAGING_ROOT", "/home/micho/guru-api/staging"))
REPORTS_DIR = MG_ROOT / "data" / "consolida_reports"
LOOKUP_CACHE = MG_ROOT / "data" / "staging_lookup_cache.json"
STAGING_ROOT.mkdir(parents=True, exist_ok=True)

AUDIO_EXT = (".flac", ".mp3", ".m4a", ".opus", ".ogg")

_COMP_HINTS = ("now that's", "now ", "hits", "compilation", "remix",
               "best of", "greatest", "collection", "vol.", "vol ", "mix",
               "dance", "club", "ministry", "various")


def _tokens(s: str) -> list[str]:
    import re
    return [t for t in re.sub(r"[^a-z0-9 ]", " ", s.lower()).split() if len(t) > 2]


def _looks_compilation(name: str) -> bool:
    n = name.lower()
    return any(h in n for h in _COMP_HINTS)


def _find_downloads(artist: str, title: str, since_ts: float) -> list[Path]:
    """File audio in downloads nuovi (mtime >= since) che contengono
    tutti i token del titolo (euristica di isolamento per job)."""
    want = _tokens(title)
    if not want:
        return []
    out = []
    if not DOWNLOADS_DIR.is_dir():
        return out
    for p in DOWNLOADS_DIR.rglob("*"):
        if not (p.is_file() and p.suffix.lower() in AUDIO_EXT):
            continue
        try:
            if p.stat().st_mtime < since_ts:
                continue
        except OSError:
            continue
        fn = " ".join(_tokens(p.name))
        if all(t in fn for t in want):
            out.append(p)
    return sorted(out)


def _parse_retag_dest(stdout: str) -> list[str]:
    """Estrae le destinazioni `a: ...` dal report di retag --staging."""
    import re
    return re.findall(r"^\s*a:\s+(.+)$", stdout, re.M)


def _lookup_choice(artist: str, title: str) -> dict:
    """Legge l'album scelto dal lookup (staging_lookup_cache.json).
    Match tollerante: la chiave cache usa l'artista canonizzato dai tag
    del file (es. 'Supermen'), mentre il job ha la stringa richiesta
    dall'utente (es. 'Superman') — si matcha per titolo + token artista."""
    try:
        cache = json.loads(LOOKUP_CACHE.read_text())
    except (OSError, json.JSONDecodeError):
        return {}
    v = cache.get(f"{artist}|{title}")
    if isinstance(v, dict):
        return {"album": v.get("album", ""), "year": v.get("year", ""),
                "track_number": v.get("track_number", "")}
    atoks = set(_tokens(artist))
    for k, vv in cache.items():
        ka, _, kt = k.partition("|")
        if kt.casefold() == title.casefold() and isinstance(vv, dict) \
                and (atoks & set(_tokens(ka))):
            return {"album": vv.get("album", ""), "year": vv.get("year", ""),
                    "track_number": vv.get("track_number", ""),
                    "cache_key": k}
    return {}


def _library_has(artist: str, title: str) -> list[str]:
    """File audio in libreria che matchano artista+titolo (anti-duplicati:
    un fetch-job già importato non viene re-importato)."""
    wt, wa = _tokens(title), set(_tokens(artist))
    out = []
    if not wt or not LIBRARY_PATH or not Path(LIBRARY_PATH).is_dir():
        return out
    for p in Path(LIBRARY_PATH).rglob("*"):
        if not (p.is_file() and p.suffix.lower() in AUDIO_EXT):
            continue
        fn = _tokens(p.name)
        if all(t in fn for t in wt) and (wa & set(fn)):
            out.append(str(p))
    return sorted(out)[:10]


def _new_manifests(before: set[str]) -> list[Path]:
    return [p for p in REPORTS_DIR.glob("*.manifest.json")
            if p.name not in before]


def _manifest_touches_library(m: Path) -> str:
    """'full' se il manifest sposta/rimuove file GIA' in libreria
    (rename/merge/drain di esistenti -> ghost -> serve full scan);
    'quick' se aggiunge solo file nuovi (li vede lo scan incrementale);
    'none' se non cambia la libreria. Illeggibile -> prudenza: 'full'."""
    try:
        ops = json.loads(m.read_text()).get("ops", [])
    except (json.JSONDecodeError, OSError):
        return "full"
    adds = touches = False
    for op in ops:
        src = op.get("src", "")
        dest = op.get("dest", "")
        if op.get("op") in ("trash", "rename", "move", "delete") \
                and src.startswith(LIBRARY_PATH + "/"):
            touches = True
        if dest.startswith(LIBRARY_PATH + "/"):
            adds = True
    if touches:
        return "full"
    return "quick" if adds else "none"


async def _import_worker(job_id: str, artist: str | None, full: bool,
                       fetch_job_id: str | None) -> None:
    async with _lock:
        jobs = _load_jobs()
        jobs[job_id]["status"] = "running"
        jobs[job_id]["started_at"] = time.strftime("%Y-%m-%dT%H:%M:%S")
        _save_jobs(jobs)
    note: list[str] = []
    staging_info: dict = {}
    # Fase 0 — staging+retag per un fetch-job: isola i suoi file in una
    # staging dedicata e lancia `retag --staging` (crea cartella artista
    # da MusicBrainz, drena compilation). Mai tutto downloads/.
    if fetch_job_id:
        fjobs = _load_jobs()
        fj = fjobs.get(fetch_job_id, {})
        fart, ftitle = fj.get("artist", ""), fj.get("title", "")
        already = _library_has(fart, ftitle) if fj.get("status") == "done" else []
        staging_info = {"fetch_job": fetch_job_id}
        if already:
            staging_info["already_in_library"] = already
            note.append("staging: già in libreria, skip re-import "
                        f"({len(already)} match)")
            cands = []
        else:
            try:
                since = time.mktime(time.strptime(
                    fj.get("created_at", "2000-01-01T00:00:00"), "%Y-%m-%dT%H:%M:%S")) - 300
            except ValueError:
                since = 0
            cands = _find_downloads(fart, ftitle, since) if fj.get("status") == "done" else []
        staging_info["candidates"] = [str(p) for p in cands]
        if cands:
            import shutil
            stage = STAGING_ROOT / fetch_job_id
            stage.mkdir(parents=True, exist_ok=True)
            src_release = cands[0].parent.name
            for p in cands:
                dst = stage / p.name
                if not dst.exists():
                    shutil.copy2(p, dst)
            staging_info["source_release"] = src_release
            staging_info["source_is_comp"] = _looks_compilation(src_release)
            rargs = ["retag", "--staging", str(stage)]
            if not DRY_RUN:
                rargs.append("--commit")
            rres = await run_cli(*rargs, timeout=1800)
            staging_info["retag_rc"] = rres["rc"]
            staging_info["dest"] = _parse_retag_dest(rres["stdout"])
            import re as _re
            m = _re.search(r"Spostati:\s*(\d+),\s*Dup:\s*(\d+)", rres["stdout"])
            if m:
                staging_info["moved"] = int(m.group(1))
                staging_info["dup"] = int(m.group(2))
            choice = _lookup_choice(fart, ftitle)
            staging_info["chosen"] = choice
            calbum = choice.get("album", "")
            staging_info["chosen_is_comp"] = _looks_compilation(calbum) if calbum else False
            staging_info["comp_to_comp"] = bool(
                staging_info["source_is_comp"] and staging_info["chosen_is_comp"])
            if fj.get("album") and calbum and fj["album"].lower() != calbum.lower():
                staging_info["album_mismatch"] = {
                    "requested": fj["album"], "chosen": calbum}
            note.append(f"retag --staging rc={rres['rc']} dest={staging_info['dest']}")
            if staging_info["comp_to_comp"]:
                note.append("ATTENZIONE: compilation -> compilation, verificare album")
        else:
            if "already_in_library" not in staging_info:
                note.append("staging: nessun file del fetch-job trovato in downloads")
    if artist:
        args = ["consolida", artist]
    else:
        args = ["consolida", "--queue"]
    if not DRY_RUN:
        args.append("--commit")
    before = {p.name for p in REPORTS_DIR.glob("*.manifest.json")}
    res = await run_cli(*args, timeout=3600)
    note.append(f"consolida rc={res['rc']}")
    # Politica scan: Navidrome ha auto-scan ogni 6h, quindi di default
    # non si triggera nulla; quick solo per rendere subito disponibili
    # i file nuovi, full solo per purgare ghost (rename/merge di esistenti).
    level = "none"
    if res["rc"] == 0 and not DRY_RUN:
        if full:
            level = "full"
        else:
            levels = [_manifest_touches_library(m) for m in _new_manifests(before)]
            if "full" in levels:
                level = "full"
            elif "quick" in levels:
                level = "quick"
        if level == "none":
            note.append("scan: nessuno (libreria invariata, basta auto-scan 6h)")
    scan_info: dict = {"triggered": False, "level": level}
    if res["rc"] == 0 and not DRY_RUN and level != "none" and ND_USER and ND_PASS:
        try:
            await nd_get("startScan", {"fullScan": "true" if level == "full" else "false"})
            scan_info["triggered"] = True
            for _ in range(24):  # ~2 min; il quick finisce in secondi
                await asyncio.sleep(5)
                st = await nd_get("getScanStatus")
                ss = st.get("scanStatus", {})
                if not ss.get("scanning", False):
                    scan_info.update(
                        {"scanning": False, "count": ss.get("count"),
                         "scanType": ss.get("scanType"),
                         "lastScan": ss.get("lastScan")})
                    break
            else:
                scan_info["note"] = ("scan ancora in corso dopo ~2 min "
                                     "(tipico del full); ricontrolla getScanStatus")
        except Exception as exc:  # noqa: BLE001 — riportato nel job
            scan_info["error"] = str(exc)[:300]
    async with _lock:
        jobs = _load_jobs()
        ok = res["rc"] == 0
        jobs[job_id]["status"] = "done" if ok else "failed"
        jobs[job_id]["finished_at"] = time.strftime("%Y-%m-%dT%H:%M:%S")
        jobs[job_id]["cli_tail"] = res["stdout"][-1500:] + "\n" + "\n".join(note)
        jobs[job_id]["scan"] = scan_info
        if staging_info:
            jobs[job_id]["staging"] = staging_info
        _save_jobs(jobs)


# --- models ---------------------------------------------------------------------
class EnqueueReq(BaseModel):
    artist: str
    title: str
    album: str | None = None


class ImportReq(BaseModel):
    artist: str | None = None
    full: bool = False  # True = forza full scan (purge ghost)
    job_id: str | None = None  # fetch-job da importare via staging+retag


# --- routes -----------------------------------------------------------------------
@app.get("/health")
async def health() -> dict:
    return {"ok": True, "dry_run": DRY_RUN, "time": time.strftime("%Y-%m-%dT%H:%M:%S")}


@app.get("/discover")
async def discover(
    q: str = Query(min_length=2, max_length=120),
    artist: str | None = Query(default=None, max_length=120),
    _: None = Depends(check_auth),
) -> dict:
    """Catalogo esterno (Deezer). Se `artist` è dato, allega il report
    `compare` grezzo per i badge posseduto/in-download/missing."""
    url = "https://api.deezer.com/search?" + urllib.parse.urlencode({"q": q, "limit": 25})
    req = urllib.request.Request(url, headers={"User-Agent": "guru-api/0.1"})
    with urllib.request.urlopen(req, timeout=20) as r:
        dz = json.loads(r.read().decode())
    tracks = [{
        "id": t.get("id"), "title": t.get("title"),
        "artist": (t.get("artist") or {}).get("name"),
        "album": (t.get("album") or {}).get("title"),
        "cover": (t.get("album") or {}).get("cover_medium"),
        "duration": t.get("duration"), "deezer_link": t.get("link"),
    } for t in dz.get("data", [])]
    out: dict = {"query": q, "count": len(tracks), "tracks": tracks}
    if artist:
        rep = JOBS_DIR / f"compare_{int(time.time())}.json"
        res = await run_cli("compare", artist, "--json-out", str(rep), timeout=300)
        out["compare_rc"] = res["rc"]
        if rep.exists():
            try:
                out["compare"] = json.loads(rep.read_text()[:20000])
            except json.JSONDecodeError:
                out["compare_error"] = "report non JSON"
            finally:
                rep.unlink(missing_ok=True)
        else:
            out["compare_tail"] = res["stdout"][-500:]
    return out


@app.post("/slskd/enqueue", status_code=202)
async def enqueue(req: EnqueueReq, _: None = Depends(check_auth)) -> dict:
    """Crea un job e lancia `fetch --strategy track` in background."""
    job_id = secrets.token_hex(6)
    payload = {
        "artist": req.artist, "generated": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "sources": ["guru-api"], "via": "slskd/enqueue",
        "tracks": [{"track": req.title, "album": req.album or "",
                    "level": 1}],
    }
    jf = JOBS_DIR / f"enqueue_{job_id}.json"
    jf.write_text(json.dumps(payload, ensure_ascii=False))
    async with _lock:
        jobs = _load_jobs()
        jobs[job_id] = {"kind": "fetch", "status": "queued",
                        "artist": req.artist, "title": req.title,
                        "album": req.album or "",
                        "created_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
                        "dry_run": DRY_RUN}
        _save_jobs(jobs)
    asyncio.create_task(_fetch_worker(job_id, str(jf)))
    return {"job_id": job_id, "status": "queued", "dry_run": DRY_RUN}


@app.get("/slskd/status/{job_id}")
async def job_status(job_id: str, _: None = Depends(check_auth)) -> dict:
    jobs = _load_jobs()
    job = jobs.get(job_id)
    if not job:
        raise HTTPException(404, "job sconosciuto")
    return {"job_id": job_id, **job}


@app.get("/slskd/queue")
async def slskd_queue(_: None = Depends(check_auth)) -> dict:
    """Passthrough read-only di `music_guru.py queue` (scrive su stderr)."""
    res = await run_cli("queue", timeout=120)
    return {"rc": res["rc"], "output": res["stdout"] or res["stderr"]}


@app.post("/import", status_code=202)
async def import_job(req: ImportReq, _: None = Depends(check_auth)) -> dict:
    """In background: [staging+retag del fetch-job] + `consolida` +
    scan Navidrome server-side (none/quick/full da manifest, `full: true`
    forza). Con `job_id` i file del download vengono isolati in staging
    dedicata e passati a `retag --staging` (mai tutto downloads/)."""
    job_id = secrets.token_hex(6)
    async with _lock:
        jobs = _load_jobs()
        jobs[job_id] = {"kind": "import", "status": "queued",
                        "artist": req.artist or "", "full": req.full,
                        "fetch_job": req.job_id or "",
                        "created_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
                        "dry_run": DRY_RUN}
        _save_jobs(jobs)
    asyncio.create_task(_import_worker(job_id, req.artist, req.full, req.job_id))
    return {"job_id": job_id, "status": "queued", "dry_run": DRY_RUN}

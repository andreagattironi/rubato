# guru-api — backend Rubato (lato Pi)

Thin FastAPI wrapper sopra `~/music-guru/music_guru.py`. Non reimplementa
matching/scan: delega via subprocess. Pi 4 condiviso → solo `asyncio`, niente Celery.

## Endpoint

| Metodo | Path | Note |
|---|---|---|
| `GET` | `/health` | no auth, per watchdog/systemd |
| `GET` | `/discover?q=&artist?` | Deezer + report `compare` grezzo opzionale |
| `POST` | `/slskd/enqueue {artist,title,album?}` | job 202 → `fetch --strategy track` in background |
| `GET` | `/slskd/status/{id}` | stato job persistito |
| `GET` | `/slskd/queue` | passthrough read-only di `queue` |
| `POST` | `/import {artist?, full?}` | job 202 → `consolida` + scan Navidrome server-side (quick di default; full solo se il manifest tocca file già in libreria o se `full: true`) |

Auth: `Authorization: Bearer <GURU_API_TOKEN>` su tutto tranne `/health`.

## Deploy (da PC)

```powershell
ssh pi "mkdir -p ~/guru-api/app ~/guru-api/jobs"
scp -r "C:\Users\andre\Rubato\guru-api\app" "C:\Users\andre\Rubato\guru-api\requirements.txt" pi:~/guru-api/
ssh pi "~/guru-api/venv/bin/python -V || python3 -m venv ~/guru-api/venv"
ssh pi "~/guru-api/venv/bin/pip install -r ~/guru-api/requirements.txt"
# .env SOLO sul Pi (vedi .env.example), poi:
scp "C:\Users\andre\Rubato\guru-api\systemd\guru-api.service" pi:~/.config/systemd/user/
ssh pi "systemctl --user daemon-reload; systemctl --user enable --now guru-api"
```

`GURU_DRY_RUN=1` = fetch/consolida senza `--commit` (test sicuri).
Per lo scan server-side `/import` vuole `ND_USER`/`ND_PASS` nel `.env` del Pi.

# Notes — Architecture, Setup, and Known Issues

Context for anyone (including future you) picking this project back up.

---

## Architecture, in short

```
Android app  --HTTPS-->  Express server (Render, Docker)  --spawns-->  yt-dlp / ffmpeg
                                |
                                v
                        Supabase Storage (public URL)
                                |
                                v
                    Android app downloads the finished file
```

The phone never runs a media binary itself — it only talks to the backend over HTTP and
polls job status. All the actual conversion work happens server-side, inside a Docker
container that has `yt-dlp`, `ffmpeg`, Python, and Node installed.

---

## Environment variables & secrets (Render → Environment tab)

| Name | Type | Value |
|---|---|---|
| `SUPABASE_URL` | Environment Variable | Your Supabase project URL (Project Settings → API) |
| `SUPABASE_SERVICE_KEY` | Environment Variable | The `service_role` key, **not** `anon` — this server needs elevated Storage access |
| `COOKIES_PATH` | Environment Variable | `/etc/secrets/cookies.txt` (only matters if cookies are reintroduced — see below) |
| `cookies.txt` | Secret File | YouTube session cookies, Netscape format — currently unused, see Known Issues |

None of these should ever be committed to git. `.env` is gitignored locally; Secret Files
only exist inside Render, never in the repo.

---

## Known issue: YouTube downloads are currently disabled

**Symptom:** every YouTube URL fails with `HTTP Error 429: Too Many Requests` followed by
`Sign in to confirm you're not a bot`, regardless of mitigation attempted.

**What was tried, in order, and why each one wasn't sufficient on its own:**

1. **Cookies from a real YouTube account** (`--cookies`) — worked temporarily, but
   sessions rotate/expire on YouTube's side outside our control, and it ties the whole
   pipeline to one person's personal account, which isn't a stable foundation.
2. **PO Token provider** (`bgutil-ytdlp-pot-provider`, running as a sidecar process on
   port 4416) — successfully generates tokens and clears the initial bot-detection wall,
   but on its own isn't sufficient for every format/client combination.
3. **JS challenge runtime** (`--js-runtimes node` + `yt-dlp-ejs` +
   `--remote-components ejs:github`) — needed for YouTube's "n challenge" signature
   solving; got this working using Node (already present in the base image) instead of
   installing Deno separately.
4. Combined, these got past the specific errors seen in earlier sessions, but the
   underlying issue — Render's datacenter IP being distrusted by YouTube — resurfaced
   again afterward. This is a known, active, and still-unresolved arms race between
   YouTube and the yt-dlp project as a whole, not something specific to a mistake in this
   codebase.

**Current decision:** YouTube is being treated as unsupported for now. The
`youtubepot-bgutilhttp` extractor args, the `bgutil` sidecar process in `start.sh`, and
the cookie-handling code in `routes/jobs.js` are all still in place and functional — they
just aren't reliably clearing YouTube's block on Render's IP range. Nothing needs to be
ripped out; it can be revisited later if needed.

**If picking this back up later,** the realistic remaining option not yet tried is a
residential/rotating proxy for the server's outbound requests, which addresses the actual
root cause (IP reputation) rather than working around symptoms of it. This has a real
cost and adds infrastructure complexity, so it's a deliberate scope decision, not
something to reach for casually.

**For demos and testing in the meantime:** use any non-YouTube source — direct video file
URLs, X/Twitter, or other sites yt-dlp supports. These all work with zero special
handling since the anti-bot measures above are YouTube-specific, not something every site
does.

---

## Running the backend locally

```bash
npm install
node index.js
```

Needs a local `.env` with `SUPABASE_URL` and `SUPABASE_SERVICE_KEY` (see table above), and
`yt-dlp` + `ffmpeg` installed on your machine for full functionality (non-YouTube sources
will still work without cookies/PO token setup).

Test with:
```bash
curl -X POST http://localhost:3000/jobs -H "Content-Type: application/json" -d '{"url": "..."}'
curl http://localhost:3000/jobs/JOB_ID
```

---

## Running/deploying via Docker

The Dockerfile installs Python, ffmpeg, yt-dlp, yt-dlp-ejs, and the PO token provider, then
`start.sh` runs the PO token sidecar server in the background before starting the Express
app in the foreground. On Render, make sure the service's Runtime is set to **Docker**, not
the default Node buildpack — the default buildpack skips the Dockerfile entirely and the
app will fail with `yt-dlp: command not found`.

---

## API contract (for frontend reference)

**POST `/jobs`**
```json
{ "url": "https://example.com/video", "format": "mp4" }
```
→
```json
{ "id": "abc-123", "status": "processing", "outputUrl": null }
```

**GET `/jobs/{id}`** → same shape, `status` is one of `processing`, `done`, `failed`; on
`done`, `outputUrl` is a public Supabase Storage link.

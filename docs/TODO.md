# TODO

Status as of the last working session. See `NOTES.md` in this folder for context on
*why* some of these exist, especially the YouTube item.

---

## Known limitation (read first)

- **YouTube downloads are currently not supported.** Every mitigation attempted (cookies,
  PO token provider, JS runtime challenge solving) has been fought off by YouTube's
  anti-bot system at some point, and cookie-based auth in particular is unreliable since
  sessions rotate/expire outside our control. Treat YouTube as unavailable for now — see
  `NOTES.md` for the full history and what would be needed to revisit it.
- All other sites yt-dlp supports (X/Twitter, Reddit, Vimeo, Twitch, direct video URLs,
  etc.) work normally with no special handling. Use one of these for demos/testing.

---

## Backend

- [ ] Persistent job history — replace the in-memory `Map` in `routes/jobs.js` with a
      Supabase Postgres `jobs` table so status survives a Render restart/redeploy
- [ ] Decide whether to pursue the optional Supabase Auth bonus (gate `POST /jobs` behind
      a verified JWT) — self-contained, do this only after everything below is solid
- [ ] Add a basic non-YouTube error message / URL validation so a YouTube link fails
      clearly and immediately instead of running through the full failed pipeline
      (nice-to-have, not required)

## Frontend

- [ ] Build a real `FileRepository` that reads the device filesystem
      (`File(path).listFiles()`), replacing the hardcoded sample list in
      `BrowserFragment`
- [ ] Runtime storage permission flow (`READ_EXTERNAL_STORAGE` or
      `MANAGE_EXTERNAL_STORAGE`) with graceful handling of denial — no crash on refusal
- [ ] Folder navigation — tapping a directory should navigate into it, not to File Detail;
      branch on `file.isDirectory`
- [ ] Sorting / filtering in `BrowserFragment` (name, date, size, type)
- [ ] Empty-state and loading-state UI for `BrowserFragment`
- [ ] Flesh out `FilePropertiesActivity` with real file stats (size, last modified,
      permissions) instead of the current stub
- [ ] Polish `item_file.xml` / `item_job.xml` — file-type icons, spacing, visual hierarchy
- [ ] Visible progress + retry-on-failure control on the job list in `DownloadsFragment`
- [ ] Re-test the Share button against real files once `FileRepository` is wired up (it
      currently only toasts "doesn't exist" against fake sample data — expected, resolves
      naturally once real files exist)

## Done

- [x] Backend deployed and live on Render
- [x] `POST /jobs` / `GET /jobs/:id` working end to end
- [x] Supabase Storage upload + public URL flow working
- [x] Non-YouTube downloads working with no special handling
- [x] Docker/Render deploy pipeline confirmed working
- [x] Nav graph, argument passing, bottom nav — complete
- [x] Explicit Intent (File Properties) and implicit Intent (Share) — complete, tested
      against sample data
- [x] Downloads fragment wired to the live backend

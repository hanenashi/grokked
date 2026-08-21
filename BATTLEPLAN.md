# Grokked — Battleplan

## Mission

Build **Grokked** as a small, personal, pay-as-you-go frontend for the xAI Imagine API, based on `DE0CH/grok-frontend`, deployable on Vercel Hobby.

Primary use case: occasional image/video generation without a Grok subscription. Keep it simple enough that it can be maintained by Codex without turning into a framework petting zoo.

Upstream:
- https://github.com/DE0CH/grok-frontend
- MIT licensed
- React + Vite + TypeScript
- One Vercel serverless proxy at `api/proxy.ts`

Target:
- https://github.com/hanenashi/grokked

Production deployment already connected:
- https://grokked-rose.vercel.app/

---

## What upstream already gives us

- Text → image
- Image → image/edit
- Image → video
- Duration control, currently 1–15 s in the UI
- 480p / 720p video handling
- xAI API-key login
- Vercel deployment support
- CORS workaround via `/api/proxy`
- Polling for asynchronous video jobs
- Media download/playback through the proxy

This is a good base. Do **not** rewrite it from scratch unless the upstream code proves broken.

---

# Phase 0 — Import upstream cleanly

## Goal

Bring the current upstream source into this repository while preserving attribution and making future upstream comparisons possible.

## Tasks

1. Import the current `DE0CH/grok-frontend` `main` tree into this repo.
2. Preserve `LICENSE` and upstream copyright/license information.
3. Add an `upstream` Git remote locally when working with Git:

   ```bash
   git remote add upstream https://github.com/DE0CH/grok-frontend.git
   ```

4. Record the exact upstream commit SHA used for the initial import in this file or `README.md`.
5. Confirm a completely untouched import can:
   - `npm install`
   - `npm run build`
   - `npm run lint` if available
   - `npm run dev`
6. Do not mix upstream import and Grokked changes in the same commit.

Suggested commit sequence:

```text
Import DE0CH/grok-frontend upstream
Add Grokked security and API updates
Add Grokked cost controls and UX
```

---

# Phase 1 — Verify current xAI API before changing code

The upstream was pushed in March 2026. Treat API assumptions as potentially stale.

Before implementation, Codex must check current official xAI docs for:

- current image model names
- current video model names
- `grok-imagine-video` availability
- `grok-imagine-video-1.5` availability
- text → video support
- image → video request schema
- supported durations
- supported resolutions
- video polling/status response schema
- generated-media URL lifetime
- current per-second video pricing
- image pricing

Do not hardcode pricing from this document without checking current xAI docs.

---

# Phase 2 — Security pass

## 2.1 API-key storage

Upstream stores the xAI API key in a normal JavaScript-readable cookie for 365 days.

For a personal frontend this works, but Grokked should improve it.

Preferred initial behavior:

- default: keep API key **in memory for the session only**
- optional checkbox: `Remember API key on this device`
- if persistent storage is enabled, clearly explain that the browser stores the key locally
- never commit an API key
- never put the key into Vercel environment variables for this personal BYO-key design
- never log `Authorization` headers
- never return the key in error output

Possible future improvement: encrypted local persistence, but do not add complexity unless needed.

## 2.2 Proxy allowlist

Retain the proxy's strict allowlist concept.

Requirements:

- proxy only `https://api.x.ai` endpoints Grokked actually uses
- media proxy only known xAI media/CDN origins
- reject arbitrary URLs
- reject arbitrary methods
- do not forward unrelated request headers
- never become an open proxy

Add small tests for allow/deny cases if practical.

## 2.3 Error handling

Errors shown to the user may contain useful xAI response text, but:

- redact secrets/tokens
- cap huge responses
- provide HTTP status + useful message
- keep a collapsible `details` section for raw API errors

---

# Phase 3 — Fix the Vercel bandwidth path

Upstream currently proxies generated media from `imgen.x.ai` and `vidgen.x.ai` through `/api/proxy`, buffering the complete file before returning it.

That is functional, but it means Vercel can become the delivery pipe for every generated MP4.

## First investigate

Check whether current xAI generated media URLs can be:

1. loaded directly by `<video src>` / `<img src>`
2. downloaded directly by normal browser navigation
3. fetched directly from browser JavaScript without CORS issues

## Preferred behavior

- API calls may go through our Vercel proxy.
- Generated media should go **direct browser ↔ xAI CDN** whenever possible.
- Only proxy media when direct playback/download genuinely fails.

If proxying remains necessary:

- stream the response body instead of `await res.arrayBuffer()` where Vercel runtime permits it
- preserve useful headers (`Content-Type`, length/range headers where safe)
- document that video delivery consumes Vercel bandwidth

Do not optimize this blindly; test actual xAI CDN behavior first.

---

# Phase 4 — Bring video support up to date

Upstream currently hardcodes:

```text
grok-imagine-video
```

Grokked should expose supported current models rather than burying one model name in the API layer.

## UI

Add a simple video model selector, based on models confirmed from current xAI docs.

Example concept only:

```text
Model
○ Imagine Video — cheaper
● Imagine Video 1.5 — newer / higher quality
```

Only show models that actually exist at implementation time.

## Modes

Support, if current API allows them:

- Text → Video
- Image → Video

Keep Image → Video working first; Text → Video is the first useful extension.

Later candidates, only after core generation works:

- video extension
- video editing
- reference-image/video workflows

Do not front-load obscure API features.

---

# Phase 5 — Cost controls: the whole point of Grokked

This is the main Grokked-specific feature.

The UI should make it difficult to accidentally burn money.

## Before Generate

Display an estimated generation cost immediately beside the Generate button.

Concept:

```text
720p · 6 s · Imagine Video 1.5
Estimated xAI cost: $0.84
```

Pricing must live in one clearly isolated config/module, not scattered through UI components.

Include:

- model
- resolution
- duration
- estimated cost

If xAI price cannot be determined reliably, show `Cost estimate unavailable` rather than guessing.

## Optional local budget guard

Add a user-configurable soft limit such as:

```text
Warn me above: $1.00 per generation
```

This is a warning, not an accounting system.

Later, if useful:

- local session spend tally
- daily/local-history tally

Do not pretend these are authoritative xAI billing totals.

---

# Phase 6 — Generation UX

Keep the interface intentionally boring and useful.

## Video page

Recommended layout:

1. mode: Text → Video / Image → Video
2. image drop zone when needed
3. prompt
4. model
5. resolution
6. duration
7. live cost estimate
8. Generate
9. progress/status
10. resulting video
11. Download

Useful status messages:

```text
Submitting…
Queued…
Generating…
Still generating…
Done
```

Do not use an infinite spinner with no explanation.

## Polling

Upstream polls every 3 seconds forever.

Improve this:

- support `AbortController`
- add Cancel
- set a sensible maximum polling duration
- distinguish cancelled / timed-out / failed
- avoid duplicate jobs from double-clicking Generate

A timeout must stop local polling; it does not imply xAI cancelled the billed generation unless the API explicitly supports cancellation.

---

# Phase 7 — Mobile-first sanity

This will often be used from a phone.

Test explicitly on narrow screens.

Requirements:

- no horizontal scrolling
- large tap targets
- file picker works on Android
- image preview does not explode layout
- video controls remain usable
- prompt textarea is comfortable
- model/resolution/duration controls fit without microscopic UI
- cost estimate remains visible near Generate

Desktop should remain good, but mobile is not an afterthought.

---

# Phase 8 — Local history, but no backend/database

Optional after MVP.

Store only lightweight generation metadata locally in the browser:

- prompt
- mode
- model
- resolution
- duration
- estimated cost
- timestamp
- resulting xAI URL while valid

Do **not** store generated video blobs in localStorage.

No Firebase/Supabase/database for MVP.

Provide `Clear history`.

---

# Phase 9 — Vercel deployment

Target Vercel Hobby for personal use.

Current production deployment:

```text
https://grokked-rose.vercel.app/
```

Expected deployment flow:

```text
GitHub hanenashi/grokked
        ↓
Vercel project
        ↓
auto-deploy main
        ↓
https://grokked-rose.vercel.app/
```

Vercel project policy:

- Hobby plan
- keep the project compatible with Hobby limits
- do not introduce paid Vercel services unless clearly necessary
- root directory: repository root
- framework: Vite / auto-detect
- install: default `npm install`
- build: `npm run build`
- output: `dist`
- no Vercel environment variables are required for the current BYO xAI-key design

Requirements:

- no mandatory server-side secrets
- no database
- no paid Vercel feature dependency
- production build works with `npm run build`
- SPA routing still works on direct URLs
- `/api/proxy` works in Vercel production

After each meaningful deployment milestone, verify both:

1. local production build
2. the live production app at `https://grokked-rose.vercel.app/` where practical

After first working production deploy, test:

1. enter API key
2. text → image
3. image → image
4. cheapest possible short video
5. download video
6. logout/clear key
7. reload browser and verify intended key persistence behavior

Start with cheap generation settings while debugging. Burning API credit to discover a CSS bug is an unusually stupid benchmarking method.

## Vercel Coding Agent Plugin

Codex CLI should have Vercel's official coding-agent plugin installed before doing deployment/configuration work.

Preferred Codex route:

```text
/plugins
```

Then select/install **Vercel**.

Generic installer advertised by Vercel:

```bash
npx plugins add vercel/vercel-plugin
```

Codex instructions after installation:

```text
Vercel production deployment already exists at:
https://grokked-rose.vercel.app/

Use the installed Vercel plugin when making deployment/configuration decisions.
Keep the project compatible with Vercel Hobby.
Do not introduce paid Vercel services unless clearly necessary.
After each meaningful milestone, verify npm build locally and verify the deployed production app where practical.
```

Important distinction: the Vercel coding-agent plugin provides current Vercel platform guidance. Do not assume it automatically grants account/project access. If actual deployment logs, project metadata, or account actions are needed, use explicit Vercel account tooling separately.

---

# Phase 10 — Documentation

Rewrite upstream README into a Grokked README containing:

- what Grokked is
- upstream attribution
- screenshots later
- xAI API key setup
- local development
- Vercel deployment
- production URL
- security explanation
- where the API key lives
- what crosses the Vercel proxy
- cost-estimate disclaimer
- how to update from upstream

Keep the MIT license.

---

# MVP definition

Grokked v0.1 is done when all of these are true:

- [ ] Upstream imported with attribution
- [ ] `npm install` succeeds
- [ ] production build succeeds
- [ ] Vercel deployment succeeds
- [ ] `https://grokked-rose.vercel.app/` serves the current production build
- [ ] API key is handled safely enough for personal use
- [ ] Text → Image works
- [ ] Image → Image works
- [ ] Image → Video works
- [ ] Text → Video works if supported by current xAI API
- [ ] current video model(s) can be selected
- [ ] resolution and duration can be selected
- [ ] estimated cost is shown before generation
- [ ] Generate cannot be accidentally double-submitted
- [ ] video polling can be cancelled/times out sensibly
- [ ] generated video can be played and downloaded
- [ ] Android/mobile UI is usable
- [ ] no arbitrary/open proxy behavior exists
- [ ] README explains deployment and API-key handling

---

# Things deliberately NOT in v0.1

Do not add these unless there is a concrete reason:

- user accounts
- database
- server-side generation history
- subscriptions/payments
- multi-user API-key management
- queue infrastructure
- social/gallery features
- elaborate component framework
- Next.js migration
- Docker/Kubernetes nonsense

This is a personal tool for spending xAI API credit deliberately, not the seed round for another AI startup.

---

# Codex working rules

When Codex starts implementation:

1. Read this file first.
2. Inspect upstream before rewriting anything.
3. Verify current xAI docs before changing API schemas/model names/prices.
4. Use the installed Vercel plugin for Vercel-specific decisions.
5. Remember production is already connected at `https://grokked-rose.vercel.app/`.
6. Make small reviewable commits.
7. Run build/lint after meaningful changes.
8. Test the proxy's allowlist after modifying it.
9. Never expose or commit an API key.
10. Prefer simple TypeScript over new dependencies.
11. Preserve working upstream behavior while adding features incrementally.
12. Keep Vercel Hobby compatibility.
13. Verify live deployment after meaningful milestones where practical.

## Suggested implementation order

```text
A. Import upstream untouched
B. Build + run untouched upstream
C. Security/key-storage cleanup
D. Verify/update xAI endpoints and model names
E. Add model config + pricing config
F. Add Text → Video
G. Add cost estimate + warning threshold
H. Add cancel/timeout/double-submit protection
I. Test direct CDN delivery vs Vercel media proxy
J. Mobile polish
K. Verify Vercel production deployment
L. Final README/security notes
```

---

# First question Codex should answer

Before writing feature code, produce a short audit stating:

```text
1. Current upstream commit imported
2. Current xAI image/video endpoints and model names
3. Current pricing used for estimates
4. Whether generated media must pass through Vercel
5. Exact planned API-key storage behavior
6. Any upstream bugs found during baseline testing
7. Whether the live Vercel deployment is correctly connected and building
```

Only then start Grokked-specific modifications.

---

## Initial audit — 2026-08-22

1. **Upstream imported:** `DE0CH/grok-frontend` commit
   `35e4f3edc96ff3b38640854961eee4a34e7f5d9c`, imported untouched in Grokked
   commit `c51caa1`.
2. **Current xAI API:** Images use `/v1/images/generations` and
   `/v1/images/edits`; current `grok-imagine-image-2.0` supports text/image
   input. Videos use asynchronous `POST /v1/videos/generations` followed by
   `GET /v1/videos/{request_id}`. `grok-imagine-video-1.5` supports text and
   image-to-video at 480p, 720p, and 1080p. `grok-imagine-video` remains
   available at 480p and 720p.
3. **Estimate pricing:** Video 1.5 costs $0.08/s (480p), $0.14/s (720p), or
   $0.25/s (1080p); Imagine Video costs $0.05/s (480p) or $0.07/s (720p).
   Image-to-video includes its documented image-input cost. Config lives in
   `src/lib/imagine.ts`; xAI billing remains authoritative.
4. **Generated media:** xAI returns ephemeral `vidgen.x.ai` URLs, while xAI
   also supports `files-cdn.x.ai` public URLs when requested. Grokked loads
   media directly first and uses its strict, streaming media fallback only on
   playback failure. No live generated asset was used for this audit.
5. **Key storage:** Default is in-memory only. An unchecked, explicit
   “Remember API key on this device” option stores it in browser local storage.
6. **Upstream baseline bugs:** `npm run build` succeeded. `npm run lint` failed
   because helper `useProxy` violated React hook naming rules, and the video
   loop had an unused eslint suppression. The Grokked API rewrite resolves both.
7. **Vercel production:** `https://grokked-rose.vercel.app/` returned Vercel
   `404` on 2026-08-22. The domain exists but a working production deployment
   still needs verification after GitHub/Vercel deployment runs.

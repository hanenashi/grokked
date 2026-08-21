# Grokked

Grokked is a small, personal, pay-as-you-go frontend for the xAI Imagine API.
Bring your own xAI API key, choose the model, resolution, and duration, see a
local estimate before generating, then make images or short videos without a
Grok subscription.

It is deliberately a frontend, not a product platform: no accounts, database,
server-side history, subscriptions, or server-side API key configuration.

## Status

Grokked is based on the upstream project `DE0CH/grok-frontend`, imported at
commit [`35e4f3edc96ff3b38640854961eee4a34e7f5d9c`](https://github.com/DE0CH/grok-frontend/commit/35e4f3edc96ff3b38640854961eee4a34e7f5d9c).
Upstream remains MIT licensed; see [LICENSE](LICENSE).

The intended Vercel address is <https://grokked-rose.vercel.app/>. As of the
initial audit on 2026-08-22 it returned Vercel `404`, so do not treat it as a
working deployment until a successful GitHub/Vercel deployment has been
verified.

## Use it

1. Create an API key in the [xAI Console](https://console.x.ai/).
2. Open Grokked and enter the key.
3. Leave **Remember API key on this device** unchecked for the default
   in-memory session. Check it only if you accept storing the key in this
   browser's local storage.
4. Select a mode, model, resolution, duration, and prompt. Review the estimate
   beside **Generate video** before submitting.

The estimate is a convenience, not a bill. xAI's actual response and billing
are authoritative.

## Security model

- By default API keys exist only in JavaScript memory for the current session.
  A reload signs the user out. Optional persistence is explicit and local to
  the browser.
- Grokked never puts a BYO key in a Vercel environment variable and does not
  log authorization headers.
- The `/api/proxy` function accepts only the image/video endpoints Grokked
  uses, their required HTTP methods, and known xAI media origins. It is not an
  arbitrary URL or header forwarding proxy.
- API requests go through the proxy because the browser cannot reliably call
  xAI's API cross-origin. Generated media loads directly from xAI by default,
  avoiding Vercel bandwidth; Grokked falls back to the restricted media proxy
  only when browser playback fails.
- Error details are redacted and capped before display.

## Current xAI models and estimates

Pricing below was checked against the [xAI pricing page](https://docs.x.ai/developers/pricing)
on 2026-08-22. It is isolated in `src/lib/imagine.ts`.

| Model | 480p | 720p | 1080p |
| --- | ---: | ---: | ---: |
| `grok-imagine-video-1.5` | $0.08/s | $0.14/s | $0.25/s |
| `grok-imagine-video` | $0.05/s | $0.07/s | — |

An image-to-video estimate includes the documented image-input cost for the
selected model. Text/image generation uses `grok-imagine-image-2.0`.

## Local development

```bash
npm install
npm run dev
```

Useful checks:

```bash
npm run lint
npm run build
```

Never commit a real API key. `.env.example` is only a placeholder for upstream
compatibility; Grokked's current BYO-key design requires no environment values.

## Vercel

The app is a Vite project with output in `dist`; `vercel.json` rewrites
non-API routes to `index.html` for SPA routing. The Vercel project `grokked`
uses Vercel's native GitHub integration: pushes to `main` create production
deployments. No GitHub Actions Vercel token workflow is used. Keep deployment
on the Vercel Hobby plan and do not add paid Vercel products for this personal
tool.

Before calling a deployment complete, verify both:

```bash
npm run build
curl -I https://grokked-rose.vercel.app/
```

Then manually test a cheap image or short video with an API key you control.

## Updating from upstream

The local remote named `upstream` points to
`https://github.com/DE0CH/grok-frontend.git`.

```bash
git fetch upstream
git log --oneline HEAD..upstream/main
```

Review and import upstream changes intentionally; do not overwrite Grokked's
security, pricing, or API compatibility changes blindly.

/**
 * Proxy for (1) media from xAI CDNs and (2) xAI API (t2i, i2i, i2v).
 * Client sends ?url=<encoded-full-url>; server decodes, checks allowlist, then proxies.
 * Vercel serverless: /api/proxy. Dev: vite middleware uses same handler.
 */
// Generated media is loaded directly by the browser whenever possible. These
// origins only exist for an explicit playback fallback, never as an open proxy.
const CDN_ORIGINS = new Set(["https://imgen.x.ai", "https://vidgen.x.ai", "https://files-cdn.x.ai"]);

// API: only allow these paths on api.x.ai
const XAI_ORIGIN = "https://api.x.ai";
const API_ALLOWED_PATHS = [
  "/v1/images/generations", // t2i
  "/v1/images/edits", // i2i
  "/v1/videos/generations", // i2v start
];
function isAllowedApiRequest(pathname: string, method: string): boolean {
  if (method === "POST" && API_ALLOWED_PATHS.includes(pathname)) return true;
  return method === "GET" && /^\/v1\/videos\/[^/]+$/.test(pathname);
}

export function isAllowedUrl(targetUrl: string, method: string): "cdn" | "api" | false {
  try {
    const u = new URL(targetUrl);
    if (method === "GET" && CDN_ORIGINS.has(u.origin)) return "cdn";
    if (u.origin === XAI_ORIGIN && isAllowedApiRequest(u.pathname, method)) return "api";
  } catch {
    // invalid URL
  }
  return false;
}

export async function proxyFetch(request: Request): Promise<Response> {
  const url = new URL(request.url);
  const encoded = url.searchParams.get("url");
  if (!encoded) return new Response("Not Found", { status: 404 });

  let targetUrl: string;
  try {
    targetUrl = decodeURIComponent(encoded);
  } catch {
    return new Response("Bad request", { status: 400 });
  }

  const allowed = isAllowedUrl(targetUrl, request.method);

  if (allowed === "cdn") {
    try {
      const res = await fetch(targetUrl);
      if (!res.ok) return new Response("Upstream error", { status: res.status });
      return new Response(res.body, { status: 200, headers: safeMediaHeaders(res.headers) });
    } catch {
      return new Response("Proxy error", { status: 502 });
    }
  }

  // allowed === "api": forward method, auth, content-type, body
  if (allowed === "api") {
    const headers: Record<string, string> = {};
    for (const k of ["authorization", "content-type"]) {
      const v = request.headers.get(k);
      if (v) headers[k] = v;
    }
    const body =
      request.method !== "GET" && request.method !== "HEAD" ? await request.arrayBuffer() : undefined;
    try {
      const res = await fetch(targetUrl, { method: request.method, headers, body });
      const contentType = res.headers.get("content-type");
      return new Response(res.body, {
        status: res.status,
        headers: contentType ? { "Content-Type": contentType } : undefined,
      });
    } catch {
      return new Response("Proxy error", { status: 502 });
    }
  }

  return new Response("Bad request", { status: 400 });
}

function safeMediaHeaders(headers: Headers): Headers {
  const responseHeaders = new Headers();
  for (const name of ["content-type", "content-length", "accept-ranges", "content-range"]) {
    const value = headers.get(name);
    if (value) responseHeaders.set(name, value);
  }
  return responseHeaders;
}

export default { fetch: proxyFetch };

import { IMAGE_MODEL, type VideoModelId, type VideoResolution } from "./imagine";

let userApiKey: string | null = null;

export function setGrokApiKey(key: string | null): void {
  userApiKey = key?.trim() || null;
}

function getApiKey(): string {
  if (!userApiKey) throw new Error("Your xAI API key is not available. Please sign in again.");
  return userApiKey;
}

const PROXY_BASE = "/api/proxy";
const XAI_API_BASE = "https://api.x.ai/v1";
const MEDIA_ORIGINS = new Set(["https://imgen.x.ai", "https://vidgen.x.ai", "https://files-cdn.x.ai"]);
const POLL_INTERVAL_MS = 5_000;
const POLL_TIMEOUT_MS = 10 * 60 * 1_000;
const MAX_ERROR_DETAILS_LENGTH = 1_000;

function proxyUrl(targetUrl: string): string {
  return `${PROXY_BASE}?url=${encodeURIComponent(targetUrl)}`;
}

export function getMediaFallbackUrl(url: string): string | null {
  try {
    return MEDIA_ORIGINS.has(new URL(url).origin) ? proxyUrl(url) : null;
  } catch {
    return null;
  }
}

export interface GrokApiError extends Error {
  status?: number;
  responseBody?: string;
}

function redactDetails(value: string): string {
  return value
    .replace(/Bearer\s+[A-Za-z0-9._~+/=-]+/gi, "Bearer [redacted]")
    .replace(/(xai[-_][A-Za-z0-9._-]{8,})/gi, "[redacted]")
    .slice(0, MAX_ERROR_DETAILS_LENGTH);
}

function statusMessage(status: number): string {
  if (status === 401) return "Unauthorized — check your API key.";
  if (status === 403) return "Forbidden — access denied.";
  if (status === 429) return "Rate limited — try again later.";
  if (status >= 500) return "xAI or proxy service error — try again later.";
  return `Request failed (${status}).`;
}

function errorFromResponse(status: number, responseBody: string): GrokApiError {
  const error = new Error(statusMessage(status)) as GrokApiError;
  error.status = status;
  error.responseBody = redactDetails(responseBody);
  return error;
}

async function xaiRequest(path: string, init: RequestInit, signal?: AbortSignal): Promise<Response> {
  const response = await fetch(proxyUrl(`${XAI_API_BASE}${path}`), {
    ...init,
    signal,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${getApiKey()}`,
      ...init.headers,
    },
  });
  if (!response.ok) throw errorFromResponse(response.status, await response.text());
  return response;
}

type Usage = { cost_in_usd_ticks?: number };
export type GenerationResult = { url: string; costInUsdTicks?: number };
type ImageGenerationResponse = { data?: Array<{ b64_json?: string; url?: string; mime_type?: string }>; usage?: Usage };

function imageResponseUrl(payload: ImageGenerationResponse): string {
  const image = payload.data?.[0];
  if (image?.url) return image.url;
  if (!image?.b64_json) throw new Error("xAI returned an unexpected image response.");
  const mime = image.mime_type && /^image\/[a-z0-9+.-]+$/i.test(image.mime_type) ? image.mime_type : "image/png";
  return `data:${mime};base64,${image.b64_json}`;
}

function costInUsdTicks(usage: Usage | undefined): number | undefined {
  const value = usage?.cost_in_usd_ticks;
  return typeof value === "number" && Number.isFinite(value) && value >= 0 ? value : undefined;
}

export async function textToImage(prompt: string): Promise<GenerationResult> {
  try {
    const response = await xaiRequest("/images/generations", {
      method: "POST",
      body: JSON.stringify({ model: IMAGE_MODEL, prompt: prompt.trim(), response_format: "b64_json" }),
    });
    const payload = (await response.json()) as ImageGenerationResponse;
    return { url: imageResponseUrl(payload), costInUsdTicks: costInUsdTicks(payload.usage) };
  } catch (error) {
    throw presentError(error);
  }
}

export async function imageEdit(prompt: string, imageDataUri: string): Promise<GenerationResult> {
  try {
    const response = await xaiRequest("/images/edits", {
      method: "POST",
      body: JSON.stringify({
        model: IMAGE_MODEL,
        prompt: prompt.trim(),
        image: { url: imageDataUri, type: "image_url" },
        response_format: "b64_json",
      }),
    });
    const payload = (await response.json()) as ImageGenerationResponse;
    return { url: imageResponseUrl(payload), costInUsdTicks: costInUsdTicks(payload.usage) };
  } catch (error) {
    throw presentError(error);
  }
}

type VideoStatus = "submitting" | "queued" | "generating" | "done";
type VideoResponse = {
  request_id?: string;
  status?: string;
  video?: { url?: string };
  error?: { message?: string } | string;
  usage?: Usage;
};

export type VideoGenerationOptions = {
  model: VideoModelId;
  resolution: VideoResolution;
  duration: number;
  imageDataUri?: string | null;
  signal?: AbortSignal;
  onStatus?: (status: VideoStatus) => void;
};

function waitForPoll(signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(resolve, POLL_INTERVAL_MS);
    signal?.addEventListener("abort", () => {
      window.clearTimeout(timer);
      reject(new DOMException("Generation polling cancelled", "AbortError"));
    }, { once: true });
  });
}

export async function generateVideo(prompt: string, options: VideoGenerationOptions): Promise<GenerationResult> {
  const { imageDataUri, signal, onStatus } = options;
  try {
    onStatus?.("submitting");
    const body: Record<string, unknown> = {
      model: options.model,
      prompt: prompt.trim(),
      duration: options.duration,
      resolution: options.resolution,
    };
    if (imageDataUri) body.image = { url: imageDataUri };

    const startResponse = await xaiRequest("/videos/generations", {
      method: "POST",
      body: JSON.stringify(body),
    }, signal);
    const requestId = ((await startResponse.json()) as VideoResponse).request_id;
    if (!requestId) throw new Error("xAI did not return a video request ID.");

    const deadline = Date.now() + POLL_TIMEOUT_MS;
    onStatus?.("queued");
    while (Date.now() < deadline) {
      await waitForPoll(signal);
      const response = await xaiRequest(`/videos/${encodeURIComponent(requestId)}`, { method: "GET" }, signal);
      const result = (await response.json()) as VideoResponse;
      if (result.status === "done" && result.video?.url) {
        onStatus?.("done");
        return { url: result.video.url, costInUsdTicks: costInUsdTicks(result.usage) };
      }
      if (result.status === "failed" || result.status === "expired" || result.error) {
        const message = typeof result.error === "string" ? result.error : result.error?.message;
        throw new Error(redactDetails(message || `Video generation ${result.status || "failed"}.`));
      }
      onStatus?.("generating");
    }
    throw new Error("Timed out waiting for xAI. The generation may still complete in xAI, but Grokked stopped polling.");
  } catch (error) {
    throw presentError(error);
  }
}

function presentError(error: unknown): Error {
  if (error instanceof DOMException && error.name === "AbortError") return new Error("Generation polling cancelled.");
  if (error instanceof Error) return error;
  return new Error("Request failed.");
}

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import ImageUpload from "../components/ImageUpload";
import { estimateVideoCost, formatUsd, getVideoModel, VIDEO_MODELS, type VideoModelId, type VideoResolution } from "../lib/imagine";
import { generateVideo, getMediaFallbackUrl } from "../lib/grokApi";

type Mode = "text" | "image";
type GenerationStatus = "ready" | "submitting" | "queued" | "generating" | "done" | "cancelled" | "timed-out" | "failed";

const STATUS_COPY: Record<GenerationStatus, string> = {
  ready: "Ready when you are.",
  submitting: "Submitting…",
  queued: "Queued. xAI will start the generation shortly.",
  generating: "Generating… this can take a few minutes.",
  done: "Done.",
  cancelled: "Polling cancelled. xAI may still finish the billed generation.",
  "timed-out": "Stopped waiting after 10 minutes. xAI may still finish the billed generation.",
  failed: "Generation failed.",
};

function getStoredThreshold(): number {
  try {
    const rawValue = localStorage.getItem("grokked-warning-threshold");
    if (rawValue === null) return 1;
    const saved = Number(rawValue);
    return Number.isFinite(saved) && saved >= 0 ? saved : 1;
  } catch {
    return 1;
  }
}

function VideoResult({ url }: { url: string }) {
  const [source, setSource] = useState(url);
  const fallbackUsed = useRef(false);
  const useFallback = () => {
    if (fallbackUsed.current) return;
    const fallback = getMediaFallbackUrl(url);
    if (fallback) {
      fallbackUsed.current = true;
      setSource(fallback);
    }
  };
  return (
    <section className="video-result" aria-label="Generated video">
      <h2>Generated video</h2>
      <video src={source} controls className="result-video" onError={useFallback} />
      <a href={source} download="grokked-video.mp4" className="download-link">Download MP4</a>
    </section>
  );
}

export default function VideoGeneration({ mode }: { mode: Mode }) {
  const [preview, setPreview] = useState<string | null>(null);
  const [prompt, setPrompt] = useState("");
  const [model, setModel] = useState<VideoModelId>("grok-imagine-video-1.5");
  const [resolution, setResolution] = useState<VideoResolution>("480p");
  const [duration, setDuration] = useState(5);
  const [warningThreshold, setWarningThreshold] = useState(getStoredThreshold);
  const [status, setStatus] = useState<GenerationStatus>("ready");
  const [resultUrl, setResultUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const controllerRef = useRef<AbortController | null>(null);
  const isImageMode = mode === "image";
  const selectedModel = getVideoModel(model);
  const estimate = useMemo(() => estimateVideoCost(model, resolution, duration, isImageMode), [duration, isImageMode, model, resolution]);
  const busy = ["submitting", "queued", "generating"].includes(status);

  useEffect(() => {
    if (!selectedModel.resolutions.includes(resolution)) setResolution(selectedModel.resolutions[0]);
  }, [resolution, selectedModel]);

  useEffect(() => {
    try { localStorage.setItem("grokked-warning-threshold", String(warningThreshold)); } catch { /* optional local preference */ }
  }, [warningThreshold]);

  useEffect(() => () => controllerRef.current?.abort(), []);

  const onFileSelect = useCallback((file: File) => {
    if (!file.type.startsWith("image/")) {
      setError("Choose an image file.");
      return;
    }
    const reader = new FileReader();
    reader.onload = () => setPreview(reader.result as string);
    reader.readAsDataURL(file);
  }, []);

  const submit = async () => {
    if (busy) return;
    if (!prompt.trim() || (isImageMode && !preview)) {
      setError(isImageMode ? "Upload an image and describe the video you want to make." : "Describe the video you want to make.");
      return;
    }
    if (estimate !== null && estimate > warningThreshold && !window.confirm(`${formatUsd(estimate)} is above your warning threshold. Generate anyway?`)) return;

    const controller = new AbortController();
    controllerRef.current = controller;
    setError(null);
    setResultUrl(null);
    try {
      const url = await generateVideo(prompt, {
        model,
        resolution,
        duration,
        imageDataUri: preview,
        signal: controller.signal,
        onStatus: (next) => setStatus(next),
      });
      setResultUrl(url);
      setStatus("done");
    } catch (caught) {
      const message = caught instanceof Error ? caught.message : "Request failed.";
      if (message.includes("cancelled")) setStatus("cancelled");
      else if (message.startsWith("Timed out")) setStatus("timed-out");
      else setStatus("failed");
      setError(message);
    } finally {
      controllerRef.current = null;
    }
  };

  const cancel = () => controllerRef.current?.abort();

  return (
    <div className="page generation-page">
      <header className="page-header">
        <h1>{isImageMode ? "Image to video" : "Text to video"}</h1>
        <p className="subtitle">Choose the settings first. Grokked shows a local estimate before it sends a paid request to xAI.</p>
      </header>
      <div className="generation-grid">
        <section className="generation-form" aria-label="Video generation settings">
          {isImageMode && <ImageUpload preview={preview} onFileSelect={onFileSelect} label="Source image" />}
          <label className="block">
            <span>Describe the video you want to make</span>
            <textarea value={prompt} onChange={(event) => setPrompt(event.target.value)} placeholder="Ready when you are" rows={6} maxLength={2_000} />
          </label>
          <div className="settings-grid">
            <label className="block"><span>Model</span><select value={model} onChange={(event) => setModel(event.target.value as VideoModelId)}>{VIDEO_MODELS.map((item) => <option value={item.id} key={item.id}>{item.label}</option>)}</select></label>
            <label className="block"><span>Resolution</span><select value={resolution} onChange={(event) => setResolution(event.target.value as VideoResolution)}>{selectedModel.resolutions.map((item) => <option value={item} key={item}>{item}</option>)}</select></label>
            <label className="block"><span>Duration</span><select value={duration} onChange={(event) => setDuration(Number(event.target.value))}>{[1, 2, 3, 4, 5, 6, 8, 10, 12, 15].map((item) => <option value={item} key={item}>{item} seconds</option>)}</select></label>
          </div>
          <div className="cost-row">
            <div><span>Estimated xAI cost</span><strong>{formatUsd(estimate)}</strong></div>
            <label><span>Warn me above</span><input aria-label="Warning threshold in US dollars" type="number" min="0" step="0.01" value={warningThreshold} onChange={(event) => setWarningThreshold(Math.max(0, Number(event.target.value)))} /></label>
          </div>
          <button type="button" className="primary-button" onClick={submit} disabled={busy || !prompt.trim() || (isImageMode && !preview)}>{busy ? "Generating video…" : "Generate video"}</button>
          {busy && <button type="button" className="secondary-button" onClick={cancel}>Cancel polling</button>}
          <p className="estimate-note">Estimate only; xAI’s response and billing are authoritative.</p>
          {error && <details className="error" open><summary>{STATUS_COPY[status]}</summary><pre>{error}</pre></details>}
        </section>
        <aside className="generation-status">
          <h2>Progress</h2>
          <ol className="progress-list"><li className={status === "submitting" ? "active" : ""}>Submitting</li><li className={status === "queued" ? "active" : ""}>Queued</li><li className={status === "generating" ? "active" : ""}>Generating</li><li className={status === "done" ? "active" : ""}>Done</li></ol>
          <p>{STATUS_COPY[status]}</p>
          {resultUrl ? <VideoResult key={resultUrl} url={resultUrl} /> : <div className="result-placeholder">Your finished video will appear here.</div>}
        </aside>
      </div>
    </div>
  );
}

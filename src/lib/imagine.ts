export type VideoModelId = "grok-imagine-video-1.5" | "grok-imagine-video";
export type VideoResolution = "480p" | "720p" | "1080p";

type VideoModel = {
  id: VideoModelId;
  label: string;
  resolutions: readonly VideoResolution[];
  costPerSecond: Partial<Record<VideoResolution, number>>;
  imageInputCost: number;
};

/** Checked against xAI's public pricing page on 2026-08-22. */
export const VIDEO_MODELS: readonly VideoModel[] = [
  {
    id: "grok-imagine-video-1.5",
    label: "Imagine Video 1.5",
    resolutions: ["480p", "720p", "1080p"],
    costPerSecond: { "480p": 0.08, "720p": 0.14, "1080p": 0.25 },
    imageInputCost: 0.01,
  },
  {
    id: "grok-imagine-video",
    label: "Imagine Video",
    resolutions: ["480p", "720p"],
    costPerSecond: { "480p": 0.05, "720p": 0.07 },
    imageInputCost: 0.002,
  },
];

export const IMAGE_MODEL = "grok-imagine-image-2.0";

export function getVideoModel(modelId: VideoModelId): VideoModel {
  return VIDEO_MODELS.find((model) => model.id === modelId) ?? VIDEO_MODELS[0];
}

export function estimateVideoCost(modelId: VideoModelId, resolution: VideoResolution, duration: number, hasImageInput: boolean): number | null {
  const model = getVideoModel(modelId);
  const perSecond = model.costPerSecond[resolution];
  if (perSecond === undefined) return null;
  return perSecond * duration + (hasImageInput ? model.imageInputCost : 0);
}

export function formatUsd(amount: number | null): string {
  return amount === null ? "Cost estimate unavailable" : `$${amount.toFixed(2)}`;
}

const ACTIVITY_STORAGE_KEY = "grokked-activity-v1";
const MAX_ACTIVITY_ENTRIES = 50;

export const COST_TICKS_PER_USD = 10_000_000_000;

export type ActivityKind = "Text to image" | "Image to image" | "Text to video" | "Image to video";
export type ActivityStatus = "completed" | "failed" | "cancelled" | "timed-out";

export type ActivityEntry = {
  id: string;
  createdAt: number;
  kind: ActivityKind;
  status: ActivityStatus;
  model: string;
  settings: string;
  costInUsdTicks?: number;
};

type NewActivityEntry = Omit<ActivityEntry, "id" | "createdAt">;

function isActivityEntry(value: unknown): value is ActivityEntry {
  if (!value || typeof value !== "object") return false;
  const entry = value as Partial<ActivityEntry>;
  return typeof entry.id === "string"
    && typeof entry.createdAt === "number"
    && typeof entry.kind === "string"
    && typeof entry.status === "string"
    && typeof entry.model === "string"
    && typeof entry.settings === "string"
    && (entry.costInUsdTicks === undefined || (typeof entry.costInUsdTicks === "number" && Number.isFinite(entry.costInUsdTicks)));
}

export function getActivity(): ActivityEntry[] {
  try {
    const raw = localStorage.getItem(ACTIVITY_STORAGE_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter(isActivityEntry).slice(0, MAX_ACTIVITY_ENTRIES) : [];
  } catch {
    return [];
  }
}

export function addActivity(entry: NewActivityEntry): ActivityEntry {
  const activityEntry: ActivityEntry = {
    ...entry,
    id: globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`,
    createdAt: Date.now(),
  };
  try {
    localStorage.setItem(ACTIVITY_STORAGE_KEY, JSON.stringify([activityEntry, ...getActivity()].slice(0, MAX_ACTIVITY_ENTRIES)));
  } catch {
    // Local activity is an optional convenience, not a requirement to generate.
  }
  return activityEntry;
}

export function clearActivity(): void {
  try {
    localStorage.removeItem(ACTIVITY_STORAGE_KEY);
  } catch {
    // Ignore unavailable browser storage.
  }
}

export function formatActualCost(costInUsdTicks: number | undefined): string {
  if (costInUsdTicks === undefined) return "Not returned";
  return `$${(costInUsdTicks / COST_TICKS_PER_USD).toFixed(4)}`;
}

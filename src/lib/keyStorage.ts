const API_KEY_STORAGE_KEY = "grokked-api-key";

/** API keys stay in module memory by default; persistence is explicit opt-in. */
export function getRememberedApiKey(): string | null {
  try {
    return localStorage.getItem(API_KEY_STORAGE_KEY) || null;
  } catch {
    return null;
  }
}

export function rememberApiKey(key: string): void {
  try {
    localStorage.setItem(API_KEY_STORAGE_KEY, key.trim());
  } catch {
    // A restricted browser can still use the in-memory session.
  }
}

export function forgetApiKey(): void {
  try {
    localStorage.removeItem(API_KEY_STORAGE_KEY);
  } catch {
    // Nothing else to clean up.
  }
}

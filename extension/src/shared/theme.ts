export type ColorSchemePreference = "system" | "light" | "dark";

const STORAGE_KEY = "openvault-color-scheme";

export function readColorSchemePreference(): ColorSchemePreference {
  try {
    const val = localStorage.getItem(STORAGE_KEY);
    if (val === "light" || val === "dark") return val;
  } catch {
    // Storage unavailable
  }
  return "system";
}

export function getResolvedSystemScheme(): "light" | "dark" {
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function applyColorScheme(pref: ColorSchemePreference): void {
  const meta = document.querySelector<HTMLMetaElement>('meta[name="color-scheme"]');
  const content = pref === "system" ? "light dark" : pref;
  if (meta) {
    meta.content = content;
  }
  try {
    if (pref === "system") {
      localStorage.removeItem(STORAGE_KEY);
    } else {
      localStorage.setItem(STORAGE_KEY, pref);
    }
  } catch {
    // Still apply theme even when storage is blocked
  }
}

export function subscribeColorSchemeSync(onChange: (pref: ColorSchemePreference) => void): () => void {
  const handler = (event: StorageEvent) => {
    if (event.key === STORAGE_KEY || event.key === null) {
      const next = readColorSchemePreference();
      const meta = document.querySelector<HTMLMetaElement>('meta[name="color-scheme"]');
      if (meta) {
        meta.content = next === "system" ? "light dark" : next;
      }
      onChange(next);
    }
  };
  window.addEventListener("storage", handler);
  return () => window.removeEventListener("storage", handler);
}

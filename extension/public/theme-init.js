// Synchronous external script (MV3 CSP-compliant, zero inline JS) preventing color-scheme FOUC
try {
  const stored = localStorage.getItem("openvault-color-scheme");
  const meta = document.querySelector('meta[name="color-scheme"]');
  if (meta && (stored === "light" || stored === "dark")) {
    meta.setAttribute("content", stored);
  }
} catch {
  // Ignore storage restrictions and keep system default "light dark"
}

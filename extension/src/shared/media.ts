import type { CipherField } from "@openvault/crypto-core";

/**
 * Hidden Bitwarden custom field name shared across Android & Chrome Extension
 * for Zero-Knowledge E2EE custom item icons (96x96 WebP, ~3-8 KB raw base64).
 */
export const OV_CUSTOM_ICON_FIELD = "_ov_icon";

export function getCustomIconDataUrl(fields?: CipherField[] | null): string | null {
  if (!fields || fields.length === 0) return null;
  const match = fields.find((f) => f.name === OV_CUSTOM_ICON_FIELD && f.value?.trim());
  if (!match?.value) return null;
  const raw = match.value.trim();
  return raw.startsWith("data:") ? raw : `data:image/webp;base64,${raw}`;
}

export function getRawCustomIconBase64(fields?: CipherField[] | null): string | null {
  if (!fields || fields.length === 0) return null;
  const match = fields.find((f) => f.name === OV_CUSTOM_ICON_FIELD && f.value?.trim());
  if (!match?.value) return null;
  const raw = match.value.trim();
  const idx = raw.indexOf("base64,");
  return idx >= 0 ? raw.slice(idx + 7) : raw;
}

export function getVisibleCustomFields(fields?: CipherField[] | null): CipherField[] {
  if (!fields || fields.length === 0) return [];
  return fields.filter((f) => f.name !== OV_CUSTOM_ICON_FIELD);
}

export function normalizeAvatarDataUrl(base64?: string | null): string | null {
  if (!base64 || !base64.trim()) return null;
  const raw = base64.trim();
  return raw.startsWith("data:") ? raw : `data:image/webp;base64,${raw}`;
}

/**
 * Center-crops and compresses any picked image file to a crisp square WebP (raw base64)
 * that fits safely inside Cloudflare D1 (under 32 KB) and syncs seamlessly with OpenVault Android.
 */
export async function compressImageFileToBase64(
  file: File,
  targetPx = 96,
  quality = 0.82,
): Promise<string> {
  const dataUrl = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new Error("Could not read selected image"));
    reader.readAsDataURL(file);
  });

  const img = await new Promise<HTMLImageElement>((resolve, reject) => {
    const el = new Image();
    el.onload = () => resolve(el);
    el.onerror = () => reject(new Error("Invalid image format"));
    el.src = dataUrl;
  });

  const side = Math.min(img.naturalWidth || img.width, img.naturalHeight || img.height);
  const sx = Math.max(0, Math.floor(((img.naturalWidth || img.width) - side) / 2));
  const sy = Math.max(0, Math.floor(((img.naturalHeight || img.height) - side) / 2));

  const canvas = document.createElement("canvas");
  canvas.width = targetPx;
  canvas.height = targetPx;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Canvas context unavailable");

  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = "high";
  ctx.drawImage(img, sx, sy, side, side, 0, 0, targetPx, targetPx);

  let out = canvas.toDataURL("image/webp", quality);
  if (!out.startsWith("data:image/webp")) {
    out = canvas.toDataURL("image/jpeg", quality);
  }
  const idx = out.indexOf("base64,");
  const rawBase64 = idx >= 0 ? out.slice(idx + 7) : out;
  if (rawBase64.length > 44_000) {
    throw new Error("Image is too large after compression");
  }
  return rawBase64;
}

export const RECOVERY_CODES_SLOT_COUNT = 20;
export const RECOVERY_CODE_MAX_LEN = 4;

export function isRecoveryCodesField(name?: string | null): boolean {
  if (!name) return false;
  const trimmed = name.trim();
  return /(recovery\s*code|backup\s*code|mã\s*khôi\s*phục)/i.test(trimmed);
}

/**
 * Splits a multi-code pasted string (numbered lists, newlines, dashes, 8-digit Google codes,
 * spaces, commas, etc.) into up to 20 clean <=4-character chunks while preserving leading zeros.
 */
export function parseRecoveryCodesPaste(rawText: string): string[] {
  if (!rawText) return [];
  const chunks: string[] = [];

  for (const rawLine of rawText.split(/\r?\n/)) {
    // Strip optional list prefixes such as "1. ", "2) ", "- ", "* "
    const withoutPrefix = rawLine.replace(/^\s*(?:\d+[.):]\s+|[-•*]\s+)/, "");
    const rawTokens = withoutPrefix.split(/[\s,;|\-_/\\]+/);
    for (const rawToken of rawTokens) {
      const clean = rawToken.replace(/[^A-Za-z0-9]/g, "");
      if (!clean) continue;
      if (clean.length <= RECOVERY_CODE_MAX_LEN) {
        chunks.push(clean);
      } else {
        for (let i = 0; i < clean.length; i += RECOVERY_CODE_MAX_LEN) {
          chunks.push(clean.slice(i, i + RECOVERY_CODE_MAX_LEN));
          if (chunks.length >= RECOVERY_CODES_SLOT_COUNT) break;
        }
      }
      if (chunks.length >= RECOVERY_CODES_SLOT_COUNT) break;
    }
    if (chunks.length >= RECOVERY_CODES_SLOT_COUNT) break;
  }

  return chunks.slice(0, RECOVERY_CODES_SLOT_COUNT);
}

/**
 * Parses a stored custom-field string into a fixed 20-slot array.
 */
export function parseRecoveryCodesToSlots(value?: string | null): string[] {
  const slots = Array<string>(RECOVERY_CODES_SLOT_COUNT).fill("");
  if (!value) return slots;
  const tokens = parseRecoveryCodesPaste(value);
  for (let i = 0; i < Math.min(tokens.length, RECOVERY_CODES_SLOT_COUNT); i++) {
    slots[i] = tokens[i]!;
  }
  return slots;
}

/**
 * Applies pasted clipboard text into the 20-box grid.
 * Multi-code pastes auto-start from box 0 if triggered from the Paste button, box 0,
 * or whenever all earlier boxes are empty.
 */
export function applyRecoveryCodesPaste(
  currentSlots: string[],
  targetIndex: number,
  rawText: string,
  forceFromStart = false,
): { slots: string[]; nextFocusIndex: number } {
  const next = Array.from({ length: RECOVERY_CODES_SLOT_COUNT }, (_, i) =>
    (currentSlots[i] ?? "").slice(0, RECOVERY_CODE_MAX_LEN),
  );
  const chunks = parseRecoveryCodesPaste(rawText);
  if (chunks.length === 0) {
    return { slots: next, nextFocusIndex: targetIndex };
  }

  const boundedTarget = Math.max(0, Math.min(RECOVERY_CODES_SLOT_COUNT - 1, targetIndex));
  const earlierEmpty = next.slice(0, boundedTarget).every((s) => s.length === 0);
  const startIdx = forceFromStart || (chunks.length > 1 && (boundedTarget === 0 || earlierEmpty))
    ? 0
    : boundedTarget;

  let cursor = startIdx;
  for (const chunk of chunks) {
    if (cursor >= RECOVERY_CODES_SLOT_COUNT) break;
    next[cursor] = chunk.slice(0, RECOVERY_CODE_MAX_LEN);
    cursor++;
  }

  const lastFilled = Math.max(startIdx, cursor - 1);
  const nextFocusIndex =
    cursor < RECOVERY_CODES_SLOT_COUNT && (next[lastFilled]?.length ?? 0) >= RECOVERY_CODE_MAX_LEN
      ? cursor
      : Math.min(lastFilled, RECOVERY_CODES_SLOT_COUNT - 1);

  return { slots: next, nextFocusIndex };
}

/**
 * Serializes the 20 slots cleanly in rows of 4 for compact cross-platform storage.
 */
export function serializeRecoveryCodesSlots(slots: string[]): string {
  const normalized = Array.from({ length: RECOVERY_CODES_SLOT_COUNT }, (_, i) =>
    (slots[i] ?? "").replace(/[^A-Za-z0-9]/g, "").slice(0, RECOVERY_CODE_MAX_LEN),
  );
  let lastNonEmpty = -1;
  for (let i = normalized.length - 1; i >= 0; i--) {
    if (normalized[i]) {
      lastNonEmpty = i;
      break;
    }
  }
  if (lastNonEmpty < 0) return "";

  const rows: string[] = [];
  for (let rowStart = 0; rowStart <= lastNonEmpty; rowStart += 4) {
    const slice = normalized.slice(rowStart, Math.min(lastNonEmpty + 1, rowStart + 4));
    rows.push(slice.filter(Boolean).join(" "));
  }
  return rows.join("\n");
}

export function countFilledRecoveryCodes(slots: string[]): number {
  return slots.filter((s) => s.trim().length > 0).length;
}

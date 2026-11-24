export const RECOVERY_CODES_DEFAULT_SLOTS = 10;
export const RECOVERY_CODES_MAX_SLOTS = 100;
export const RECOVERY_CODE_MAX_LEN = 24;

export function isRecoveryCodesField(name?: string | null): boolean {
  if (!name) return false;
  return /(recovery\s*code|backup\s*code|mã\s*khôi\s*phục)/i.test(name.trim());
}

/**
 * Normalizes a single recovery code entry while preserving leading zeros,
 * hyphens (e.g. `abcde-12345`), and single internal spaces (e.g. `0642 0983` or `123456 789012`).
 */
export function normalizeRecoveryCode(raw: string): string {
  return raw
    .replace(/[^A-Za-z0-9\s-]/g, "")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, RECOVERY_CODE_MAX_LEN);
}

/**
 * Detects legacy 20x4 space-separated serialization (`0642 0983 7890 4684\n9027 ...`)
 * and pairs adjacent 4-char tokens back into 8-digit spaced codes (`0642 0983`, `7890 4684`).
 */
function tryParseLegacyFourCharGrid(rawText: string): string[] | null {
  const nonEmptyLines = rawText
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean);
  if (nonEmptyLines.length === 0) return null;

  const lineTokenRegex = /^[A-Za-z0-9]{4}(?:\s+[A-Za-z0-9]{4}){0,3}$/;
  if (!nonEmptyLines.every((l) => lineTokenRegex.test(l))) {
    return null;
  }

  const lineGroups = nonEmptyLines.map((l) => l.split(/\s+/));
  const hasMultiFourLine = lineGroups.some((g) => g.length >= 3);
  if (!hasMultiFourLine) return null;

  const flat = lineGroups.flat();
  const paired: string[] = [];
  for (let i = 0; i < flat.length; i += 2) {
    if (i + 1 < flat.length) {
      paired.push(`${flat[i]!} ${flat[i + 1]!}`);
    } else {
      paired.push(flat[i]!);
    }
    if (paired.length >= RECOVERY_CODES_MAX_SLOTS) break;
  }
  return paired;
}

/**
 * Splits pasted recovery code text (numbered lists, multi-column copies, tabs, commas,
 * 6-digit codes, 6-digit pairs `123456 789012`, Google 4+4 `0642 0983`, GitHub `abcde-12345`,
 * or >10 codes) into clean whole entries while preserving leading zeros.
 */
export function parseRecoveryCodesPaste(rawText: string): string[] {
  if (!rawText) return [];

  const legacy = tryParseLegacyFourCharGrid(rawText);
  if (legacy) return legacy;

  const codes: string[] = [];

  for (const rawLine of rawText.split(/\r?\n/)) {
    // Split multi-column lines on tabs, commas, semicolons, pipes, 2+ spaces, or inline "1. ... 2. ..."
    const segments = rawLine.split(/(?:\t|[,;|]+|\s{2,}|\s+(?=\d+[.):]\s+))/);
    for (const seg of segments) {
      const stripped = seg
        .replace(/^\s*(?:\d+[.):]\s+|[-•*]\s+)/, "")
        .replace(/[^A-Za-z0-9\s-]/g, " ")
        .replace(/\s+/g, " ")
        .trim();
      if (!stripped) continue;

      const parts = stripped.split(" ").filter(Boolean);
      if (parts.length === 1) {
        const clean = normalizeRecoveryCode(parts[0]!);
        if (clean) codes.push(clean);
      } else if (parts.length === 2) {
        const [p1, p2] = [parts[0]!, parts[1]!];
        const canPair =
          !p1.includes("-") &&
          !p2.includes("-") &&
          p1.length >= 2 &&
          p1.length <= 6 &&
          p2.length >= 2 &&
          p2.length <= 6 &&
          p1.length + 1 + p2.length <= RECOVERY_CODE_MAX_LEN;
        if (canPair) {
          codes.push(`${p1} ${p2}`);
        } else {
          const c1 = normalizeRecoveryCode(p1);
          const c2 = normalizeRecoveryCode(p2);
          if (c1) codes.push(c1);
          if (c2 && codes.length < RECOVERY_CODES_MAX_SLOTS) codes.push(c2);
        }
      } else {
        // 3+ space-separated tokens on the same segment
        const allFourChar = parts.every((p) => /^[A-Za-z0-9]{4}$/.test(p));
        const allLongCodes = parts.every((p) => p.length >= 5);
        if (allFourChar && parts.length % 2 === 0) {
          for (let i = 0; i < parts.length; i += 2) {
            codes.push(`${parts[i]!} ${parts[i + 1]!}`);
            if (codes.length >= RECOVERY_CODES_MAX_SLOTS) break;
          }
        } else if (allLongCodes) {
          for (const part of parts) {
            const clean = normalizeRecoveryCode(part);
            if (clean) codes.push(clean);
            if (codes.length >= RECOVERY_CODES_MAX_SLOTS) break;
          }
        } else if (stripped.length <= RECOVERY_CODE_MAX_LEN) {
          codes.push(stripped);
        } else {
          for (const part of parts) {
            const clean = normalizeRecoveryCode(part);
            if (clean) codes.push(clean);
            if (codes.length >= RECOVERY_CODES_MAX_SLOTS) break;
          }
        }
      }

      if (codes.length >= RECOVERY_CODES_MAX_SLOTS) break;
    }
    if (codes.length >= RECOVERY_CODES_MAX_SLOTS) break;
  }

  return codes.slice(0, RECOVERY_CODES_MAX_SLOTS);
}

/**
 * Parses a stored custom-field string into a dynamic even-sized slot list (minimum 10, up to 100).
 */
export function parseRecoveryCodesToSlots(
  value?: string | null,
  minSlots = RECOVERY_CODES_DEFAULT_SLOTS,
): string[] {
  const parsed = value ? parseRecoveryCodesPaste(value) : [];
  const rawLen = Math.max(minSlots, parsed.length);
  const slotCount = Math.min(RECOVERY_CODES_MAX_SLOTS, rawLen % 2 === 0 ? rawLen : rawLen + 1);
  const slots = Array<string>(slotCount).fill("");
  for (let i = 0; i < Math.min(parsed.length, slotCount); i++) {
    slots[i] = parsed[i]!;
  }
  return slots;
}

/**
 * Applies pasted clipboard text into the recovery code grid, expanding beyond 10 slots if needed.
 */
export function applyRecoveryCodesPaste(
  currentSlots: string[],
  targetIndex: number,
  rawText: string,
  forceFromStart = false,
): { slots: string[]; nextFocusIndex: number } {
  const baseCount = Math.max(RECOVERY_CODES_DEFAULT_SLOTS, currentSlots.length);
  const existing = Array.from({ length: baseCount }, (_, i) =>
    normalizeRecoveryCode(currentSlots[i] ?? ""),
  );
  const chunks = parseRecoveryCodesPaste(rawText);
  if (chunks.length === 0) {
    return { slots: existing, nextFocusIndex: Math.min(targetIndex, existing.length - 1) };
  }

  const boundedTarget = Math.max(0, Math.min(existing.length - 1, targetIndex));
  const earlierEmpty = existing.slice(0, boundedTarget).every((s) => s.length === 0);
  const startIdx =
    forceFromStart || (chunks.length > 1 && (boundedTarget === 0 || earlierEmpty))
      ? 0
      : boundedTarget;

  const neededLen = Math.min(RECOVERY_CODES_MAX_SLOTS, Math.max(baseCount, startIdx + chunks.length));
  const finalLen = neededLen % 2 === 0 ? neededLen : Math.min(RECOVERY_CODES_MAX_SLOTS, neededLen + 1);
  const next = Array.from({ length: finalLen }, (_, i) => existing[i] ?? "");

  let cursor = startIdx;
  for (const chunk of chunks) {
    if (cursor >= RECOVERY_CODES_MAX_SLOTS) break;
    next[cursor] = chunk;
    cursor++;
  }

  const lastFilled = Math.max(startIdx, cursor - 1);
  const nextFocusIndex =
    cursor < next.length ? cursor : Math.min(lastFilled, next.length - 1);

  return { slots: next, nextFocusIndex };
}

/**
 * Serializes recovery codes one complete code per line for clean cross-platform E2EE sync.
 */
export function serializeRecoveryCodesSlots(slots: string[]): string {
  const cleaned = slots.map((s) => normalizeRecoveryCode(s)).filter(Boolean);
  return cleaned.join("\n");
}

export function countFilledRecoveryCodes(slots: string[]): number {
  return slots.filter((s) => s.trim().length > 0).length;
}

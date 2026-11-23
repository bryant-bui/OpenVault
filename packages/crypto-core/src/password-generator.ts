const LOWER = "abcdefghijklmnopqrstuvwxyz";
const LOWER_SAFE = "abcdefghijkmnopqrstuvwxyz"; // omits l
const UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
const UPPER_SAFE = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // omits I, O
const DIGITS = "0123456789";
const DIGITS_SAFE = "23456789"; // omits 0, 1
const SYMBOLS = "!@#$%^&*()-_=+[]{};:,.<>?";

export interface PasswordGeneratorOptions {
  length: number;
  lowercase?: boolean;
  uppercase?: boolean;
  digits?: boolean;
  symbols?: boolean;
  avoidAmbiguous?: boolean;
}

/**
 * Unbiased uniform random integer in [0, max) using CSPRNG rejection sampling.
 */
export function secureRandomInt(max: number): number {
  if (!Number.isInteger(max) || max <= 0 || max > 0xffffffff) {
    throw new Error(`Invalid max value: ${max}`);
  }
  const range = 0x100000000;
  const limit = range - (range % max);
  const buf = new Uint32Array(1);
  while (true) {
    crypto.getRandomValues(buf);
    const val = buf[0]!;
    if (val < limit) {
      return val % max;
    }
  }
}

function pickChar(charset: string): string {
  return charset[secureRandomInt(charset.length)]!;
}

/**
 * Fisher-Yates shuffle backed by CSPRNG.
 */
function secureShuffle<T>(arr: T[]): T[] {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = secureRandomInt(i + 1);
    const tmp = arr[i]!;
    arr[i] = arr[j]!;
    arr[j] = tmp;
  }
  return arr;
}

export function generatePassword(options: PasswordGeneratorOptions): string {
  const {
    length = 20,
    lowercase = true,
    uppercase = true,
    digits = true,
    symbols = true,
    avoidAmbiguous = false,
  } = options;

  const pools: string[] = [];
  if (lowercase) pools.push(avoidAmbiguous ? LOWER_SAFE : LOWER);
  if (uppercase) pools.push(avoidAmbiguous ? UPPER_SAFE : UPPER);
  if (digits) pools.push(avoidAmbiguous ? DIGITS_SAFE : DIGITS);
  if (symbols) pools.push(SYMBOLS);

  if (pools.length === 0) {
    throw new Error("At least one character set must be enabled");
  }
  if (!Number.isInteger(length) || length < pools.length || length > 128) {
    throw new Error(`Invalid password length: ${length}`);
  }

  const combined = pools.join("");
  const chars: string[] = [];

  // Guarantee at least one character from each enabled pool
  for (const pool of pools) {
    chars.push(pickChar(pool));
  }
  while (chars.length < length) {
    chars.push(pickChar(combined));
  }

  return secureShuffle(chars).join("");
}

const PASSPHRASE_WORDS = [
  "anchor", "apricot", "atlas", "aurora", "bamboo", "beacon", "berry", "bison",
  "breeze", "bridge", "cactus", "canyon", "cedar", "cipher", "cloud", "clover",
  "cobalt", "comet", "compass", "coral", "crane", "crystal", "cypress", "delta",
  "dolphin", "eclipse", "ember", "emerald", "falcon", "fern", "fjord", "flame",
  "forest", "frost", "galaxy", "garden", "glacier", "granite", "harbor", "hazel",
  "horizon", "indigo", "island", "ivory", "jasper", "jungle", "kayak", "kestrel",
  "lagoon", "lantern", "laurel", "lemon", "lilac", "lotus", "lunar", "magnet",
  "mango", "maple", "marble", "meadow", "meteor", "mint", "monarch", "moss",
  "nebula", "nectar", "nimbus", "north", "oak", "oasis", "ocean", "olive",
  "onyx", "opal", "orbit", "orchid", "osprey", "otter", "palm", "panda",
  "pebble", "pelican", "pepper", "phoenix", "pine", "planet", "plaza", "plum",
  "polar", "prism", "quartz", "quiver", "radish", "raven", "reef", "ridge",
  "river", "robin", "rocket", "ruby", "saffron", "sage", "sailor", "salmon",
  "sapphire", "saturn", "scarlet", "sequoia", "shadow", "silver", "solstice", "sparrow",
  "spruce", "starlight", "summit", "sunset", "timber", "topaz", "tulip", "tundra",
  "valley", "velvet", "verbena", "violet", "voyage", "walnut", "willow", "zephyr",
];

export interface PassphraseOptions {
  wordCount?: number;
  separator?: string;
  capitalize?: boolean;
  includeNumber?: boolean;
}

export function generatePassphrase(options: PassphraseOptions = {}): string {
  const {
    wordCount = 4,
    separator = "-",
    capitalize = true,
    includeNumber = true,
  } = options;

  const count = Math.min(Math.max(3, wordCount), 10);
  const words: string[] = [];
  for (let i = 0; i < count; i++) {
    let w = PASSPHRASE_WORDS[secureRandomInt(PASSPHRASE_WORDS.length)]!;
    if (capitalize) {
      w = w[0]!.toUpperCase() + w.slice(1);
    }
    words.push(w);
  }
  if (includeNumber) {
    const idx = secureRandomInt(words.length);
    words[idx] = `${words[idx]}${secureRandomInt(90) + 10}`;
  }
  return words.join(separator);
}

export interface PasswordStrength {
  entropyBits: number;
  score: 0 | 1 | 2 | 3 | 4;
  label: "weak" | "fair" | "good" | "strong" | "excellent";
}

export function estimatePasswordStrength(password: string): PasswordStrength {
  if (!password) {
    return { entropyBits: 0, score: 0, label: "weak" };
  }
  let charsetSize = 0;
  if (/[a-z]/.test(password)) charsetSize += 26;
  if (/[A-Z]/.test(password)) charsetSize += 26;
  if (/[0-9]/.test(password)) charsetSize += 10;
  if (/[^a-zA-Z0-9]/.test(password)) charsetSize += 32;

  const entropyBits = Math.round(password.length * Math.log2(Math.max(charsetSize, 1)));

  if (entropyBits < 40) return { entropyBits, score: 0, label: "weak" };
  if (entropyBits < 60) return { entropyBits, score: 1, label: "fair" };
  if (entropyBits < 80) return { entropyBits, score: 2, label: "good" };
  if (entropyBits < 100) return { entropyBits, score: 3, label: "strong" };
  return { entropyBits, score: 4, label: "excellent" };
}

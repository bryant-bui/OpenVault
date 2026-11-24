/**
 * Zero-dependency PNG icon generator for OpenVault Chrome Extension.
 * Produces real pixel-accurate 16x16, 32x32, 48x48, and 128x128 RGBA PNG icons
 * featuring the OpenVault cobalt blue rounded emblem and white keyhole shield.
 */
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";

const SIZES = [16, 32, 48, 128];
const OUT_DIR = path.resolve(import.meta.dirname, "../public/icons");

function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    c ^= buf[i];
    for (let k = 0; k < 8; k++) {
      c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
    }
  }
  return (c ^ 0xffffffff) >>> 0;
}

function pngChunk(type, data) {
  const typeBuf = Buffer.from(type, "ascii");
  const lenBuf = Buffer.alloc(4);
  lenBuf.writeUInt32BE(data.length, 0);
  const crcInput = Buffer.concat([typeBuf, data]);
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(crcInput), 0);
  return Buffer.concat([lenBuf, typeBuf, data, crcBuf]);
}

function encodePng(width, height, rgba) {
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // RGBA
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;

  const stride = width * 4;
  const raw = Buffer.alloc((stride + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0; // filter None
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
  }
  const idat = zlib.deflateSync(raw, { level: 9 });
  return Buffer.concat([
    sig,
    pngChunk("IHDR", ihdr),
    pngChunk("IDAT", idat),
    pngChunk("IEND", Buffer.alloc(0)),
  ]);
}

function sdRoundedBox(px, py, bx, by, r) {
  const qx = Math.abs(px) - bx + r;
  const qy = Math.abs(py) - by + r;
  return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r;
}

function renderIcon(size, locked = false) {
  const rgba = Buffer.alloc(size * size * 4);
  const samples = 4; // 4x4 supersampling for razor-sharp anti-aliasing
  const invSamples = 1 / (samples * samples);

  // Bottom-right padlock badge coordinates (normalized [-1, 1])
  const badgeCx = 0.56;
  const badgeCy = 0.56;
  const badgeR = 0.42;

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let accR = 0;
      let accG = 0;
      let accB = 0;
      let accA = 0;

      for (let sy = 0; sy < samples; sy++) {
        for (let sx = 0; sx < samples; sx++) {
          const u = ((x + (sx + 0.5) / samples) / size) * 2 - 1;
          const v = ((y + (sy + 0.5) / samples) / size) * 2 - 1;

          // Locked overlay badge takes top z-order over the base squircle
          if (locked) {
            const dx = u - badgeCx;
            const dy = v - badgeCy;
            const distBadge = Math.hypot(dx, dy);

            if (distBadge <= badgeR + 0.06) {
              // Crisp dark slate outline ring separating the lock badge from the cobalt shield
              if (distBadge > badgeR) {
                accR += 15;
                accG += 23;
                accB += 42;
                accA += 255;
                continue;
              }

              // Vivid high-contrast amber-orange lock badge background (#F59E0B)
              let br = 245;
              let bg = 158;
              let bb = 11;

              // Padlock body (rounded rectangle inside badge)
              const bodyDist = sdRoundedBox(dx, dy - 0.085, 0.19, 0.145, 0.05);
              // Padlock shackle arch (ring in upper half)
              const shackleDist = Math.hypot(dx, dy + 0.055);
              const inShackleArch =
                dy <= -0.02 && shackleDist <= 0.145 && shackleDist >= 0.085;
              const inShackleLegs =
                dy >= -0.055 &&
                dy <= -0.01 &&
                Math.abs(dx) <= 0.145 &&
                Math.abs(dx) >= 0.085;

              if (bodyDist <= 0 || inShackleArch || inShackleLegs) {
                br = 255;
                bg = 255;
                bb = 255;
                // Tiny inner keyhole dot inside white lock body for extra sharpness at >= 32px
                if (bodyDist <= 0 && Math.hypot(dx, dy - 0.085) < 0.042) {
                  br = 180;
                  bg = 83;
                  bb = 9;
                }
              }

              accR += br;
              accG += bg;
              accB += bb;
              accA += 255;
              continue;
            }
          }

          // Rounded square squircle backdrop
          const dBg = sdRoundedBox(u, v, 0.9, 0.9, 0.42);
          if (dBg > 0) continue;

          // Gradient from #0A84FF (top-left) to #0455BF (bottom-right)
          const t = Math.min(1, Math.max(0, (u + v + 2) / 4));
          let r = 14 * (1 - t) + 4 * t;
          let g = 132 * (1 - t) + 85 * t;
          let b = 255 * (1 - t) + 195 * t;

          // Slightly dim the shield when locked so the amber lock badge pops immediately
          if (locked) {
            r *= 0.82;
            g *= 0.82;
            b *= 0.86;
          }

          // Outer subtle translucent ring
          const distCenter = Math.hypot(u, v);
          const ringDist = Math.abs(distCenter - 0.58);
          if (ringDist < 0.085) {
            // Crisp white 1Password-inspired vault dial ring
            r = 255;
            g = 255;
            b = 255;
          } else if (distCenter < 0.495) {
            // Inner deep slate-cobalt dial core
            r = 12;
            g = 34;
            b = 74;

            // Keyhole: upper circle + lower tapered stem
            const dHead = Math.hypot(u, v + 0.09) - 0.145;
            const stemHalfWidth = 0.068 + Math.max(0, v - 0.02) * 0.07;
            const inStem = v >= -0.02 && v <= 0.28 && Math.abs(u) <= stemHalfWidth;
            if (dHead <= 0 || inStem) {
              r = 255;
              g = 255;
              b = 255;
            }
          }

          accR += r;
          accG += g;
          accB += b;
          accA += 255;
        }
      }

      const idx = (y * size + x) * 4;
      if (accA > 0) {
        const alpha = accA * invSamples;
        const norm = 255 / alpha;
        rgba[idx] = Math.min(255, Math.round(accR * invSamples * norm));
        rgba[idx + 1] = Math.min(255, Math.round(accG * invSamples * norm));
        rgba[idx + 2] = Math.min(255, Math.round(accB * invSamples * norm));
        rgba[idx + 3] = Math.min(255, Math.round(alpha));
      }
    }
  }

  return encodePng(size, size, rgba);
}

fs.mkdirSync(OUT_DIR, { recursive: true });
for (const size of SIZES) {
  fs.writeFileSync(path.join(OUT_DIR, `icon-${size}.png`), renderIcon(size, false));
  fs.writeFileSync(path.join(OUT_DIR, `icon-locked-${size}.png`), renderIcon(size, true));
}
console.log(`Generated OpenVault unlocked & locked icons (${SIZES.join(", ")}px) in ${OUT_DIR}`);

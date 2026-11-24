/**
 * Zero-dependency PNG icon generator for OpenVault Chrome Extension.
 * Produces pixel-accurate 16x16, 32x32, 48x48, and 128x128 RGBA PNG icons
 * featuring the canonical OpenVault Cobalt Squircle + Precision Vault Dial.
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
    raw[y * (stride + 1)] = 0;
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

function pointInTriangle(px, py, ax, ay, bx, by, cx, cy) {
  const d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
  const d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
  const d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
  const hasNeg = d1 < 0 || d2 < 0 || d3 < 0;
  const hasPos = d1 > 0 || d2 > 0 || d3 > 0;
  return !(hasNeg && hasPos);
}

function lerp(a, b, t) {
  return a * (1 - t) + b * t;
}

function renderIcon(size, locked = false) {
  const rgba = Buffer.alloc(size * size * 4);
  const samples = 4; // 4x4 supersampling
  const invSamples = 1 / (samples * samples);

  // Bottom-right amber padlock badge (locked state)
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

          // Locked overlay badge takes top z-order
          if (locked) {
            const dx = u - badgeCx;
            const dy = v - badgeCy;
            const distBadge = Math.hypot(dx, dy);

            if (distBadge <= badgeR + 0.06) {
              if (distBadge > badgeR) {
                accR += 15;
                accG += 23;
                accB += 42;
                accA += 255;
                continue;
              }

              // Vivid Amber badge (#F59E0B)
              let br = 245;
              let bg = 158;
              let bb = 11;

              const bodyDist = sdRoundedBox(dx, dy - 0.085, 0.19, 0.145, 0.05);
              const shackleDist = Math.hypot(dx, dy + 0.055);
              const inShackleArch = dy <= -0.02 && shackleDist <= 0.145 && shackleDist >= 0.085;
              const inShackleLegs =
                dy >= -0.055 && dy <= -0.01 && Math.abs(dx) <= 0.145 && Math.abs(dx) >= 0.085;

              if (bodyDist <= 0 || inShackleArch || inShackleLegs) {
                br = 255;
                bg = 255;
                bb = 255;
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

          // Canonical Cobalt Squircle (#0C74F2 -> #0558C9)
          const dBg = sdRoundedBox(u, v, 0.92, 0.92, 0.44);
          if (dBg > 0) continue;

          const gradT = Math.min(1, Math.max(0, (u + v + 2) / 4));
          let r = lerp(12, 5, gradT);
          let g = lerp(116, 88, gradT);
          let b = lerp(242, 201, gradT);

          const distCenter = Math.hypot(u, v);

          if (distCenter <= 0.68) {
            if (distCenter > 0.55) {
              // Outer polished silver/white dial bevel (#FFFFFF -> #E2E8F0)
              const st = Math.min(1, Math.max(0, (u + v + 1.36) / 2.72));
              r = lerp(255, 226, st);
              g = lerp(255, 232, st);
              b = lerp(255, 240, st);
            } else if (distCenter > 0.384) {
              // Inner Cobalt-to-Sky bevel ring (#1A56DB -> #0C74F2 -> #60A5FA)
              const ct = Math.min(1, Math.max(0, (v + 0.55) / 1.1));
              if (ct < 0.52) {
                const k = ct / 0.52;
                r = lerp(26, 12, k);
                g = lerp(86, 116, k);
                b = lerp(219, 242, k);
              } else {
                const k = (ct - 0.52) / 0.48;
                r = lerp(12, 96, k);
                g = lerp(116, 165, k);
                b = lerp(242, 250, k);
              }
              // Subtle sky halo stroke inside ring
              if (Math.abs(distCenter - 0.456) < 0.022) {
                r = lerp(r, 147, 0.45);
                g = lerp(g, 197, 0.45);
                b = lerp(b, 253, 0.45);
              }
            } else {
              // Bright recessed center disc (#FFFFFF -> #E5ECF6)
              const dt = Math.min(1, Math.max(0, (u + v + 0.76) / 1.52));
              r = lerp(255, 229, dt);
              g = lerp(255, 236, dt);
              b = lerp(255, 246, dt);

              // Notched dark-navy keyhole bolt (#0F172A)
              const inBolt = sdRoundedBox(u, v, 0.088, 0.248, 0.05) <= 0;
              const inLeftNotch = pointInTriangle(u, v, -0.096, -0.08, -0.01, -0.024, -0.096, 0.03);
              const inRightNotch = pointInTriangle(u, v, 0.096, -0.016, 0.01, 0.04, 0.096, 0.092);

              if (inBolt && !inLeftNotch && !inRightNotch) {
                r = 15;
                g = 23;
                b = 42;
              }
            }
          }

          if (locked) {
            r *= 0.82;
            g *= 0.82;
            b *= 0.86;
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

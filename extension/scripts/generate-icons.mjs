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

function renderIcon(size) {
  const rgba = Buffer.alloc(size * size * 4);
  const samples = 4; // 4x4 supersampling for razor-sharp anti-aliasing
  const invSamples = 1 / (samples * samples);

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

          // Rounded square squircle backdrop
          const dBg = sdRoundedBox(u, v, 0.9, 0.9, 0.42);
          if (dBg > 0) continue;

          // Gradient from #0A84FF (top-left) to #0455BF (bottom-right)
          const t = Math.min(1, Math.max(0, (u + v + 2) / 4));
          let r = 14 * (1 - t) + 4 * t;
          let g = 132 * (1 - t) + 85 * t;
          let b = 255 * (1 - t) + 195 * t;

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
  const png = renderIcon(size);
  const outPath = path.join(OUT_DIR, `icon-${size}.png`);
  fs.writeFileSync(outPath, png);
}
console.log(`Generated OpenVault icons (${SIZES.join(", ")}px) in ${OUT_DIR}`);

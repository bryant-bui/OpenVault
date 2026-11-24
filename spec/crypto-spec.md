# OpenVault Cryptographic & Wire Protocol Specification (Bitwarden-Compatible)

This document is the single source of truth for all cryptographic primitives shared across:
- `packages/crypto-core` (TypeScript — Chrome Extension & shared tools)
- `backend` (Cloudflare Workers + D1 + R2)
- `android` (Kotlin — Android App)

Every implementation **MUST** pass 100% of the vectors in [`spec/test-vectors.json`](./test-vectors.json).

---

## 1. Zero-Knowledge Principles

1. The **Master Password** and **Master Key** MUST NEVER be transmitted to the server or written to persistent disk in plaintext.
2. All vault item fields (login username, password, URIs, TOTP secret, Passkey private key, secure notes, folder names) MUST be encrypted client-side before transmission.
3. All cryptographic random values (IVs, symmetric keys, salts, session IDs, tokens) MUST use CSPRNG (`crypto.getRandomValues` in Web/Workers, `java.security.SecureRandom` on Android).
4. All MAC and secret comparisons MUST execute in constant time (`crypto.subtle.timingSafeEqual` / constant-time XOR loop / `MessageDigest.isEqual`).

---

## 2. Master Key Derivation (Client-Side)

Normalize email before use: `normalizedEmail = email.trim().toLowerCase()`.

### 2.1 `KdfType = 0` (`PBKDF2_SHA256`)
- `salt = UTF8(normalizedEmail)`
- `iterations`: integer, default `600000` (minimum `100000` accepted for legacy imports; new accounts default to Argon2id or 600,000)
- `masterKey = PBKDF2-HMAC-SHA256(password = UTF8(masterPassword), salt = salt, iterations = iterations, dkLen = 32)`

### 2.2 `KdfType = 1` (`Argon2id`) — Recommended Default
- `salt = SHA-256(UTF8(normalizedEmail))` *(32 raw bytes)*
- `iterations` (`t`): default `3` (allowed `1..10`)
- `memory` (`m`): in MiB, default `64` (`64 * 1024 = 65536` KiB, allowed `15..1024`)
- `parallelism` (`p`): default `4` (allowed `1..16`)
- `masterKey = Argon2id(password = UTF8(masterPassword), salt = salt, t = iterations, m = memory * 1024 KiB, p = parallelism, hashLength = 32)`

Output: `masterKey` = **32 bytes** (256-bit).

---

## 3. Master Password Hash (Authentication Only)

Used solely to authenticate with `POST /identity/connect/token` and `POST /identity/accounts/register/finish`:

```text
masterPasswordHashBytes = PBKDF2-HMAC-SHA256(
  password   = masterKey (32 raw bytes),
  salt       = UTF8(masterPassword),
  iterations = 1,
  dkLen      = 32
)
masterPasswordHash = Base64(masterPasswordHashBytes)
```

For offline local unlock verification (`localMasterPasswordHash`), run the same operation with `iterations = 2`.

### Server-Side Password Hash Verification (Cloudflare Workers)
Because Cloudflare Workers enforces a maximum of `100,000` iterations for Web Crypto `PBKDF2` and a `10ms` CPU budget on the Free tier:
- On registration/password-change, the Worker generates a random 16-byte `password_salt`.
- The Worker derives:
  ```text
  storedHash = PBKDF2-HMAC-SHA256(
    password   = UTF8(masterPasswordHash) || UTF8(PASSWORD_PEPPER),
    salt       = password_salt,
    iterations = 100000,   // configurable via SERVER_KDF_ITERATIONS (<= 100000)
    dkLen      = 32
  )
  ```
- Verification hashes the incoming `masterPasswordHash` identically and compares using `crypto.subtle.timingSafeEqual` on fixed-length 32-byte buffers.

---

## 4. Stretched Master Key (Raw HKDF-Expand — No Extract!)

Bitwarden expands the 32-byte `masterKey` directly into a 64-byte `stretchedMasterKey` using **RFC 5869 HKDF-Expand** with `PRK = masterKey` (**skipping HKDF-Extract**):

```text
stretchedEncKey = HMAC-SHA256(key = masterKey, data = UTF8("enc") || 0x01)   // 32 bytes
stretchedMacKey = HMAC-SHA256(key = masterKey, data = UTF8("mac") || 0x01)   // 32 bytes
stretchedMasterKey = stretchedEncKey || stretchedMacKey                       // 64 bytes
```

> **CRITICAL:** Do NOT call Web Crypto's `crypto.subtle.deriveKey("HKDF", ...)` directly, as it executes `HKDF-Extract` before `HKDF-Expand` and will produce incompatible keys.

---

## 5. User Symmetric Key & RSA Keypair

- On account registration, the client generates a random 64-byte `userSymmetricKey`:
  - `encKey = userSymmetricKey[0..32]` (AES-256-CBC key)
  - `macKey = userSymmetricKey[32..64]` (HMAC-SHA256 key)
- The client encrypts `userSymmetricKey` (raw 64 bytes) with `stretchedMasterKey` as a Type 2 CipherString → stored on server as `Key` (`symmetric_key`).
- Changing the Master Password only re-encrypts `userSymmetricKey` with the new `stretchedMasterKey` (no need to re-encrypt the vault).
- For full compatibility with official Bitwarden clients, the client also generates an **RSA-2048 OAEP-SHA1** keypair:
  - `publicKey`: Base64-encoded SPKI DER.
  - `encryptedPrivateKey`: PKCS#8 DER encrypted with `userSymmetricKey` as a Type 2 CipherString.

---

## 6. CipherString Type 2 (`AesCbc256_HmacSha256_B64`)

### Format
```text
2.<Base64_IV>|<Base64_Ciphertext>|<Base64_MAC>
```

### Encryption
1. Generate 16 cryptographically random bytes for `iv`.
2. Encrypt `plaintext` (UTF-8 string or raw bytes) using **AES-256-CBC with PKCS#7 padding** under `encKey` and `iv` → `ct`.
3. Compute `mac = HMAC-SHA256(key = macKey, data = iv || ct)` → 32 bytes.
4. Serialize as `2.${Base64(iv)}|${Base64(ct)}|${Base64(mac)}`.

### Decryption (Encrypt-then-MAC — Strict Order)
1. Validate prefix `2.` and exactly 3 `|`-delimited non-empty Base64 parts (`iv`, `ct`, `mac`).
2. Decode Base64 and validate `iv.length === 16`, `mac.length === 32`, `ct.length >= 16 && ct.length % 16 === 0`.
3. Recompute `expectedMac = HMAC-SHA256(key = macKey, data = iv || ct)`.
4. Compare `expectedMac` and `mac` in **constant time**. If not equal, **abort immediately** without running AES decryption.
5. Decrypt `ct` with AES-256-CBC + PKCS#7 unpadding under `encKey` and `iv`.

---

## 7. TOTP (RFC 6238 / RFC 4226)

- Accepts raw Base32 secret (case-insensitive, spaces/hyphens stripped) or `otpauth://totp/<label>?secret=...&issuer=...&algorithm=SHA1|SHA256|SHA512&digits=6|8&period=30`.
- Counter `T = floor(unixSeconds / period)` encoded as 8-byte unsigned big-endian integer.
- `digest = HMAC(algorithm, key = base32Decode(secret), data = T_bytes)`.
- Dynamic truncation: `offset = digest[digest.length - 1] & 0x0f`, `code = ((digest[offset] & 0x7f) << 24 | (digest[offset+1] & 0xff) << 16 | (digest[offset+2] & 0xff) << 8 | (digest[offset+3] & 0xff)) % 10^digits`, zero-padded to `digits`.

---

## 8. Passkey (`login.fido2Credentials`) Compatibility

Each entry inside `login.fido2Credentials` follows Bitwarden's schema:
- `credentialId`, `keyType` (`"public-key"`), `keyAlgorithm` (`"ECDSA"`), `keyCurve` (`"P-256"`), `keyValue` (PKCS#8 base64url private key), `rpId`, `rpName`, `userHandle`, `userName`, `userDisplayName`, `counter`, `discoverable` — all stored encrypted as Type 2 CipherStrings on the server.
- `creationDate`: ISO-8601 UTC timestamp string.
- WebAuthn signatures from Web Crypto (`ECDSA` P-256 IEEE P1363 64-byte `r || s`) MUST be encoded to **ASN.1 DER** `SEQUENCE { INTEGER r, INTEGER s }` before returning to relying parties.

---

## 9. Vault+ (Personal Safe) Cryptographic Protocol

Vault+ provides an isolated Zero-Knowledge encrypted document storage space inside OpenVault, cryptographically independent from the main password vault's `userSymmetricKey`.

### 9.1 `SafeKey` & 6-Digit Secondary PIN Wrapping
- On Vault+ setup, the client generates a CSPRNG 64-byte `safeKey` (`encKey[0..32] || macKey[32..64]`).
- Default secondary passcode is a **6-digit numeric PIN** (`^\d{6}$`).
- Client generates a random 16-byte `passcodeSalt` and derives a 32-byte key using `Argon2id` (default: `t = 3`, `m = 64 MiB` / `65536 KiB`, `p = 4`, `hashLength = 32`, `PBKDF2-SHA256` also accepted as KDF fallback).
- The 32-byte derived key is stretched via raw RFC 5869 `HKDF-Expand` (`"enc"` / `"mac"`) to a 64-byte `passcodeWrappingKey`, which encrypts `safeKey` as a Bitwarden Type 2 CipherString (`passcodeEncryptedSafeKey`).

### 9.2 Independent Recovery Key (`OVRK-...`)
- On Vault+ setup, the client generates 20 CSPRNG bytes (**160-bit entropy**) and encodes them using **Crockford Base32** (`0123456789ABCDEFGHJKMNPQRSTVWXYZ`, 32 chars) formatted as:
  `OVRK-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX`
- Decoding strips optional `OVRK-` prefix, whitespace, and hyphens, uppercases, and normalizes `I`/`L` → `1` and `O` → `0`.
- Client generates a random 16-byte `recoverySalt` and derives a 32-byte key using `PBKDF2-HMAC-SHA256(password = recoveryRawBytes(20B), salt = recoverySalt, iterations = 100000, dkLen = 32)`, stretches it via raw `HKDF-Expand` (`"enc"` / `"mac"`), and encrypts `safeKey` as a Bitwarden Type 2 CipherString (`recoveryEncryptedSafeKey`).

### 9.3 Binary File Envelope (`OVSF v1`)
- Each document uses a fresh CSPRNG 32-byte `fileKey` (and optional 32-byte `thumbKey`) stored inside the item's `SafeKey`-encrypted metadata JSON (`SafeItemMetadata`).
- Maximum plaintext file size: **5 MiB (`5,242,880` bytes)**; maximum plaintext thumbnail size: **128 KiB (`131,072` bytes)**.
- Binary format (`AES-256-GCM`, 12-byte IV, 16-byte tag, total overhead = 34 bytes):
  ```text
  Bytes 0..3   : Magic ASCII "OVSF" (0x4F 0x56 0x53 0x46)
  Byte  4      : Version = 0x01
  Byte  5      : CipherSuite = 0x01 (AES-256-GCM)
  Bytes 6..17  : Random 12-byte IV
  Bytes 18..N  : Ciphertext || 16-byte GCM Authentication Tag
  AAD          : UTF8("OVSF:v1:" + itemId)         // Main blob
               | UTF8("OVSF:v1:thumb:" + itemId)   // Encrypted thumbnail
  ```

### 9.4 Server-Enforced Lockout Policy
- **Cooldown:** Every 5 consecutive failed PIN attempts (`5`, `10`), the server sets a **15-minute cooldown** (`locked_until = now + 900000`) and withholds `passcodeEncryptedSafeKey` (`null`).
- **Permanent PIN Lockout:** At **15 total consecutive failed PIN attempts**, the server sets `recovery_required = 1`, permanently withholds `passcodeEncryptedSafeKey`, and requires either Recovery Key recovery (`PUT /api/openvault/safe/passcode`) or Master Password-verified Vault+ wipe (`DELETE /api/openvault/safe/reset`).

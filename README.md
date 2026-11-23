# OpenVault

**OpenVault** is an open-source, zero-knowledge personal password manager built around the **Bitwarden Official API & Zero-Knowledge Cryptographic Protocol**, designed to run **serverless at near-zero cost on Cloudflare Workers + D1 + R2**, paired with modern **1Password / Apple Glass** inspired clients.

---

## Architecture

```text
OpenVault/
├── spec/                    # Bitwarden-compatible crypto specification & shared test vectors
├── packages/crypto-core/    # Shared TypeScript crypto engine (Argon2id/PBKDF2, Type 2, TOTP, Passkey)
├── backend/                 # Cloudflare Workers (Hono) + D1 (SQLite) + R2 Daily Cron Backups
├── extension/               # Chrome Extension (Manifest V3, React + Tailwind) [Phase 2]
└── android/                 # Android App (Kotlin + Jetpack Compose + Apple Glass UI) [Phase 3]
```

### Security Model (Zero-Knowledge)
- **Client-side KDF:** `Argon2id` (default: 64 MiB, 3 iterations, parallelism 4, salt = `SHA-256(lowercase(email))`) or `PBKDF2-SHA256` (600,000 iterations).
- **Authenticated Encryption:** Bitwarden Encryption Type 2 (`2.<Base64_IV>|<Base64_Ciphertext>|<Base64_MAC>`) using **AES-256-CBC + HMAC-SHA256** (strict constant-time Encrypt-then-MAC).
- **Key Stretching:** Exact raw RFC 5869 `HKDF-Expand("enc")` / `HKDF-Expand("mac")` matching official Bitwarden clients.
- **Hardened Personal Serverless Backend:**
  - Single-user lock (`MAX_USERS=1`) + configurable `ALLOW_SIGNUP=false`.
  - Per-user salted PBKDF2-SHA256 + Worker secret pepper verified with `crypto.subtle.timingSafeEqual`.
  - Short-lived JWT access tokens bound to `security_stamp` (instant global revocation on password change).
  - Rotating opaque refresh tokens with **replay-theft family revocation**.
  - Per-IP/account brute-force rate limiting in D1.
  - Automated daily **Zero-Knowledge R2 Snapshots** via Cron Trigger (`0 3 * * *`) with SHA-256 checksums and automatic retention pruning (excluding password hashes and active tokens).

---

## Quick Start (Phase 1 — Crypto Core & Cloudflare Backend)

### Prerequisites
- Node.js `>= 22.18`
- A Cloudflare account (Free tier for Workers + D1; R2 Free 10 GB requires a payment method on file)

### 1. Install dependencies & run tests
```bash
npm install
npm run typecheck
npm test
```

### 2. Local development
```bash
cp backend/.dev.vars.example backend/.dev.vars
# Edit backend/.dev.vars with random 32+ char secrets
cd backend
npx cf d1 migrations apply openvault-db --local
npx cf dev
```

### 3. Deploy to your own Cloudflare account
```bash
npm install --global cf
cf auth login

cd backend
# Create D1 database and R2 backup bucket (replace apac with your closest region if desired)
cf d1 create --name openvault-db --primary-location-hint apac
cf r2 buckets create --name openvault-backups --location-hint apac

# Apply D1 schema migration remotely using the UUID returned by `cf d1 create`
cf d1 migrations apply <YOUR_D1_DATABASE_UUID> --dir ./migrations

# Generate a temporary secrets file, deploy Worker + secrets, then delete the temporary file
node -e "const c=require('node:crypto'),fs=require('node:fs'); fs.writeFileSync('.secrets.tmp.json', JSON.stringify({ JWT_SIGNING_SECRET: c.randomBytes(48).toString('base64url'), PASSWORD_PEPPER: c.randomBytes(48).toString('base64url') }), { mode: 0o600 });"
cf deploy --secrets-file .secrets.tmp.json
rm .secrets.tmp.json
```

> **Tip:** After registering your personal account, set `ALLOW_SIGNUP: bindings.text("false")` in `backend/cloudflare.config.ts` and run `cf deploy` again to lock down signup permanently.

---

## Roadmap

- [x] **Phase 1:** Bitwarden Crypto Spec, Cross-Platform Test Vectors, `@openvault/crypto-core`, Cloudflare Workers + D1 + R2 Daily Backup Backend
- [ ] **Phase 2:** Chrome Extension (Manifest V3, 1Password-inspired UI, Autofill, built-in TOTP)
- [ ] **Phase 3:** Android App (`io.openvault.app`, Jetpack Compose Apple Glass UI, BiometricPrompt + Keystore, Offline-first Room, AutofillService, TOTP QR Scanner)
- [ ] **Phase 4:** Cross-Platform Passkeys (WebAuthn / FIDO2 on Chrome Extension + Android 14+ `CredentialProviderService`)

---

## License

[MIT](./LICENSE)

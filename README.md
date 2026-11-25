<div align="center">

<img src="docs/screenshots/banner.png" alt="OpenVault — Own your passwords. Encrypted everywhere." width="860" />

<br/>

<img src="docs/screenshots/icon.png" width="94" alt="OpenVault" />

# OpenVault

A free, open-source, zero-knowledge password manager for Android and Chrome.<br/>
Self-host your vault on Cloudflare Workers, unlock with biometrics or PIN, and autofill passwords, 2FA &amp; passkeys everywhere.

<br/>

<p>
  <img alt="Android 9.0+" src="https://img.shields.io/badge/Android-9.0%2B-3DDC84?style=flat&logo=android&logoColor=white" />
  <img alt="Chrome MV3" src="https://img.shields.io/badge/Chrome-MV3-4285F4?style=flat&logo=googlechrome&logoColor=white" />
  <img alt="Cloudflare Workers" src="https://img.shields.io/badge/Cloudflare-Workers%20%2B%20D1%20%2B%20R2-F38020?style=flat&logo=cloudflare&logoColor=white" />
  <a href="LICENSE"><img alt="License MIT" src="https://img.shields.io/badge/License-MIT-0C74F2?style=flat" /></a>
  <a href="https://github.com/bryant-bui/OpenVault/stargazers"><img alt="Stars" src="https://img.shields.io/github/stars/bryant-bui/OpenVault?style=flat&color=60A5FA&labelColor=181717&logo=github" /></a>
<br/>
  <a href="https://ko-fi.com/bryantbui"><img src="https://ko-fi.com/img/githubbutton_sm.svg" alt="Support me on Ko-fi" height="34" /></a>
</p>

<sub><b>English</b> · <a href="docs/readme/README.vi.md">Tiếng Việt</a></sub>

<br/>
<br/>

<img src="docs/screenshots/showcase/android-01-home.png" width="196" alt="Your vault, pinned and ready" />
<img src="docs/screenshots/showcase/android-02-detail.png" width="196" alt="Passwords, 2FA and passkeys" />
<img src="docs/screenshots/showcase/android-03-watchtower.png" width="196" alt="Local security health score" />
<img src="docs/screenshots/showcase/android-04-unlock.png" width="196" alt="Vault-door PIN and biometrics" />

<br/>

<img src="docs/screenshots/showcase/ext-01-vault.png" width="400" alt="Master-detail vault and instant autofill" />
<img src="docs/screenshots/showcase/ext-02-generator.png" width="400" alt="Slide-over generator and live 2FA" />

<br/>

<details>
<summary><sub><b>Plain screenshots</b>, straight from the Android app &amp; Chrome extension</sub></summary>
<br/>

<img src="docs/screenshots/android/01-home.png" width="196" alt="Android Home" />
<img src="docs/screenshots/android/02-detail.png" width="196" alt="Android Item Detail" />
<img src="docs/screenshots/android/03-watchtower.png" width="196" alt="Android Watchtower" />
<img src="docs/screenshots/android/04-unlock.png" width="196" alt="Android Vault-Door Unlock" />

<sub><b>Home</b> &nbsp;·&nbsp; <b>Item detail</b> &nbsp;·&nbsp; <b>Watchtower</b> &nbsp;·&nbsp; <b>Vault-door unlock</b></sub>

<br/>
<br/>

<img src="docs/screenshots/android/05-items.png" width="196" alt="Android Items and Folders" />
<img src="docs/screenshots/android/06-generator.png" width="196" alt="Android Password Generator" />
<img src="docs/screenshots/android/07-onboarding.png" width="196" alt="Android Onboarding" />
<img src="docs/screenshots/android/08-home-light.png" width="196" alt="Android Light Theme" />

<sub><b>Items &amp; folders</b> &nbsp;·&nbsp; <b>Generator</b> &nbsp;·&nbsp; <b>Onboarding</b> &nbsp;·&nbsp; <b>Light mode</b></sub>

<br/>
<br/>

<img src="docs/screenshots/extension/01-vault-dark.png" width="390" alt="Extension Master-Detail Vault (Dark)" />
<img src="docs/screenshots/extension/02-generator-dark.png" width="390" alt="Extension Slide-Over Generator" />

<sub><b>Vault &amp; 2FA detail (Dark)</b> &nbsp;·&nbsp; <b>Slide-over password generator</b></sub>

<br/>
<br/>

<img src="docs/screenshots/extension/03-watchtower-dark.png" width="390" alt="Extension Watchtower Security" />
<img src="docs/screenshots/extension/04-unlock-dark.png" width="390" alt="Extension 3+3 PIN Unlock" />

<sub><b>Watchtower audit</b> &nbsp;·&nbsp; <b>3+3 PIN unlock</b></sub>

<br/>
<br/>

<img src="docs/screenshots/extension/05-vault-light.png" width="430" alt="Extension Vault (Light)" />
&nbsp;
<img src="docs/screenshots/extension/06-sidepanel-dark.png" width="202" alt="Extension Persistent Side Panel" />

<sub><b>Light mode popup</b> &nbsp;·&nbsp; <b>Persistent Chrome Side Panel</b></sub>

</details>

</div>

## What it does

<table>
<tr>
<td width="50%" valign="top">

### Android app

- Clean **1Password-inspired** Material 3 UI in OLED black and light mode
- Adaptive **foldable &amp; tablet layout** with a navigation rail and floating detail sheet
- **Vault-door unlock** with rotating keyhole, hardware Keystore biometrics and 6-digit PIN
- System-wide **`AutofillService`** and **1-tap Android 14+ Passkeys**
- Built-in **2FA codes** (TOTP) with a CameraX **QR code scanner**
- **Offline-first** encrypted Room cache with lightweight 25s revision sync
- **Watchtower** health gauge, custom E2EE item icons, `FLAG_SECURE` and English / Vietnamese UI

</td>
<td width="50%" valign="top">

### Chrome extension

- **Manifest V3** wide master-detail popup (`Ctrl+Shift+Y`) and persistent **Side Panel**
- **Instant autofill** (`Ctrl+Shift+L`) for Logins, Credit Cards, Identities, 2FA and Basic Auth
- **Inline PIN / Master unlock** and password **autosuggest** right next to login fields
- **MAIN-world WebAuthn Passkeys** synced encrypted across Chrome and Android
- **Slide-over Password Generator** for random passwords and memorable passphrases
- Local **Watchtower** security score and **3+3 segmented PIN** unlock
- One-click **Proton Pass** (ZIP / JSON / CSV) and **Bitwarden JSON** import, plus R2 backup triggers

</td>
</tr>
<tr>
<td width="50%" valign="top">

### Zero-knowledge crypto

- Built around the **Bitwarden Official API &amp; Cryptographic Protocol**
- Cross-platform test vectors in [`spec/test-vectors.json`](spec/test-vectors.json) verified in TypeScript and Kotlin
- Client-side **Argon2id** (64 MiB, 3 iterations, parallelism 4) or **PBKDF2-SHA256** (600,000 iterations)
- Type 2 **AES-256-CBC + HMAC-SHA256** constant-time Encrypt-then-MAC
- Exact raw RFC 5869 **HKDF-Expand** (`"enc"` / `"mac"`), ES256 **P-256 Passkeys** and 5-attempt **PIN self-destruct**

</td>
<td width="50%" valign="top">

### Self-hosted backend

- Runs serverless at near-zero cost on **Cloudflare Workers** (Hono) + **D1** (SQLite)
- Automated daily **Zero-Knowledge R2 Snapshots** (`03:00 UTC` Cron) with SHA-256 checksums and 30-day retention
- Personal single-user lock (`MAX_USERS=1`) and `ALLOW_SIGNUP=false` lockdown
- Per-user salted **PBKDF2-SHA256** + Worker pepper verified with `timingSafeEqual`
- `security_stamp`-bound JWTs, rotating refresh tokens with **replay-theft family revocation**, and D1 brute-force rate limiting

</td>
</tr>
</table>

## Passkeys &amp; Autofill

Create and store **WebAuthn / FIDO2 Passkeys** right inside your encrypted vault — no vendor lock-in. In Chrome, the MAIN-world bridge intercepts `navigator.credentials` cleanly; on Android 14+, `CredentialProviderService` signs you in with one tap after biometric verification. Every passkey private key is encrypted client-side inside the cipher before touching Cloudflare D1, so your passkeys sync seamlessly across browser and phone without exposing keys to the server.

## Download &amp; Setup

| Platform | Version | Status |
|---|---|---|
| Android 9.0+ | `0.8.0` | Supported (`io.openvault.app`) |
| Chrome / Edge / Brave (MV3) | `0.5.0` | Supported (`extension/dist`) |
| Cloudflare Workers Backend | `0.1.0` | Supported (`Workers + D1 + R2`) |

### Self-hosted backend

Requires Node.js `>= 22.18` and a Cloudflare account (Free tier covers Workers + D1; R2 Free 10 GB requires a payment method on file).

```bash
npm install --global cf
cf auth login

cd backend
# Create D1 database and R2 backup bucket (replace apac with your closest region if desired)
cf d1 create --name openvault-db --primary-location-hint apac
cf r2 buckets create --name openvault-backups --location-hint apac

# Apply D1 schema migration remotely using the UUID returned by `cf d1 create`
cf d1 migrations apply <YOUR_D1_DATABASE_UUID> --dir ./migrations

# Provision secrets, deploy Worker, then remove the temporary file
node -e "const c=require('node:crypto'),fs=require('node:fs'); fs.writeFileSync('.secrets.tmp.json', JSON.stringify({ JWT_SIGNING_SECRET: c.randomBytes(48).toString('base64url'), PASSWORD_PEPPER: c.randomBytes(48).toString('base64url') }), { mode: 0o600 });"
cf deploy --secrets-file .secrets.tmp.json
rm .secrets.tmp.json
```

> **Tip:** After registering your personal account, set `ALLOW_SIGNUP: bindings.text("false")` in [`backend/cloudflare.config.ts`](backend/cloudflare.config.ts) and run `cf deploy` again to lock down public signup permanently.

### Chrome extension

```bash
npm install
npm run build:extension
```

1. Open `chrome://extensions/` in Chrome, Edge, or Brave.
2. Enable **Developer mode** in the top-right corner.
3. Click **Load unpacked** and select `extension/dist`.
4. Pin **OpenVault** to your toolbar and connect to your Cloudflare Worker URL.

### Android app

Open `android/` in Android Studio or build a release APK directly with Gradle (JDK 17+):

```bash
cd android
./gradlew :app:assembleRelease
```

## Architecture

```text
OpenVault/
├── spec/                    # Bitwarden-compatible crypto specification & shared test vectors
├── packages/crypto-core/    # Shared TypeScript crypto engine (Argon2id/PBKDF2, Type 2, TOTP, Passkey, Importers)
├── backend/                 # Cloudflare Workers (Hono) + D1 (SQLite) + R2 Daily Cron Backups
├── extension/               # Chrome Extension (Manifest V3, React 19 + Tailwind CSS v4)
└── android/                 # Android App (Kotlin + Jetpack Compose + Room + Autofill + Passkeys)
```

## Security &amp; Privacy

Your master password, master key, and decrypted vault items **never leave your device**. Everything is encrypted client-side before sync, and `backend/src/services/backup.ts` excludes password hashes and active refresh tokens from daily R2 snapshots. Clipboards automatically clear after your chosen timeout, and PIN unlock wipes local PIN material after failed attempts.

On Android, `allowBackup="false"` blocks cloud backup extraction, `FLAG_SECURE` blocks screenshots and screen recordings by default, `USE_BIOMETRIC` binds vault unlocking to hardware-backed Android Keystore keys, `CAMERA` is requested solely when scanning 2FA QR codes, and `INTERNET` is used only to talk to your self-hosted Cloudflare Worker (plus optional website favicons, which can be disabled in Settings). See [`spec/crypto-spec.md`](spec/crypto-spec.md) for the full threat model and wire specification.

## Contributing

Bug reports, feature ideas, and pull requests are welcome. For major changes, open an issue first to discuss the design.

```bash
# Install workspace dependencies and run all TypeScript checks & unit tests
npm install
npm run typecheck
npm test

# Run local Cloudflare Workers + D1 dev server
cp backend/.dev.vars.example backend/.dev.vars
cd backend
npx cf d1 migrations apply openvault-db --local
npx cf dev

# Run Android unit & crypto-vector tests
cd android
./gradlew :app:testDebugUnitTest
```

## License

OpenVault is open-source software licensed under the [MIT License](LICENSE).

## Star history

<a href="https://www.star-history.com/?repos=bryant-bui%2FOpenVault&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=bryant-bui/OpenVault&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=bryant-bui/OpenVault&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=bryant-bui/OpenVault&type=date&legend=top-left" />
 </picture>
</a>

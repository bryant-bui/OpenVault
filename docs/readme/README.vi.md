<div align="center">

<img src="../../docs/screenshots/banner.png" alt="OpenVault — Làm chủ mật khẩu của bạn. Mã hóa mọi nơi." width="860" />

<br/>

<img src="../../docs/screenshots/icon.png" width="94" alt="OpenVault" />

# OpenVault

Trình quản lý mật khẩu mã nguồn mở, Zero-Knowledge miễn phí cho Android và Chrome.<br/>
Tự host kho dữ liệu trên Cloudflare Workers, mở khóa bằng sinh trắc học hoặc PIN, và tự động điền mật khẩu, 2FA &amp; Passkey mọi nơi.

<br/>

<p>
  <img alt="Android 9.0+" src="https://img.shields.io/badge/Android-9.0%2B-3DDC84?style=flat&logo=android&logoColor=white" />
  <img alt="Chrome MV3" src="https://img.shields.io/badge/Chrome-MV3-4285F4?style=flat&logo=googlechrome&logoColor=white" />
  <img alt="Cloudflare Workers" src="https://img.shields.io/badge/Cloudflare-Workers%20%2B%20D1%20%2B%20R2-F38020?style=flat&logo=cloudflare&logoColor=white" />
  <a href="../../LICENSE"><img alt="License MIT" src="https://img.shields.io/badge/License-MIT-0C74F2?style=flat" /></a>
  <a href="https://github.com/bryant-bui/OpenVault/stargazers"><img alt="Stars" src="https://img.shields.io/github/stars/bryant-bui/OpenVault?style=flat&color=60A5FA&labelColor=181717&logo=github" /></a>
</p>

<sub><a href="../../README.md">English</a> · <b>Tiếng Việt</b></sub>

<br/>
<br/>

<img src="../../docs/screenshots/showcase/android-01-home.png" width="196" alt="Kho mật khẩu sẵn sàng mọi lúc" />
<img src="../../docs/screenshots/showcase/android-02-detail.png" width="196" alt="Mật khẩu, 2FA và Passkey" />
<img src="../../docs/screenshots/showcase/android-03-watchtower.png" width="196" alt="Điểm sức khỏe bảo mật Watchtower" />
<img src="../../docs/screenshots/showcase/android-04-unlock.png" width="196" alt="Mở khóa cửa kho bằng PIN và vân tay" />

<br/>

<img src="../../docs/screenshots/showcase/ext-01-vault.png" width="400" alt="Giao diện Master-Detail và tự động điền tức thì" />
<img src="../../docs/screenshots/showcase/ext-02-generator.png" width="400" alt="Trình tạo mật khẩu trượt ngang và mã 2FA trực tiếp" />

<br/>

<details>
<summary><sub><b>Ảnh chụp màn hình gốc</b> trực tiếp từ ứng dụng Android &amp; tiện ích Chrome</sub></summary>
<br/>

<img src="../../docs/screenshots/android/01-home.png" width="196" alt="Trang chủ Android" />
<img src="../../docs/screenshots/android/02-detail.png" width="196" alt="Chi tiết mục Android" />
<img src="../../docs/screenshots/android/03-watchtower.png" width="196" alt="Watchtower Android" />
<img src="../../docs/screenshots/android/04-unlock.png" width="196" alt="Màn hình mở khóa cửa kho Android" />

<sub><b>Trang chủ</b> &nbsp;·&nbsp; <b>Chi tiết tài khoản</b> &nbsp;·&nbsp; <b>Watchtower</b> &nbsp;·&nbsp; <b>Mở khóa cửa kho</b></sub>

<br/>
<br/>

<img src="../../docs/screenshots/android/05-items.png" width="196" alt="Danh mục và thư mục Android" />
<img src="../../docs/screenshots/android/06-generator.png" width="196" alt="Trình tạo mật khẩu Android" />
<img src="../../docs/screenshots/android/07-onboarding.png" width="196" alt="Giới thiệu ban đầu Android" />
<img src="../../docs/screenshots/android/08-home-light.png" width="196" alt="Giao diện Sáng Android" />

<sub><b>Danh mục &amp; thư mục</b> &nbsp;·&nbsp; <b>Tạo mật khẩu</b> &nbsp;·&nbsp; <b>Chào mừng</b> &nbsp;·&nbsp; <b>Chế độ sáng</b></sub>

<br/>
<br/>

<img src="../../docs/screenshots/extension/01-vault-dark.png" width="390" alt="Tiện ích Chrome chế độ tối" />
<img src="../../docs/screenshots/extension/02-generator-dark.png" width="390" alt="Trình tạo mật khẩu trượt ngang trên Chrome" />

<sub><b>Kho mật khẩu &amp; 2FA (Tối)</b> &nbsp;·&nbsp; <b>Trình tạo mật khẩu trượt ngang</b></sub>

<br/>
<br/>

<img src="../../docs/screenshots/extension/03-watchtower-dark.png" width="390" alt="Watchtower trên Chrome" />
<img src="../../docs/screenshots/extension/04-unlock-dark.png" width="390" alt="Mở khóa PIN 3+3 trên Chrome" />

<sub><b>Kiểm tra Watchtower</b> &nbsp;·&nbsp; <b>Mở khóa PIN 3+3</b></sub>

<br/>
<br/>

<img src="../../docs/screenshots/extension/05-vault-light.png" width="430" alt="Tiện ích Chrome chế độ sáng" />
&nbsp;
<img src="../../docs/screenshots/extension/06-sidepanel-dark.png" width="202" alt="Bảng điều khiển Side Panel" />

<sub><b>Popup chế độ sáng</b> &nbsp;·&nbsp; <b>Chrome Side Panel cố định</b></sub>

</details>

</div>

## Tính năng nổi bật

<table>
<tr>
<td width="50%" valign="top">

### Ứng dụng Android

- Giao diện Material 3 phẳng **lấy cảm hứng từ 1Password**, hỗ trợ đen OLED và chế độ sáng
- Tối ưu **màn hình gập &amp; máy tính bảng** với Navigation Rail và sheet nổi (Z Fold)
- **Mở khóa cửa kho** xoay lỗ khóa 90°, sinh trắc học Android Keystore và PIN 6 chữ số
- Hỗ trợ **`AutofillService`** toàn hệ thống và **Passkey 1 chạm trên Android 14+**
- Tích hợp sẵn **mã 2FA (TOTP)** cùng **máy quét QR CameraX**
- Lưu đệm **Room mã hóa ngoại tuyến** với tự động kiểm tra đồng bộ nhẹ mỗi 25 giây
- Thang điểm **Watchtower**, biểu tượng tùy chỉnh E2EE, chặn chụp màn hình `FLAG_SECURE` và hỗ trợ Tiếng Việt / Tiếng Anh

</td>
<td width="50%" valign="top">

### Tiện ích Chrome

- Popup **Manifest V3** dạng Master-Detail rộng rãi (`Ctrl+Shift+Y`) và **Side Panel** cố định
- **Tự động điền tức thì** (`Ctrl+Shift+L`) cho Đăng nhập, Thẻ thanh toán, Danh tính, 2FA và Basic Auth
- **Mở khóa PIN / Master inline** và **gợi ý mật khẩu mạnh** ngay cạnh ô nhập liệu trên web
- Cầu nối **WebAuthn Passkey MAIN-world** đồng bộ mã hóa giữa Chrome và Android
- **Trình tạo mật khẩu trượt ngang** tạo chuỗi ngẫu nhiên hoặc cụm từ dễ nhớ
- Chấm điểm bảo mật **Watchtower** cục bộ và màn hình **PIN 3+3**
- Nhập dữ liệu 1 chạm từ **Proton Pass** (ZIP / JSON / CSV) và **Bitwarden JSON**, cùng sao lưu R2

</td>
</tr>
<tr>
<td width="50%" valign="top">

### Mã hóa Zero-Knowledge

- Xây dựng theo **Giao thức &amp; API chính thức của Bitwarden**
- Bộ test vector dùng chung tại [`spec/test-vectors.json`](../../spec/test-vectors.json) cho cả TypeScript và Kotlin
- Tạo khóa phía client bằng **Argon2id** (64 MiB, 3 vòng lặp, 4 luồng) hoặc **PBKDF2-SHA256** (600.000 vòng)
- Mã hóa xác thực Type 2 **AES-256-CBC + HMAC-SHA256** theo thời gian hằng (Encrypt-then-MAC)
- Mở rộng khóa RFC 5869 **HKDF-Expand** (`"enc"` / `"mac"`), **Passkey ES256 P-256** và **tự hủy PIN** sau tối đa 5 lần nhập sai

</td>
<td width="50%" valign="top">

### Backend tự host

- Chạy serverless gần như miễn phí trên **Cloudflare Workers** (Hono) + **D1** (SQLite)
- Tự động chụp **Snapshot R2 Zero-Knowledge** hằng ngày (`03:00 UTC`) kèm mã kiểm tra SHA-256 và tự dọn sau 30 ngày
- Khóa chế độ cá nhân một người dùng (`MAX_USERS=1`) và chặn đăng ký công khai (`ALLOW_SIGNUP=false`)
- Lưu băm mật khẩu server với muối ngẫu nhiên **PBKDF2-SHA256** + pepper, so sánh bằng `timingSafeEqual`
- JWT gắn với `security_stamp`, xoay vòng refresh token với **thu hồi toàn bộ chuỗi khi phát hiện đánh cắp**, và giới hạn tốc độ brute-force trên D1

</td>
</tr>
</table>

## Passkey &amp; Tự động điền

Tạo và lưu trữ **Passkey (WebAuthn / FIDO2)** trực tiếp trong kho mã hóa của riêng bạn mà không phụ thuộc bên thứ ba. Trên Chrome, script MAIN-world xử lý mượt mà `navigator.credentials`; trên Android 14+, `CredentialProviderService` cho phép đăng nhập chỉ với một chạm sau khi xác thực sinh trắc học. Khóa riêng tư của mỗi Passkey luôn được mã hóa ở thiết bị người dùng trước khi đồng bộ lên Cloudflare D1.

## Tải về &amp; Cài đặt

| Nền tảng | Phiên bản | Trạng thái |
|---|---|---|
| Android 9.0+ | `0.8.0` | Hỗ trợ (`io.openvault.app`) |
| Chrome / Edge / Brave (MV3) | `0.5.0` | Hỗ trợ (`extension/dist`) |
| Backend Cloudflare Workers | `0.1.0` | Hỗ trợ (`Workers + D1 + R2`) |

### Backend tự host

Yêu cầu Node.js `>= 22.18` và một tài khoản Cloudflare (gói Free đáp ứng Workers + D1; R2 miễn phí 10 GB cần có phương thức thanh toán trên tài khoản).

```bash
npm install --global cf
cf auth login

cd backend
# Tạo cơ sở dữ liệu D1 và bucket sao lưu R2 (đổi apac sang khu vực gần bạn nếu muốn)
cf d1 create --name openvault-db --primary-location-hint apac
cf r2 buckets create --name openvault-backups --location-hint apac

# Chạy migration cho D1 bằng UUID trả về từ lệnh `cf d1 create`
cf d1 migrations apply <YOUR_D1_DATABASE_UUID> --dir ./migrations

# Tạo file secrets tạm, triển khai Worker + secrets, rồi xóa ngay file tạm
node -e "const c=require('node:crypto'),fs=require('node:fs'); fs.writeFileSync('.secrets.tmp.json', JSON.stringify({ JWT_SIGNING_SECRET: c.randomBytes(48).toString('base64url'), PASSWORD_PEPPER: c.randomBytes(48).toString('base64url') }), { mode: 0o600 });"
cf deploy --secrets-file .secrets.tmp.json
rm .secrets.tmp.json
```

> **Mẹo:** Sau khi đăng ký tài khoản cá nhân của bạn, hãy đổi `ALLOW_SIGNUP: bindings.text("false")` trong [`backend/cloudflare.config.ts`](../../backend/cloudflare.config.ts) và chạy lại `cf deploy` để khóa đăng ký công khai vĩnh viễn.

### Tiện ích Chrome

```bash
npm install
npm run build:extension
```

1. Mở `chrome://extensions/` trên Chrome, Edge hoặc Brave.
2. Bật **Chế độ dành cho nhà phát triển** (Developer mode) ở góc trên phải.
3. Chọn **Tải tiện ích đã giải nén** (Load unpacked) và chọn thư mục `extension/dist`.
4. Ghim **OpenVault** lên thanh công cụ và kết nối tới địa chỉ Cloudflare Worker của bạn.

### Ứng dụng Android

Mở thư mục `android/` trong Android Studio hoặc biên dịch trực tiếp bản release APK bằng Gradle (JDK 17+):

```bash
cd android
./gradlew :app:assembleRelease
```

## Kiến trúc

```text
OpenVault/
├── spec/                    # Đặc tả mật mã tương thích Bitwarden & vector kiểm thử dùng chung
├── packages/crypto-core/    # Thư viện mật mã TypeScript dùng chung (Argon2id/PBKDF2, Type 2, TOTP, Passkey, Importers)
├── backend/                 # Cloudflare Workers (Hono) + D1 (SQLite) + R2 Daily Cron Backups
├── extension/               # Tiện ích Chrome (Manifest V3, React 19 + Tailwind CSS v4)
└── android/                 # Ứng dụng Android (Kotlin + Jetpack Compose + Room + Autofill + Passkeys)
```

## Bảo mật &amp; Quyền riêng tư

Mật khẩu chính (Master Password), khóa chính và dữ liệu giải mã **không bao giờ rời khỏi thiết bị của bạn**. Mọi dữ liệu kho đều được mã hóa cục bộ trước khi đồng bộ, và `backend/src/services/backup.ts` loại bỏ hoàn toàn password hash cũng như refresh token khỏi bản sao lưu R2 hằng ngày. Bộ nhớ tạm (clipboard) tự xóa sau thời gian cấu hình, và mở khóa PIN tự xóa khóa PIN cục bộ khi nhập sai quá giới hạn.

Trên Android, `allowBackup="false"` ngăn sao lưu dữ liệu qua đám mây hệ thống, `FLAG_SECURE` chặn chụp hoặc quay màn hình mặc định, `USE_BIOMETRIC` gắn chặt việc mở khóa với Android Keystore phần cứng, `CAMERA` chỉ dùng khi bạn chủ động quét mã QR 2FA, và `INTERNET` chỉ kết nối tới Cloudflare Worker do bạn làm chủ (cùng tùy chọn tải favicon trang web có thể tắt trong Cài đặt). Xem chi tiết mô hình bảo mật tại [`spec/crypto-spec.md`](../../spec/crypto-spec.md).

## Đóng góp

Mọi báo lỗi, ý tưởng cải tiến và Pull Request đều được hoan nghênh. Với các thay đổi lớn, vui lòng mở Issue thảo luận trước.

```bash
# Cài đặt thư viện và chạy toàn bộ kiểm tra TypeScript & unit test
npm install
npm run typecheck
npm test

# Chạy máy chủ Cloudflare Workers + D1 cục bộ
cp backend/.dev.vars.example backend/.dev.vars
cd backend
npx cf d1 migrations apply openvault-db --local
npx cf dev

# Chạy unit test & kiểm tra vector mật mã trên Android
cd android
./gradlew :app:testDebugUnitTest
```

## Giấy phép

OpenVault là phần mềm mã nguồn mở phát hành theo [Giấy phép MIT](../../LICENSE).

## Lịch sử Star

<a href="https://www.star-history.com/?repos=bryant-bui%2FOpenVault&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=bryant-bui/OpenVault&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=bryant-bui/OpenVault&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=bryant-bui/OpenVault&type=date&legend=top-left" />
 </picture>
</a>

import { extractVaultTextFromZip, parseUniversalVaultImport } from "@openvault/crypto-core";
import {
  Camera,
  CloudUpload,
  Download,
  Globe,
  Info,
  Lock,
  LogOut,
  Monitor,
  Moon,
  PanelRightOpen,
  Sun,
  Trash2,
  Upload,
  X,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { formatLocalTimestamp, getStrings, type AppLanguage } from "../../shared/i18n.js";
import { compressImageFileToBase64, normalizeAvatarDataUrl } from "../../shared/media.js";
import {
  applyColorScheme,
  getResolvedSystemScheme,
  readColorSchemePreference,
  subscribeColorSchemeSync,
  type ColorSchemePreference,
} from "../../shared/theme.js";
import type { ExtensionSettings, R2BackupItem } from "../../shared/types.js";
import { sendBg } from "../components/bridge.js";
import { ConfirmDialog } from "../components/ConfirmDialog.js";

interface SettingsScreenProps {
  email: string;
  lastSyncAt: string | null;
  settings: ExtensionSettings;
  pinEnabled: boolean;
  pinRequireMasterOnRestart: boolean;
  pinMaxAttempts: number;
  isPopup: boolean;
  onUpdateSettings: (partial: Partial<ExtensionSettings>) => Promise<void>;
  onPinUpdated: () => Promise<void>;
  onImported: () => Promise<void>;
  onLock: () => Promise<void>;
  onLogout: (allDevices: boolean) => Promise<void>;
  onOpenSidePanel: () => Promise<void>;
  onClose?: () => void;
  onToast: (text: string, tone?: "info" | "success" | "error") => void;
}

interface ToggleRowProps {
  title: string;
  description: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}

function ToggleRow({ title, description, checked, onChange }: ToggleRowProps) {
  return (
    <div className="flex items-start justify-between gap-3">
      <div className="min-w-0">
        <div className="text-[12.5px] font-medium leading-snug text-[var(--text-primary)]">
          {title}
        </div>
        <div className="text-[11px] text-[var(--text-muted)] leading-snug mt-0.5">
          {description}
        </div>
      </div>
      <label className="ov-switch shrink-0 mt-0.5">
        <input
          type="checkbox"
          checked={checked}
          onChange={(e) => onChange(e.target.checked)}
        />
        <span className="ov-switch-track" />
      </label>
    </div>
  );
}

export function SettingsScreen({
  email,
  lastSyncAt,
  settings,
  pinEnabled,
  pinRequireMasterOnRestart,
  pinMaxAttempts,
  isPopup,
  onUpdateSettings,
  onPinUpdated,
  onImported,
  onLock,
  onLogout,
  onOpenSidePanel,
  onClose,
  onToast,
}: SettingsScreenProps) {
  const lang: AppLanguage = settings.language ?? "en";
  const s = getStrings(lang);

  const [schemePref, setSchemePref] = useState<ColorSchemePreference>(() =>
    readColorSchemePreference(),
  );
  const [systemScheme] = useState<"light" | "dark">(() => getResolvedSystemScheme());
  const [r2Backups, setR2Backups] = useState<R2BackupItem[]>([]);
  const [backingUp, setBackingUp] = useState(false);
  const [importing, setImporting] = useState(false);
  const [confirmLogoutOpen, setConfirmLogoutOpen] = useState(false);
  const [showPinSetup, setShowPinSetup] = useState(false);
  const [pinValue, setPinValue] = useState("");
  const [pinConfirm, setPinConfirm] = useState("");
  const [requireMasterOnRestart, setRequireMasterOnRestart] = useState(pinRequireMasterOnRestart);
  const [savingPin, setSavingPin] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const avatarInputRef = useRef<HTMLInputElement>(null);

  const avatarDataUrl = normalizeAvatarDataUrl(settings.accountAvatarBase64);
  const accountInitial = (email.split("@")[0]?.[0] ?? "O").toUpperCase();

  const handleAvatarUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const b64 = await compressImageFileToBase64(file, 160, 0.84);
      await onUpdateSettings({ accountAvatarBase64: b64 });
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Could not update avatar", "error");
    } finally {
      if (avatarInputRef.current) avatarInputRef.current.value = "";
    }
  };

  const handleSavePin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (pinValue.length < 4) {
      onToast(s.isVi ? "Mã PIN phải dài ít nhất 4 ký tự." : "PIN must be at least 4 characters.", "error");
      return;
    }
    if (pinValue !== pinConfirm) {
      onToast(s.isVi ? "Mã PIN xác nhận không khớp." : "PINs do not match.", "error");
      return;
    }
    setSavingPin(true);
    try {
      await sendBg({
        type: "SET_PIN",
        pin: pinValue,
        requireMasterOnRestart,
        maxAttempts: settings.pinMaxAttempts,
      });
      setPinValue("");
      setPinConfirm("");
      setShowPinSetup(false);
      await onPinUpdated();
      onToast(
        s.isVi
          ? `Đã bật mở khóa bằng mã PIN (PBKDF2 600k · tối đa ${settings.pinMaxAttempts} lần thử)!`
          : `PIN unlock enabled (PBKDF2 600k · max ${settings.pinMaxAttempts} attempts)!`,
        "success",
      );
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Failed to save PIN", "error");
    } finally {
      setSavingPin(false);
    }
  };

  const handleDisablePin = async () => {
    try {
      await sendBg({ type: "DISABLE_PIN" });
      await onPinUpdated();
      onToast(s.isVi ? "Đã tắt mở khóa bằng mã PIN" : "PIN unlock disabled", "info");
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Failed to disable PIN", "error");
    }
  };

  useEffect(() => {
    return subscribeColorSchemeSync((next) => setSchemePref(next));
  }, []);

  useEffect(() => {
    void (async () => {
      try {
        const list = await sendBg<R2BackupItem[]>({ type: "LIST_R2_BACKUPS" });
        setR2Backups(list.slice(0, 5));
      } catch {
        // Offline or custom non-OpenVault server without R2 endpoint
      }
    })();
  }, []);

  const handleThemeChange = (pref: ColorSchemePreference) => {
    setSchemePref(pref);
    applyColorScheme(pref);
  };

  const handleR2Backup = async () => {
    setBackingUp(true);
    try {
      const res = await sendBg<{ cipherCount: number; bytes: number }>({
        type: "TRIGGER_R2_BACKUP",
      });
      onToast(
        s.isVi
          ? `Đã sao lưu ${res.cipherCount} mục (${Math.max(1, Math.round(res.bytes / 1024))} KB) lên Cloudflare R2!`
          : `Backed up ${res.cipherCount} items (${Math.max(1, Math.round(res.bytes / 1024))} KB) to Cloudflare R2!`,
        "success",
      );
      const list = await sendBg<R2BackupItem[]>({ type: "LIST_R2_BACKUPS" });
      setR2Backups(list.slice(0, 5));
    } catch (err) {
      onToast(err instanceof Error ? err.message : "R2 backup failed", "error");
    } finally {
      setBackingUp(false);
    }
  };

  const handleExportEncrypted = async () => {
    try {
      const jsonText = await sendBg<string>({ type: "EXPORT_ENCRYPTED_BACKUP" });
      const blob = new Blob([jsonText], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `openvault-encrypted-backup-${new Date().toISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(url);
      onToast(s.isVi ? "Đã tải xuống bản sao lưu mã hóa JSON" : "Downloaded encrypted JSON backup", "success");
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Export failed", "error");
    }
  };

  const handleImportFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setImporting(true);
    try {
      let rawText: string;
      const isZip = file.name.toLowerCase().endsWith(".zip");
      if (isZip) {
        const bytes = new Uint8Array(await file.arrayBuffer());
        const extracted = await extractVaultTextFromZip(bytes);
        rawText = extracted.text;
      } else {
        rawText = await file.text();
      }

      const preview = parseUniversalVaultImport(rawText);
      const itemCount = preview.exportData.items?.length ?? 0;
      if (itemCount === 0) {
        onToast(
          s.isVi ? "Không tìm thấy mục mật khẩu nào trong tệp đã chọn." : "No items found in selected file.",
          "error",
        );
        return;
      }

      const res = await sendBg<{
        format?: string;
        importedFolders: number;
        importedCiphers: number;
        skippedCount?: number;
      }>({
        type: "IMPORT_BITWARDEN_JSON",
        rawJson: rawText,
      });

      if (!res.format) {
        onToast(
          s.isVi
            ? "Service Worker đang dùng bản cũ — vui lòng vào chrome://extensions bấm ↻ Tải lại OpenVault!"
            : "Outdated service worker — please reload OpenVault in chrome://extensions!",
          "error",
        );
        return;
      }

      const sourceLabel =
        res.format === "proton-pass-json"
          ? isZip
            ? "Proton Pass (ZIP)"
            : "Proton Pass (JSON)"
          : res.format === "proton-pass-csv"
            ? "Proton Pass (CSV)"
            : "Bitwarden (JSON)";

      onToast(
        s.isVi
          ? `Đã mã hóa & nhập ${res.importedCiphers} mục (${res.importedFolders} thư mục) từ ${sourceLabel}!`
          : `Encrypted & imported ${res.importedCiphers} items (${res.importedFolders} folders) from ${sourceLabel}!`,
        "success",
      );
      await onImported();
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Invalid import file", "error");
    } finally {
      setImporting(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  return (
    <div className="flex-1 flex flex-col min-h-0 bg-[var(--bg)]">
      <header className="px-4 py-2.5 border-b border-[var(--border)] bg-[var(--surface)] flex items-center justify-between">
        <h2 className="text-[14px] font-bold m-0">{s.settingsTitle}</h2>
        {onClose ? (
          <button
            type="button"
            onClick={onClose}
            className="ov-icon-btn w-7 h-7"
            aria-label={s.close}
          >
            <X size={15} />
          </button>
        ) : null}
      </header>

      <div className="flex-1 overflow-y-auto ov-scroll p-3.5 flex flex-col gap-3">
        {/* Clean Account Card with Custom Avatar Picker & NO Server URL Clutter (#7 & #17) */}
        <div className="ov-card p-3 flex items-center gap-3">
          <input
            ref={avatarInputRef}
            type="file"
            accept="image/*"
            className="sr-only"
            onChange={(e) => {
              void handleAvatarUpload(e);
            }}
          />
          <button
            type="button"
            onClick={() => avatarInputRef.current?.click()}
            className="relative w-11 h-11 rounded-[12px] shrink-0 overflow-hidden border border-[var(--border)] p-0 cursor-pointer flex items-center justify-center font-bold text-white text-[16px]"
            style={{
              background: avatarDataUrl
                ? "var(--surface-subtle)"
                : "linear-gradient(135deg, #0A84FF, #0455BF)",
            }}
            title={s.changeAvatar}
          >
            {avatarDataUrl ? (
              <img src={avatarDataUrl} alt="" className="w-full h-full object-cover" />
            ) : (
              accountInitial
            )}
            <span className="absolute bottom-0 right-0 w-4 h-4 rounded-tl-[6px] bg-black/65 text-white flex items-center justify-center">
              <Camera size={9} />
            </span>
          </button>

          <div className="min-w-0 flex-1">
            <div className="text-[13px] font-bold truncate">{email}</div>
            <div className="text-[11px] text-[var(--text-muted)] mt-0.5 truncate">
              {s.autoSyncActive}
              {lastSyncAt ? ` · ${formatLocalTimestamp(lastSyncAt, lang)}` : ""}
            </div>
          </div>

          {avatarDataUrl ? (
            <button
              type="button"
              onClick={() => {
                void onUpdateSettings({ accountAvatarBase64: null });
              }}
              className="ov-icon-btn w-7 h-7 shrink-0 hover:text-[var(--danger)]"
              title={s.removeAvatar}
              aria-label={s.removeAvatar}
            >
              <Trash2 size={13} />
            </button>
          ) : null}
        </div>

        {/* Appearance & Language (#9) */}
        <div className="ov-card p-3 flex flex-col gap-3">
          <div className="text-[11px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
            {s.appearanceAndLang}
          </div>

          <div className="grid grid-cols-3 gap-1.5">
            {[
              {
                id: "system" as const,
                label: `${s.themeSystem} (${systemScheme === "dark" ? s.themeDark : s.themeLight})`,
                icon: Monitor,
              },
              { id: "light" as const, label: s.themeLight, icon: Sun },
              { id: "dark" as const, label: s.themeDark, icon: Moon },
            ].map((opt) => {
              const Icon = opt.icon;
              const active = schemePref === opt.id;
              return (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => handleThemeChange(opt.id)}
                  className={`py-2 px-1.5 rounded-[10px] border text-[11px] font-semibold flex flex-col items-center gap-1 cursor-pointer transition-colors ${
                    active
                      ? "bg-[var(--accent-soft)] text-[var(--accent)] border-[var(--accent-border)]"
                      : "bg-[var(--surface-subtle)] text-[var(--text-secondary)] border-transparent"
                  }`}
                >
                  <Icon size={14} />
                  <span className="truncate max-w-full">{opt.label}</span>
                </button>
              );
            })}
          </div>

          <div className="flex items-center justify-between gap-2 pt-2 border-t border-[var(--border)]">
            <span className="inline-flex items-center gap-1.5 text-[12.5px] font-medium">
              <Globe size={13} className="text-[var(--text-muted)]" />
              <span>{s.languageLabel}</span>
            </span>
            <div className="inline-flex rounded-[8px] p-0.5 bg-[var(--surface-subtle)] border border-[var(--border)]">
              {(
                [
                  { id: "en", label: "English" },
                  { id: "vi", label: "Tiếng Việt" },
                ] as const
              ).map((opt) => (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => {
                    void onUpdateSettings({ language: opt.id });
                  }}
                  className={`px-2.5 py-1 rounded-[6px] border-0 text-[11.5px] font-semibold cursor-pointer transition-colors ${
                    lang === opt.id
                      ? "bg-[var(--accent)] text-white"
                      : "bg-transparent text-[var(--text-secondary)]"
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Autofill */}
        <div className="ov-card p-3 flex flex-col gap-3">
          <div className="text-[11px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
            {s.autofillSection}
          </div>

          <ToggleRow
            title={s.isVi ? "Tự điền đăng nhập" : "Autofill logins"}
            description={s.isVi ? "Hiện gợi ý điền nhanh tên đăng nhập & mật khẩu." : "Suggest and fill saved usernames and passwords."}
            checked={settings.showInlineAutofill}
            onChange={(v) => {
              void onUpdateSettings({ showInlineAutofill: v });
            }}
          />
          <ToggleRow
            title={s.isVi ? "Tự điền danh tính" : "Autofill identities"}
            description={s.isVi ? "Điền nhanh họ tên, email, điện thoại và địa chỉ." : "Fill saved names, emails, phones, and addresses."}
            checked={settings.autofillIdentity}
            onChange={(v) => {
              void onUpdateSettings({ autofillIdentity: v });
            }}
          />
          <ToggleRow
            title={s.isVi ? "Tự điền 2FA (TOTP)" : "Autofill 2FA codes"}
            description={s.isVi ? "Nhận diện ô OTP (kể cả 6 ô rời) để điền mã 2FA." : "Detect OTP inputs (including split 6-digit boxes)."}
            checked={settings.autofillTotp}
            onChange={(v) => {
              void onUpdateSettings({ autofillTotp: v });
            }}
          />
          <ToggleRow
            title={s.isVi ? "Tự chép mã 2FA sau khi điền" : "Auto-copy 2FA code"}
            description={s.isVi ? "Sao chép mã TOTP vào bảng tạm sau khi điền đăng nhập." : "Copy TOTP to clipboard right after filling a login."}
            checked={settings.autoCopyTotp}
            onChange={(v) => {
              void onUpdateSettings({ autoCopyTotp: v });
            }}
          />
          <ToggleRow
            title={s.isVi ? "Tự điền thẻ tín dụng" : "Autofill credit cards"}
            description={s.isVi ? "Điền nhanh số thẻ, hạn dùng và CVV khi thanh toán." : "Fill card number, expiry, and CVV at checkout."}
            checked={settings.autofillCard}
            onChange={(v) => {
              void onUpdateSettings({ autofillCard: v });
            }}
          />
          <ToggleRow
            title={s.isVi ? "Tự điền HTTP Basic Auth" : "Autofill HTTP Basic Auth"}
            description={s.isVi ? "Tự điền hộp thoại đăng nhập hệ thống của trình duyệt." : "Automatically fill browser Basic Auth prompts."}
            checked={settings.autofillBasicAuth}
            onChange={(v) => {
              void onUpdateSettings({ autofillBasicAuth: v });
            }}
          />

          <div className="pt-2 border-t border-[var(--border)] flex flex-col gap-3">
            <ToggleRow
              title={s.isVi ? "Hỏi lưu tài khoản mới" : "Prompt to save new logins"}
              description={s.isVi ? "Gợi ý lưu nhanh khi bạn đăng nhập tài khoản mới." : "Offer to save credentials when signing into new sites."}
              checked={settings.promptSaveCredentials}
              onChange={(v) => {
                void onUpdateSettings({ promptSaveCredentials: v });
              }}
            />
            <ToggleRow
              title={s.isVi ? "Hiển thị biểu tượng website" : "Show website icons"}
              description={s.isVi ? "Tự tải favicon rõ nét cho các trang web đã lưu." : "Fetch crisp favicons for saved websites."}
              checked={settings.showFavicons}
              onChange={(v) => {
                void onUpdateSettings({ showFavicons: v });
              }}
            />
          </div>
        </div>

        {/* Autosuggest & Passkeys */}
        <div className="ov-card p-3 flex flex-col gap-3">
          <div className="text-[11px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
            {s.passkeysSection} &amp; {s.autosuggestSection}
          </div>

          <ToggleRow
            title={s.isVi ? "Lưu mã khóa (Passkey)" : "Save passkeys"}
            description={s.isVi ? "Tạo & đồng bộ mã khóa ECDSA P-256 sang Android." : "Create & sync ECDSA P-256 passkeys across devices."}
            checked={settings.passkeySaveEnabled}
            onChange={(v) => {
              void onUpdateSettings({ passkeySaveEnabled: v });
            }}
          />
          <ToggleRow
            title={s.isVi ? "Đăng nhập bằng Passkey" : "Authenticate with passkeys"}
            description={s.isVi ? "Gợi ý xác thực không mật khẩu bằng Passkey đã lưu." : "Sign in passwordlessly with stored passkeys."}
            checked={settings.passkeyAuthEnabled}
            onChange={(v) => {
              void onUpdateSettings({ passkeyAuthEnabled: v });
            }}
          />
          <ToggleRow
            title={s.isVi ? "Gợi ý mật khẩu mạnh" : "Suggest strong passwords"}
            description={s.isVi ? "Gợi ý tạo mật khẩu 20 ký tự trên ô mật khẩu mới." : "Suggest 20-char random passwords on signup forms."}
            checked={settings.autosuggestPassword}
            onChange={(v) => {
              void onUpdateSettings({ autosuggestPassword: v });
            }}
          />
          <ToggleRow
            title={s.isVi ? "Tự chép mật khẩu vừa tạo" : "Auto-copy generated passwords"}
            description={s.isVi ? "Tự chép mật khẩu mới vào bảng tạm." : "Automatically copy generated passwords to clipboard."}
            checked={settings.autoCopyGeneratedPassword}
            onChange={(v) => {
              void onUpdateSettings({ autoCopyGeneratedPassword: v });
            }}
          />
          <ToggleRow
            title={s.isVi ? "Đặt làm trình quản lý mật khẩu mặc định" : "Default browser password manager"}
            description={s.isVi ? "Tắt popup lưu mật khẩu tích hợp của Chrome." : "Disable Chrome's built-in password prompts to avoid overlap."}
            checked={settings.defaultBrowserPasswordManager}
            onChange={(v) => {
              void onUpdateSettings({ defaultBrowserPasswordManager: v });
            }}
          />
        </div>

        {/* Security & Lock */}
        <div className="ov-card p-3 flex flex-col gap-3">
          <div className="text-[11px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
            {s.securitySection}
          </div>

          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between gap-2">
              <div>
                <div className="text-[12.5px] font-semibold">
                  {s.isVi ? "Mở khóa nhanh bằng mã PIN" : "Quick PIN unlock"}
                </div>
                <div className="text-[11px] text-[var(--text-muted)]">
                  {pinEnabled
                    ? s.isVi
                      ? `${pinRequireMasterOnRestart ? "Hỏi Master Password khi mở lại Chrome" : "Dùng ngay khi mở lại Chrome"} · Tối đa ${pinMaxAttempts} lần sai`
                      : `${pinRequireMasterOnRestart ? "Requires Master Password on restart" : "Available on browser restart"} · Max ${pinMaxAttempts} attempts`
                    : s.isVi
                      ? `PBKDF2 600k · Tự hủy sau ${settings.pinMaxAttempts} lần sai`
                      : `PBKDF2 600k · Wipes after ${settings.pinMaxAttempts} failed attempts`}
                </div>
              </div>
              <div className="flex items-center gap-1.5 shrink-0">
                <button
                  type="button"
                  onClick={() => setShowPinSetup((v) => !v)}
                  className="ov-btn-ghost px-2.5 py-1 text-[11.5px]"
                >
                  {pinEnabled ? (s.isVi ? "Đổi PIN" : "Change") : (s.isVi ? "Thiết lập" : "Set PIN")}
                </button>
                {pinEnabled ? (
                  <button
                    type="button"
                    onClick={() => {
                      void handleDisablePin();
                    }}
                    className="px-2.5 py-1 rounded-[8px] border border-[var(--danger)] text-[var(--danger)] bg-transparent text-[11.5px] font-semibold cursor-pointer"
                  >
                    {s.isVi ? "Tắt" : "Off"}
                  </button>
                ) : null}
              </div>
            </div>

            {showPinSetup ? (
              <form
                onSubmit={(e) => {
                  void handleSavePin(e);
                }}
                className="p-2.5 rounded-[10px] bg-[var(--surface-subtle)] flex flex-col gap-2"
              >
                <div className="grid grid-cols-2 gap-2">
                  <input
                    type="password"
                    inputMode="numeric"
                    className="ov-input font-mono-code text-[12.5px]"
                    placeholder={s.isVi ? "Mã PIN (6 số)" : "PIN (6 digits)"}
                    value={pinValue}
                    onChange={(e) => setPinValue(e.target.value)}
                    required
                  />
                  <input
                    type="password"
                    inputMode="numeric"
                    className="ov-input font-mono-code text-[12.5px]"
                    placeholder={s.isVi ? "Nhập lại PIN" : "Confirm PIN"}
                    value={pinConfirm}
                    onChange={(e) => setPinConfirm(e.target.value)}
                    required
                  />
                </div>
                <label className="flex items-center justify-between text-[11.5px] text-[var(--text-secondary)] cursor-pointer">
                  <span>
                    {s.isVi
                      ? "Yêu cầu Master Password khi khởi động lại Chrome"
                      : "Require Master Password after Chrome restarts"}
                  </span>
                  <input
                    type="checkbox"
                    checked={requireMasterOnRestart}
                    onChange={(e) => setRequireMasterOnRestart(e.target.checked)}
                  />
                </label>
                <div className="flex justify-end gap-1.5">
                  <button
                    type="button"
                    onClick={() => setShowPinSetup(false)}
                    className="ov-btn-ghost px-2.5 py-1 text-[11.5px]"
                  >
                    {s.cancel}
                  </button>
                  <button
                    type="submit"
                    disabled={savingPin}
                    className="ov-btn-primary px-3 py-1 text-[11.5px]"
                  >
                    {savingPin ? s.saving : s.save}
                  </button>
                </div>
              </form>
            ) : null}
          </div>

          <div className="pt-2.5 border-t border-[var(--border)] flex flex-col gap-2.5">
            <div className="flex items-center justify-between gap-2">
              <label htmlFor="pin-attempts-select" className="text-[12.5px] font-medium">
                {s.isVi ? "Tự hủy PIN khi nhập sai" : "Wipe PIN after failed attempts"}
              </label>
              <select
                id="pin-attempts-select"
                value={settings.pinMaxAttempts}
                onChange={(e) => {
                  const nextAttempts = Number(e.target.value);
                  void (async () => {
                    await onUpdateSettings({ pinMaxAttempts: nextAttempts });
                    await onPinUpdated();
                  })();
                }}
                className="ov-input w-auto py-1 text-[12px]"
              >
                <option value={3}>3</option>
                <option value={5}>5</option>
              </select>
            </div>

            <div className="flex items-center justify-between gap-2">
              <label htmlFor="autolock-select" className="text-[12.5px] font-medium">
                {s.isVi ? "Tự động khóa sau" : "Auto-lock after"}
              </label>
              <select
                id="autolock-select"
                value={settings.autoLockMinutes}
                onChange={(e) => {
                  void onUpdateSettings({ autoLockMinutes: Number(e.target.value) });
                }}
                className="ov-input w-auto py-1 text-[12px]"
              >
                <option value={1}>1 min</option>
                <option value={5}>5 min</option>
                <option value={10}>10 min</option>
                <option value={15}>15 min</option>
                <option value={30}>30 min</option>
                <option value={60}>1 hour</option>
                <option value={0}>{s.isVi ? "Khi đóng Chrome" : "Browser restart"}</option>
              </select>
            </div>

            <div className="flex items-center justify-between gap-2">
              <label htmlFor="clipboard-clear-select" className="text-[12.5px] font-medium">
                {s.isVi ? "Xóa bảng tạm sau" : "Clear clipboard after"}
              </label>
              <select
                id="clipboard-clear-select"
                value={settings.clipboardClearSeconds}
                onChange={(e) => {
                  void onUpdateSettings({ clipboardClearSeconds: Number(e.target.value) });
                }}
                className="ov-input w-auto py-1 text-[12px]"
              >
                <option value={15}>15s</option>
                <option value={30}>30s</option>
                <option value={60}>60s</option>
                <option value={120}>2 min</option>
                <option value={0}>{s.isVi ? "Tắt" : "Never"}</option>
              </select>
            </div>
          </div>
        </div>

        {/* Backup & Migration */}
        <div className="ov-card p-3 flex flex-col gap-2.5">
          <div className="text-[11px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
            {s.backupSection}
          </div>

          <button
            type="button"
            disabled={backingUp}
            onClick={() => {
              void handleR2Backup();
            }}
            className="ov-btn-primary w-full py-2 inline-flex items-center justify-center gap-1.5 text-[12.5px]"
          >
            <CloudUpload size={14} />
            <span>
              {backingUp
                ? s.isVi
                  ? "Đang tạo snapshot R2..."
                  : "Creating R2 snapshot..."
                : s.isVi
                  ? "Sao lưu mã hóa lên Cloudflare R2 ngay"
                  : "Back up encrypted vault to R2 now"}
            </span>
          </button>

          {r2Backups.length > 0 ? (
            <div className="rounded-[10px] bg-[var(--surface-subtle)] p-2 flex flex-col gap-1.5">
              <div className="text-[10.5px] font-semibold text-[var(--text-muted)]">
                {s.isVi
                  ? "Bản sao lưu R2 gần nhất (tự động 03:00 UTC)"
                  : "Recent R2 Snapshots (daily at 03:00 UTC)"}
              </div>
              {r2Backups.map((b) => (
                <div
                  key={b.key}
                  className="flex items-center justify-between text-[11px] text-[var(--text-secondary)]"
                >
                  <span>{formatLocalTimestamp(b.uploaded, lang)}</span>
                  <span className="font-mono-code text-[10.5px]">
                    {b.cipherCount ?? "?"} · {Math.max(1, Math.round(b.size / 1024))} KB
                  </span>
                </div>
              ))}
            </div>
          ) : null}

          <div className="grid grid-cols-2 gap-2 pt-1">
            <input
              ref={fileInputRef}
              type="file"
              accept=".json,.csv,.zip,application/json,text/csv,application/zip"
              className="sr-only"
              onChange={(e) => {
                void handleImportFile(e);
              }}
            />
            <button
              type="button"
              disabled={importing}
              onClick={() => fileInputRef.current?.click()}
              className="ov-btn-ghost py-2 inline-flex items-center justify-center gap-1.5 text-[12px]"
            >
              <Upload size={13} />
              <span>{importing ? (s.isVi ? "Đang nhập..." : "Importing...") : "Import Proton / BW"}</span>
            </button>
            <button
              type="button"
              onClick={() => {
                void handleExportEncrypted();
              }}
              className="ov-btn-ghost py-2 inline-flex items-center justify-center gap-1.5 text-[12px]"
            >
              <Download size={13} />
              <span>{s.isVi ? "Xuất JSON mã hóa" : "Export Encrypted"}</span>
            </button>
          </div>
        </div>

        {/* About OpenVault (#10) */}
        <div className="ov-card p-3 flex items-center justify-between gap-3">
          <div className="flex items-center gap-2.5 min-w-0">
            <img src="/icons/icon-48.png" alt="" className="w-8 h-8 rounded-[9px] shrink-0" />
            <div className="min-w-0">
              <div className="text-[12.5px] font-bold flex items-center gap-1.5">
                <span>OpenVault for Chrome</span>
                <span className="text-[10.5px] font-semibold px-1.5 py-0.5 rounded-full bg-[var(--accent-soft)] text-[var(--accent)]">
                  v0.5.0
                </span>
              </div>
              <div className="text-[11px] text-[var(--text-muted)] truncate">
                Zero-Knowledge · AES-256-CBC + HMAC · Argon2id · Passkeys
              </div>
            </div>
          </div>
          <Info size={15} className="text-[var(--text-muted)] shrink-0" />
        </div>

        {/* Actions */}
        <div className="flex flex-col gap-2 pb-2">
          {isPopup ? (
            <button
              type="button"
              onClick={() => {
                void onOpenSidePanel();
              }}
              className="ov-btn-ghost w-full py-2 inline-flex items-center justify-center gap-1.5"
            >
              <PanelRightOpen size={14} />
              <span>{s.openSidePanel}</span>
            </button>
          ) : null}

          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={() => {
                void onLock();
              }}
              className="ov-btn-ghost py-2 inline-flex items-center justify-center gap-1.5"
            >
              <Lock size={14} />
              <span>{s.lockNow}</span>
            </button>
            <button
              type="button"
              onClick={() => setConfirmLogoutOpen(true)}
              className="py-2 rounded-[10px] border border-[var(--danger)] text-[var(--danger)] bg-transparent font-semibold text-[12.5px] inline-flex items-center justify-center gap-1.5 cursor-pointer hover:bg-[var(--danger-soft)]"
            >
              <LogOut size={14} />
              <span>{s.signOut}</span>
            </button>
          </div>
        </div>
      </div>

      <ConfirmDialog
        open={confirmLogoutOpen}
        title={s.signOutTitle}
        description={s.signOutDesc}
        confirmLabel={s.signOut}
        onConfirm={() => {
          setConfirmLogoutOpen(false);
          void onLogout(false);
        }}
        onCancel={() => setConfirmLogoutOpen(false)}
      />
    </div>
  );
}

import { extractVaultTextFromZip, parseUniversalVaultImport } from "@openvault/crypto-core";
import {
  Camera,
  CloudUpload,
  Database,
  Download,
  Globe,
  Lock,
  LogOut,
  Monitor,
  Moon,
  Shield,
  SlidersHorizontal,
  Sparkles,
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

type SettingsTab = "general" | "autofill" | "security" | "data";

interface SettingsScreenProps {
  email: string;
  lastSyncAt: string | null;
  settings: ExtensionSettings;
  pinEnabled: boolean;
  pinRequireMasterOnRestart: boolean;
  pinMaxAttempts: number;
  onUpdateSettings: (partial: Partial<ExtensionSettings>) => Promise<void>;
  onAvatarUpdated: (settings: ExtensionSettings) => void;
  onPinUpdated: () => Promise<void>;
  onImported: () => Promise<void>;
  onLock: () => Promise<void>;
  onLogout: (allDevices: boolean) => Promise<void>;
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
    <div className="flex items-center justify-between gap-3 py-1">
      <div className="min-w-0">
        <div className="text-[13.5px] font-semibold leading-snug text-[var(--text-primary)]">
          {title}
        </div>
        <div className="text-[12px] text-[var(--text-muted)] leading-snug mt-0.5">
          {description}
        </div>
      </div>
      <label className="ov-switch shrink-0">
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
  onUpdateSettings,
  onAvatarUpdated,
  onPinUpdated,
  onImported,
  onLock,
  onLogout,
  onClose,
  onToast,
}: SettingsScreenProps) {
  const lang: AppLanguage = settings.language ?? "en";
  const s = getStrings(lang);

  const [tab, setTab] = useState<SettingsTab>("general");
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
      const b64 = await compressImageFileToBase64(file, 144, 0.8);
      const next = await sendBg<ExtensionSettings>({
        type: "SET_ACCOUNT_AVATAR",
        avatarBase64: b64,
      });
      onAvatarUpdated(next);
      onToast(s.isVi ? "Đã cập nhật & đồng bộ ảnh đại diện" : "Avatar updated & synced", "success");
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Could not update avatar", "error");
    } finally {
      if (avatarInputRef.current) avatarInputRef.current.value = "";
    }
  };

  const handleRemoveAvatar = async () => {
    try {
      const next = await sendBg<ExtensionSettings>({
        type: "SET_ACCOUNT_AVATAR",
        avatarBase64: null,
      });
      onAvatarUpdated(next);
      onToast(s.isVi ? "Đã xóa ảnh đại diện" : "Avatar removed", "info");
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Could not remove avatar", "error");
    }
  };

  const handleSavePin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (pinValue.length < 4) {
      onToast(
        s.isVi ? "Mã PIN phải dài ít nhất 4 ký tự." : "PIN must be at least 4 characters.",
        "error",
      );
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
        s.isVi ? "Đã bật mở khóa bằng mã PIN!" : "PIN unlock enabled!",
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
        setR2Backups(list.slice(0, 4));
      } catch {
        // Offline or non-R2 server
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
          ? `Đã sao lưu ${res.cipherCount} mục lên Cloudflare R2!`
          : `Backed up ${res.cipherCount} items to Cloudflare R2!`,
        "success",
      );
      const list = await sendBg<R2BackupItem[]>({ type: "LIST_R2_BACKUPS" });
      setR2Backups(list.slice(0, 4));
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
      onToast(
        s.isVi ? "Đã tải xuống bản sao lưu mã hóa JSON" : "Downloaded encrypted JSON backup",
        "success",
      );
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
          s.isVi
            ? "Không tìm thấy mục mật khẩu nào trong tệp đã chọn."
            : "No items found in selected file.",
          "error",
        );
        return;
      }

      const res = await sendBg<{
        format?: string;
        importedFolders: number;
        importedCiphers: number;
      }>({
        type: "IMPORT_BITWARDEN_JSON",
        rawJson: rawText,
      });

      onToast(
        s.isVi
          ? `Đã mã hóa & nhập ${res.importedCiphers} mục (${res.importedFolders} thư mục)!`
          : `Encrypted & imported ${res.importedCiphers} items (${res.importedFolders} folders)!`,
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

  const tabs: Array<{ id: SettingsTab; label: string; icon: typeof SlidersHorizontal }> = [
    { id: "general", label: s.settingsTabGeneral, icon: SlidersHorizontal },
    { id: "autofill", label: s.settingsTabAutofill, icon: Sparkles },
    { id: "security", label: s.settingsTabSecurity, icon: Shield },
    { id: "data", label: s.settingsTabData, icon: Database },
  ];

  return (
    <div className="flex-1 flex flex-col min-h-0 bg-[var(--bg)]">
      {/* Clean Top Bar */}
      <header className="h-[46px] shrink-0 px-4 border-b border-[var(--border)] bg-[var(--surface)] flex items-center justify-between">
        <h2 className="text-[16px] font-bold m-0">{s.settingsTitle}</h2>
        {onClose ? (
          <button
            type="button"
            onClick={onClose}
            className="ov-icon-btn w-8 h-8"
            aria-label={s.close}
          >
            <X size={17} />
          </button>
        ) : null}
      </header>

      {/* Clean 4-Tab Segmented Switch */}
      <div className="px-4 py-2.5 border-b border-[var(--border)] bg-[var(--surface)] shrink-0">
        <div className="grid grid-cols-4 gap-1.5 p-1 rounded-[11px] bg-[var(--surface-subtle)] border border-[var(--border)]">
          {tabs.map((item) => {
            const Icon = item.icon;
            const active = tab === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => setTab(item.id)}
                className={`py-1.5 px-2 rounded-[8px] border-0 text-[12.5px] font-semibold inline-flex items-center justify-center gap-1.5 cursor-pointer transition-all ${
                  active
                    ? "bg-[var(--surface)] text-[var(--accent)] shadow-xs"
                    : "bg-transparent text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
                }`}
              >
                <Icon size={14} strokeWidth={2} />
                <span className="truncate">{item.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto ov-scroll p-4">
        <div className="flex flex-col gap-3.5 pb-4">
          {tab === "general" ? (
            <>
              {/* Account + Synced Avatar */}
              <div className="ov-card p-3.5 flex items-center gap-3.5">
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
                  className="relative w-12 h-12 rounded-[13px] shrink-0 overflow-hidden border border-[var(--border)] p-0 cursor-pointer flex items-center justify-center font-bold text-white text-[18px]"
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
                  <span className="absolute bottom-0 right-0 w-4.5 h-4.5 rounded-tl-[6px] bg-black/65 text-white flex items-center justify-center">
                    <Camera size={10} />
                  </span>
                </button>

                <div className="min-w-0 flex-1">
                  <div className="text-[14.5px] font-bold truncate">{email}</div>
                  <div className="text-[12px] text-[var(--text-muted)] mt-0.5 truncate">
                    {s.autoSyncActive}
                    {lastSyncAt ? ` · ${formatLocalTimestamp(lastSyncAt, lang)}` : ""}
                  </div>
                </div>

                {avatarDataUrl ? (
                  <button
                    type="button"
                    onClick={() => {
                      void handleRemoveAvatar();
                    }}
                    className="ov-icon-btn w-8 h-8 shrink-0 hover:text-[var(--danger)]"
                    title={s.removeAvatar}
                    aria-label={s.removeAvatar}
                  >
                    <Trash2 size={15} />
                  </button>
                ) : null}
              </div>

              {/* Theme & Language */}
              <div className="ov-card p-3.5 flex flex-col gap-3">
                <div className="text-[12px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
                  {s.appearanceAndLang}
                </div>

                <div className="grid grid-cols-3 gap-2">
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
                        className={`py-2.5 px-2 rounded-[10px] border text-[12.5px] font-semibold flex flex-col items-center gap-1.5 cursor-pointer transition-colors ${
                          active
                            ? "bg-[var(--accent-soft)] text-[var(--accent)] border-[var(--accent-border)]"
                            : "bg-[var(--surface-subtle)] text-[var(--text-secondary)] border-transparent"
                        }`}
                      >
                        <Icon size={16} />
                        <span className="truncate max-w-full">{opt.label}</span>
                      </button>
                    );
                  })}
                </div>

                <div className="flex items-center justify-between gap-2 pt-2.5 border-t border-[var(--border)]">
                  <span className="inline-flex items-center gap-2 text-[13.5px] font-semibold">
                    <Globe size={15} className="text-[var(--text-muted)]" />
                    <span>{s.languageLabel}</span>
                  </span>
                  <div className="inline-flex rounded-[9px] p-0.5 bg-[var(--surface-subtle)] border border-[var(--border)]">
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
                        className={`px-3 py-1 rounded-[7px] border-0 text-[12.5px] font-semibold cursor-pointer transition-colors ${
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

                <div className="pt-2 border-t border-[var(--border)]">
                  <ToggleRow
                    title={s.isVi ? "Hiển Thị Biểu Tượng Website" : "Show Website Icons"}
                    description={s.isVi ? "Tải favicon rõ nét cho trang web đã lưu." : "Fetch crisp favicons for saved websites."}
                    checked={settings.showFavicons}
                    onChange={(v) => {
                      void onUpdateSettings({ showFavicons: v });
                    }}
                  />
                </div>
              </div>

              {/* Quick Actions */}
              <div className="grid grid-cols-2 gap-2.5">
                <button
                  type="button"
                  onClick={() => {
                    void onLock();
                  }}
                  className="ov-btn-ghost py-2.5 inline-flex items-center justify-center gap-2"
                >
                  <Lock size={15} />
                  <span>{s.lockNow}</span>
                </button>
                <button
                  type="button"
                  onClick={() => setConfirmLogoutOpen(true)}
                  className="py-2.5 rounded-[9px] border border-[var(--danger)] text-[var(--danger)] bg-transparent font-semibold text-[13.5px] inline-flex items-center justify-center gap-2 cursor-pointer hover:bg-[var(--danger-soft)]"
                >
                  <LogOut size={15} />
                  <span>{s.signOut}</span>
                </button>
              </div>
            </>
          ) : null}

          {tab === "autofill" ? (
            <>
              <div className="ov-card p-3.5 flex flex-col gap-2.5 divide-y divide-[var(--border)]">
                <ToggleRow
                  title={s.isVi ? "Tự Điền Đăng Nhập" : "Autofill Logins"}
                  description={s.isVi ? "Hiện biểu tượng điền nhanh tên đăng nhập & mật khẩu." : "Show quick autofill icon on login fields."}
                  checked={settings.showInlineAutofill}
                  onChange={(v) => {
                    void onUpdateSettings({ showInlineAutofill: v });
                  }}
                />
                <div className="pt-2.5">
                  <ToggleRow
                    title={s.isVi ? "Hỏi Lưu / Cập Nhật Mật Khẩu" : "Prompt To Save / Update Logins"}
                    description={s.isVi ? "Tự phát hiện đăng nhập mới hoặc đổi mật khẩu." : "Detect new logins or password changes."}
                    checked={settings.promptSaveCredentials}
                    onChange={(v) => {
                      void onUpdateSettings({ promptSaveCredentials: v });
                    }}
                  />
                </div>
                <div className="pt-2.5">
                  <ToggleRow
                    title={s.isVi ? "Tự Điền 2FA (TOTP) & Sao Chép Nhanh" : "Autofill & Auto-Copy 2FA"}
                    description={s.isVi ? "Điền ô OTP và sao chép mã 2FA sau khi đăng nhập." : "Fill OTP inputs and copy 2FA code after login."}
                    checked={settings.autofillTotp && settings.autoCopyTotp}
                    onChange={(v) => {
                      void onUpdateSettings({ autofillTotp: v, autoCopyTotp: v });
                    }}
                  />
                </div>
                <div className="pt-2.5">
                  <ToggleRow
                    title={s.isVi ? "Thẻ Tín Dụng & Danh Tính" : "Credit Cards & Identities"}
                    description={s.isVi ? "Gợi ý điền thẻ thanh toán và biểu mẫu cá nhân." : "Fill saved payment cards and personal details."}
                    checked={settings.autofillCard && settings.autofillIdentity}
                    onChange={(v) => {
                      void onUpdateSettings({ autofillCard: v, autofillIdentity: v });
                    }}
                  />
                </div>
                <div className="pt-2.5">
                  <ToggleRow
                    title={s.isVi ? "Passkeys (WebAuthn)" : "Passkeys (WebAuthn)"}
                    description={s.isVi ? "Tạo và đăng nhập bằng Passkey đồng bộ đa thiết bị." : "Create and sign in with cross-platform Passkeys."}
                    checked={settings.passkeySaveEnabled && settings.passkeyAuthEnabled}
                    onChange={(v) => {
                      void onUpdateSettings({ passkeySaveEnabled: v, passkeyAuthEnabled: v });
                    }}
                  />
                </div>
                <div className="pt-2.5">
                  <ToggleRow
                    title={s.isVi ? "Gợi Ý Mật Khẩu Mạnh" : "Suggest Strong Passwords"}
                    description={s.isVi ? "Đề xuất mật khẩu 20 ký tự ở biểu mẫu đăng ký." : "Suggest 20-char random passwords on sign-up."}
                    checked={settings.autosuggestPassword}
                    onChange={(v) => {
                      void onUpdateSettings({ autosuggestPassword: v });
                    }}
                  />
                </div>
                <div className="pt-2.5">
                  <ToggleRow
                    title={s.isVi ? "Đặt Làm Trình Quản Lý Mặc Định" : "Default Browser Password Manager"}
                    description={s.isVi ? "Tắt popup lưu mật khẩu tích hợp của Chrome." : "Silence Chrome's built-in password prompts."}
                    checked={settings.defaultBrowserPasswordManager}
                    onChange={(v) => {
                      void onUpdateSettings({ defaultBrowserPasswordManager: v });
                    }}
                  />
                </div>
              </div>
            </>
          ) : null}

          {tab === "security" ? (
            <div className="ov-card p-3.5 flex flex-col gap-3">
              <div className="flex items-center justify-between gap-2">
                <div>
                  <div className="text-[14px] font-semibold">
                    {s.isVi ? "Mở Khóa Nhanh Bằng Mã PIN" : "Quick PIN Unlock"}
                  </div>
                  <div className="text-[12px] text-[var(--text-muted)]">
                    {pinEnabled
                      ? `${pinMaxAttempts} ${s.isVi ? "lần thử tối đa" : "max attempts"} · PBKDF2-600k`
                      : s.isVi
                        ? `Tự hủy sau ${settings.pinMaxAttempts} lần nhập sai`
                        : `Wipes after ${settings.pinMaxAttempts} failed attempts`}
                  </div>
                </div>
                <div className="flex items-center gap-1.5 shrink-0">
                  <button
                    type="button"
                    onClick={() => setShowPinSetup((v) => !v)}
                    className="ov-btn-ghost px-3 py-1.5 text-[12.5px]"
                  >
                    {pinEnabled ? (s.isVi ? "Đổi PIN" : "Change") : s.isVi ? "Thiết Lập" : "Set PIN"}
                  </button>
                  {pinEnabled ? (
                    <button
                      type="button"
                      onClick={() => {
                        void handleDisablePin();
                      }}
                      className="px-3 py-1.5 rounded-[8px] border border-[var(--danger)] text-[var(--danger)] bg-transparent text-[12.5px] font-semibold cursor-pointer"
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
                  className="p-3 rounded-[10px] bg-[var(--surface-subtle)] flex flex-col gap-2.5"
                >
                  <div className="grid grid-cols-2 gap-2">
                    <input
                      type="password"
                      inputMode="numeric"
                      className="ov-input font-mono-code text-[13.5px]"
                      placeholder={s.isVi ? "Mã PIN (6 Số)" : "PIN (6 Digits)"}
                      value={pinValue}
                      onChange={(e) => setPinValue(e.target.value)}
                      required
                    />
                    <input
                      type="password"
                      inputMode="numeric"
                      className="ov-input font-mono-code text-[13.5px]"
                      placeholder={s.isVi ? "Nhập Lại PIN" : "Confirm PIN"}
                      value={pinConfirm}
                      onChange={(e) => setPinConfirm(e.target.value)}
                      required
                    />
                  </div>
                  <label className="flex items-center justify-between text-[12.5px] text-[var(--text-secondary)] cursor-pointer">
                    <span>
                      {s.isVi
                        ? "Yêu cầu Master Password khi khởi động lại Chrome"
                        : "Require Master Password on Chrome restart"}
                    </span>
                    <input
                      type="checkbox"
                      checked={requireMasterOnRestart}
                      onChange={(e) => setRequireMasterOnRestart(e.target.checked)}
                    />
                  </label>
                  <div className="flex justify-end gap-2">
                    <button
                      type="button"
                      onClick={() => setShowPinSetup(false)}
                      className="ov-btn-ghost px-3 py-1.5 text-[12.5px]"
                    >
                      {s.cancel}
                    </button>
                    <button
                      type="submit"
                      disabled={savingPin}
                      className="ov-btn-primary px-3.5 py-1.5 text-[12.5px]"
                    >
                      {savingPin ? s.saving : s.save}
                    </button>
                  </div>
                </form>
              ) : null}

              <div className="pt-3 border-t border-[var(--border)] flex flex-col gap-3">
                <div className="flex items-center justify-between gap-2">
                  <label htmlFor="pin-attempts-select" className="text-[13.5px] font-medium">
                    {s.isVi ? "Giới Hạn Nhập Sai PIN" : "PIN Attempt Limit"}
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
                    className="ov-input w-auto py-1.5 text-[13px]"
                  >
                    <option value={3}>3</option>
                    <option value={5}>5</option>
                  </select>
                </div>

                <div className="flex items-center justify-between gap-2">
                  <label htmlFor="autolock-select" className="text-[13.5px] font-medium">
                    {s.isVi ? "Tự Động Khóa Sau" : "Auto-Lock Timeout"}
                  </label>
                  <select
                    id="autolock-select"
                    value={settings.autoLockMinutes}
                    onChange={(e) => {
                      void onUpdateSettings({ autoLockMinutes: Number(e.target.value) });
                    }}
                    className="ov-input w-auto py-1.5 text-[13px]"
                  >
                    <option value={1}>1 min</option>
                    <option value={5}>5 min</option>
                    <option value={10}>10 min</option>
                    <option value={15}>15 min</option>
                    <option value={30}>30 min</option>
                    <option value={60}>1 hour</option>
                    <option value={0}>{s.isVi ? "Khi Đóng Chrome" : "Browser Restart"}</option>
                  </select>
                </div>

                <div className="flex items-center justify-between gap-2">
                  <label htmlFor="clipboard-clear-select" className="text-[13.5px] font-medium">
                    {s.isVi ? "Xóa Bảng Tạm Sau" : "Clear Clipboard After"}
                  </label>
                  <select
                    id="clipboard-clear-select"
                    value={settings.clipboardClearSeconds}
                    onChange={(e) => {
                      void onUpdateSettings({ clipboardClearSeconds: Number(e.target.value) });
                    }}
                    className="ov-input w-auto py-1.5 text-[13px]"
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
          ) : null}

          {tab === "data" ? (
            <>
              <div className="ov-card p-3.5 flex flex-col gap-3">
                <div className="text-[12px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
                  {s.backupSection}
                </div>

                <button
                  type="button"
                  disabled={backingUp}
                  onClick={() => {
                    void handleR2Backup();
                  }}
                  className="ov-btn-primary w-full py-2.5 inline-flex items-center justify-center gap-2"
                >
                  <CloudUpload size={16} />
                  <span>
                    {backingUp
                      ? s.isVi
                        ? "Đang Tạo Snapshot R2..."
                        : "Creating R2 Snapshot..."
                      : s.isVi
                        ? "Sao Lưu Mã Hóa Lên Cloudflare R2"
                        : "Back Up Encrypted Vault to R2"}
                  </span>
                </button>

                {r2Backups.length > 0 ? (
                  <div className="rounded-[10px] bg-[var(--surface-subtle)] p-2.5 flex flex-col gap-1.5">
                    <div className="text-[11.5px] font-semibold text-[var(--text-muted)]">
                      {s.isVi ? "Bản Sao Lưu R2 Gần Nhất" : "Recent R2 Snapshots"}
                    </div>
                    {r2Backups.map((b) => (
                      <div
                        key={b.key}
                        className="flex items-center justify-between text-[12px] text-[var(--text-secondary)]"
                      >
                        <span>{formatLocalTimestamp(b.uploaded, lang)}</span>
                        <span className="font-mono-code text-[11.5px]">
                          {b.cipherCount ?? "?"} · {Math.max(1, Math.round(b.size / 1024))} KB
                        </span>
                      </div>
                    ))}
                  </div>
                ) : null}

                <div className="grid grid-cols-2 gap-2.5 pt-1">
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
                    className="ov-btn-ghost py-2.5 inline-flex items-center justify-center gap-1.5"
                  >
                    <Upload size={15} />
                    <span>{importing ? (s.isVi ? "Đang Nhập..." : "Importing...") : s.importBtn}</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      void handleExportEncrypted();
                    }}
                    className="ov-btn-ghost py-2.5 inline-flex items-center justify-center gap-1.5"
                  >
                    <Download size={15} />
                    <span>{s.isVi ? "Xuất JSON Mã Hóa" : "Export Encrypted"}</span>
                  </button>
                </div>
              </div>

              <div className="ov-card p-3.5 flex items-center gap-3">
                <img src="/icons/icon-48.png" alt="" className="w-9 h-9 rounded-[10px] shrink-0" />
                <div className="min-w-0">
                  <div className="text-[13.5px] font-bold flex items-center gap-2">
                    <span>OpenVault Extension</span>
                    <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-[var(--accent-soft)] text-[var(--accent)]">
                      v0.6.0
                    </span>
                  </div>
                  <div className="text-[12px] text-[var(--text-muted)]">
                    Zero-Knowledge · AES-256-CBC + HMAC · Argon2id · Passkeys
                  </div>
                </div>
              </div>
            </>
          ) : null}
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

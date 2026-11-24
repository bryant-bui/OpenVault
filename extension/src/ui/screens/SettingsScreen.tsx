import { extractVaultTextFromZip, parseUniversalVaultImport } from "@openvault/crypto-core";
import {
  AlertTriangle,
  Camera,
  CheckCircle2,
  CloudUpload,
  Code2,
  Database,
  Download,
  ExternalLink,
  Globe,
  Info,
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
import type {
  DefaultManagerControlStatus,
  ExtensionSettings,
  R2BackupItem,
} from "../../shared/types.js";
import { ConfirmDialog } from "../components/ConfirmDialog.js";
import { OpenVaultLogo } from "../components/OpenVaultLogo.js";
import { sendBg } from "../components/bridge.js";

type SettingsTab = "general" | "autofill" | "security" | "data" | "about";

interface SettingsScreenProps {
  email: string;
  lastSyncAt: string | null;
  settings: ExtensionSettings;
  pinEnabled: boolean;
  pinRequireMasterOnRestart: boolean;
  pinMaxAttempts: number;
  defaultManagerControl?: DefaultManagerControlStatus;
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

/**
 * Illustrated Teal-Cyan About Hero Banner matching Android `AboutHeroBanner` (SettingsScreen.kt):
 * Soft keyhole silhouette + tilted blue credit card, golden key, pink matte padlock, and document sheet.
 */
function AboutHeroBanner() {
  return (
    <div className="w-full h-[108px] rounded-[12px] overflow-hidden border border-[var(--border)] bg-[#6BC7D9]">
      <svg viewBox="0 0 640 140" className="w-full h-full block" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
        <rect width="640" height="140" fill="#6BC7D9" />
        {/* Soft glowing keyhole silhouette */}
        <circle cx="320" cy="82" r="126" fill="#9DE2EC" />
        <rect x="275" y="0" width="90" height="140" rx="12" fill="#7CD3E3" />
        {/* Tilted blue credit card */}
        <g transform="translate(60, 108) rotate(14)">
          <rect x="-48" y="-44" width="116" height="82" rx="10" fill="#3366CC" />
          <rect x="-24" y="-44" width="24" height="82" fill="#1D4499" />
          <rect x="18" y="2" width="28" height="18" rx="3" fill="#93C5FD" />
        </g>
        {/* Golden key */}
        <g transform="translate(212, 114) rotate(-12)">
          <rect x="-70" y="-7" width="96" height="14" rx="5" fill="#F5B942" />
          <circle cx="28" cy="0" r="21" fill="#FCD34D" />
          <circle cx="28" cy="0" r="14" stroke="#D9941E" strokeWidth="4" fill="none" />
        </g>
        {/* Center-right matte pink padlock */}
        <rect x="336" y="76" width="58" height="58" rx="12" fill="#F49AC2" />
        <circle cx="365" cy="100" r="8" fill="#1E293B" />
        <rect x="361" y="104" width="8" height="15" rx="4" fill="#1E293B" />
        {/* Far-right curved document sheet */}
        <path d="M525 140 Q572 118 582 68 L652 68 L652 140 Z" fill="#E5E7EB" />
      </svg>
    </div>
  );
}

function openExternalUrl(url: string) {
  if (typeof chrome !== "undefined" && chrome.tabs?.create) {
    void chrome.tabs.create({ url });
  } else {
    window.open(url, "_blank", "noopener,noreferrer");
  }
}

export function SettingsScreen({
  email,
  lastSyncAt,
  settings,
  pinEnabled,
  pinRequireMasterOnRestart,
  pinMaxAttempts,
  defaultManagerControl,
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

  const [aboutModal, setAboutModal] = useState<{ title: string; lines: string[] } | null>(null);
  const aboutDialogRef = useRef<HTMLDialogElement>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const avatarInputRef = useRef<HTMLInputElement>(null);

  const manifestVersion =
    (typeof chrome !== "undefined" && chrome.runtime?.getManifest?.().version) || "0.6.0";
  const avatarDataUrl = normalizeAvatarDataUrl(settings.accountAvatarBase64);
  const accountInitial = (email.split("@")[0]?.[0] ?? "O").toUpperCase();

  useEffect(() => {
    const dlg = aboutDialogRef.current;
    if (!dlg) return;
    if (aboutModal && !dlg.open) {
      dlg.showModal();
    } else if (!aboutModal && dlg.open) {
      dlg.close();
    }
  }, [aboutModal]);

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
    { id: "about", label: s.settingsTabAbout, icon: Info },
  ];

  const isControlledByOtherOrPolicy =
    defaultManagerControl === "controlled_by_other_extensions" ||
    defaultManagerControl === "not_controllable";

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

      {/* Clean 5-Tab Segmented Switch */}
      <div className="px-3.5 py-2.5 border-b border-[var(--border)] bg-[var(--surface)] shrink-0">
        <div className="grid grid-cols-5 gap-1 p-1 rounded-[11px] bg-[var(--surface-subtle)] border border-[var(--border)]">
          {tabs.map((item) => {
            const Icon = item.icon;
            const active = tab === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => setTab(item.id)}
                className={`py-1.5 px-1.5 rounded-[8px] border-0 text-[12px] font-semibold inline-flex items-center justify-center gap-1 cursor-pointer transition-all ${
                  active
                    ? "bg-[var(--surface)] text-[var(--accent)] shadow-xs"
                    : "bg-transparent text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
                }`}
              >
                <Icon size={13.5} strokeWidth={2} className="shrink-0" />
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
                      : "linear-gradient(135deg, #0C74F2, #0558C9)",
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
                <div className="pt-2.5 flex flex-col gap-2">
                  <ToggleRow
                    title={s.isVi ? "Đặt Làm Trình Quản Lý Mặc Định" : "Default Browser Password Manager"}
                    description={
                      s.isVi
                        ? "Tắt trình quản lý mật khẩu & popup Passkey mặc định của Chrome."
                        : "Suppress Chrome's built-in password & conditional passkey prompts."
                    }
                    checked={settings.defaultBrowserPasswordManager}
                    onChange={(v) => {
                      void onUpdateSettings({ defaultBrowserPasswordManager: v });
                    }}
                  />
                  {settings.defaultBrowserPasswordManager && isControlledByOtherOrPolicy ? (
                    <div className="px-3 py-2 rounded-[9px] bg-[var(--warning-soft)] text-[var(--warning)] text-[11.5px] flex items-start gap-2">
                      <AlertTriangle size={14} className="shrink-0 mt-0.5" />
                      <span>
                        {s.isVi
                          ? "Một tiện ích mật khẩu khác hoặc chính sách trình duyệt đang giành quyền kiểm soát cài đặt mật khẩu của Chrome."
                          : "Another password extension or browser policy currently overrides Chrome's password saving setting."}
                      </span>
                    </div>
                  ) : settings.defaultBrowserPasswordManager &&
                    defaultManagerControl === "controlled_by_this_extension" ? (
                    <div className="px-3 py-1.5 rounded-[9px] bg-[var(--success-soft)] text-[var(--success)] text-[11.5px] font-medium flex items-center gap-1.5">
                      <CheckCircle2 size={13} className="shrink-0" />
                      <span>
                        {s.isVi
                          ? "Đang hoạt động — OpenVault đang quản lý trình duyệt mặc định."
                          : "Active — OpenVault is controlling Chrome's password manager."}
                      </span>
                    </div>
                  ) : null}
                </div>
              </div>
            </>
          ) : null}

          {tab === "security" ? (
            <>
              {/* Card 1: Quick PIN Unlock & Attempt Limit */}
              <div className="ov-card p-3.5 flex flex-col gap-3">
                <div className="text-[11.5px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
                  {s.isVi ? "Mở Khóa Nhanh" : "Quick Unlock"}
                </div>

                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="text-[13.5px] font-semibold text-[var(--text-primary)]">
                      {s.isVi ? "Mã PIN Mở Khóa" : "Unlock with PIN"}
                    </div>
                    <div className="text-[12px] text-[var(--text-muted)] mt-0.5">
                      {pinEnabled
                        ? s.isVi
                          ? `Đang bật · ${pinMaxAttempts} lần thử tối đa · PBKDF2-600k`
                          : `Enabled · ${pinMaxAttempts} max attempts · PBKDF2-600k`
                        : s.isVi
                          ? "Mở khóa nhanh bằng mã PIN 6 số"
                          : "Fast unlock with a 6-digit PIN"}
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
                        className="px-3 py-1.5 rounded-[8px] border border-[var(--danger)] text-[var(--danger)] bg-transparent text-[12.5px] font-semibold cursor-pointer hover:bg-[var(--danger-soft)]"
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
                    className="p-3 rounded-[10px] bg-[var(--surface-subtle)] border border-[var(--border)] flex flex-col gap-2.5"
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
                    <label className="flex items-center justify-between gap-2 text-[12.5px] text-[var(--text-secondary)] cursor-pointer">
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

                <div className="pt-3 border-t border-[var(--border)] flex items-center justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <label
                      htmlFor="pin-attempts-select"
                      className="text-[13.5px] font-semibold text-[var(--text-primary)] block"
                    >
                      {s.isVi ? "Giới Hạn Nhập Sai PIN" : "PIN Attempt Limit"}
                    </label>
                    <div className="text-[12px] text-[var(--text-muted)] mt-0.5">
                      {s.isVi
                        ? "Tự xóa mã PIN cục bộ khi nhập sai quá số lần."
                        : "Wipes local PIN after consecutive failed tries."}
                    </div>
                  </div>
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
                    className="ov-select"
                  >
                    <option value={3}>{s.isVi ? "3 lần" : "3 attempts"}</option>
                    <option value={5}>{s.isVi ? "5 lần" : "5 attempts"}</option>
                  </select>
                </div>
              </div>

              {/* Card 2: Session Timeout & Clipboard Guard */}
              <div className="ov-card p-3.5 flex flex-col gap-3">
                <div className="text-[11.5px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
                  {s.isVi ? "Phiên Làm Việc & Bảng Tạm" : "Session & Clipboard"}
                </div>

                <div className="flex items-center justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <label
                      htmlFor="autolock-select"
                      className="text-[13.5px] font-semibold text-[var(--text-primary)] block"
                    >
                      {s.isVi ? "Tự Động Khóa Sau" : "Auto-Lock Timeout"}
                    </label>
                    <div className="text-[12px] text-[var(--text-muted)] mt-0.5">
                      {s.isVi
                        ? "Khóa kho tự động khi không thao tác."
                        : "Lock your vault automatically after inactivity."}
                    </div>
                  </div>
                  <select
                    id="autolock-select"
                    value={settings.autoLockMinutes}
                    onChange={(e) => {
                      void onUpdateSettings({ autoLockMinutes: Number(e.target.value) });
                    }}
                    className="ov-select"
                  >
                    <option value={1}>{s.isVi ? "1 phút" : "1 min"}</option>
                    <option value={5}>{s.isVi ? "5 phút" : "5 min"}</option>
                    <option value={10}>{s.isVi ? "10 phút" : "10 min"}</option>
                    <option value={15}>{s.isVi ? "15 phút" : "15 min"}</option>
                    <option value={30}>{s.isVi ? "30 phút" : "30 min"}</option>
                    <option value={60}>{s.isVi ? "1 giờ" : "1 hour"}</option>
                    <option value={0}>{s.isVi ? "Khi đóng Chrome" : "Browser Restart"}</option>
                  </select>
                </div>

                <div className="pt-3 border-t border-[var(--border)] flex items-center justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <label
                      htmlFor="clipboard-clear-select"
                      className="text-[13.5px] font-semibold text-[var(--text-primary)] block"
                    >
                      {s.isVi ? "Xóa Bảng Tạm Sau" : "Clear Clipboard After"}
                    </label>
                    <div className="text-[12px] text-[var(--text-muted)] mt-0.5">
                      {s.isVi
                        ? "Tự động xóa mật khẩu và mã 2FA đã sao chép."
                        : "Automatically clear copied secrets from clipboard."}
                    </div>
                  </div>
                  <select
                    id="clipboard-clear-select"
                    value={settings.clipboardClearSeconds}
                    onChange={(e) => {
                      void onUpdateSettings({ clipboardClearSeconds: Number(e.target.value) });
                    }}
                    className="ov-select"
                  >
                    <option value={15}>{s.isVi ? "15 giây" : "15s"}</option>
                    <option value={30}>{s.isVi ? "30 giây" : "30s"}</option>
                    <option value={60}>{s.isVi ? "60 giây" : "60s"}</option>
                    <option value={120}>{s.isVi ? "2 phút" : "2 min"}</option>
                    <option value={0}>{s.isVi ? "Tắt" : "Never"}</option>
                  </select>
                </div>
              </div>
            </>
          ) : null}

          {tab === "data" ? (
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
          ) : null}

          {tab === "about" ? (
            <div className="flex flex-col gap-3.5">
              {/* Illustrated Hero Banner */}
              <AboutHeroBanner />

              {/* Brand Identity */}
              <div className="flex flex-col items-center text-center gap-1 pt-1">
                <div className="flex items-center justify-center gap-2.5">
                  <OpenVaultLogo variant="crest" size={42} />
                  <span className="text-[22px] font-bold tracking-tight text-[var(--text-primary)]">
                    OpenVault
                  </span>
                </div>
                <div className="text-[13px] font-medium text-[var(--text-secondary)]">
                  OpenVault for Chrome {manifestVersion}
                </div>
              </div>

              {/* Clickable Version & Release Notes Row */}
              <button
                type="button"
                onClick={() =>
                  setAboutModal({
                    title: s.isVi ? "Ghi Chú Phát Hành" : "Release Notes",
                    lines: [
                      `v${manifestVersion}`,
                      s.isVi
                        ? "• Giao diện Mở Khóa Vault Door đồng bộ trực tiếp với OpenVault Android"
                        : "• Split Vault-Door unlock experience synchronized with OpenVault Android",
                      s.isVi
                        ? "• Vault+ tích hợp thư mục E2EE, tìm kiếm, sắp xếp, xem Danh sách / Ô xếp và sửa lỗi tải tài liệu"
                        : "• Vault+ upgraded with E2EE folders, search, 5 sort modes, list/grid views, and instant viewer",
                      s.isVi
                        ? "• Mã khôi phục hỗ trợ cặp 6+ số, mã Google 8 số và tự mở rộng trên 10 mã"
                        : "• Recovery codes upgraded to preserve 6+ digit codes/pairs and expand beyond 10 codes",
                      s.isVi
                        ? "• Ổn định quyền Trình Quản Lý Mật Khẩu Mặc Định & chặn popup Passkey gốc của Chrome"
                        : "• Reliable Default Password Manager control with inline conditional Passkey support",
                      s.isVi
                        ? "• Đồng bộ toàn diện Logo Cobalt Dial, font Inter & JetBrains Mono trên Extension & Android"
                        : "• Unified Cobalt Dial logo, Inter UI & JetBrains Mono fonts, and color palette",
                    ],
                  })
                }
                className="ov-card px-4 py-3 flex items-center justify-between gap-3 text-left cursor-pointer hover:border-[var(--accent-border)] transition-colors"
              >
                <div>
                  <div className="text-[14px] font-semibold text-[var(--text-primary)]">
                    Version {manifestVersion}
                  </div>
                  <div className="text-[11.5px] text-[var(--text-muted)] mt-0.5">
                    AES-256-CBC + HMAC · Argon2id · WebAuthn · OVSF
                  </div>
                </div>
                <span className="text-[13px] font-semibold text-[var(--accent)] underline shrink-0">
                  {s.isVi ? "Ghi chú phát hành" : "Release Notes"}
                </span>
              </button>

              {/* Author & Open Source Card (Synced with Android AboutAuthorGitHubCard) */}
              <div className="ov-card p-3.5 flex flex-col gap-3">
                <div className="text-[11px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
                  {s.isVi ? "TÁC GIẢ & MÃ NGUỒN MỞ" : "AUTHOR & OPEN SOURCE"}
                </div>

                <div className="min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-[15px] font-bold text-[var(--text-primary)]">
                      Bryant Bui
                    </span>
                    <span className="px-2 py-0.5 rounded-full text-[11px] font-semibold bg-[var(--accent-soft)] text-[var(--accent)]">
                      @bryant-bui
                    </span>
                  </div>
                  <div className="text-[12.5px] text-[var(--text-secondary)] mt-0.5">
                    {s.isVi
                      ? "Tác giả & Nhà phát triển OpenVault"
                      : "Creator & Maintainer of OpenVault"}
                  </div>
                </div>

                <div className="flex flex-wrap gap-1.5">
                  <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-[7px] bg-[var(--surface-subtle)] border border-[var(--border)] text-[11.5px] font-medium text-[var(--text-secondary)]">
                    <Code2 size={12.5} />
                    <span>bryant-bui/OpenVault</span>
                  </span>
                  <span className="inline-flex items-center px-2.5 py-1 rounded-[7px] bg-[var(--surface-subtle)] border border-[var(--border)] text-[11.5px] font-medium text-[var(--text-secondary)]">
                    TypeScript & Kotlin
                  </span>
                  <span className="inline-flex items-center px-2.5 py-1 rounded-[7px] bg-[var(--surface-subtle)] border border-[var(--border)] text-[11.5px] font-medium text-[var(--text-secondary)]">
                    MIT License
                  </span>
                </div>

                <div className="grid grid-cols-2 gap-2.5">
                  <button
                    type="button"
                    onClick={() => openExternalUrl("https://github.com/bryant-bui")}
                    className="ov-btn-ghost py-2 inline-flex items-center justify-center gap-1.5 text-[12.5px]"
                  >
                    <span>GitHub</span>
                    <ExternalLink size={13} className="text-[var(--text-secondary)]" />
                  </button>
                  <button
                    type="button"
                    onClick={() => openExternalUrl("https://github.com/bryant-bui/OpenVault")}
                    className="ov-btn-ghost py-2 inline-flex items-center justify-center gap-1.5 text-[12.5px]"
                  >
                    <span>Repository</span>
                    <ExternalLink size={13} className="text-[var(--text-secondary)]" />
                  </button>
                </div>
              </div>

              {/* Copyright & Modal Links Footer */}
              <div className="flex flex-col items-center gap-1.5 pt-1 text-center">
                <div className="text-[12.5px] text-[var(--text-secondary)]">
                  © 2026 OpenVault. MIT Open Source.
                </div>
                <div className="flex flex-wrap items-center justify-center gap-x-3 gap-y-1">
                  <button
                    type="button"
                    onClick={() =>
                      setAboutModal({
                        title: "MIT License",
                        lines: [
                          s.isVi
                            ? "OpenVault là phần mềm mã nguồn mở miễn phí theo giấy phép MIT. Bạn toàn quyền kiểm soát kho mật khẩu, Worker Cloudflare và bản sao lưu mã hóa của mình."
                            : "OpenVault is free, open-source software licensed under the MIT License. You own your vault, your Cloudflare Worker, and your encrypted backups.",
                        ],
                      })
                    }
                    className="text-[12.5px] font-medium text-[var(--accent)] underline bg-transparent border-0 cursor-pointer px-1.5 py-0.5"
                  >
                    {s.isVi ? "Điều khoản sử dụng (MIT)" : "Terms of Service"}
                  </button>
                  <button
                    type="button"
                    onClick={() =>
                      setAboutModal({
                        title: s.isVi ? "Bảo Mật Zero-Knowledge" : "Zero-Knowledge Privacy",
                        lines: [
                          s.isVi
                            ? "Mọi tài khoản, mật khẩu, khóa 2FA, khóa riêng Passkey và tài liệu Vault+ đều được mã hóa cục bộ bằng AES-256-CBC + HMAC-SHA256 / AES-256-GCM trước khi rời trình duyệt. Master Password không bao giờ được gửi lên máy chủ."
                            : "Every vault item, password, 2FA secret, Passkey private key, and Vault+ document is encrypted locally in your browser using AES-256-CBC + HMAC-SHA256 / AES-256-GCM before leaving your device. Your Master Password never leaves your device.",
                        ],
                      })
                    }
                    className="text-[12.5px] font-medium text-[var(--accent)] underline bg-transparent border-0 cursor-pointer px-1.5 py-0.5"
                  >
                    {s.isVi ? "Chính sách bảo mật" : "Privacy Policy"}
                  </button>
                  <button
                    type="button"
                    onClick={() =>
                      setAboutModal({
                        title: s.isVi ? "Ghi Công & Thư Viện Mở" : "Credits & Acknowledgements",
                        lines: [
                          "TypeScript · React 19 · Vite · Tailwind CSS · Lucide Icons · WebCrypto API · Argon2-browser (WASM) · Inter & JetBrains Mono · Cloudflare Workers + D1 + R2.",
                        ],
                      })
                    }
                    className="text-[12.5px] font-medium text-[var(--accent)] underline bg-transparent border-0 cursor-pointer px-1.5 py-0.5"
                  >
                    {s.isVi ? "Lời cảm ơn" : "Credits"}
                  </button>
                </div>
              </div>
            </div>
          ) : null}
        </div>
      </div>

      {/* Centered Modal Dialog for About Details */}
      <dialog
        ref={aboutDialogRef}
        className="ov-dialog"
        onCancel={(e) => {
          e.preventDefault();
          setAboutModal(null);
        }}
        onClick={(e) => {
          if (e.target === aboutDialogRef.current) setAboutModal(null);
        }}
      >
        {aboutModal ? (
          <div className="p-4 flex flex-col gap-3">
            <div className="text-[15px] font-bold text-[var(--text-primary)]">
              {aboutModal.title}
            </div>
            <div className="flex flex-col gap-1.5 text-[12.5px] leading-relaxed text-[var(--text-secondary)]">
              {aboutModal.lines.map((line, i) => (
                <p key={i} className="m-0">
                  {line}
                </p>
              ))}
            </div>
            <div className="flex justify-end pt-1">
              <button
                type="button"
                onClick={() => setAboutModal(null)}
                className="ov-btn-primary px-4 py-1.5 text-[12.5px]"
              >
                {s.done}
              </button>
            </div>
          </div>
        ) : null}
      </dialog>

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

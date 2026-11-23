import { extractVaultTextFromZip, parseUniversalVaultImport } from "@openvault/crypto-core";
import {
  CloudUpload,
  Download,
  Lock,
  LogOut,
  Monitor,
  Moon,
  PanelRightOpen,
  Sun,
  Upload,
  X,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
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
  serverUrl: string;
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
  serverUrl,
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

  const handleSavePin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (pinValue.length < 4) {
      onToast("Mã PIN phải dài ít nhất 4 ký tự.", "error");
      return;
    }
    if (pinValue !== pinConfirm) {
      onToast("Mã PIN xác nhận không khớp.", "error");
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
        `Đã bật mở khóa bằng mã PIN (PBKDF2 600k · tối đa ${settings.pinMaxAttempts} lần thử)!`,
        "success",
      );
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Không thể lưu mã PIN", "error");
    } finally {
      setSavingPin(false);
    }
  };

  const handleDisablePin = async () => {
    try {
      await sendBg({ type: "DISABLE_PIN" });
      await onPinUpdated();
      onToast("Đã tắt mở khóa bằng mã PIN", "info");
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Không thể tắt mã PIN", "error");
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
        `Đã sao lưu ${res.cipherCount} mục (${Math.max(1, Math.round(res.bytes / 1024))} KB) lên Cloudflare R2!`,
        "success",
      );
      const list = await sendBg<R2BackupItem[]>({ type: "LIST_R2_BACKUPS" });
      setR2Backups(list.slice(0, 5));
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Sao lưu R2 thất bại", "error");
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
      onToast("Đã tải xuống bản sao lưu mã hóa JSON", "success");
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Xuất backup thất bại", "error");
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
        onToast("Không tìm thấy mục mật khẩu nào trong tệp đã chọn.", "error");
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
          "Service Worker đang dùng bản cũ — vui lòng vào chrome://extensions bấm ↻ Tải lại OpenVault rồi nhập lại!",
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
        `Đã mã hóa & nhập ${res.importedCiphers} mục (${res.importedFolders} thư mục) từ ${sourceLabel}!`,
        "success",
      );
      await onImported();
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Tệp nhập liệu không hợp lệ", "error");
    } finally {
      setImporting(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  return (
    <div className="flex-1 flex flex-col min-h-0 bg-[var(--bg)]">
      <header className="px-4 py-2.5 border-b border-[var(--border)] bg-[var(--surface)] flex items-center justify-between">
        <h2 className="text-[14px] font-bold m-0">Cài đặt OpenVault</h2>
        {onClose ? (
          <button
            type="button"
            onClick={onClose}
            className="ov-icon-btn w-7 h-7"
            aria-label="Đóng Cài đặt"
          >
            <X size={15} />
          </button>
        ) : null}
      </header>

      <div className="flex-1 overflow-y-auto ov-scroll p-3.5 flex flex-col gap-3">
        {/* Account summary */}
        <div className="ov-card p-3">
          <div className="text-[13px] font-bold truncate">{email}</div>
          <div className="text-[11.5px] text-[var(--text-secondary)] truncate mt-0.5">
            {serverUrl}
          </div>
          {lastSyncAt ? (
            <div className="text-[11px] text-[var(--text-muted)] mt-1">
              Đồng bộ gần nhất: {new Date(lastSyncAt).toLocaleString("vi-VN")}
            </div>
          ) : null}
        </div>

        {/* Appearance (3-state system / light / dark) */}
        <div className="ov-card p-3 flex flex-col gap-2">
          <div className="text-[11px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
            Giao diện
          </div>
          <div className="grid grid-cols-3 gap-1.5">
            {[
              {
                id: "system" as const,
                label: `Hệ thống (${systemScheme === "dark" ? "Tối" : "Sáng"})`,
                icon: Monitor,
              },
              { id: "light" as const, label: "Sáng", icon: Sun },
              { id: "dark" as const, label: "Tối", icon: Moon },
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
        </div>

        {/* Autofill (Proton Pass granular controls) */}
        <div className="ov-card p-3 flex flex-col gap-3">
          <div className="text-[11px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
            Tự động điền
          </div>

          <ToggleRow
            title="Tự điền thông tin đăng nhập"
            description="Hiện gợi ý và điền nhanh tên đăng nhập, mật khẩu đã lưu."
            checked={settings.showInlineAutofill}
            onChange={(v) => {
              void onUpdateSettings({ showInlineAutofill: v });
            }}
          />
          <ToggleRow
            title="Tự động điền danh tính"
            description="Điền nhanh họ tên, email, số điện thoại và địa chỉ đã lưu."
            checked={settings.autofillIdentity}
            onChange={(v) => {
              void onUpdateSettings({ autofillIdentity: v });
            }}
          />
          <ToggleRow
            title="Tự động điền 2FA"
            description="Nhận diện ô OTP (kể cả 6 ô rời) để điền nhanh mã 2FA TOTP."
            checked={settings.autofillTotp}
            onChange={(v) => {
              void onUpdateSettings({ autofillTotp: v });
            }}
          />
          <ToggleRow
            title="Tự động sao chép mã 2FA"
            description="Sau khi tự động điền đăng nhập, mã 2FA sẽ tự động chép vào bảng tạm."
            checked={settings.autoCopyTotp}
            onChange={(v) => {
              void onUpdateSettings({ autoCopyTotp: v });
            }}
          />
          <ToggleRow
            title="Tự động điền thẻ tín dụng"
            description="Điền nhanh số thẻ, ngày hết hạn và mã CVV khi thanh toán."
            checked={settings.autofillCard}
            onChange={(v) => {
              void onUpdateSettings({ autofillCard: v });
            }}
          />
          <ToggleRow
            title="Tự động điền xác thực cơ bản"
            description="Tự động điền vào hộp thoại HTTP Basic Auth bằng tài khoản đã lưu."
            checked={settings.autofillBasicAuth}
            onChange={(v) => {
              void onUpdateSettings({ autofillBasicAuth: v });
            }}
          />

          <div className="pt-2 border-t border-[var(--border)] flex flex-col gap-3">
            <ToggleRow
              title="Gợi ý lưu khi đăng nhập tài khoản mới"
              description="Hiện thông báo lưu nhanh khi bạn đăng nhập tài khoản chưa có trong kho."
              checked={settings.promptSaveCredentials}
              onChange={(v) => {
                void onUpdateSettings({ promptSaveCredentials: v });
              }}
            />
            <ToggleRow
              title="Hiển thị biểu tượng (favicon) website"
              description="Tải logo trang web trong danh sách và khung chi tiết."
              checked={settings.showFavicons}
              onChange={(v) => {
                void onUpdateSettings({ showFavicons: v });
              }}
            />
          </div>
        </div>

        {/* Autosuggest */}
        <div className="ov-card p-3 flex flex-col gap-3">
          <div className="text-[11px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
            Tự động đề xuất
          </div>

          <ToggleRow
            title="Mật khẩu mạnh khi đăng ký"
            description="OpenVault sẽ gợi ý tạo và điền mật khẩu mạnh 20 ký tự trên ô mật khẩu mới."
            checked={settings.autosuggestPassword}
            onChange={(v) => {
              void onUpdateSettings({ autosuggestPassword: v });
            }}
          />
          <ToggleRow
            title="Tự động sao chép mật khẩu vừa tạo"
            description="Tự động sao chép các mật khẩu mới tạo vào bảng tạm (và tự xóa theo lịch)."
            checked={settings.autoCopyGeneratedPassword}
            onChange={(v) => {
              void onUpdateSettings({ autoCopyGeneratedPassword: v });
            }}
          />
        </div>

        {/* Passkeys (WebAuthn / FIDO2) */}
        <div className="ov-card p-3 flex flex-col gap-3">
          <div className="text-[11px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
            Mã khóa (Passkeys)
          </div>

          <ToggleRow
            title="Lưu mã khóa"
            description="OpenVault sẽ gợi ý tạo và lưu mã khóa (ECDSA P-256) đồng bộ sang Android."
            checked={settings.passkeySaveEnabled}
            onChange={(v) => {
              void onUpdateSettings({ passkeySaveEnabled: v });
            }}
          />
          <ToggleRow
            title="Xác thực bằng mã khóa"
            description="OpenVault sẽ gợi ý đăng nhập bằng các mã khóa đã lưu trong kho."
            checked={settings.passkeyAuthEnabled}
            onChange={(v) => {
              void onUpdateSettings({ passkeyAuthEnabled: v });
            }}
          />
        </div>

        {/* Browser Settings */}
        <div className="ov-card p-3 flex flex-col gap-2.5">
          <div className="text-[11px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
            Thiết lập trình duyệt
          </div>
          <ToggleRow
            title="Dùng OpenVault làm trình quản lý mật khẩu mặc định"
            description="Tắt trình lưu mật khẩu, thẻ thanh toán và địa chỉ tích hợp của Chrome để không bị popup đè."
            checked={settings.defaultBrowserPasswordManager}
            onChange={(v) => {
              void onUpdateSettings({ defaultBrowserPasswordManager: v });
            }}
          />
        </div>

        {/* Security & Lock */}
        <div className="ov-card p-3 flex flex-col gap-3">
          <div className="text-[11px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
            Bảo mật & Mở khóa
          </div>

          {/* Zero-Knowledge PIN Unlock */}
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between gap-2">
              <div>
                <div className="text-[12.5px] font-semibold">Mở khóa nhanh bằng mã PIN</div>
                <div className="text-[11px] text-[var(--text-muted)]">
                  {pinEnabled
                    ? `${pinRequireMasterOnRestart ? "Hỏi Master Password khi mở lại Chrome" : "Dùng được ngay khi mở lại Chrome"} · Tự hủy sau ${pinMaxAttempts} lần sai`
                    : `Bọc khóa bằng PBKDF2 600k · Tự hủy sau ${settings.pinMaxAttempts} lần nhập sai`}
                </div>
              </div>
              <div className="flex items-center gap-1.5 shrink-0">
                <button
                  type="button"
                  onClick={() => setShowPinSetup((v) => !v)}
                  className="ov-btn-ghost px-2.5 py-1 text-[11.5px]"
                >
                  {pinEnabled ? "Đổi PIN" : "Thiết lập"}
                </button>
                {pinEnabled ? (
                  <button
                    type="button"
                    onClick={() => {
                      void handleDisablePin();
                    }}
                    className="px-2.5 py-1 rounded-[8px] border border-[var(--danger)] text-[var(--danger)] bg-transparent text-[11.5px] font-semibold cursor-pointer"
                  >
                    Tắt
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
                    placeholder="Mã PIN (khuyên dùng 6 số)"
                    value={pinValue}
                    onChange={(e) => setPinValue(e.target.value)}
                    required
                  />
                  <input
                    type="password"
                    inputMode="numeric"
                    className="ov-input font-mono-code text-[12.5px]"
                    placeholder="Nhập lại PIN"
                    value={pinConfirm}
                    onChange={(e) => setPinConfirm(e.target.value)}
                    required
                  />
                </div>
                <label className="flex items-center justify-between text-[11.5px] text-[var(--text-secondary)] cursor-pointer">
                  <span>Yêu cầu Master Password khi khởi động lại Chrome</span>
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
                    Hủy
                  </button>
                  <button
                    type="submit"
                    disabled={savingPin}
                    className="ov-btn-primary px-3 py-1 text-[11.5px]"
                  >
                    {savingPin ? "Đang bọc khóa..." : "Lưu mã PIN"}
                  </button>
                </div>
              </form>
            ) : null}
          </div>

          <div className="pt-2.5 border-t border-[var(--border)] flex flex-col gap-2.5">
            <div className="flex items-center justify-between gap-2">
              <label htmlFor="pin-attempts-select" className="text-[12.5px] font-medium">
                Tự hủy PIN khi nhập sai
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
                <option value={3}>3 lần</option>
                <option value={5}>5 lần</option>
              </select>
            </div>

            <div className="flex items-center justify-between gap-2">
              <label htmlFor="autolock-select" className="text-[12.5px] font-medium">
                Tự động khóa sau
              </label>
              <select
                id="autolock-select"
                value={settings.autoLockMinutes}
                onChange={(e) => {
                  void onUpdateSettings({ autoLockMinutes: Number(e.target.value) });
                }}
                className="ov-input w-auto py-1 text-[12px]"
              >
                <option value={1}>1 phút</option>
                <option value={5}>5 phút</option>
                <option value={10}>10 phút</option>
                <option value={15}>15 phút</option>
                <option value={30}>30 phút</option>
                <option value={60}>1 giờ</option>
                <option value={0}>Khi đóng Chrome</option>
              </select>
            </div>

            <div className="flex flex-col gap-1">
              <div className="flex items-center justify-between gap-2">
                <label htmlFor="clipboard-clear-select" className="text-[12.5px] font-medium">
                  Xóa bảng tạm sau
                </label>
                <select
                  id="clipboard-clear-select"
                  value={settings.clipboardClearSeconds}
                  onChange={(e) => {
                    void onUpdateSettings({ clipboardClearSeconds: Number(e.target.value) });
                  }}
                  className="ov-input w-auto py-1 text-[12px]"
                >
                  <option value={15}>15 giây</option>
                  <option value={30}>30 giây</option>
                  <option value={60}>60 giây</option>
                  <option value={120}>2 phút</option>
                  <option value={0}>Không tự xóa</option>
                </select>
              </div>
              <div className="text-[11px] text-[var(--text-muted)]">
                Vui lòng giữ trình duyệt mở để có thể tự động xóa bảng tạm.
              </div>
            </div>
          </div>
        </div>

        {/* Cloudflare R2 Backups & Universal Import/Export */}
        <div className="ov-card p-3 flex flex-col gap-2.5">
          <div className="text-[11px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
            Sao lưu & Di chuyển dữ liệu
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
              {backingUp ? "Đang tạo snapshot R2..." : "Sao lưu mã hóa lên Cloudflare R2 ngay"}
            </span>
          </button>

          {r2Backups.length > 0 ? (
            <div className="rounded-[10px] bg-[var(--surface-subtle)] p-2 flex flex-col gap-1.5">
              <div className="text-[10.5px] font-semibold text-[var(--text-muted)]">
                Bản sao lưu R2 gần nhất (tự động hằng ngày 03:00 UTC)
              </div>
              {r2Backups.map((b) => (
                <div
                  key={b.key}
                  className="flex items-center justify-between text-[11px] text-[var(--text-secondary)]"
                >
                  <span>{new Date(b.uploaded).toLocaleString("vi-VN")}</span>
                  <span className="font-mono-code text-[10.5px]">
                    {b.cipherCount ?? "?"} mục · {Math.max(1, Math.round(b.size / 1024))} KB
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
              <span>{importing ? "Đang mã hóa & nhập..." : "Import Proton / BW"}</span>
            </button>
            <button
              type="button"
              onClick={() => {
                void handleExportEncrypted();
              }}
              className="ov-btn-ghost py-2 inline-flex items-center justify-center gap-1.5 text-[12px]"
            >
              <Download size={13} />
              <span>Xuất JSON mã hóa</span>
            </button>
          </div>
          <p className="text-[11px] text-[var(--text-muted)] m-0 leading-snug">
            Hỗ trợ trực tiếp file <strong>ZIP (&quot;Mã bưu chính&quot;)</strong>,{" "}
            <strong>data.json</strong>, <strong>CSV</strong> xuất từ Proton Pass và{" "}
            <strong>JSON</strong> từ Bitwarden.
          </p>
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
              <span>Mở OpenVault trong Side Panel</span>
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
              <span>Khóa ngay</span>
            </button>
            <button
              type="button"
              onClick={() => setConfirmLogoutOpen(true)}
              className="py-2 rounded-[10px] border border-[var(--danger)] text-[var(--danger)] bg-transparent font-semibold text-[12.5px] inline-flex items-center justify-center gap-1.5 cursor-pointer hover:bg-[var(--danger-soft)]"
            >
              <LogOut size={14} />
              <span>Đăng xuất</span>
            </button>
          </div>
        </div>
      </div>

      <ConfirmDialog
        open={confirmLogoutOpen}
        title="Đăng xuất tài khoản?"
        description="Khóa phiên RAM và bộ nhớ đệm mã hóa cục bộ sẽ bị xóa. Bạn sẽ cần nhập lại email và Master Password để đăng nhập."
        confirmLabel="Đăng xuất"
        onConfirm={() => {
          setConfirmLogoutOpen(false);
          void onLogout(false);
        }}
        onCancel={() => setConfirmLogoutOpen(false)}
      />
    </div>
  );
}

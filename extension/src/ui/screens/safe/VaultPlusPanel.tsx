import {
  SAFE_COOLDOWN_AFTER_FAILS,
  SAFE_MAX_FILE_BYTES,
  SAFE_MAX_TOTAL_FAILS,
  SAFE_PIN_LENGTH,
  base64ToBytes,
  decryptSafeFileBinary,
  encryptSafeFileBinary,
  sha256HexBytes,
  zeroize,
  type SafeDocumentCategory,
} from "@openvault/crypto-core";
import {
  AlertTriangle,
  Check,
  Copy,
  Download,
  FileText,
  Image as ImageIcon,
  Loader2,
  Lock,
  Plus,
  RefreshCw,
  Settings,
  ShieldAlert,
  ShieldPlus,
  Star,
  Trash2,
  Upload,
  X,
} from "lucide-react";
import { Fragment, useCallback, useEffect, useId, useMemo, useRef, useState } from "react";
import { getStrings, type AppLanguage } from "../../../shared/i18n.js";
import { apiDownloadSafeBinary, apiUploadSafeBinary } from "../../../shared/safe-api.js";
import type {
  DecryptedSafeItem,
  SafeBlobAuthResult,
  SafePrepareUploadResult,
  SafeStatusResponse,
} from "../../../shared/safe-types.js";
import { sendBg } from "../../components/bridge.js";
import { SafePreviewModal } from "./SafePreviewModal.js";

interface VaultPlusPanelProps {
  language: AppLanguage;
  onClose: () => void;
  onToast: (msg: string, kind?: "success" | "error") => void;
}

type FilterTab = "all" | "favorites" | SafeDocumentCategory | "trash";

const DOC_CATEGORIES: { id: SafeDocumentCategory; en: string; vi: string }[] = [
  { id: "id_card", en: "ID Card", vi: "CCCD" },
  { id: "passport", en: "Passport", vi: "Hộ chiếu" },
  { id: "driver_license", en: "Driver's License", vi: "Bằng lái" },
  { id: "medical", en: "Medical", vi: "Y tế" },
  { id: "financial", en: "Financial", vi: "Tài chính" },
  { id: "photo", en: "Photos", vi: "Ảnh" },
  { id: "other", en: "Other", vi: "Khác" },
];

async function generateImageThumbnail(file: File): Promise<Uint8Array | null> {
  if (!file.type.startsWith("image/")) return null;
  const imgUrl = URL.createObjectURL(file);
  try {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const el = new Image();
      el.onload = () => resolve(el);
      el.onerror = reject;
      el.src = imgUrl;
    });
    const maxDim = 240;
    const scale = Math.min(1, maxDim / Math.max(img.width, img.height, 1));
    const w = Math.max(1, Math.round(img.width * scale));
    const h = Math.max(1, Math.round(img.height * scale));
    const canvas = document.createElement("canvas");
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext("2d");
    if (!ctx) return null;
    ctx.drawImage(img, 0, 0, w, h);
    const blob = await new Promise<Blob | null>((r) => canvas.toBlob(r, "image/jpeg", 0.76));
    if (!blob) return null;
    return new Uint8Array(await blob.arrayBuffer());
  } catch {
    return null;
  } finally {
    URL.revokeObjectURL(imgUrl);
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(1)} KB`;
  return `${(kb / 1024).toFixed(2)} MB`;
}

export function VaultPlusPanel({ language, onClose, onToast }: VaultPlusPanelProps) {
  const s = getStrings(language);
  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState<SafeStatusResponse>({
    initialized: false,
    unlocked: false,
    autoLockMinutes: 3,
    unlockedExpiresAtMs: null,
    failedAttempts: 0,
    lockedUntilMs: null,
    recoveryRequired: false,
    usedBytes: 0,
    itemCount: 0,
    maxFileBytes: SAFE_MAX_FILE_BYTES,
    hasRecoveryConfig: false,
  });
  const [items, setItems] = useState<DecryptedSafeItem[]>([]);
  const [recoverySheetKey, setRecoverySheetKey] = useState<string | null>(null);
  const [recoveryConfirmed, setRecoveryConfirmed] = useState(false);

  // Setup state
  const [setupPin, setSetupPin] = useState("");
  const [setupConfirm, setSetupConfirm] = useState("");
  const [setupBusy, setSetupBusy] = useState(false);
  const [setupErr, setSetupErr] = useState<string | null>(null);

  // Unlock state
  const [pin, setPin] = useState("");
  const [pinFocused, setPinFocused] = useState(false);
  const [unlockBusy, setUnlockBusy] = useState(false);
  const [unlockErr, setUnlockErr] = useState<string | null>(null);
  const [shakeKey, setShakeKey] = useState(0);
  const [nowMs, setNowMs] = useState(() => Date.now());
  const pinInputRef = useRef<HTMLInputElement>(null);
  const pinInputId = useId();

  // Recovery / Wipe modal states
  const [showRecoverModal, setShowRecoverModal] = useState(false);
  const [recoverKeyInput, setRecoverKeyInput] = useState("");
  const [recoverNewPin, setRecoverNewPin] = useState("");
  const [recoverConfirmPin, setRecoverConfirmPin] = useState("");
  const [recoverRotate, setRecoverRotate] = useState(false);
  const [recoverBusy, setRecoverBusy] = useState(false);
  const [recoverErr, setRecoverErr] = useState<string | null>(null);

  const [showWipeModal, setShowWipeModal] = useState(false);
  const [wipeMasterPw, setWipeMasterPw] = useState("");
  const [wipeBusy, setWipeBusy] = useState(false);
  const [wipeErr, setWipeErr] = useState<string | null>(null);

  // Unlocked workspace states
  const [filterTab, setFilterTab] = useState<FilterTab>("all");
  const [showSettings, setShowSettings] = useState(false);
  const [changeCurPin, setChangeCurPin] = useState("");
  const [changeNewPin, setChangeNewPin] = useState("");
  const [changeConfPin, setChangeConfPin] = useState("");
  const [changeBusy, setChangeBusy] = useState(false);

  // Upload states
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [uploadTitle, setUploadTitle] = useState("");
  const [uploadCategory, setUploadCategory] = useState<SafeDocumentCategory>("id_card");
  const [uploadExpires, setUploadExpires] = useState("");
  const [uploadNotes, setUploadNotes] = useState("");
  const [uploading, setUploading] = useState(false);
  const [dragOver, setDragOver] = useState(false);

  // Thumbnails & Preview
  const [thumbUrls, setThumbUrls] = useState<Record<string, string>>({});
  const thumbUrlsRef = useRef<Record<string, string>>({});
  const [previewItem, setPreviewItem] = useState<DecryptedSafeItem | null>(null);

  const revokeAllThumbs = useCallback(() => {
    for (const url of Object.values(thumbUrlsRef.current)) {
      URL.revokeObjectURL(url);
    }
    thumbUrlsRef.current = {};
    setThumbUrls({});
  }, []);

  const loadItems = useCallback(async () => {
    const list = await sendBg<DecryptedSafeItem[]>({ type: "SAFE_LIST_ITEMS" });
    setItems(list);
  }, []);

  const loadStatus = useCallback(
    async (forceSync = true) => {
      try {
        const st = await sendBg<SafeStatusResponse>({ type: "SAFE_GET_STATUS", forceSync });
        setStatus(st);
        if (st.unlocked) {
          await loadItems();
        } else {
          setItems([]);
          revokeAllThumbs();
        }
      } catch (err) {
        onToast(err instanceof Error ? err.message : "Failed to load Vault+", "error");
      } finally {
        setLoading(false);
      }
    },
    [loadItems, onToast, revokeAllThumbs],
  );

  useEffect(() => {
    void loadStatus(true);
    return () => revokeAllThumbs();
  }, [loadStatus, revokeAllThumbs]);

  // Live ticker for 15-min cooldown and auto-lock countdown
  useEffect(() => {
    const id = window.setInterval(() => {
      const now = Date.now();
      setNowMs(now);
      if (status.lockedUntilMs && now >= status.lockedUntilMs) {
        void loadStatus(true);
      }
      if (status.unlocked && status.unlockedExpiresAtMs && now >= status.unlockedExpiresAtMs) {
        void loadStatus(false);
      }
    }, 1000);
    return () => window.clearInterval(id);
  }, [status.lockedUntilMs, status.unlocked, status.unlockedExpiresAtMs, loadStatus]);

  // Load decrypted thumbnails in-memory for items that have thumbnails
  useEffect(() => {
    if (!status.unlocked || items.length === 0) return;
    const toLoad = items.filter(
      (i) => i.hasThumbnail && i.metadata.thumbKeyB64 && !thumbUrlsRef.current[i.id],
    );
    if (toLoad.length === 0) return;

    let cancelled = false;
    void (async () => {
      try {
        const auth = await sendBg<SafeBlobAuthResult>({ type: "SAFE_GET_AUTH_FOR_BLOB" });
        for (const item of toLoad) {
          if (cancelled || !item.metadata.thumbKeyB64) break;
          try {
            const enc = await apiDownloadSafeBinary(
              auth.serverUrl,
              auth.accessToken,
              item.id,
              "thumbnail",
            );
            const tKey = base64ToBytes(item.metadata.thumbKeyB64);
            const plain = await decryptSafeFileBinary(enc, tKey, item.id, "thumb");
            zeroize(tKey);
            const url = URL.createObjectURL(new Blob([plain], { type: "image/jpeg" }));
            zeroize(plain);
            if (!cancelled) {
              thumbUrlsRef.current[item.id] = url;
              setThumbUrls((prev) => ({ ...prev, [item.id]: url }));
            } else {
              URL.revokeObjectURL(url);
            }
          } catch {
            // Fallback to category icon
          }
        }
      } catch {
        // Ignore transient lock/network error
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [items, status.unlocked]);

  const handleSetupSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!/^\d{6}$/.test(setupPin)) {
      setSetupErr(s.isVi ? "Mã PIN phải gồm đúng 6 chữ số." : "PIN must be 6 numeric digits.");
      return;
    }
    if (setupPin !== setupConfirm) {
      setSetupErr(s.isVi ? "Hai mã PIN không khớp." : "PINs do not match.");
      return;
    }
    setSetupBusy(true);
    setSetupErr(null);
    try {
      const res = await sendBg<{ recoveryKey: string; status: SafeStatusResponse }>({
        type: "SAFE_SETUP",
        pin: setupPin,
      });
      setSetupPin("");
      setSetupConfirm("");
      setStatus(res.status);
      setRecoveryConfirmed(false);
      setRecoverySheetKey(res.recoveryKey);
    } catch (err) {
      setSetupErr(err instanceof Error ? err.message : "Setup failed");
    } finally {
      setSetupBusy(false);
    }
  };

  const submitUnlockPin = useCallback(
    async (candidate: string) => {
      if (unlockBusy || !/^\d{6}$/.test(candidate)) return;
      setUnlockBusy(true);
      setUnlockErr(null);
      try {
        const nextStatus = await sendBg<SafeStatusResponse>({
          type: "SAFE_UNLOCK_PIN",
          pin: candidate,
        });
        setPin("");
        setStatus(nextStatus);
        await loadItems();
      } catch (err) {
        setPin("");
        setShakeKey((k) => k + 1);
        setUnlockErr(err instanceof Error ? err.message : "Incorrect PIN");
        await loadStatus(false);
        window.requestAnimationFrame(() => pinInputRef.current?.focus());
      } finally {
        setUnlockBusy(false);
      }
    },
    [loadItems, loadStatus, unlockBusy],
  );

  const handleRecoverSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!/^\d{6}$/.test(recoverNewPin) || recoverNewPin !== recoverConfirmPin) {
      setRecoverErr(
        s.isVi ? "Mã PIN mới phải là 6 chữ số và khớp nhau." : "New 6-digit PINs must match.",
      );
      return;
    }
    setRecoverBusy(true);
    setRecoverErr(null);
    try {
      const res = await sendBg<{ recoveryKey: string | null; status: SafeStatusResponse }>({
        type: "SAFE_RECOVER",
        recoveryKey: recoverKeyInput,
        newPin: recoverNewPin,
        rotateRecoveryKey: recoverRotate,
      });
      setShowRecoverModal(false);
      setRecoverKeyInput("");
      setRecoverNewPin("");
      setRecoverConfirmPin("");
      setStatus(res.status);
      if (res.recoveryKey) {
        setRecoveryConfirmed(false);
        setRecoverySheetKey(res.recoveryKey);
      } else {
        onToast(s.isVi ? "Đã khôi phục và mở khóa Vault+!" : "Vault+ recovered!", "success");
      }
      await loadItems();
    } catch (err) {
      setRecoverErr(err instanceof Error ? err.message : "Recovery failed");
    } finally {
      setRecoverBusy(false);
    }
  };

  const handleWipeSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!wipeMasterPw || wipeBusy) return;
    setWipeBusy(true);
    setWipeErr(null);
    try {
      await sendBg({ type: "SAFE_RESET", masterPassword: wipeMasterPw });
      setShowWipeModal(false);
      setWipeMasterPw("");
      setShowSettings(false);
      revokeAllThumbs();
      setItems([]);
      await loadStatus(true);
      onToast(s.isVi ? "Đã xóa toàn bộ dữ liệu Vault+." : "Vault+ data wiped.", "success");
    } catch (err) {
      setWipeErr(err instanceof Error ? err.message : "Reset failed");
    } finally {
      setWipeBusy(false);
    }
  };

  const handlePickFile = (file: File | null | undefined) => {
    if (!file) return;
    if (file.type !== "application/pdf" && !file.type.startsWith("image/")) {
      onToast(
        s.isVi
          ? "Chỉ hỗ trợ tệp PDF hoặc Ảnh (JPEG, PNG, WebP)."
          : "Only PDF or image files are supported.",
        "error",
      );
      return;
    }
    if (file.size > SAFE_MAX_FILE_BYTES) {
      onToast(
        s.isVi
          ? `Tệp vượt quá giới hạn tối đa 5 MB (${formatSize(file.size)}).`
          : `File exceeds 5 MB maximum (${formatSize(file.size)}).`,
        "error",
      );
      return;
    }
    setPendingFile(file);
    setUploadTitle(file.name.replace(/\.[^.]+$/, ""));
    setUploadCategory(file.type.startsWith("image/") ? "photo" : "id_card");
    setUploadExpires("");
    setUploadNotes("");
  };

  const handleUploadSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!pendingFile || uploading) return;
    setUploading(true);
    try {
      const rawBytes = new Uint8Array(await pendingFile.arrayBuffer());
      const sha256 = await sha256HexBytes(rawBytes);
      const thumbPlain = await generateImageThumbnail(pendingFile);

      const prep = await sendBg<SafePrepareUploadResult>({
        type: "SAFE_PREPARE_UPLOAD",
        title: uploadTitle.trim() || pendingFile.name,
        fileName: pendingFile.name,
        mimeType: pendingFile.type || "application/octet-stream",
        category: uploadCategory,
        notes: uploadNotes.trim() || null,
        expiresAt: uploadExpires.trim() || null,
        originalSize: rawBytes.byteLength,
        originalSha256: sha256,
        hasThumb: Boolean(thumbPlain),
      });

      const fileKey = base64ToBytes(prep.fileKeyB64);
      const ovsfBlob = await encryptSafeFileBinary(rawBytes, fileKey, prep.itemId, "blob");
      zeroize(rawBytes, fileKey);
      await apiUploadSafeBinary(prep.serverUrl, prep.accessToken, prep.itemId, "blob", ovsfBlob);

      if (thumbPlain && prep.thumbKeyB64) {
        const tKey = base64ToBytes(prep.thumbKeyB64);
        const ovsfThumb = await encryptSafeFileBinary(thumbPlain, tKey, prep.itemId, "thumb");
        zeroize(thumbPlain, tKey);
        await apiUploadSafeBinary(
          prep.serverUrl,
          prep.accessToken,
          prep.itemId,
          "thumbnail",
          ovsfThumb,
        ).catch(() => undefined);
      }

      setPendingFile(null);
      await loadStatus(true);
      onToast(s.isVi ? "Đã mã hóa và lưu vào Vault+!" : "Encrypted & saved to Vault+!", "success");
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Upload failed", "error");
    } finally {
      setUploading(false);
    }
  };

  const handleChangePin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!/^\d{6}$/.test(changeCurPin) || !/^\d{6}$/.test(changeNewPin) || changeNewPin !== changeConfPin) {
      onToast(s.isVi ? "Vui lòng nhập PIN 6 số hợp lệ." : "Enter valid matching 6-digit PINs.", "error");
      return;
    }
    setChangeBusy(true);
    try {
      await sendBg({
        type: "SAFE_CHANGE_PIN",
        currentPin: changeCurPin,
        newPin: changeNewPin,
      });
      setChangeCurPin("");
      setChangeNewPin("");
      setChangeConfPin("");
      setShowSettings(false);
      onToast(s.isVi ? "Đã đổi mã PIN Vault+!" : "Vault+ PIN updated!", "success");
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Failed", "error");
    } finally {
      setChangeBusy(false);
    }
  };

  const filteredItems = useMemo(() => {
    return items.filter((item) => {
      if (filterTab === "trash") return Boolean(item.deletedAt);
      if (item.deletedAt) return false;
      if (filterTab === "favorites") return item.favorite;
      if (filterTab === "all") return true;
      return item.category === filterTab;
    });
  }, [items, filterTab]);

  const isCoolingDown = Boolean(status.lockedUntilMs && status.lockedUntilMs > nowMs);
  const cooldownRemSec = status.lockedUntilMs
    ? Math.max(0, Math.ceil((status.lockedUntilMs - nowMs) / 1000))
    : 0;
  const unlockRemSec = status.unlockedExpiresAtMs
    ? Math.max(0, Math.ceil((status.unlockedExpiresAtMs - nowMs) / 1000))
    : 0;

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center bg-[var(--surface)]">
        <Loader2 size={24} className="animate-spin text-[var(--accent)]" />
      </div>
    );
  }

  return (
    <div className="flex-1 flex flex-col min-h-0 bg-[var(--surface)] relative">
      {/* Header */}
      <div className="px-4 py-3 border-b border-[var(--border)] flex items-center justify-between gap-2 shrink-0">
        <div className="flex items-center gap-2.5 min-w-0">
          <div className="w-8 h-8 rounded-[9px] bg-[var(--accent-soft)] text-[var(--accent)] flex items-center justify-center shrink-0">
            <ShieldPlus size={18} />
          </div>
          <div className="min-w-0">
            <div className="text-[14.5px] font-bold text-[var(--text-primary)] leading-tight">
              {s.vaultPlus}
            </div>
            <div className="text-[11.5px] text-[var(--text-secondary)] truncate">
              {status.unlocked
                ? `${formatSize(status.usedBytes)} · ${status.itemCount} ${s.isVi ? "mục" : "files"} · ${Math.floor(unlockRemSec / 60)}:${String(unlockRemSec % 60).padStart(2, "0")}`
                : s.vaultPlusSubtitle}
            </div>
          </div>
        </div>

        <div className="flex items-center gap-1 shrink-0">
          {status.unlocked ? (
            <>
              <button
                type="button"
                className="ov-icon-btn w-7 h-7"
                onClick={() => void loadStatus(true)}
                title={s.syncNow}
              >
                <RefreshCw size={14} />
              </button>
              <button
                type="button"
                data-active={showSettings}
                className="ov-icon-btn w-7 h-7"
                onClick={() => setShowSettings((v) => !v)}
                title={s.settingsTitle}
              >
                <Settings size={14} />
              </button>
              <button
                type="button"
                className="ov-btn-ghost px-2.5 py-1 text-[12px] inline-flex items-center gap-1"
                onClick={() => {
                  void sendBg({ type: "SAFE_LOCK" }).then(() => loadStatus(false));
                }}
              >
                <Lock size={12} />
                <span>{s.lockNow}</span>
              </button>
            </>
          ) : null}
          <button type="button" className="ov-icon-btn w-7 h-7" onClick={onClose} title={s.close}>
            <X size={16} />
          </button>
        </div>
      </div>

      {/* Body */}
      <div className="flex-1 overflow-y-auto ov-scroll p-4">
        {recoverySheetKey ? (
          <div className="max-w-[420px] mx-auto flex flex-col gap-3">
            <div className="flex items-center gap-2 text-[var(--warning)] font-bold text-[15px]">
              <AlertTriangle size={18} />
              <span>{s.vaultPlusRecoveryTitle}</span>
            </div>
            <p className="text-[12.5px] text-[var(--text-secondary)] m-0 leading-relaxed">
              {s.vaultPlusRecoveryDesc}
            </p>
            <div className="p-3 rounded-[10px] border border-[var(--accent-border)] bg-[var(--accent-soft)] font-mono-code text-[13px] font-bold text-[var(--text-primary)] break-all select-all">
              {recoverySheetKey}
            </div>
            <div className="flex gap-2">
              <button
                type="button"
                className="ov-btn-ghost flex-1 py-2 inline-flex items-center justify-center gap-1.5 text-[12.5px]"
                onClick={() => {
                  void navigator.clipboard.writeText(recoverySheetKey);
                  void sendBg({ type: "SCHEDULE_CLIPBOARD_CLEAR" }).catch(() => undefined);
                  onToast(s.isVi ? "Đã sao chép Recovery Key." : "Copied Recovery Key.", "success");
                }}
              >
                <Copy size={14} />
                <span>{s.copy}</span>
              </button>
              <button
                type="button"
                className="ov-btn-ghost flex-1 py-2 inline-flex items-center justify-center gap-1.5 text-[12.5px]"
                onClick={() => {
                  const blob = new Blob(
                    [
                      `OPENVAULT VAULT+ EMERGENCY RECOVERY KIT\n=========================================\nRecovery Key:\n${recoverySheetKey}\n\nKeep this offline in a safe place.\n`,
                    ],
                    { type: "text/plain;charset=utf-8" },
                  );
                  const url = URL.createObjectURL(blob);
                  const a = document.createElement("a");
                  a.href = url;
                  a.download = "OpenVault-VaultPlus-RecoveryKey.txt";
                  document.body.appendChild(a);
                  a.click();
                  a.remove();
                  URL.revokeObjectURL(url);
                  setRecoveryConfirmed(true);
                }}
              >
                <Download size={14} />
                <span>{s.vaultPlusSaveKit}</span>
              </button>
            </div>
            <label className="flex items-center gap-2 text-[12.5px] text-[var(--text-primary)] cursor-pointer select-none">
              <input
                type="checkbox"
                checked={recoveryConfirmed}
                onChange={(e) => setRecoveryConfirmed(e.target.checked)}
              />
              <span>{s.vaultPlusRecoverySavedCheck}</span>
            </label>
            <button
              type="button"
              disabled={!recoveryConfirmed}
              className="ov-btn-primary py-2.5 text-[13px]"
              onClick={() => {
                setRecoverySheetKey(null);
                void loadItems();
              }}
            >
              <Check size={14} className="inline mr-1" />
              {s.vaultPlus}
            </button>
          </div>
        ) : !status.initialized ? (
          <form onSubmit={handleSetupSubmit} className="max-w-[400px] mx-auto flex flex-col gap-3">
            <div className="ov-card p-3.5 flex flex-col gap-2">
              <div className="text-[14px] font-bold text-[var(--text-primary)]">
                {s.vaultPlusSetupTitle}
              </div>
              <p className="text-[12.5px] text-[var(--text-secondary)] m-0 leading-relaxed">
                {s.vaultPlusSetupDesc}
              </p>
            </div>
            <div className="ov-card p-3.5 flex flex-col gap-2.5">
              <label className="text-[12px] font-semibold text-[var(--text-secondary)]">
                {s.vaultPlusCreatePin}
                <input
                  type="password"
                  inputMode="numeric"
                  maxLength={6}
                  value={setupPin}
                  onChange={(e) => {
                    setSetupPin(e.target.value.replace(/\D/g, "").slice(0, 6));
                    setSetupErr(null);
                  }}
                  placeholder="••••••"
                  className="ov-input mt-1 font-mono-code tracking-[0.25em] text-center"
                />
              </label>
              <label className="text-[12px] font-semibold text-[var(--text-secondary)]">
                {s.vaultPlusConfirmPin}
                <input
                  type="password"
                  inputMode="numeric"
                  maxLength={6}
                  value={setupConfirm}
                  onChange={(e) => {
                    setSetupConfirm(e.target.value.replace(/\D/g, "").slice(0, 6));
                    setSetupErr(null);
                  }}
                  placeholder="••••••"
                  className="ov-input mt-1 font-mono-code tracking-[0.25em] text-center"
                />
              </label>
              {setupErr ? <div className="text-[12px] text-[var(--danger)]">{setupErr}</div> : null}
              <button
                type="submit"
                disabled={setupBusy || setupPin.length !== 6 || setupConfirm.length !== 6}
                className="ov-btn-primary py-2.5 text-[13px] mt-1"
              >
                {setupBusy ? (
                  <Loader2 size={14} className="animate-spin inline mr-1.5" />
                ) : null}
                {s.vaultPlusSetupTitle}
              </button>
            </div>
          </form>
        ) : !status.unlocked ? (
          <div className="max-w-[360px] mx-auto pt-4 flex flex-col items-center gap-3.5 text-center">
            <div
              className={`w-14 h-14 rounded-[16px] flex items-center justify-center ${
                status.recoveryRequired
                  ? "bg-[var(--danger-soft)] text-[var(--danger)]"
                  : "bg-[var(--accent-soft)] text-[var(--accent)]"
              }`}
            >
              {status.recoveryRequired ? <ShieldAlert size={28} /> : <ShieldPlus size={28} />}
            </div>

            <div className="text-[16px] font-bold text-[var(--text-primary)]">
              {status.recoveryRequired
                ? s.vaultPlusRecoveryRequiredTitle
                : isCoolingDown
                  ? s.vaultPlusCooldownTitle
                  : s.vaultPlusLockedTitle}
            </div>

            {status.recoveryRequired ? (
              <div className="ov-card p-3.5 w-full flex flex-col gap-2.5 text-left">
                <p className="text-[12.5px] text-[var(--text-secondary)] m-0 leading-relaxed">
                  {s.vaultPlusRecoveryRequiredDesc}
                </p>
                <button
                  type="button"
                  onClick={() => setShowRecoverModal(true)}
                  className="ov-btn-primary py-2 text-[12.5px]"
                >
                  {s.vaultPlusRecoverCta}
                </button>
                <button
                  type="button"
                  onClick={() => setShowWipeModal(true)}
                  className="ov-btn-ghost py-2 text-[12.5px] text-[var(--danger)] border-[var(--danger)]/40"
                >
                  {s.vaultPlusWipeCta}
                </button>
              </div>
            ) : isCoolingDown ? (
              <div className="ov-card p-4 w-full flex flex-col items-center gap-2">
                <div className="font-mono-code text-[28px] font-bold text-[var(--warning)]">
                  {String(Math.floor(cooldownRemSec / 60)).padStart(2, "0")}:
                  {String(cooldownRemSec % 60).padStart(2, "0")}
                </div>
                <p className="text-[12px] text-[var(--text-secondary)] m-0">
                  {s.isVi
                    ? `Đã sai ${status.failedAttempts}/${SAFE_MAX_TOTAL_FAILS} lần. Vui lòng chờ hết thời gian hoặc khôi phục bằng Recovery Key.`
                    : `Failed ${status.failedAttempts}/${SAFE_MAX_TOTAL_FAILS}. Wait for timer or use Recovery Key.`}
                </p>
                <button
                  type="button"
                  onClick={() => setShowRecoverModal(true)}
                  className="text-[12.5px] font-semibold text-[var(--accent)] bg-transparent border-0 cursor-pointer mt-1"
                >
                  {s.vaultPlusRecoverCta}
                </button>
              </div>
            ) : (
              <>
                <p className="text-[12.5px] text-[var(--text-secondary)] m-0">
                  {s.vaultPlusEnterPin}
                </p>
                {status.failedAttempts > 0 ? (
                  <div className="text-[11.5px] text-[var(--warning)] font-medium">
                    {s.isVi
                      ? `Còn ${SAFE_COOLDOWN_AFTER_FAILS - (status.failedAttempts % SAFE_COOLDOWN_AFTER_FAILS)} lần thử trước khi tạm khóa 15p · Tổng sai ${status.failedAttempts}/${SAFE_MAX_TOTAL_FAILS}`
                      : `${SAFE_COOLDOWN_AFTER_FAILS - (status.failedAttempts % SAFE_COOLDOWN_AFTER_FAILS)} tries before 15m wait · Total ${status.failedAttempts}/${SAFE_MAX_TOTAL_FAILS}`}
                  </div>
                ) : null}

                <div className="ov-pin-wrapper" onClick={() => pinInputRef.current?.focus()}>
                  <input
                    ref={pinInputRef}
                    id={pinInputId}
                    type="password"
                    inputMode="numeric"
                    autoComplete="off"
                    maxLength={SAFE_PIN_LENGTH}
                    value={pin}
                    disabled={unlockBusy}
                    autoFocus
                    onFocus={() => setPinFocused(true)}
                    onBlur={() => setPinFocused(false)}
                    onChange={(e) => {
                      const clean = e.target.value.replace(/\D/g, "").slice(0, SAFE_PIN_LENGTH);
                      setPin(clean);
                      setUnlockErr(null);
                      if (clean.length === SAFE_PIN_LENGTH) {
                        void submitUnlockPin(clean);
                      }
                    }}
                    className="ov-pin-native-input"
                  />
                  <div
                    key={shakeKey}
                    className={`ov-pin-slots ${shakeKey > 0 && unlockErr ? "ov-pin-shake" : ""}`}
                    aria-hidden="true"
                  >
                    {Array.from({ length: SAFE_PIN_LENGTH }).map((_, idx) => {
                      const isFilled = idx < pin.length;
                      const isActive =
                        pinFocused && !unlockBusy && idx === Math.min(pin.length, SAFE_PIN_LENGTH - 1);
                      return (
                        <Fragment key={idx}>
                          <div
                            className="ov-pin-slot"
                            data-active={isActive ? "true" : "false"}
                            data-filled={isFilled ? "true" : "false"}
                            data-error={unlockErr ? "true" : "false"}
                            data-loading={unlockBusy ? "true" : "false"}
                          >
                            {isFilled ? (
                              <span className="ov-pin-dot" />
                            ) : isActive ? (
                              <span className="ov-pin-caret" />
                            ) : null}
                          </div>
                          {idx === 2 ? <div className="ov-pin-gap" /> : null}
                        </Fragment>
                      );
                    })}
                  </div>
                </div>

                {unlockBusy ? (
                  <div className="text-[12px] text-[var(--accent)] inline-flex items-center gap-1.5">
                    <Loader2 size={13} className="animate-spin" />
                    <span>{s.unlocking}</span>
                  </div>
                ) : unlockErr ? (
                  <div className="text-[12px] text-[var(--danger)]">{unlockErr}</div>
                ) : null}

                <div className="flex items-center gap-3 text-[12px]">
                  <button
                    type="button"
                    onClick={() => setShowRecoverModal(true)}
                    className="text-[var(--accent)] font-semibold bg-transparent border-0 cursor-pointer"
                  >
                    {s.isVi ? "Quên PIN?" : "Forgot PIN?"}
                  </button>
                  <span className="text-[var(--border-strong)]">·</span>
                  <button
                    type="button"
                    onClick={() => setShowWipeModal(true)}
                    className="text-[var(--text-secondary)] hover:text-[var(--danger)] bg-transparent border-0 cursor-pointer"
                  >
                    {s.isVi ? "Xóa Két" : "Reset Vault+"}
                  </button>
                </div>
              </>
            )}
          </div>
        ) : (
          /* Unlocked Vault+ Workspace */
          <div className="flex flex-col gap-3">
            {showSettings ? (
              <div className="ov-card p-3.5 flex flex-col gap-3">
                <div className="flex items-center justify-between">
                  <span className="text-[13px] font-bold text-[var(--text-primary)]">
                    {s.isVi ? "Cài Đặt Vault+" : "Vault+ Settings"}
                  </span>
                  <button
                    type="button"
                    onClick={() => setShowSettings(false)}
                    className="ov-icon-btn w-6 h-6"
                  >
                    <X size={14} />
                  </button>
                </div>

                <div className="flex items-center justify-between gap-2 text-[12.5px]">
                  <span className="text-[var(--text-secondary)]">
                    {s.isVi ? "Tự khóa sau" : "Auto-lock after"}
                  </span>
                  <div className="flex gap-1">
                    {[1, 3, 5, 10].map((m) => (
                      <button
                        key={m}
                        type="button"
                        onClick={() => {
                          void sendBg({ type: "SAFE_UPDATE_SETTINGS", autoLockMinutes: m }).then(
                            () => loadStatus(false),
                          );
                        }}
                        className={`px-2 py-1 rounded-[6px] text-[12px] border cursor-pointer ${
                          status.autoLockMinutes === m
                            ? "bg-[var(--accent)] text-white border-transparent"
                            : "bg-transparent text-[var(--text-primary)] border-[var(--border)]"
                        }`}
                      >
                        {m}m
                      </button>
                    ))}
                  </div>
                </div>

                <form onSubmit={handleChangePin} className="grid grid-cols-3 gap-1.5">
                  <input
                    type="password"
                    inputMode="numeric"
                    maxLength={6}
                    value={changeCurPin}
                    onChange={(e) => setChangeCurPin(e.target.value.replace(/\D/g, "").slice(0, 6))}
                    placeholder={s.isVi ? "PIN cũ" : "Old PIN"}
                    className="ov-input text-[12px] py-1.5 text-center"
                  />
                  <input
                    type="password"
                    inputMode="numeric"
                    maxLength={6}
                    value={changeNewPin}
                    onChange={(e) => setChangeNewPin(e.target.value.replace(/\D/g, "").slice(0, 6))}
                    placeholder={s.isVi ? "PIN mới" : "New PIN"}
                    className="ov-input text-[12px] py-1.5 text-center"
                  />
                  <input
                    type="password"
                    inputMode="numeric"
                    maxLength={6}
                    value={changeConfPin}
                    onChange={(e) => setChangeConfPin(e.target.value.replace(/\D/g, "").slice(0, 6))}
                    placeholder={s.isVi ? "Nhập lại" : "Confirm"}
                    className="ov-input text-[12px] py-1.5 text-center"
                  />
                  <button
                    type="submit"
                    disabled={changeBusy}
                    className="col-span-3 ov-btn-primary py-1.5 text-[12px]"
                  >
                    {s.isVi ? "Đổi mã PIN 6 số" : "Change 6-Digit PIN"}
                  </button>
                </form>

                <button
                  type="button"
                  onClick={() => setShowWipeModal(true)}
                  className="ov-btn-ghost py-1.5 text-[12px] text-[var(--danger)] inline-flex items-center justify-center gap-1"
                >
                  <Trash2 size={13} />
                  <span>{s.vaultPlusWipeCta}</span>
                </button>
              </div>
            ) : null}

            {/* Drag-and-drop & Picker Upload Zone */}
            <div
              onDragOver={(e) => {
                e.preventDefault();
                setDragOver(true);
              }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(e) => {
                e.preventDefault();
                setDragOver(false);
                handlePickFile(e.dataTransfer.files?.[0]);
              }}
              className={`rounded-[10px] border border-dashed p-3 flex items-center justify-between gap-2 transition-colors ${
                dragOver
                  ? "border-[var(--accent)] bg-[var(--accent-soft)]"
                  : "border-[var(--border-strong)] bg-[var(--surface-subtle)]"
              }`}
            >
              <div className="flex items-center gap-2.5 min-w-0">
                <Upload size={16} className="text-[var(--accent)] shrink-0" />
                <div className="text-[12px] text-[var(--text-secondary)] truncate">
                  {s.vaultPlusEmptyHint}
                </div>
              </div>
              <input
                ref={fileInputRef}
                type="file"
                accept="application/pdf,image/*"
                className="hidden"
                onChange={(e) => {
                  handlePickFile(e.target.files?.[0]);
                  e.target.value = "";
                }}
              />
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="ov-btn-primary px-3 py-1.5 text-[12px] inline-flex items-center gap-1 shrink-0"
              >
                <Plus size={14} />
                <span>{s.vaultPlusAddDoc}</span>
              </button>
            </div>

            {pendingFile ? (
              <form
                onSubmit={handleUploadSubmit}
                className="ov-card p-3 flex flex-col gap-2 border-[var(--accent-border)]"
              >
                <div className="flex items-center justify-between">
                  <span className="text-[12.5px] font-bold text-[var(--text-primary)] truncate">
                    {pendingFile.name} ({formatSize(pendingFile.size)})
                  </span>
                  <button
                    type="button"
                    className="ov-icon-btn w-6 h-6"
                    onClick={() => setPendingFile(null)}
                  >
                    <X size={13} />
                  </button>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <input
                    type="text"
                    value={uploadTitle}
                    onChange={(e) => setUploadTitle(e.target.value)}
                    placeholder={s.isVi ? "Tên giấy tờ" : "Document Title"}
                    className="ov-input text-[12.5px] py-1.5"
                    required
                  />
                  <select
                    value={uploadCategory}
                    onChange={(e) => setUploadCategory(e.target.value as SafeDocumentCategory)}
                    className="ov-input text-[12.5px] py-1.5"
                  >
                    {DOC_CATEGORIES.map((c) => (
                      <option key={c.id} value={c.id}>
                        {s.isVi ? c.vi : c.en}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <input
                    type="text"
                    value={uploadExpires}
                    onChange={(e) => setUploadExpires(e.target.value)}
                    placeholder={s.isVi ? "HSD (YYYY-MM-DD, tùy chọn)" : "Expiry (YYYY-MM-DD)"}
                    className="ov-input text-[12.5px] py-1.5"
                  />
                  <input
                    type="text"
                    value={uploadNotes}
                    onChange={(e) => setUploadNotes(e.target.value)}
                    placeholder={s.notesPlaceholder}
                    className="ov-input text-[12.5px] py-1.5"
                  />
                </div>
                <div className="flex justify-end gap-1.5">
                  <button
                    type="button"
                    onClick={() => setPendingFile(null)}
                    className="ov-btn-ghost px-2.5 py-1 text-[12px]"
                  >
                    {s.cancel}
                  </button>
                  <button
                    type="submit"
                    disabled={uploading}
                    className="ov-btn-primary px-3 py-1 text-[12px] inline-flex items-center gap-1"
                  >
                    {uploading ? <Loader2 size={12} className="animate-spin" /> : null}
                    <span>{s.save}</span>
                  </button>
                </div>
              </form>
            ) : null}

            {/* Category Filter Pills */}
            <div className="flex flex-wrap gap-1">
              {(
                [
                  { id: "all", label: s.allItems },
                  { id: "favorites", label: s.favorites },
                  ...DOC_CATEGORIES.map((c) => ({ id: c.id, label: s.isVi ? c.vi : c.en })),
                  { id: "trash", label: s.trash },
                ] as { id: FilterTab; label: string }[]
              ).map((tab) => (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => setFilterTab(tab.id)}
                  className={`px-2.5 py-1 rounded-full text-[11.5px] font-medium border cursor-pointer transition-colors ${
                    filterTab === tab.id
                      ? "bg-[var(--accent)] text-white border-transparent"
                      : "bg-transparent text-[var(--text-secondary)] border-[var(--border)] hover:text-[var(--text-primary)]"
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            {/* Items List */}
            {filteredItems.length === 0 ? (
              <div className="py-10 text-center text-[var(--text-secondary)] text-[12.5px]">
                <div className="font-semibold text-[var(--text-primary)] mb-1">
                  {s.vaultPlusEmptyTitle}
                </div>
                <div>{s.vaultPlusEmptyHint}</div>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-2">
                {filteredItems.map((item) => {
                  const thumb = thumbUrls[item.id];
                  const isPdf = item.metadata.mimeType === "application/pdf";
                  const catLabel =
                    DOC_CATEGORIES.find((c) => c.id === item.category)?.[s.isVi ? "vi" : "en"] ??
                    item.category;

                  return (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => setPreviewItem(item)}
                      className="ov-card p-2.5 flex items-center gap-3 text-left hover:border-[var(--accent-border)] cursor-pointer transition-colors"
                    >
                      <div className="w-11 h-11 rounded-[8px] bg-[var(--accent-soft)] text-[var(--accent)] flex items-center justify-center shrink-0 overflow-hidden">
                        {thumb ? (
                          <img src={thumb} alt="" className="w-full h-full object-cover" />
                        ) : isPdf ? (
                          <FileText size={20} />
                        ) : (
                          <ImageIcon size={20} />
                        )}
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-1.5">
                          <span className="text-[13.5px] font-semibold text-[var(--text-primary)] truncate">
                            {item.metadata.title}
                          </span>
                          {item.favorite ? (
                            <Star
                              size={12}
                              className="fill-[var(--warning)] text-[var(--warning)] shrink-0"
                            />
                          ) : null}
                        </div>
                        <div className="text-[11.5px] text-[var(--text-secondary)] truncate">
                          {catLabel} · {formatSize(item.metadata.originalSize)}
                          {item.metadata.expiresAt ? ` · HSD ${item.metadata.expiresAt}` : ""}
                        </div>
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Recovery Key Modal */}
      {showRecoverModal ? (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
          <form
            onSubmit={handleRecoverSubmit}
            className="w-full max-w-[360px] bg-[var(--surface)] border border-[var(--border)] rounded-[14px] p-4 flex flex-col gap-2.5 shadow-xl"
          >
            <div className="text-[14.5px] font-bold text-[var(--text-primary)]">
              {s.vaultPlusRecoverCta}
            </div>
            <input
              type="text"
              value={recoverKeyInput}
              onChange={(e) => {
                setRecoverKeyInput(e.target.value);
                setRecoverErr(null);
              }}
              placeholder="OVRK-XXXX-XXXX-..."
              className="ov-input font-mono-code text-[12px]"
              required
            />
            <input
              type="password"
              inputMode="numeric"
              maxLength={6}
              value={recoverNewPin}
              onChange={(e) => setRecoverNewPin(e.target.value.replace(/\D/g, "").slice(0, 6))}
              placeholder={s.isVi ? "Mã PIN mới (6 số)" : "New 6-Digit PIN"}
              className="ov-input text-[12.5px] text-center"
              required
            />
            <input
              type="password"
              inputMode="numeric"
              maxLength={6}
              value={recoverConfirmPin}
              onChange={(e) => setRecoverConfirmPin(e.target.value.replace(/\D/g, "").slice(0, 6))}
              placeholder={s.vaultPlusConfirmPin}
              className="ov-input text-[12.5px] text-center"
              required
            />
            <label className="flex items-center gap-2 text-[12px] text-[var(--text-secondary)]">
              <input
                type="checkbox"
                checked={recoverRotate}
                onChange={(e) => setRecoverRotate(e.target.checked)}
              />
              <span>
                {s.isVi ? "Tạo Recovery Key mới sau khi khôi phục" : "Rotate Recovery Key"}
              </span>
            </label>
            {recoverErr ? <div className="text-[12px] text-[var(--danger)]">{recoverErr}</div> : null}
            <div className="flex justify-end gap-2 mt-1">
              <button
                type="button"
                onClick={() => setShowRecoverModal(false)}
                className="ov-btn-ghost px-3 py-1.5 text-[12px]"
              >
                {s.cancel}
              </button>
              <button
                type="submit"
                disabled={recoverBusy}
                className="ov-btn-primary px-3 py-1.5 text-[12px]"
              >
                {recoverBusy ? <Loader2 size={12} className="animate-spin inline mr-1" /> : null}
                {s.restoreFromTrash}
              </button>
            </div>
          </form>
        </div>
      ) : null}

      {/* Nuclear Wipe Modal */}
      {showWipeModal ? (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
          <form
            onSubmit={handleWipeSubmit}
            className="w-full max-w-[360px] bg-[var(--surface)] border border-[var(--border)] rounded-[14px] p-4 flex flex-col gap-2.5 shadow-xl"
          >
            <div className="text-[14.5px] font-bold text-[var(--danger)]">{s.vaultPlusWipeCta}</div>
            <p className="text-[12px] text-[var(--text-secondary)] m-0">
              {s.isVi
                ? "Hành động này sẽ xóa vĩnh viễn toàn bộ tài liệu Vault+ trên Cloudflare R2 & D1. Nhập Master Password để xác nhận."
                : "This permanently wipes all Vault+ files from Cloudflare R2 & D1. Enter Master Password to confirm."}
            </p>
            <input
              type="password"
              value={wipeMasterPw}
              onChange={(e) => {
                setWipeMasterPw(e.target.value);
                setWipeErr(null);
              }}
              placeholder={s.enterMasterPrompt}
              className="ov-input text-[12.5px]"
              required
            />
            {wipeErr ? <div className="text-[12px] text-[var(--danger)]">{wipeErr}</div> : null}
            <div className="flex justify-end gap-2 mt-1">
              <button
                type="button"
                onClick={() => setShowWipeModal(false)}
                className="ov-btn-ghost px-3 py-1.5 text-[12px]"
              >
                {s.cancel}
              </button>
              <button
                type="submit"
                disabled={wipeBusy}
                className="px-3 py-1.5 rounded-[8px] bg-[var(--danger)] text-white font-semibold text-[12px] border-0 cursor-pointer"
              >
                {wipeBusy ? <Loader2 size={12} className="animate-spin inline mr-1" /> : null}
                {s.deletePermanently}
              </button>
            </div>
          </form>
        </div>
      ) : null}

      {previewItem ? (
        <SafePreviewModal
          item={previewItem}
          language={language}
          onClose={() => setPreviewItem(null)}
          onUpdated={loadItems}
          onSafeLocked={() => void loadStatus(false)}
          onToast={onToast}
        />
      ) : null}
    </div>
  );
}

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
  ArrowLeft,
  Check,
  Copy,
  Download,
  Edit3,
  Eye,
  FileText,
  Folder,
  FolderInput,
  FolderPlus,
  Image as ImageIcon,
  Info,
  LayoutGrid,
  LayoutList,
  Loader2,
  Lock,
  MoreVertical,
  Plus,
  RefreshCw,
  RotateCcw,
  Search,
  Settings,
  ShieldAlert,
  ShieldPlus,
  Star,
  StickyNote,
  Trash2,
  Upload,
  X,
} from "lucide-react";
import { Fragment, useCallback, useEffect, useId, useMemo, useRef, useState } from "react";
import { LOCAL_KEYS } from "../../../shared/constants.js";
import { formatLocalTimestamp, getStrings, type AppLanguage } from "../../../shared/i18n.js";
import { apiDownloadSafeBinary, apiUploadSafeBinary } from "../../../shared/safe-api.js";
import type {
  DecryptedSafeItem,
  SafeBlobAuthResult,
  SafePrepareUploadResult,
  SafeStatusResponse,
} from "../../../shared/safe-types.js";
import { sendBg } from "../../components/bridge.js";
import { ConfirmDialog } from "../../components/ConfirmDialog.js";
import { SafePreviewModal } from "./SafePreviewModal.js";

interface VaultPlusPanelProps {
  language: AppLanguage;
  onClose: () => void;
  onToast: (msg: string, kind?: "success" | "error") => void;
}

type FilterTab = "all" | "favorites" | SafeDocumentCategory | "trash";
type SafeSortMode = "NAME_ASC" | "NAME_DESC" | "DATE_DESC" | "DATE_ASC" | "SIZE_DESC";
type SafeViewMode = "list" | "grid";

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

/**
 * Isolated 1-second timer so parent VaultPlusPanel (and open SafePreviewModal!) never re-render every second.
 */
function SafeCountdownBadge({
  expiresAtMs,
  onExpired,
}: {
  expiresAtMs: number | null;
  onExpired: () => void;
}) {
  const [remSec, setRemSec] = useState(() =>
    expiresAtMs ? Math.max(0, Math.ceil((expiresAtMs - Date.now()) / 1000)) : 0,
  );
  const onExpiredRef = useRef(onExpired);
  onExpiredRef.current = onExpired;

  useEffect(() => {
    if (!expiresAtMs) {
      setRemSec(0);
      return;
    }
    const tick = () => {
      const next = Math.max(0, Math.ceil((expiresAtMs - Date.now()) / 1000));
      setRemSec(next);
      if (next <= 0) {
        onExpiredRef.current();
      }
    };
    tick();
    const id = window.setInterval(tick, 1000);
    return () => window.clearInterval(id);
  }, [expiresAtMs]);

  if (!expiresAtMs) return null;
  return (
    <span className="font-mono-code tabular-nums">
      {Math.floor(remSec / 60)}:{String(remSec % 60).padStart(2, "0")}
    </span>
  );
}

/**
 * Isolated 1-second cooldown countdown timer when locked for 15 minutes.
 */
function SafeCooldownTimer({
  lockedUntilMs,
  onExpired,
}: {
  lockedUntilMs: number;
  onExpired: () => void;
}) {
  const [remSec, setRemSec] = useState(() =>
    Math.max(0, Math.ceil((lockedUntilMs - Date.now()) / 1000)),
  );
  const onExpiredRef = useRef(onExpired);
  onExpiredRef.current = onExpired;

  useEffect(() => {
    const tick = () => {
      const next = Math.max(0, Math.ceil((lockedUntilMs - Date.now()) / 1000));
      setRemSec(next);
      if (next <= 0) {
        onExpiredRef.current();
      }
    };
    tick();
    const id = window.setInterval(tick, 1000);
    return () => window.clearInterval(id);
  }, [lockedUntilMs]);

  return (
    <div className="font-mono-code text-[28px] font-bold text-[var(--warning)] tabular-nums">
      {String(Math.floor(remSec / 60)).padStart(2, "0")}:{String(remSec % 60).padStart(2, "0")}
    </div>
  );
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
  const [customFolders, setCustomFolders] = useState<string[]>([]);
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
  const [activeFolder, setActiveFolder] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [viewMode, setViewMode] = useState<SafeViewMode>("list");
  const [sortMode, setSortMode] = useState<SafeSortMode>("DATE_DESC");

  const [showSettings, setShowSettings] = useState(false);
  const [changeCurPin, setChangeCurPin] = useState("");
  const [changeNewPin, setChangeNewPin] = useState("");
  const [changeConfPin, setChangeConfPin] = useState("");
  const [changeBusy, setChangeBusy] = useState(false);

  // Folder modals
  const [showCreateFolder, setShowCreateFolder] = useState(false);
  const [newFolderName, setNewFolderName] = useState("");
  const [renamingFolder, setRenamingFolder] = useState<string | null>(null);
  const [renameFolderInput, setRenameFolderInput] = useState("");
  const [deletingFolder, setDeletingFolder] = useState<string | null>(null);
  const [folderBusy, setFolderBusy] = useState(false);
  const [openFolderMenu, setOpenFolderMenu] = useState<string | null>(null);

  // Item context menu & action sheets
  const [openItemMenuId, setOpenItemMenuId] = useState<string | null>(null);
  const [editingItem, setEditingItem] = useState<DecryptedSafeItem | null>(null);
  const [editTitle, setEditTitle] = useState("");
  const [editCategory, setEditCategory] = useState<SafeDocumentCategory>("id_card");
  const [editFolderName, setEditFolderName] = useState("");
  const [editExpiresAt, setEditExpiresAt] = useState("");
  const [editNotes, setEditNotes] = useState("");
  const [editBusy, setEditBusy] = useState(false);

  const [movingItem, setMovingItem] = useState<DecryptedSafeItem | null>(null);
  const [moveInlineNewFolder, setMoveInlineNewFolder] = useState("");
  const [notesModalItem, setNotesModalItem] = useState<DecryptedSafeItem | null>(null);
  const [notesDraft, setNotesDraft] = useState("");
  const [detailsModalItem, setDetailsModalItem] = useState<DecryptedSafeItem | null>(null);
  const [deletingItem, setDeletingItem] = useState<DecryptedSafeItem | null>(null);

  // Upload states
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [uploadTitle, setUploadTitle] = useState("");
  const [uploadCategory, setUploadCategory] = useState<SafeDocumentCategory>("id_card");
  const [uploadFolder, setUploadFolder] = useState("");
  const [uploadInlineFolder, setUploadInlineFolder] = useState("");
  const [showUploadInlineFolder, setShowUploadInlineFolder] = useState(false);
  const [uploadExpires, setUploadExpires] = useState("");
  const [uploadNotes, setUploadNotes] = useState("");
  const [uploading, setUploading] = useState(false);
  const [uploadStage, setUploadStage] = useState<string | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const dragDepthRef = useRef(0);

  // Thumbnails & Preview
  const [thumbUrls, setThumbUrls] = useState<Record<string, string>>({});
  const thumbUrlsRef = useRef<Record<string, string>>({});
  const [previewItemId, setPreviewItemId] = useState<string | null>(null);

  // Load persisted View & Sort preferences
  useEffect(() => {
    void chrome.storage?.local
      ?.get(LOCAL_KEYS.SAFE_UI_PREFS)
      .then((res) => {
        const prefs = res?.[LOCAL_KEYS.SAFE_UI_PREFS] as
          | { viewMode?: SafeViewMode; sortMode?: SafeSortMode }
          | undefined;
        if (prefs?.viewMode === "list" || prefs?.viewMode === "grid") {
          setViewMode(prefs.viewMode);
        }
        if (prefs?.sortMode) {
          setSortMode(prefs.sortMode);
        }
      })
      .catch(() => undefined);
  }, []);

  const persistUiPrefs = useCallback((nextView: SafeViewMode, nextSort: SafeSortMode) => {
    void chrome.storage?.local
      ?.set({ [LOCAL_KEYS.SAFE_UI_PREFS]: { viewMode: nextView, sortMode: nextSort } })
      .catch(() => undefined);
  }, []);

  const revokeAllThumbs = useCallback(() => {
    for (const url of Object.values(thumbUrlsRef.current)) {
      URL.revokeObjectURL(url);
    }
    thumbUrlsRef.current = {};
    setThumbUrls({});
  }, []);

  const loadFolders = useCallback(async () => {
    try {
      const list = await sendBg<string[]>({ type: "SAFE_LIST_FOLDERS" });
      setCustomFolders(Array.isArray(list) ? list : []);
    } catch {
      // Ignore transient error
    }
  }, []);

  const loadItems = useCallback(async () => {
    const [list] = await Promise.all([
      sendBg<DecryptedSafeItem[]>({ type: "SAFE_LIST_ITEMS" }),
      loadFolders(),
    ]);
    setItems(list);
  }, [loadFolders]);

  const loadStatus = useCallback(
    async (forceSync = true) => {
      try {
        const st = await sendBg<SafeStatusResponse>({ type: "SAFE_GET_STATUS", forceSync });
        setStatus(st);
        if (st.unlocked) {
          await loadItems();
        } else {
          setItems([]);
          setPreviewItemId(null);
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

  // Close menus on outside click
  useEffect(() => {
    if (!openItemMenuId && !openFolderMenu) return;
    const onDown = () => {
      setOpenItemMenuId(null);
      setOpenFolderMenu(null);
    };
    window.addEventListener("pointerdown", onDown);
    return () => window.removeEventListener("pointerdown", onDown);
  }, [openItemMenuId, openFolderMenu]);

  // Load decrypted thumbnails in-memory
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

  const handleClosePreview = useCallback(() => setPreviewItemId(null), []);
  const handleSafeLockedFromModal = useCallback(() => {
    void loadStatus(false);
  }, [loadStatus]);

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
      setActiveFolder(null);
      revokeAllThumbs();
      setItems([]);
      setCustomFolders([]);
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
    setUploadFolder(activeFolder ?? "");
    setUploadInlineFolder("");
    setShowUploadInlineFolder(false);
    setUploadExpires("");
    setUploadNotes("");
  };

  const handleUploadSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!pendingFile || uploading) return;
    setUploading(true);
    setUploadStage(s.isVi ? "Đang chuẩn bị dữ liệu..." : "Preparing file...");
    try {
      const targetFolder = showUploadInlineFolder
        ? uploadInlineFolder.trim()
        : uploadFolder.trim();
      const rawBytes = new Uint8Array(await pendingFile.arrayBuffer());
      const sha256 = await sha256HexBytes(rawBytes);
      const thumbPlain = await generateImageThumbnail(pendingFile);

      setUploadStage(s.isVi ? "Đang khởi tạo bản ghi E2EE..." : "Creating E2EE record...");
      const prep = await sendBg<SafePrepareUploadResult>({
        type: "SAFE_PREPARE_UPLOAD",
        title: uploadTitle.trim() || pendingFile.name,
        fileName: pendingFile.name,
        mimeType: pendingFile.type || "application/octet-stream",
        category: uploadCategory,
        folderName: targetFolder || null,
        notes: uploadNotes.trim() || null,
        expiresAt: uploadExpires.trim() || null,
        originalSize: rawBytes.byteLength,
        originalSha256: sha256,
        hasThumb: Boolean(thumbPlain),
      });

      setUploadStage(s.isVi ? "Đang mã hóa OVSF..." : "Encrypting OVSF...");
      const fileKey = base64ToBytes(prep.fileKeyB64);
      const ovsfBlob = await encryptSafeFileBinary(rawBytes, fileKey, prep.itemId, "blob");
      zeroize(rawBytes, fileKey);

      setUploadStage(s.isVi ? "Đang tải lên đám mây R2..." : "Uploading to R2...");
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
      setUploadStage(null);
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

  // Derive all folders (merged local custom folders + folderName from active decrypted items)
  const { allFolders, folderCounts } = useMemo(() => {
    const counts = new Map<string, number>();
    const canonical = new Map<string, string>();
    for (const f of customFolders) {
      const clean = f.trim();
      if (!clean) continue;
      if (!canonical.has(clean.toLowerCase())) {
        canonical.set(clean.toLowerCase(), clean);
      }
    }
    for (const item of items) {
      if (item.deletedAt) continue;
      const fn = item.metadata.folderName?.trim();
      if (!fn) continue;
      const key = fn.toLowerCase();
      if (!canonical.has(key)) canonical.set(key, fn);
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
    const sorted = Array.from(canonical.values()).sort((a, b) => a.localeCompare(b));
    return { allFolders: sorted, folderCounts: counts };
  }, [customFolders, items]);

  const handleCreateFolderSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const clean = newFolderName.trim();
    if (!clean || folderBusy) return;
    setFolderBusy(true);
    try {
      const updated = await sendBg<string[]>({ type: "SAFE_CREATE_FOLDER", name: clean });
      setCustomFolders(updated);
      setNewFolderName("");
      setShowCreateFolder(false);
      onToast(s.isVi ? `Đã tạo thư mục "${clean}".` : `Created folder "${clean}".`, "success");
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Failed", "error");
    } finally {
      setFolderBusy(false);
    }
  };

  const handleRenameFolderSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!renamingFolder || !renameFolderInput.trim() || folderBusy) return;
    const oldName = renamingFolder;
    const newName = renameFolderInput.trim();
    setFolderBusy(true);
    try {
      const res = await sendBg<{ folders: string[]; updatedCount: number }>({
        type: "SAFE_RENAME_FOLDER",
        oldName,
        newName,
      });
      setCustomFolders(res.folders);
      if (activeFolder?.toLowerCase() === oldName.toLowerCase()) {
        setActiveFolder(newName);
      }
      setRenamingFolder(null);
      await loadItems();
      onToast(s.isVi ? `Đã đổi tên thành "${newName}".` : `Renamed to "${newName}".`, "success");
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Rename failed", "error");
    } finally {
      setFolderBusy(false);
    }
  };

  const handleConfirmDeleteFolder = async () => {
    if (!deletingFolder || folderBusy) return;
    const target = deletingFolder;
    setFolderBusy(true);
    try {
      const res = await sendBg<{ folders: string[]; updatedCount: number }>({
        type: "SAFE_DELETE_FOLDER",
        name: target,
      });
      setCustomFolders(res.folders);
      if (activeFolder?.toLowerCase() === target.toLowerCase()) {
        setActiveFolder(null);
      }
      setDeletingFolder(null);
      await loadItems();
      onToast(
        s.isVi
          ? `Đã xóa thư mục "${target}". Các tệp đã được đưa về gốc.`
          : `Deleted "${target}". Items moved to root.`,
        "success",
      );
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Delete failed", "error");
    } finally {
      setFolderBusy(false);
    }
  };

  // Item Quick Actions
  const handleToggleFavorite = async (item: DecryptedSafeItem) => {
    setOpenItemMenuId(null);
    try {
      await sendBg({
        type: "SAFE_UPDATE_ITEM",
        id: item.id,
        title: item.metadata.title,
        category: item.category,
        folderName: item.metadata.folderName ?? null,
        notes: item.metadata.notes ?? null,
        expiresAt: item.metadata.expiresAt ?? null,
        favorite: !item.favorite,
      });
      await loadItems();
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Failed", "error");
    }
  };

  const openEditModal = (item: DecryptedSafeItem) => {
    setOpenItemMenuId(null);
    setEditingItem(item);
    setEditTitle(item.metadata.title);
    setEditCategory(item.category);
    setEditFolderName(item.metadata.folderName ?? "");
    setEditExpiresAt(item.metadata.expiresAt ?? "");
    setEditNotes(item.metadata.notes ?? "");
  };

  const handleSaveEditModal = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingItem || !editTitle.trim() || editBusy) return;
    setEditBusy(true);
    try {
      await sendBg({
        type: "SAFE_UPDATE_ITEM",
        id: editingItem.id,
        title: editTitle.trim(),
        category: editCategory,
        folderName: editFolderName.trim() || null,
        notes: editNotes.trim() || null,
        expiresAt: editExpiresAt.trim() || null,
        favorite: editingItem.favorite,
      });
      setEditingItem(null);
      await loadItems();
      onToast(s.isVi ? "Đã cập nhật tài liệu." : "Document updated.", "success");
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Failed", "error");
    } finally {
      setEditBusy(false);
    }
  };

  const handleMoveToFolder = async (item: DecryptedSafeItem, targetFolder: string | null) => {
    setEditBusy(true);
    try {
      const clean = targetFolder?.trim() || null;
      await sendBg({
        type: "SAFE_UPDATE_ITEM",
        id: item.id,
        title: item.metadata.title,
        category: item.category,
        folderName: clean,
        notes: item.metadata.notes ?? null,
        expiresAt: item.metadata.expiresAt ?? null,
        favorite: item.favorite,
      });
      setMovingItem(null);
      setMoveInlineNewFolder("");
      await loadItems();
      onToast(
        clean
          ? s.isVi
            ? `Đã chuyển vào "${clean}".`
            : `Moved to "${clean}".`
          : s.isVi
            ? "Đã đưa về thư mục gốc."
            : "Moved to root.",
        "success",
      );
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Move failed", "error");
    } finally {
      setEditBusy(false);
    }
  };

  const handleSaveQuickNotes = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!notesModalItem || editBusy) return;
    setEditBusy(true);
    try {
      await sendBg({
        type: "SAFE_UPDATE_ITEM",
        id: notesModalItem.id,
        title: notesModalItem.metadata.title,
        category: notesModalItem.category,
        folderName: notesModalItem.metadata.folderName ?? null,
        notes: notesDraft.trim() || null,
        expiresAt: notesModalItem.metadata.expiresAt ?? null,
        favorite: notesModalItem.favorite,
      });
      setNotesModalItem(null);
      await loadItems();
      onToast(s.isVi ? "Đã lưu ghi chú." : "Notes saved.", "success");
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Save failed", "error");
    } finally {
      setEditBusy(false);
    }
  };

  const handleConfirmDeleteItem = async () => {
    if (!deletingItem) return;
    const item = deletingItem;
    setDeletingItem(null);
    const permanent = Boolean(item.deletedAt);
    try {
      await sendBg({ type: "SAFE_DELETE_ITEM", id: item.id, permanent });
      await loadStatus(true);
      onToast(
        permanent
          ? s.isVi
            ? "Đã xóa vĩnh viễn khỏi R2."
            : "Permanently deleted."
          : s.isVi
            ? "Đã chuyển vào Thùng rác."
            : "Moved to Trash.",
        "success",
      );
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Delete failed", "error");
    }
  };

  const handleRestoreItem = async (item: DecryptedSafeItem) => {
    setOpenItemMenuId(null);
    try {
      await sendBg({ type: "SAFE_RESTORE_ITEM", id: item.id });
      await loadStatus(true);
      onToast(s.isVi ? "Đã khôi phục tài liệu." : "Document restored.", "success");
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Restore failed", "error");
    }
  };

  // Filter & Sort visible folders + items
  const q = searchQuery.trim().toLowerCase();
  const visibleFolders = useMemo(() => {
    if (activeFolder !== null || filterTab !== "all") return [];
    const list = q
      ? allFolders.filter((f) => f.toLowerCase().includes(q))
      : allFolders;
    return sortMode === "NAME_DESC" ? [...list].reverse() : list;
  }, [activeFolder, filterTab, allFolders, q, sortMode]);

  const filteredItems = useMemo(() => {
    const base = items.filter((item) => {
      if (filterTab === "trash") return Boolean(item.deletedAt);
      if (item.deletedAt) return false;

      if (activeFolder !== null) {
        if ((item.metadata.folderName ?? "").trim().toLowerCase() !== activeFolder.toLowerCase()) {
          return false;
        }
      } else if (!q && filterTab === "all") {
        // At root All Items without active search, items inside folders live in their folders (mirrors Android)
        if (item.metadata.folderName?.trim()) return false;
      }

      if (filterTab === "favorites" && !item.favorite) return false;
      if (filterTab !== "all" && filterTab !== "favorites" && item.category !== filterTab) {
        return false;
      }

      if (!q) return true;
      const hay = [
        item.metadata.title,
        item.metadata.fileName,
        item.metadata.notes ?? "",
        item.metadata.folderName ?? "",
      ]
        .join(" ")
        .toLowerCase();
      return hay.includes(q);
    });

    return [...base].sort((a, b) => {
      switch (sortMode) {
        case "NAME_ASC":
          return a.metadata.title.localeCompare(b.metadata.title);
        case "NAME_DESC":
          return b.metadata.title.localeCompare(a.metadata.title);
        case "DATE_ASC":
          return (a.createdAt ?? "").localeCompare(b.createdAt ?? "");
        case "SIZE_DESC":
          return b.metadata.originalSize - a.metadata.originalSize;
        case "DATE_DESC":
        default:
          return (b.updatedAt ?? b.createdAt ?? "").localeCompare(a.updatedAt ?? a.createdAt ?? "");
      }
    });
  }, [items, filterTab, activeFolder, q, sortMode]);

  const previewItem = useMemo(
    () => (previewItemId ? items.find((i) => i.id === previewItemId) ?? null : null),
    [items, previewItemId],
  );

  const isCoolingDown = Boolean(status.lockedUntilMs && status.lockedUntilMs > Date.now());

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center bg-[var(--surface)]">
        <Loader2 size={24} className="animate-spin text-[var(--accent)]" />
      </div>
    );
  }

  return (
    <div
      className="flex-1 flex flex-col min-h-0 bg-[var(--surface)] relative"
      onDragEnter={(e) => {
        if (!status.unlocked || !e.dataTransfer?.types?.includes("Files")) return;
        e.preventDefault();
        dragDepthRef.current += 1;
        setDragOver(true);
      }}
      onDragOver={(e) => {
        if (!status.unlocked || !e.dataTransfer?.types?.includes("Files")) return;
        e.preventDefault();
      }}
      onDragLeave={(e) => {
        if (!status.unlocked || dragDepthRef.current === 0) return;
        e.preventDefault();
        dragDepthRef.current = Math.max(0, dragDepthRef.current - 1);
        if (dragDepthRef.current === 0) setDragOver(false);
      }}
      onDrop={(e) => {
        if (!status.unlocked) return;
        dragDepthRef.current = 0;
        setDragOver(false);
        const file = e.dataTransfer?.files?.[0];
        if (!file) return;
        e.preventDefault();
        handlePickFile(file);
      }}
    >
      {/* Header */}
      <div className="px-4 py-3 border-b border-[var(--border)] flex items-center justify-between gap-2 shrink-0">
        {status.unlocked && activeFolder ? (
          <div className="flex items-center gap-2 min-w-0">
            <button
              type="button"
              onClick={() => setActiveFolder(null)}
              className="ov-icon-btn w-7 h-7 shrink-0"
              title={s.back}
              aria-label={s.back}
            >
              <ArrowLeft size={16} />
            </button>
            <div className="w-8 h-8 rounded-[9px] bg-[#FEF3C7] dark:bg-[#3A2B12] flex items-center justify-center shrink-0">
              <Folder size={17} className="fill-[#FBBF24] text-[#D97706]" />
            </div>
            <div className="min-w-0">
              <div className="text-[14.5px] font-bold text-[var(--text-primary)] truncate leading-tight">
                {activeFolder}
              </div>
              <div className="text-[11.5px] text-[var(--text-secondary)] truncate flex items-center gap-1 mt-0.5">
                <span>
                  {folderCounts.get(activeFolder.toLowerCase()) ?? 0}{" "}
                  {s.isVi ? "tài liệu" : "files"}
                </span>
                <span className="inline-flex items-center px-1.5 py-0.5 rounded-[5px] bg-[var(--surface-subtle)] border border-[var(--border)]/60 text-[10.5px] font-medium text-[var(--text-secondary)]">
                  <SafeCountdownBadge
                    expiresAtMs={status.unlockedExpiresAtMs}
                    onExpired={handleSafeLockedFromModal}
                  />
                </span>
              </div>
            </div>
          </div>
        ) : (
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="w-8 h-8 rounded-[9px] bg-[var(--accent-soft)] text-[var(--accent)] flex items-center justify-center shrink-0">
              <ShieldPlus size={18} />
            </div>
            <div className="min-w-0">
              <div className="text-[14.5px] font-bold text-[var(--text-primary)] leading-tight">
                {s.vaultPlus}
              </div>
              <div className="text-[11.5px] text-[var(--text-secondary)] truncate flex items-center gap-1 mt-0.5">
                {status.unlocked ? (
                  <>
                    <span>
                      {formatSize(status.usedBytes)} · {status.itemCount}{" "}
                      {s.isVi ? "mục" : "files"}
                    </span>
                    <span className="inline-flex items-center px-1.5 py-0.5 rounded-[5px] bg-[var(--surface-subtle)] border border-[var(--border)]/60 text-[10.5px] font-medium text-[var(--text-secondary)]">
                      <SafeCountdownBadge
                        expiresAtMs={status.unlockedExpiresAtMs}
                        onExpired={handleSafeLockedFromModal}
                      />
                    </span>
                  </>
                ) : (
                  s.vaultPlusSubtitle
                )}
              </div>
            </div>
          </div>
        )}

        <div className="flex items-center gap-1 shrink-0">
          {status.unlocked ? (
            <>
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
                className="ov-btn-primary px-2.5 py-1.5 text-[12px] inline-flex items-center gap-1 mr-0.5 shrink-0"
                title={s.vaultPlusAddDoc}
              >
                <Plus size={13.5} />
                <span>{s.isVi ? "Thêm" : "Upload"}</span>
              </button>
              {!activeFolder ? (
                <button
                  type="button"
                  className="ov-icon-btn w-7 h-7"
                  onClick={() => {
                    setNewFolderName("");
                    setShowCreateFolder(true);
                  }}
                  title={s.isVi ? "Tạo thư mục mới" : "New Folder"}
                  aria-label={s.isVi ? "Tạo thư mục mới" : "New Folder"}
                >
                  <FolderPlus size={15} />
                </button>
              ) : null}
              <button
                type="button"
                className="ov-icon-btn w-7 h-7"
                onClick={() => void loadStatus(true)}
                title={s.syncNow}
                aria-label={s.syncNow}
              >
                <RefreshCw size={14} />
              </button>
              <button
                type="button"
                data-active={showSettings}
                className="ov-icon-btn w-7 h-7"
                onClick={() => setShowSettings((v) => !v)}
                title={s.settingsTitle}
                aria-label={s.settingsTitle}
              >
                <Settings size={14} />
              </button>
              <button
                type="button"
                className="ov-icon-btn w-7 h-7"
                onClick={() => {
                  void sendBg({ type: "SAFE_LOCK" }).then(() => loadStatus(false));
                }}
                title={s.lockNow}
                aria-label={s.lockNow}
              >
                <Lock size={14} />
              </button>
            </>
          ) : null}
          <button type="button" className="ov-icon-btn w-7 h-7" onClick={onClose} title={s.close}>
            <X size={16} />
          </button>
        </div>
      </div>

      {status.unlocked && dragOver ? (
        <div className="absolute inset-3 top-[64px] z-40 rounded-[14px] border-2 border-dashed border-[var(--accent)] bg-[var(--surface)]/95 flex flex-col items-center justify-center gap-2 pointer-events-none shadow-lg">
          <div className="w-11 h-11 rounded-[12px] bg-[var(--accent-soft)] text-[var(--accent)] flex items-center justify-center">
            <Upload size={20} />
          </div>
          <div className="text-[13px] font-semibold text-[var(--text-primary)] px-4 text-center">
            {activeFolder
              ? s.isVi
                ? `Thả tệp để mã hóa vào "${activeFolder}"`
                : `Drop file to encrypt into "${activeFolder}"`
              : s.isVi
                ? "Thả PDF hoặc Ảnh (≤ 5 MB) để mã hóa OVSF"
                : "Drop PDF or image (≤ 5 MB) to encrypt"}
          </div>
        </div>
      ) : null}

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
                {setupBusy ? <Loader2 size={14} className="animate-spin inline mr-1.5" /> : null}
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
            ) : isCoolingDown && status.lockedUntilMs ? (
              <div className="ov-card p-4 w-full flex flex-col items-center gap-2">
                <SafeCooldownTimer
                  lockedUntilMs={status.lockedUntilMs}
                  onExpired={() => void loadStatus(true)}
                />
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
          /* Unlocked Vault+ Workspace (Synchronized with commit f269f53!) */
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
                    disabled={uploading}
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
                <div className="grid grid-cols-2 gap-2 items-center">
                  {showUploadInlineFolder ? (
                    <div className="flex items-center gap-1">
                      <input
                        type="text"
                        value={uploadInlineFolder}
                        onChange={(e) => setUploadInlineFolder(e.target.value)}
                        placeholder={s.isVi ? "Tên thư mục mới" : "New folder name"}
                        className="ov-input text-[12.5px] py-1.5 flex-1"
                        autoFocus
                      />
                      <button
                        type="button"
                        onClick={() => setShowUploadInlineFolder(false)}
                        className="ov-icon-btn w-7 h-7 shrink-0"
                        title={s.cancel}
                      >
                        <X size={13} />
                      </button>
                    </div>
                  ) : (
                    <div className="flex items-center gap-1">
                      <select
                        value={uploadFolder}
                        onChange={(e) => setUploadFolder(e.target.value)}
                        className="ov-input text-[12.5px] py-1.5 flex-1"
                        aria-label={s.folderLabel}
                      >
                        <option value="">{s.noFolder}</option>
                        {allFolders.map((fn) => (
                          <option key={fn} value={fn}>
                            {fn}
                          </option>
                        ))}
                      </select>
                      <button
                        type="button"
                        onClick={() => setShowUploadInlineFolder(true)}
                        className="ov-icon-btn w-8 h-8 shrink-0"
                        title={s.isVi ? "Tạo thư mục mới" : "New Folder"}
                      >
                        <FolderPlus size={14} />
                      </button>
                    </div>
                  )}
                  <input
                    type="text"
                    value={uploadExpires}
                    onChange={(e) => setUploadExpires(e.target.value)}
                    placeholder={s.isVi ? "HSD (YYYY-MM-DD)" : "Expiry (YYYY-MM-DD)"}
                    className="ov-input text-[12.5px] py-1.5"
                  />
                </div>
                <input
                  type="text"
                  value={uploadNotes}
                  onChange={(e) => setUploadNotes(e.target.value)}
                  placeholder={s.notesPlaceholder}
                  className="ov-input text-[12.5px] py-1.5"
                />
                {uploadStage ? (
                  <div className="flex items-center gap-2 text-[12px] text-[var(--accent)] font-medium">
                    <Loader2 size={13} className="animate-spin shrink-0" />
                    <span>{uploadStage}</span>
                  </div>
                ) : null}
                <div className="flex justify-end gap-1.5">
                  <button
                    type="button"
                    onClick={() => setPendingFile(null)}
                    disabled={uploading}
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

            {/* Search, Sort & List/Grid View Toolbar */}
            <div className="flex items-center gap-1.5">
              <div className="relative flex-1 min-w-0">
                <Search
                  size={13.5}
                  className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--text-muted)] pointer-events-none"
                />
                <input
                  type="search"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder={s.isVi ? "Tìm trong Vault+..." : "Search Vault+..."}
                  className="ov-search-input h-[32px] py-1 text-[12.5px]"
                />
              </div>

              <select
                value={sortMode}
                onChange={(e) => {
                  const nextSort = e.target.value as SafeSortMode;
                  setSortMode(nextSort);
                  persistUiPrefs(viewMode, nextSort);
                }}
                className="ov-select ov-select-compact"
                aria-label={s.isVi ? "Sắp xếp" : "Sort"}
              >
                <option value="DATE_DESC">{s.isVi ? "Mới nhất" : "Newest"}</option>
                <option value="DATE_ASC">{s.isVi ? "Cũ nhất" : "Oldest"}</option>
                <option value="NAME_ASC">{s.isVi ? "Tên A→Z" : "Name A→Z"}</option>
                <option value="NAME_DESC">{s.isVi ? "Tên Z→A" : "Name Z→A"}</option>
                <option value="SIZE_DESC">{s.isVi ? "Dung lượng" : "Largest"}</option>
              </select>

              <div className="inline-flex items-center h-[32px] rounded-[9px] p-0.5 bg-[var(--surface-subtle)] border border-[var(--border)] shrink-0">
                <button
                  type="button"
                  onClick={() => {
                    setViewMode("list");
                    persistUiPrefs("list", sortMode);
                  }}
                  className={`p-1.5 rounded-[6px] border-0 cursor-pointer transition-colors ${
                    viewMode === "list"
                      ? "bg-[var(--surface)] text-[var(--accent)] shadow-xs"
                      : "bg-transparent text-[var(--text-secondary)]"
                  }`}
                  title={s.isVi ? "Danh sách" : "List"}
                  aria-label={s.isVi ? "Danh sách" : "List"}
                >
                  <LayoutList size={14} />
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setViewMode("grid");
                    persistUiPrefs("grid", sortMode);
                  }}
                  className={`p-1.5 rounded-[6px] border-0 cursor-pointer transition-colors ${
                    viewMode === "grid"
                      ? "bg-[var(--surface)] text-[var(--accent)] shadow-xs"
                      : "bg-transparent text-[var(--text-secondary)]"
                  }`}
                  title={s.isVi ? "Ô xếp" : "Grid"}
                  aria-label={s.isVi ? "Ô xếp" : "Grid"}
                >
                  <LayoutGrid size={14} />
                </button>
              </div>
            </div>

            {/* Single-Line Borderless Filter Chip Strip (shown when at root) */}
            {!activeFolder ? (
              <div className="ov-chip-strip">
                {(
                  [
                    { id: "all", label: s.allItems },
                    { id: "favorites", label: s.favorites },
                  ] as { id: FilterTab; label: string }[]
                ).map((tab) => (
                  <button
                    key={tab.id}
                    type="button"
                    onClick={() => setFilterTab(tab.id)}
                    className={`border-0 cursor-pointer whitespace-nowrap shrink-0 px-2.5 py-1 rounded-[7px] text-[12px] transition-colors ${
                      filterTab === tab.id
                        ? "bg-[var(--accent)] text-white font-semibold"
                        : "bg-transparent text-[var(--text-secondary)] font-medium hover:bg-[var(--surface-subtle)] hover:text-[var(--text-primary)]"
                    }`}
                  >
                    {tab.label}
                  </button>
                ))}
                <div className="w-px h-3.5 bg-[var(--border)] mx-0.5 shrink-0" aria-hidden="true" />
                {DOC_CATEGORIES.map((c) => (
                  <button
                    key={c.id}
                    type="button"
                    onClick={() => setFilterTab(c.id)}
                    className={`border-0 cursor-pointer whitespace-nowrap shrink-0 px-2.5 py-1 rounded-[7px] text-[12px] transition-colors ${
                      filterTab === c.id
                        ? "bg-[var(--accent)] text-white font-semibold"
                        : "bg-transparent text-[var(--text-secondary)] font-medium hover:bg-[var(--surface-subtle)] hover:text-[var(--text-primary)]"
                    }`}
                  >
                    {s.isVi ? c.vi : c.en}
                  </button>
                ))}
                <div className="w-px h-3.5 bg-[var(--border)] mx-0.5 shrink-0" aria-hidden="true" />
                <button
                  type="button"
                  onClick={() => setFilterTab("trash")}
                  className={`border-0 cursor-pointer whitespace-nowrap shrink-0 px-2.5 py-1 rounded-[7px] text-[12px] transition-colors ${
                    filterTab === "trash"
                      ? "bg-[var(--danger-soft)] text-[var(--danger)] font-semibold"
                      : "bg-transparent text-[var(--text-secondary)] font-medium hover:bg-[var(--danger-soft)]/60 hover:text-[var(--danger)]"
                  }`}
                >
                  {s.trash}
                </button>
              </div>
            ) : null}

            {/* Golden Folders Section (compact 2-column grid in both list & grid modes) */}
            {visibleFolders.length > 0 ? (
              <div className="flex flex-col gap-1.5">
                <div className="text-[11px] font-bold uppercase tracking-wider text-[var(--text-muted)] px-0.5">
                  {s.folders} ({visibleFolders.length})
                </div>
                <div className="grid grid-cols-2 gap-2">
                  {visibleFolders.map((folderName) => {
                    const count = folderCounts.get(folderName.toLowerCase()) ?? 0;
                    const menuOpen = openFolderMenu === folderName;
                    return (
                      <div
                        key={folderName}
                        className={`rounded-[10px] border border-[var(--border)] bg-[var(--surface-subtle)]/70 hover:bg-[var(--surface-hover)] hover:border-[var(--accent-border)] px-2.5 py-2 flex items-center justify-between gap-1.5 transition-colors relative ${
                          menuOpen ? "z-20" : ""
                        }`}
                      >
                        <button
                          type="button"
                          onClick={() => setActiveFolder(folderName)}
                          className="flex items-center gap-2 min-w-0 flex-1 text-left bg-transparent border-0 p-0 cursor-pointer"
                        >
                          <div className="w-8 h-8 rounded-[8px] bg-[#FEF3C7] dark:bg-[#3A2B12] flex items-center justify-center shrink-0">
                            <Folder size={16} className="fill-[#FBBF24] text-[#D97706]" />
                          </div>
                          <div className="min-w-0 flex-1">
                            <div className="text-[12.5px] font-semibold text-[var(--text-primary)] truncate">
                              {folderName}
                            </div>
                            <div className="text-[11px] text-[var(--text-secondary)]">
                              {count} {s.isVi ? "mục" : count === 1 ? "item" : "items"}
                            </div>
                          </div>
                        </button>
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            setOpenItemMenuId(null);
                            setOpenFolderMenu(menuOpen ? null : folderName);
                          }}
                          className="ov-icon-btn w-6 h-6 shrink-0"
                          aria-label={s.itemOptions}
                        >
                          <MoreVertical size={13.5} />
                        </button>

                        {menuOpen ? (
                          <div
                            onPointerDown={(e) => e.stopPropagation()}
                            className="absolute right-2 top-10 z-30 min-w-[152px] rounded-[10px] border border-[var(--border)] bg-[var(--surface)] p-1 shadow-xl"
                          >
                            <button
                              type="button"
                              onClick={() => {
                                setOpenFolderMenu(null);
                                setActiveFolder(folderName);
                              }}
                              className="ov-popover-item"
                            >
                              <Folder size={14} />
                              <span>{s.isVi ? "Mở thư mục" : "Open"}</span>
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                setOpenFolderMenu(null);
                                setRenamingFolder(folderName);
                                setRenameFolderInput(folderName);
                              }}
                              className="ov-popover-item"
                            >
                              <Edit3 size={14} />
                              <span>{s.isVi ? "Đổi tên" : "Rename"}</span>
                            </button>
                            <button
                              type="button"
                              data-danger="true"
                              onClick={() => {
                                setOpenFolderMenu(null);
                                setDeletingFolder(folderName);
                              }}
                              className="ov-popover-item"
                            >
                              <Trash2 size={14} />
                              <span>{s.delete}</span>
                            </button>
                          </div>
                        ) : null}
                      </div>
                    );
                  })}
                </div>
              </div>
            ) : null}

            {/* Items List (Single Grouped Card) or Grid Gallery */}
            {filteredItems.length === 0 && visibleFolders.length === 0 ? (
              <div className="py-10 text-center text-[var(--text-secondary)] text-[12.5px]">
                <div className="font-semibold text-[var(--text-primary)] mb-1">
                  {q ? s.noMatchingItems : s.vaultPlusEmptyTitle}
                </div>
                <div>{q ? s.tryOtherKeyword : s.vaultPlusEmptyHint}</div>
              </div>
            ) : filteredItems.length > 0 ? (
              <div className="flex flex-col gap-1.5">
                <div className="text-[11px] font-bold uppercase tracking-wider text-[var(--text-muted)] px-0.5">
                  {filterTab === "trash"
                    ? s.trash
                    : s.isVi
                      ? "Tài liệu"
                      : "Documents"}{" "}
                  ({filteredItems.length})
                </div>
                {(() => {
                  const isGrid = viewMode === "grid";
                  const renderSafeItem = (item: DecryptedSafeItem) => {
                    const thumb = thumbUrls[item.id];
                    const isPdf = item.metadata.mimeType === "application/pdf";
                    const catLabel =
                      DOC_CATEGORIES.find((c) => c.id === item.category)?.[s.isVi ? "vi" : "en"] ??
                      item.category;
                    const menuOpen = openItemMenuId === item.id;

                    return (
                      <div
                        key={item.id}
                        className={`${
                          isGrid
                            ? "ov-card relative hover:border-[var(--accent-border)] transition-colors p-2.5 flex flex-col gap-2"
                            : "relative px-3 py-2.5 flex items-center gap-3 first:rounded-t-[10px] last:rounded-b-[10px] hover:bg-[var(--surface-subtle)]/75 transition-colors"
                        } ${menuOpen ? "z-20" : ""}`}
                      >
                        <button
                          type="button"
                          onClick={() => setPreviewItemId(item.id)}
                          className={`bg-transparent border-0 p-0 text-left cursor-pointer ${
                            isGrid
                              ? "flex flex-col gap-2 w-full"
                              : "flex items-center gap-3 min-w-0 flex-1"
                          }`}
                        >
                          <div
                            className={`rounded-[8px] border border-[var(--border)]/60 bg-[var(--accent-soft)] text-[var(--accent)] flex items-center justify-center shrink-0 overflow-hidden ${
                              isGrid ? "w-full h-24" : "w-9 h-9"
                            }`}
                          >
                            {thumb ? (
                              <img src={thumb} alt="" className="w-full h-full object-cover" />
                            ) : isPdf ? (
                              <FileText size={isGrid ? 28 : 18} />
                            ) : (
                              <ImageIcon size={isGrid ? 28 : 18} />
                            )}
                          </div>
                          <div className="min-w-0 flex-1 w-full">
                            <div className={`flex items-center gap-1.5 ${isGrid ? "pr-6" : ""}`}>
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
                              {item.metadata.folderName && activeFolder === null
                                ? ` · ${item.metadata.folderName}`
                                : ""}
                            </div>
                          </div>
                        </button>

                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            setOpenFolderMenu(null);
                            setOpenItemMenuId(menuOpen ? null : item.id);
                          }}
                          className={`ov-icon-btn w-7 h-7 shrink-0 ${
                            isGrid ? "absolute bottom-2 right-1.5" : ""
                          }`}
                          aria-label={s.itemOptions}
                        >
                          <MoreVertical size={15} />
                        </button>

                        {menuOpen ? (
                          <div
                            onPointerDown={(e) => e.stopPropagation()}
                            className="absolute right-2 top-11 z-30 min-w-[186px] rounded-[10px] border border-[var(--border)] bg-[var(--surface)] p-1 shadow-xl"
                          >
                            <button
                              type="button"
                              onClick={() => {
                                setOpenItemMenuId(null);
                                setPreviewItemId(item.id);
                              }}
                              className="ov-popover-item"
                            >
                              <Eye size={14} />
                              <span>{s.isVi ? "Xem tài liệu" : "Preview"}</span>
                            </button>
                            {!item.deletedAt ? (
                              <>
                                <button
                                  type="button"
                                  onClick={() => void handleToggleFavorite(item)}
                                  className="ov-popover-item"
                                >
                                  <Star
                                    size={14}
                                    className={
                                      item.favorite
                                        ? "fill-[var(--warning)] text-[var(--warning)]"
                                        : ""
                                    }
                                  />
                                  <span>
                                    {item.favorite ? s.removeFavorite : s.addFavorite}
                                  </span>
                                </button>
                                <button
                                  type="button"
                                  onClick={() => openEditModal(item)}
                                  className="ov-popover-item"
                                >
                                  <Edit3 size={14} />
                                  <span>{s.edit}</span>
                                </button>
                                <button
                                  type="button"
                                  onClick={() => {
                                    setOpenItemMenuId(null);
                                    setMoveInlineNewFolder("");
                                    setMovingItem(item);
                                  }}
                                  className="ov-popover-item"
                                >
                                  <FolderInput size={14} />
                                  <span>{s.isVi ? "Chuyển vào thư mục" : "Move to Folder"}</span>
                                </button>
                                <button
                                  type="button"
                                  onClick={() => {
                                    setOpenItemMenuId(null);
                                    setNotesModalItem(item);
                                    setNotesDraft(item.metadata.notes ?? "");
                                  }}
                                  className="ov-popover-item"
                                >
                                  <StickyNote size={14} />
                                  <span>{s.isVi ? "Ghi chú nhanh" : "Quick Notes"}</span>
                                </button>
                              </>
                            ) : (
                              <button
                                type="button"
                                onClick={() => void handleRestoreItem(item)}
                                className="ov-popover-item"
                              >
                                <RotateCcw size={14} />
                                <span>{s.restoreFromTrash}</span>
                              </button>
                            )}
                            <button
                              type="button"
                              onClick={() => {
                                setOpenItemMenuId(null);
                                setDetailsModalItem(item);
                              }}
                              className="ov-popover-item"
                            >
                              <Info size={14} />
                              <span>{s.isVi ? "Chi tiết tệp" : "Details"}</span>
                            </button>
                            <hr className="my-1 border-0 border-t border-[var(--border)]" />
                            <button
                              type="button"
                              data-danger="true"
                              onClick={() => {
                                setOpenItemMenuId(null);
                                setDeletingItem(item);
                              }}
                              className="ov-popover-item"
                            >
                              <Trash2 size={14} />
                              <span>{item.deletedAt ? s.deletePermanently : s.moveToTrash}</span>
                            </button>
                          </div>
                        ) : null}
                      </div>
                    );
                  };

                  return isGrid ? (
                    <div className="grid grid-cols-2 gap-2.5">{filteredItems.map(renderSafeItem)}</div>
                  ) : (
                    <div className="rounded-[11px] border border-[var(--border)] bg-[var(--surface)] divide-y divide-[var(--border)]/70">
                      {filteredItems.map(renderSafeItem)}
                    </div>
                  );
                })()}
              </div>
            ) : null}
          </div>
        )}
      </div>

      {/* Create Folder Modal */}
      {showCreateFolder ? (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
          <form
            onSubmit={handleCreateFolderSubmit}
            className="w-full max-w-[340px] bg-[var(--surface)] border border-[var(--border)] rounded-[14px] p-4 flex flex-col gap-3 shadow-xl"
          >
            <div className="text-[14.5px] font-bold text-[var(--text-primary)]">
              {s.isVi ? "Tạo Thư Mục Mới" : "Create New Folder"}
            </div>
            <input
              type="text"
              value={newFolderName}
              onChange={(e) => setNewFolderName(e.target.value)}
              placeholder={s.isVi ? "Ví dụ: Giấy tờ xe, Hộ chiếu..." : "e.g. Passports, Tax..."}
              className="ov-input text-[13px]"
              autoFocus
              required
            />
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowCreateFolder(false)}
                className="ov-btn-ghost px-3 py-1.5 text-[12px]"
              >
                {s.cancel}
              </button>
              <button
                type="submit"
                disabled={folderBusy || !newFolderName.trim()}
                className="ov-btn-primary px-3.5 py-1.5 text-[12px]"
              >
                {s.save}
              </button>
            </div>
          </form>
        </div>
      ) : null}

      {/* Rename Folder Modal */}
      {renamingFolder ? (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
          <form
            onSubmit={handleRenameFolderSubmit}
            className="w-full max-w-[340px] bg-[var(--surface)] border border-[var(--border)] rounded-[14px] p-4 flex flex-col gap-3 shadow-xl"
          >
            <div className="text-[14.5px] font-bold text-[var(--text-primary)]">
              {s.isVi ? "Đổi Tên Thư Mục" : "Rename Folder"}
            </div>
            <input
              type="text"
              value={renameFolderInput}
              onChange={(e) => setRenameFolderInput(e.target.value)}
              className="ov-input text-[13px]"
              autoFocus
              required
            />
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setRenamingFolder(null)}
                className="ov-btn-ghost px-3 py-1.5 text-[12px]"
              >
                {s.cancel}
              </button>
              <button
                type="submit"
                disabled={folderBusy || !renameFolderInput.trim()}
                className="ov-btn-primary px-3.5 py-1.5 text-[12px]"
              >
                {folderBusy ? <Loader2 size={12} className="animate-spin inline mr-1" /> : null}
                {s.save}
              </button>
            </div>
          </form>
        </div>
      ) : null}

      {/* Delete Folder Confirmation */}
      <ConfirmDialog
        open={deletingFolder !== null}
        title={s.isVi ? "Xóa Thư Mục?" : "Delete Folder?"}
        description={
          s.isVi
            ? `Thư mục "${deletingFolder ?? ""}" sẽ bị xóa. Các tài liệu bên trong vẫn an toàn và sẽ được chuyển về mục gốc.`
            : `Folder "${deletingFolder ?? ""}" will be removed. All items inside will move safely back to root.`
        }
        confirmLabel={s.delete}
        cancelLabel={s.cancel}
        onConfirm={() => void handleConfirmDeleteFolder()}
        onCancel={() => setDeletingFolder(null)}
      />

      {/* Edit Metadata Modal */}
      {editingItem ? (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
          <form
            onSubmit={handleSaveEditModal}
            className="w-full max-w-[380px] bg-[var(--surface)] border border-[var(--border)] rounded-[14px] p-4 flex flex-col gap-2.5 shadow-xl"
          >
            <div className="text-[14.5px] font-bold text-[var(--text-primary)]">
              {s.isVi ? "Chỉnh Sửa Tài Liệu" : "Edit Document"}
            </div>
            <input
              type="text"
              value={editTitle}
              onChange={(e) => setEditTitle(e.target.value)}
              placeholder={s.isVi ? "Tên hiển thị" : "Title"}
              className="ov-input text-[12.5px]"
              required
            />
            <div className="grid grid-cols-2 gap-2">
              <select
                value={editCategory}
                onChange={(e) => setEditCategory(e.target.value as SafeDocumentCategory)}
                className="ov-input text-[12.5px]"
              >
                {DOC_CATEGORIES.map((c) => (
                  <option key={c.id} value={c.id}>
                    {s.isVi ? c.vi : c.en}
                  </option>
                ))}
              </select>
              <select
                value={editFolderName}
                onChange={(e) => setEditFolderName(e.target.value)}
                className="ov-input text-[12.5px]"
              >
                <option value="">{s.noFolder}</option>
                {allFolders.map((fn) => (
                  <option key={fn} value={fn}>
                    {fn}
                  </option>
                ))}
              </select>
            </div>
            <input
              type="text"
              value={editExpiresAt}
              onChange={(e) => setEditExpiresAt(e.target.value)}
              placeholder={s.isVi ? "HSD (YYYY-MM-DD)" : "Expiry (YYYY-MM-DD)"}
              className="ov-input text-[12.5px]"
            />
            <textarea
              rows={2}
              value={editNotes}
              onChange={(e) => setEditNotes(e.target.value)}
              placeholder={s.notesPlaceholder}
              className="ov-input text-[12.5px] resize-y"
            />
            <div className="flex justify-end gap-2 mt-1">
              <button
                type="button"
                onClick={() => setEditingItem(null)}
                className="ov-btn-ghost px-3 py-1.5 text-[12px]"
              >
                {s.cancel}
              </button>
              <button
                type="submit"
                disabled={editBusy}
                className="ov-btn-primary px-3.5 py-1.5 text-[12px]"
              >
                {editBusy ? <Loader2 size={12} className="animate-spin inline mr-1" /> : null}
                {s.save}
              </button>
            </div>
          </form>
        </div>
      ) : null}

      {/* Move to Folder Modal */}
      {movingItem ? (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
          <div className="w-full max-w-[350px] bg-[var(--surface)] border border-[var(--border)] rounded-[14px] p-4 flex flex-col gap-2.5 shadow-xl">
            <div className="text-[14.5px] font-bold text-[var(--text-primary)] truncate">
              {s.isVi ? `Di chuyển: ${movingItem.metadata.title}` : `Move: ${movingItem.metadata.title}`}
            </div>
            <div className="flex flex-col gap-1 max-h-[200px] overflow-y-auto ov-scroll">
              <button
                type="button"
                disabled={editBusy}
                onClick={() => void handleMoveToFolder(movingItem, null)}
                className={`ov-popover-item ${!movingItem.metadata.folderName ? "bg-[var(--accent-soft)] text-[var(--accent)]" : ""}`}
              >
                <ShieldPlus size={14} />
                <span>{s.noFolder}</span>
              </button>
              {allFolders.map((fn) => {
                const current =
                  movingItem.metadata.folderName?.trim().toLowerCase() === fn.toLowerCase();
                return (
                  <button
                    key={fn}
                    type="button"
                    disabled={editBusy}
                    onClick={() => void handleMoveToFolder(movingItem, fn)}
                    className={`ov-popover-item ${current ? "bg-[var(--accent-soft)] text-[var(--accent)]" : ""}`}
                  >
                    <Folder size={14} className="fill-[#FBBF24] text-[#D97706]" />
                    <span className="truncate flex-1">{fn}</span>
                    {current ? <Check size={14} /> : null}
                  </button>
                );
              })}
            </div>
            <div className="flex gap-1.5 pt-2 border-t border-[var(--border)]">
              <input
                type="text"
                value={moveInlineNewFolder}
                onChange={(e) => setMoveInlineNewFolder(e.target.value)}
                placeholder={s.isVi ? "Hoặc tạo thư mục mới..." : "Or new folder..."}
                className="ov-input text-[12.5px] py-1.5 flex-1"
              />
              <button
                type="button"
                disabled={editBusy || !moveInlineNewFolder.trim()}
                onClick={() => void handleMoveToFolder(movingItem, moveInlineNewFolder)}
                className="ov-btn-primary px-3 py-1.5 text-[12px]"
              >
                <Plus size={14} />
              </button>
            </div>
            <div className="flex justify-end">
              <button
                type="button"
                onClick={() => setMovingItem(null)}
                className="ov-btn-ghost px-3 py-1.5 text-[12px]"
              >
                {s.cancel}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {/* Quick Notes Modal */}
      {notesModalItem ? (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
          <form
            onSubmit={handleSaveQuickNotes}
            className="w-full max-w-[360px] bg-[var(--surface)] border border-[var(--border)] rounded-[14px] p-4 flex flex-col gap-2.5 shadow-xl"
          >
            <div className="text-[14.5px] font-bold text-[var(--text-primary)] truncate">
              {notesModalItem.metadata.title} · {s.notesLabel}
            </div>
            <textarea
              rows={4}
              value={notesDraft}
              onChange={(e) => setNotesDraft(e.target.value)}
              placeholder={s.notesPlaceholder}
              className="ov-input text-[13px] resize-y"
              autoFocus
            />
            <div className="flex items-center justify-between gap-2">
              {notesDraft.trim() ? (
                <button
                  type="button"
                  onClick={() => {
                    void navigator.clipboard.writeText(notesDraft);
                    onToast(s.isVi ? "Đã sao chép ghi chú." : "Notes copied.", "success");
                  }}
                  className="ov-btn-ghost px-2.5 py-1.5 text-[12px] inline-flex items-center gap-1"
                >
                  <Copy size={12} />
                  <span>{s.copy}</span>
                </button>
              ) : (
                <span />
              )}
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setNotesModalItem(null)}
                  className="ov-btn-ghost px-3 py-1.5 text-[12px]"
                >
                  {s.cancel}
                </button>
                <button
                  type="submit"
                  disabled={editBusy}
                  className="ov-btn-primary px-3.5 py-1.5 text-[12px]"
                >
                  {s.save}
                </button>
              </div>
            </div>
          </form>
        </div>
      ) : null}

      {/* Item File Details Modal */}
      {detailsModalItem ? (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
          <div className="w-full max-w-[380px] bg-[var(--surface)] border border-[var(--border)] rounded-[14px] p-4 flex flex-col gap-2.5 shadow-xl">
            <div className="flex items-center justify-between">
              <div className="text-[14.5px] font-bold text-[var(--text-primary)] truncate">
                {s.isVi ? "Chi Tiết Tài Liệu" : "File Details"}
              </div>
              <button
                type="button"
                onClick={() => setDetailsModalItem(null)}
                className="ov-icon-btn w-7 h-7"
              >
                <X size={15} />
              </button>
            </div>
            <div className="rounded-[10px] bg-[var(--surface-subtle)] p-3 flex flex-col gap-1.5 text-[12px]">
              <div className="flex justify-between gap-2">
                <span className="text-[var(--text-muted)]">{s.isVi ? "Tiêu đề" : "Title"}</span>
                <span className="font-semibold text-right truncate">{detailsModalItem.metadata.title}</span>
              </div>
              <div className="flex justify-between gap-2">
                <span className="text-[var(--text-muted)]">{s.isVi ? "Tên tệp" : "File Name"}</span>
                <span className="font-mono-code text-right truncate">{detailsModalItem.metadata.fileName}</span>
              </div>
              <div className="flex justify-between gap-2">
                <span className="text-[var(--text-muted)]">MIME</span>
                <span className="font-mono-code">{detailsModalItem.metadata.mimeType}</span>
              </div>
              <div className="flex justify-between gap-2">
                <span className="text-[var(--text-muted)]">{s.isVi ? "Dung lượng gốc" : "Original Size"}</span>
                <span>{formatSize(detailsModalItem.metadata.originalSize)}</span>
              </div>
              <div className="flex justify-between gap-2">
                <span className="text-[var(--text-muted)]">{s.isVi ? "Dung lượng mã hóa" : "Encrypted Size"}</span>
                <span>{formatSize(detailsModalItem.blobSizeBytes)}</span>
              </div>
              <div className="flex justify-between gap-2">
                <span className="text-[var(--text-muted)]">{s.folderLabel}</span>
                <span>{detailsModalItem.metadata.folderName || s.noFolder}</span>
              </div>
              {detailsModalItem.createdAt ? (
                <div className="flex justify-between gap-2">
                  <span className="text-[var(--text-muted)]">{s.added}</span>
                  <span>{formatLocalTimestamp(detailsModalItem.createdAt, language)}</span>
                </div>
              ) : null}
              <div className="pt-1 border-t border-[var(--border)]">
                <div className="text-[11px] text-[var(--text-muted)] mb-0.5">SHA-256</div>
                <div className="font-mono-code text-[11px] break-all text-[var(--text-secondary)] select-all">
                  {detailsModalItem.metadata.originalSha256}
                </div>
              </div>
            </div>
            <div className="flex justify-end">
              <button
                type="button"
                onClick={() => setDetailsModalItem(null)}
                className="ov-btn-ghost px-3.5 py-1.5 text-[12px]"
              >
                {s.close}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {/* Delete / Move to Trash Confirmation */}
      <ConfirmDialog
        open={deletingItem !== null}
        title={deletingItem?.deletedAt ? s.deletePermTitle : s.moveTrashTitle}
        description={
          deletingItem?.deletedAt
            ? s.isVi
              ? `"${deletingItem.metadata.title}" sẽ bị xóa vĩnh viễn khỏi Cloudflare R2.`
              : `"${deletingItem.metadata.title}" will be permanently deleted from R2.`
            : s.isVi
              ? `Chuyển "${deletingItem?.metadata.title ?? ""}" vào Thùng rác Vault+.`
              : `Move "${deletingItem?.metadata.title ?? ""}" to Vault+ Trash.`
        }
        confirmLabel={deletingItem?.deletedAt ? s.deletePermanently : s.moveToTrash}
        cancelLabel={s.cancel}
        onConfirm={() => void handleConfirmDeleteItem()}
        onCancel={() => setDeletingItem(null)}
      />

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
          folders={allFolders}
          language={language}
          onClose={handleClosePreview}
          onUpdated={loadItems}
          onSafeLocked={handleSafeLockedFromModal}
          onToast={onToast}
        />
      ) : null}
    </div>
  );
}

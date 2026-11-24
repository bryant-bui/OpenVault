import {
  base64ToBytes,
  decryptSafeFileBinary,
  zeroize,
  type SafeDocumentCategory,
} from "@openvault/crypto-core";
import {
  Download,
  Edit3,
  ExternalLink,
  Loader2,
  RotateCcw,
  Star,
  Trash2,
  X,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { getStrings, type AppLanguage } from "../../../shared/i18n.js";
import { apiDownloadSafeBinary } from "../../../shared/safe-api.js";
import type { DecryptedSafeItem, SafeBlobAuthResult } from "../../../shared/safe-types.js";
import { sendBg } from "../../components/bridge.js";
import { ConfirmDialog } from "../../components/ConfirmDialog.js";

interface SafePreviewModalProps {
  item: DecryptedSafeItem;
  language: AppLanguage;
  onClose: () => void;
  onUpdated: () => Promise<void>;
  onSafeLocked: () => void;
  onToast: (msg: string, kind?: "success" | "error") => void;
}

const CATEGORIES: { value: SafeDocumentCategory; en: string; vi: string }[] = [
  { value: "id_card", en: "ID Card", vi: "CCCD / CMND" },
  { value: "passport", en: "Passport", vi: "Hộ chiếu" },
  { value: "driver_license", en: "Driver's License", vi: "Bằng lái xe" },
  { value: "medical", en: "Medical", vi: "Hồ sơ y tế" },
  { value: "financial", en: "Financial", vi: "Tài chính" },
  { value: "photo", en: "Private Photo", vi: "Ảnh riêng tư" },
  { value: "other", en: "Other", vi: "Khác" },
];

export function SafePreviewModal({
  item,
  language,
  onClose,
  onUpdated,
  onSafeLocked,
  onToast,
}: SafePreviewModalProps) {
  const s = getStrings(language);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);

  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState(item.metadata.title);
  const [category, setCategory] = useState<SafeDocumentCategory>(item.category);
  const [expiresAt, setExpiresAt] = useState(item.metadata.expiresAt ?? "");
  const [notes, setNotes] = useState(item.metadata.notes ?? "");
  const [saving, setSaving] = useState(false);

  const [confirmExport, setConfirmExport] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const activeUrlRef = useRef<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    const revokeActive = () => {
      if (activeUrlRef.current) {
        URL.revokeObjectURL(activeUrlRef.current);
        activeUrlRef.current = null;
      }
    };

    window.addEventListener("beforeunload", revokeActive);

    void (async () => {
      setLoading(true);
      setError(null);
      try {
        const auth = await sendBg<SafeBlobAuthResult>({ type: "SAFE_GET_AUTH_FOR_BLOB" });
        const encrypted = await apiDownloadSafeBinary(
          auth.serverUrl,
          auth.accessToken,
          item.id,
          "blob",
        );
        const fileKey = base64ToBytes(item.metadata.fileKeyB64);
        const plain = await decryptSafeFileBinary(encrypted, fileKey, item.id, "blob");
        zeroize(fileKey);

        if (cancelled) {
          zeroize(plain);
          return;
        }

        const blob = new Blob([plain], { type: item.metadata.mimeType });
        zeroize(plain);
        const url = URL.createObjectURL(blob);
        activeUrlRef.current = url;
        setObjectUrl(url);
      } catch (err) {
        const msg = err instanceof Error ? err.message : "Failed to decrypt document";
        if (msg.includes("Vault+ đang khóa")) {
          onSafeLocked();
          onClose();
          return;
        }
        if (!cancelled) setError(msg);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
      window.removeEventListener("beforeunload", revokeActive);
      revokeActive();
    };
  }, [item.id, item.metadata.fileKeyB64, item.metadata.mimeType, onClose, onSafeLocked]);

  const handleSaveMetadata = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || saving) return;
    setSaving(true);
    try {
      await sendBg({
        type: "SAFE_UPDATE_ITEM",
        id: item.id,
        title: title.trim(),
        category,
        notes: notes.trim() || null,
        expiresAt: expiresAt.trim() || null,
        favorite: item.favorite,
      });
      await onUpdated();
      setEditing(false);
      onToast(s.isVi ? "Đã cập nhật tài liệu." : "Document updated.", "success");
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Update failed", "error");
    } finally {
      setSaving(false);
    }
  };

  const handleToggleFavorite = async () => {
    try {
      await sendBg({
        type: "SAFE_UPDATE_ITEM",
        id: item.id,
        title: item.metadata.title,
        category: item.category,
        notes: item.metadata.notes ?? null,
        expiresAt: item.metadata.expiresAt ?? null,
        favorite: !item.favorite,
      });
      await onUpdated();
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Failed", "error");
    }
  };

  const handleExportConfirmed = () => {
    setConfirmExport(false);
    if (!objectUrl) return;
    const a = document.createElement("a");
    a.href = objectUrl;
    a.download = item.metadata.fileName || "vault-plus-document";
    document.body.appendChild(a);
    a.click();
    a.remove();
    onToast(s.isVi ? "Đã tải bản giải mã về máy." : "Exported decrypted copy.", "success");
  };

  const handleDeleteConfirmed = async () => {
    setConfirmDelete(false);
    const permanent = Boolean(item.deletedAt);
    try {
      await sendBg({ type: "SAFE_DELETE_ITEM", id: item.id, permanent });
      await onUpdated();
      onToast(
        permanent
          ? s.isVi
            ? "Đã xóa vĩnh viễn khỏi Cloudflare R2."
            : "Permanently deleted."
          : s.isVi
            ? "Đã chuyển vào Thùng rác Vault+."
            : "Moved to Vault+ Trash.",
        "success",
      );
      onClose();
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Delete failed", "error");
    }
  };

  const handleRestore = async () => {
    try {
      await sendBg({ type: "SAFE_RESTORE_ITEM", id: item.id });
      await onUpdated();
      onToast(s.isVi ? "Đã khôi phục tài liệu." : "Document restored.", "success");
      onClose();
    } catch (err) {
      onToast(err instanceof Error ? err.message : "Restore failed", "error");
    }
  };

  const isPdf = item.metadata.mimeType === "application/pdf";

  return (
    <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-[2px] flex items-center justify-center p-3">
      <div className="w-full h-full max-w-[680px] bg-[var(--surface)] border border-[var(--border)] rounded-[14px] shadow-2xl flex flex-col overflow-hidden">
        {/* Top bar */}
        <div className="px-3.5 py-2.5 border-b border-[var(--border)] flex items-center gap-2 shrink-0">
          <div className="min-w-0 flex-1">
            <div className="text-[14px] font-bold text-[var(--text-primary)] truncate">
              {item.metadata.title}
            </div>
            <div className="text-[11.5px] text-[var(--text-secondary)] truncate">
              {item.metadata.fileName} · {(item.metadata.originalSize / 1024).toFixed(1)} KB
            </div>
          </div>

          {!isPdf && objectUrl ? (
            <>
              <button
                type="button"
                className="ov-icon-btn w-7 h-7"
                onClick={() => setZoom((z) => Math.max(0.5, Number((z - 0.25).toFixed(2))))}
                title="Zoom Out"
              >
                <ZoomOut size={15} />
              </button>
              <button
                type="button"
                className="ov-icon-btn w-7 h-7"
                onClick={() => setZoom((z) => Math.min(3, Number((z + 0.25).toFixed(2))))}
                title="Zoom In"
              >
                <ZoomIn size={15} />
              </button>
            </>
          ) : null}

          {objectUrl && isPdf ? (
            <button
              type="button"
              className="ov-icon-btn w-7 h-7"
              onClick={() => window.open(objectUrl, "_blank", "noopener")}
              title={s.isVi ? "Mở PDF ở tab lớn" : "Open PDF in tab"}
            >
              <ExternalLink size={15} />
            </button>
          ) : null}

          <button
            type="button"
            className="ov-icon-btn w-7 h-7"
            onClick={handleToggleFavorite}
            title={s.favorites}
          >
            <Star
              size={15}
              className={item.favorite ? "fill-[var(--warning)] text-[var(--warning)]" : ""}
            />
          </button>

          <button
            type="button"
            className="ov-icon-btn w-7 h-7"
            onClick={() => setEditing((v) => !v)}
            title={s.edit}
          >
            <Edit3 size={15} />
          </button>

          {objectUrl ? (
            <button
              type="button"
              className="ov-icon-btn w-7 h-7"
              onClick={() => setConfirmExport(true)}
              title={s.isVi ? "Xuất bản sao" : "Export copy"}
            >
              <Download size={15} />
            </button>
          ) : null}

          {item.deletedAt ? (
            <button
              type="button"
              className="ov-icon-btn w-7 h-7 text-[var(--accent)]"
              onClick={handleRestore}
              title={s.restoreFromTrash}
            >
              <RotateCcw size={15} />
            </button>
          ) : null}

          <button
            type="button"
            className="ov-icon-btn w-7 h-7 text-[var(--danger)]"
            onClick={() => setConfirmDelete(true)}
            title={item.deletedAt ? s.deletePermanently : s.moveToTrash}
          >
            <Trash2 size={15} />
          </button>

          <button type="button" className="ov-icon-btn w-7 h-7" onClick={onClose} title={s.close}>
            <X size={16} />
          </button>
        </div>

        {/* Metadata inline editor */}
        {editing ? (
          <form
            onSubmit={handleSaveMetadata}
            className="p-3 bg-[var(--surface-subtle)] border-b border-[var(--border)] flex flex-col gap-2 shrink-0"
          >
            <div className="grid grid-cols-2 gap-2">
              <input
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder={s.isVi ? "Tên hiển thị" : "Title"}
                className="ov-input text-[12.5px] py-1.5"
                required
              />
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value as SafeDocumentCategory)}
                className="ov-input text-[12.5px] py-1.5"
              >
                {CATEGORIES.map((cat) => (
                  <option key={cat.value} value={cat.value}>
                    {s.isVi ? cat.vi : cat.en}
                  </option>
                ))}
              </select>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <input
                type="text"
                value={expiresAt}
                onChange={(e) => setExpiresAt(e.target.value)}
                placeholder={s.isVi ? "HSD (YYYY-MM-DD)" : "Expiry (YYYY-MM-DD)"}
                className="ov-input text-[12.5px] py-1.5"
              />
              <input
                type="text"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder={s.notesPlaceholder}
                className="ov-input text-[12.5px] py-1.5"
              />
            </div>
            <div className="flex justify-end gap-1.5">
              <button
                type="button"
                onClick={() => setEditing(false)}
                className="ov-btn-ghost px-2.5 py-1 text-[12px]"
              >
                {s.cancel}
              </button>
              <button
                type="submit"
                disabled={saving}
                className="ov-btn-primary px-3 py-1 text-[12px]"
              >
                {saving ? s.saving : s.save}
              </button>
            </div>
          </form>
        ) : null}

        {/* Decrypted Content Viewport */}
        <div className="flex-1 min-h-0 bg-[var(--bg)] flex items-center justify-center overflow-auto p-2">
          {loading ? (
            <div className="flex flex-col items-center gap-2 text-[var(--text-secondary)]">
              <Loader2 size={24} className="animate-spin text-[var(--accent)]" />
              <span className="text-[12.5px]">
                {s.isVi ? "Đang tải & giải mã OVSF trong RAM..." : "Decrypting OVSF in RAM..."}
              </span>
            </div>
          ) : error ? (
            <div className="text-[13px] text-[var(--danger)] text-center px-4">{error}</div>
          ) : objectUrl && isPdf ? (
            <iframe
              src={objectUrl}
              title={item.metadata.title}
              className="w-full h-full border-0 rounded-[8px] bg-white"
            />
          ) : objectUrl ? (
            <img
              src={objectUrl}
              alt={item.metadata.title}
              style={{ transform: `scale(${zoom})`, transformOrigin: "center" }}
              className="max-w-full max-h-full object-contain rounded-[6px] transition-transform duration-150"
            />
          ) : null}
        </div>

        {/* Notes / Expiry Footer */}
        {item.metadata.expiresAt || item.metadata.notes ? (
          <div className="px-3.5 py-2 border-t border-[var(--border)] bg-[var(--surface-subtle)] text-[12px] text-[var(--text-secondary)] flex flex-wrap items-center gap-x-4 gap-y-1 shrink-0">
            {item.metadata.expiresAt ? (
              <span className="font-semibold text-[var(--accent)]">
                {s.isVi ? "Hết hạn:" : "Expires:"} {item.metadata.expiresAt}
              </span>
            ) : null}
            {item.metadata.notes ? <span className="truncate">{item.metadata.notes}</span> : null}
          </div>
        ) : null}
      </div>

      <ConfirmDialog
        open={confirmExport}
        title={s.isVi ? "Xuất bản sao không mã hóa?" : "Export Unencrypted Copy?"}
        description={
          s.isVi
            ? "Tệp tải xuống sẽ ở dạng văn bản/ảnh gốc không được mã hóa bởi Vault+. Hãy xóa sau khi sử dụng."
            : "The downloaded copy is unencrypted outside Vault+. Delete it once finished."
        }
        confirmLabel={s.isVi ? "Xuất tệp" : "Export"}
        cancelLabel={s.cancel}
        destructive={false}
        onConfirm={handleExportConfirmed}
        onCancel={() => setConfirmExport(false)}
      />

      <ConfirmDialog
        open={confirmDelete}
        title={item.deletedAt ? s.deletePermTitle : s.moveTrashTitle}
        description={
          item.deletedAt
            ? s.isVi
              ? "Tệp mã hóa trên Cloudflare R2 sẽ bị xóa vĩnh viễn."
              : "Encrypted file on Cloudflare R2 will be permanently deleted."
            : s.isVi
              ? "Chuyển tài liệu vào Thùng rác Vault+ (tự xóa sau 30 ngày)."
              : "Move document to Vault+ Trash (auto-purged after 30 days)."
        }
        confirmLabel={item.deletedAt ? s.deletePermanently : s.moveToTrash}
        cancelLabel={s.cancel}
        destructive
        onConfirm={() => void handleDeleteConfirmed()}
        onCancel={() => setConfirmDelete(false)}
      />
    </div>
  );
}

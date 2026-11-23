import { CipherType, type CipherPayload } from "@openvault/crypto-core";
import {
  ArrowLeft,
  Copy,
  Edit3,
  ExternalLink,
  Eye,
  EyeOff,
  Fingerprint,
  Maximize2,
  RotateCcw,
  Star,
  Trash2,
  Zap,
} from "lucide-react";
import { useState } from "react";
import { BrandAvatar } from "../components/BrandAvatar.js";
import { ColorPassword } from "../components/ColorPassword.js";
import { ConfirmDialog } from "../components/ConfirmDialog.js";
import { TotpRing } from "../components/TotpRing.js";

interface DetailScreenProps {
  cipher: CipherPayload;
  canAutofill: boolean;
  onBack: () => void;
  onEdit: (cipher: CipherPayload) => void;
  onToggleFavorite: (cipher: CipherPayload) => Promise<void>;
  onAutofill: (cipherId: string) => Promise<void>;
  onDelete: (id: string, permanent: boolean) => Promise<void>;
  onRestore: (id: string) => Promise<void>;
  onCopyText: (text: string, label: string) => Promise<void>;
}

export function DetailScreen({
  cipher,
  canAutofill,
  onBack,
  onEdit,
  onToggleFavorite,
  onAutofill,
  onDelete,
  onRestore,
  onCopyText,
}: DetailScreenProps) {
  const [revealPassword, setRevealPassword] = useState(false);
  const [largeType, setLargeType] = useState(false);
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);

  const isTrashed = Boolean(cipher.deletedDate);

  const handleConfirmDelete = async () => {
    setConfirmDeleteOpen(false);
    if (cipher.id) {
      await onDelete(cipher.id, isTrashed);
    }
  };

  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* Sticky Detail Header */}
      <header className="px-3 py-2.5 border-b border-[var(--border)] bg-[var(--surface)] flex items-center justify-between gap-2">
        <button
          type="button"
          onClick={onBack}
          className="ov-icon-btn px-2 py-1.5 gap-1 text-[12.5px] font-semibold"
        >
          <ArrowLeft size={15} />
          <span>Quay lại</span>
        </button>

        <div className="flex items-center gap-1">
          {!isTrashed ? (
            <>
              <button
                type="button"
                onClick={() => {
                  void onToggleFavorite(cipher);
                }}
                className="ov-icon-btn w-8 h-8"
                title={cipher.favorite ? "Bỏ yêu thích" : "Đánh dấu yêu thích"}
                aria-label="Yêu thích"
              >
                <Star
                  size={16}
                  className={cipher.favorite ? "fill-[var(--warning)] text-[var(--warning)]" : ""}
                />
              </button>
              <button
                type="button"
                onClick={() => onEdit(cipher)}
                className="ov-icon-btn w-8 h-8"
                title="Chỉnh sửa"
                aria-label="Chỉnh sửa"
              >
                <Edit3 size={15} />
              </button>
            </>
          ) : (
            <button
              type="button"
              onClick={() => {
                if (cipher.id) void onRestore(cipher.id);
              }}
              className="ov-btn-ghost px-2.5 py-1.5 text-[12px] inline-flex items-center gap-1"
            >
              <RotateCcw size={13} />
              <span>Khôi phục</span>
            </button>
          )}
          <button
            type="button"
            onClick={() => setConfirmDeleteOpen(true)}
            className="ov-icon-btn w-8 h-8 hover:text-[var(--danger)]"
            title={isTrashed ? "Xóa vĩnh viễn" : "Chuyển vào thùng rác"}
            aria-label="Xóa"
          >
            <Trash2 size={15} />
          </button>
        </div>
      </header>

      {/* Body */}
      <div className="flex-1 overflow-y-auto ov-scroll p-3.5 flex flex-col gap-3">
        {/* Hero Card */}
        <div className="flex items-center gap-3 px-1">
          <BrandAvatar name={cipher.name} type={cipher.type} size="lg" />
          <div className="min-w-0 flex-1">
            <h2 className="text-[16px] font-bold m-0 truncate text-[var(--text-primary)]">
              {cipher.name}
            </h2>
            <p className="text-[12px] text-[var(--text-secondary)] m-0 mt-0.5 truncate">
              {cipher.type === CipherType.Login
                ? "Tài khoản đăng nhập"
                : cipher.type === CipherType.Card
                  ? "Thẻ thanh toán"
                  : cipher.type === CipherType.SecureNote
                    ? "Ghi chú bảo mật"
                    : "Định danh"}
            </p>
          </div>
        </div>

        {/* One-click Autofill button */}
        {!isTrashed && canAutofill && cipher.type === CipherType.Login && cipher.id ? (
          <button
            type="button"
            onClick={() => {
              void onAutofill(cipher.id!);
            }}
            className="ov-btn-primary w-full py-2.5 flex items-center justify-center gap-2"
          >
            <Zap size={15} />
            <span>Tự động điền vào trang hiện tại</span>
          </button>
        ) : null}

        {/* Login Fields Section */}
        {cipher.type === CipherType.Login && cipher.login ? (
          <div className="ov-card divide-y divide-[var(--border)] overflow-hidden">
            {cipher.login.username ? (
              <div className="p-3 flex items-center justify-between gap-2">
                <div className="min-w-0 flex-1">
                  <div className="text-[11px] font-semibold text-[var(--text-muted)] uppercase tracking-wide">
                    Tên đăng nhập
                  </div>
                  <div className="text-[13.5px] font-medium mt-0.5 break-all text-[var(--text-primary)]">
                    {cipher.login.username}
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    void onCopyText(cipher.login!.username!, "Đã sao chép tên đăng nhập");
                  }}
                  className="ov-icon-btn w-8 h-8 shrink-0"
                  aria-label="Sao chép tên đăng nhập"
                >
                  <Copy size={14} />
                </button>
              </div>
            ) : null}

            {cipher.login.password ? (
              <div className="p-3">
                <div className="flex items-center justify-between gap-2">
                  <div className="text-[11px] font-semibold text-[var(--text-muted)] uppercase tracking-wide">
                    Mật khẩu
                  </div>
                  <div className="flex items-center gap-0.5">
                    <button
                      type="button"
                      onClick={() => {
                        setRevealPassword(true);
                        setLargeType((v) => !v);
                      }}
                      className="ov-icon-btn w-7 h-7"
                      title="Xem chữ lớn"
                      aria-label="Phóng to mật khẩu"
                    >
                      <Maximize2 size={13} />
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setRevealPassword((v) => !v);
                        if (revealPassword) setLargeType(false);
                      }}
                      className="ov-icon-btn w-7 h-7"
                      title={revealPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                      aria-label={revealPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                    >
                      {revealPassword ? <EyeOff size={14} /> : <Eye size={14} />}
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        void onCopyText(cipher.login!.password!, "Đã sao chép mật khẩu");
                      }}
                      className="ov-icon-btn w-7 h-7"
                      title="Sao chép mật khẩu"
                      aria-label="Sao chép mật khẩu"
                    >
                      <Copy size={14} />
                    </button>
                  </div>
                </div>
                <div className="mt-1">
                  <ColorPassword
                    value={cipher.login.password}
                    revealed={revealPassword}
                    large={largeType}
                  />
                </div>
              </div>
            ) : null}

            {cipher.login.totp ? (
              <div className="p-3">
                <div className="text-[11px] font-semibold text-[var(--text-muted)] uppercase tracking-wide mb-1">
                  Mã xác thực 2FA (TOTP)
                </div>
                <TotpRing
                  secretOrUri={cipher.login.totp}
                  onCopy={(code) => {
                    void onCopyText(code, "Đã sao chép mã 2FA");
                  }}
                />
              </div>
            ) : null}
          </div>
        ) : null}

        {/* Passkeys section */}
        {cipher.login?.fido2Credentials && cipher.login.fido2Credentials.length > 0 ? (
          <div className="ov-card p-3">
            <div className="flex items-center gap-1.5 text-[11px] font-semibold text-[var(--success)] uppercase tracking-wide mb-2">
              <Fingerprint size={14} />
              <span>Passkeys ({cipher.login.fido2Credentials.length})</span>
            </div>
            <div className="flex flex-col gap-2">
              {cipher.login.fido2Credentials.map((cred) => (
                <div
                  key={cred.credentialId}
                  className="p-2.5 rounded-[10px] bg-[var(--surface-elevated)] text-[12px]"
                >
                  <div className="font-semibold text-[var(--text-primary)]">{cred.rpId}</div>
                  <div className="text-[var(--text-secondary)] mt-0.5">
                    {cred.userName || cred.userDisplayName || "Discoverable Passkey"} ·{" "}
                    {cred.keyAlgorithm} {cred.keyCurve}
                  </div>
                </div>
              ))}
            </div>
          </div>
        ) : null}

        {/* Card Fields */}
        {cipher.type === CipherType.Card && cipher.card ? (
          <div className="ov-card divide-y divide-[var(--border)]">
            {[
              { label: "Chủ thẻ", value: cipher.card.cardholderName },
              { label: "Loại thẻ", value: cipher.card.brand },
              { label: "Số thẻ", value: cipher.card.number, mono: true },
              {
                label: "Hết hạn",
                value:
                  cipher.card.expMonth || cipher.card.expYear
                    ? `${cipher.card.expMonth ?? "MM"}/${cipher.card.expYear ?? "YYYY"}`
                    : null,
              },
              { label: "Mã CVV", value: cipher.card.code, mono: true },
            ]
              .filter((f) => Boolean(f.value))
              .map((field) => (
                <div key={field.label} className="p-3 flex items-center justify-between gap-2">
                  <div>
                    <div className="text-[11px] font-semibold text-[var(--text-muted)] uppercase">
                      {field.label}
                    </div>
                    <div className={`text-[13.5px] mt-0.5 ${field.mono ? "font-mono-code" : ""}`}>
                      {field.value}
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      void onCopyText(field.value!, `Đã sao chép ${field.label}`);
                    }}
                    className="ov-icon-btn w-8 h-8"
                    aria-label={`Sao chép ${field.label}`}
                  >
                    <Copy size={14} />
                  </button>
                </div>
              ))}
          </div>
        ) : null}

        {/* Websites */}
        {cipher.login?.uris && cipher.login.uris.some((u) => u.uri) ? (
          <div className="ov-card divide-y divide-[var(--border)]">
            {cipher.login.uris
              .filter((u) => u.uri)
              .map((u, idx) => (
                <div key={idx} className="p-3 flex items-center justify-between gap-2">
                  <div className="min-w-0 flex-1">
                    <div className="text-[11px] font-semibold text-[var(--text-muted)] uppercase">
                      Trang web {idx > 0 ? `#${idx + 1}` : ""}
                    </div>
                    <div className="text-[13px] text-[var(--accent)] truncate mt-0.5">{u.uri}</div>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      const target = /^https?:\/\//i.test(u.uri!) ? u.uri! : `https://${u.uri!}`;
                      void chrome.tabs.create({ url: target });
                    }}
                    className="ov-icon-btn w-8 h-8 shrink-0"
                    title="Mở trang web"
                    aria-label="Mở trang web"
                  >
                    <ExternalLink size={14} />
                  </button>
                </div>
              ))}
          </div>
        ) : null}

        {/* Notes */}
        {cipher.notes ? (
          <div className="ov-card p-3">
            <div className="flex items-center justify-between mb-1">
              <span className="text-[11px] font-semibold text-[var(--text-muted)] uppercase">
                Ghi chú
              </span>
              <button
                type="button"
                onClick={() => {
                  void onCopyText(cipher.notes!, "Đã sao chép ghi chú");
                }}
                className="ov-icon-btn w-7 h-7"
                aria-label="Sao chép ghi chú"
              >
                <Copy size={13} />
              </button>
            </div>
            <p className="text-[13px] leading-relaxed whitespace-pre-wrap break-words m-0 text-[var(--text-primary)]">
              {cipher.notes}
            </p>
          </div>
        ) : null}

        {cipher.revisionDate ? (
          <div className="text-center text-[11px] text-[var(--text-muted)] py-1">
            Cập nhật lần cuối: {new Date(cipher.revisionDate).toLocaleString("vi-VN")}
          </div>
        ) : null}
      </div>

      <ConfirmDialog
        open={confirmDeleteOpen}
        title={isTrashed ? "Xóa vĩnh viễn mục này?" : "Chuyển vào thùng rác?"}
        description={
          isTrashed
            ? `"${cipher.name}" sẽ bị xóa hoàn toàn khỏi Cloudflare D1 và không thể khôi phục.`
            : `"${cipher.name}" sẽ được chuyển vào Thùng rác. Bạn vẫn có thể khôi phục lại sau.`
        }
        confirmLabel={isTrashed ? "Xóa vĩnh viễn" : "Chuyển vào thùng rác"}
        onConfirm={() => {
          void handleConfirmDelete();
        }}
        onCancel={() => setConfirmDeleteOpen(false)}
      />
    </div>
  );
}

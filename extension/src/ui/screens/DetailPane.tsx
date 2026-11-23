import {
  CipherType,
  estimatePasswordStrength,
  type CipherPayload,
  type FolderPayload,
} from "@openvault/crypto-core";
import {
  ArrowLeft,
  Copy,
  Edit3,
  ExternalLink,
  Eye,
  EyeOff,
  Fingerprint,
  Folder,
  Maximize2,
  MoreVertical,
  RotateCcw,
  Star,
  Trash2,
} from "lucide-react";
import { useId, useRef, useState } from "react";
import { BrandAvatar } from "../components/BrandAvatar.js";
import { ColorPassword } from "../components/ColorPassword.js";
import { ConfirmDialog } from "../components/ConfirmDialog.js";
import { TotpRing } from "../components/TotpRing.js";

interface DetailPaneProps {
  cipher: CipherPayload;
  accountEmail: string;
  folder: FolderPayload | null;
  showFavicons: boolean;
  canAutofill: boolean;
  isCurrentSiteMatch: boolean;
  showBackButton: boolean;
  onBack: () => void;
  onEdit: (cipher: CipherPayload) => void;
  onToggleFavorite: (cipher: CipherPayload) => Promise<void>;
  onAutofill: (cipherId: string) => Promise<void>;
  onDelete: (id: string, permanent: boolean) => Promise<void>;
  onRestore: (id: string) => Promise<void>;
  onCopyText: (text: string, label: string) => Promise<void>;
}

const STRENGTH_META: Record<number, { label: string; color: string }> = {
  0: { label: "Yếu", color: "var(--danger)" },
  1: { label: "Trung bình", color: "var(--warning)" },
  2: { label: "Khá tốt", color: "var(--accent)" },
  3: { label: "Mạnh", color: "var(--success)" },
  4: { label: "Tuyệt vời", color: "var(--success)" },
};

export function DetailPane({
  cipher,
  accountEmail,
  folder,
  showFavicons,
  canAutofill,
  isCurrentSiteMatch,
  showBackButton,
  onBack,
  onEdit,
  onToggleFavorite,
  onAutofill,
  onDelete,
  onRestore,
  onCopyText,
}: DetailPaneProps) {
  const [revealPassword, setRevealPassword] = useState(false);
  const [largeType, setLargeType] = useState(false);
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);

  const morePopoverId = useId();
  const morePopoverRef = useRef<HTMLDivElement>(null);

  const isTrashed = Boolean(cipher.deletedDate);
  const accountLabel = accountEmail.split("@")[0] || "Cá nhân";
  const primaryUri = cipher.login?.uris?.find((u) => u.uri)?.uri ?? null;
  const pwStrength = cipher.login?.password
    ? estimatePasswordStrength(cipher.login.password)
    : null;
  const strengthBadge = pwStrength ? STRENGTH_META[pwStrength.score] : null;

  const closeMorePopover = () => {
    morePopoverRef.current?.hidePopover?.();
  };

  const handleConfirmDelete = async () => {
    setConfirmDeleteOpen(false);
    if (cipher.id) {
      await onDelete(cipher.id, isTrashed);
    }
  };

  const handleOpenAndFill = () => {
    if (!cipher.id) return;
    if (isCurrentSiteMatch || !primaryUri) {
      void onAutofill(cipher.id);
      return;
    }
    const target = /^https?:\/\//i.test(primaryUri) ? primaryUri : `https://${primaryUri}`;
    void chrome.tabs.create({ url: target });
  };

  return (
    <div className="flex-1 flex flex-col min-h-0 bg-[var(--surface)]">
      {/* Authentic 1Password 8 Detail Context Bar (38px aligned with left "All Items" header) */}
      <div className="h-[38px] shrink-0 px-4 border-b border-[var(--border)] flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          {showBackButton ? (
            <button
              type="button"
              onClick={onBack}
              className="ov-icon-btn w-6 h-6 shrink-0"
              aria-label="Quay lại danh sách"
            >
              <ArrowLeft size={14} />
            </button>
          ) : null}

          <span className="inline-flex items-center gap-1.5 text-[11.5px] font-medium text-[var(--text-secondary)] truncate">
            <span
              className="w-4 h-4 rounded-full text-[9px] font-bold text-white inline-flex items-center justify-center shrink-0"
              style={{ background: "linear-gradient(135deg, #0A84FF, #0455BF)" }}
            >
              {accountLabel[0]?.toUpperCase() ?? "O"}
            </span>
            <span className="truncate max-w-[82px]">{accountLabel}</span>
          </span>

          <span className="text-[var(--border-strong)] text-[11px]">|</span>

          <span className="inline-flex items-center gap-1 text-[11.5px] font-medium text-[var(--text-secondary)] truncate">
            <Folder size={12} className="shrink-0 text-[var(--text-muted)]" />
            <span className="truncate max-w-[96px]">{folder?.name ?? "Cá nhân"}</span>
          </span>
        </div>

        <div className="flex items-center gap-1.5 shrink-0">
          {!isTrashed &&
          (canAutofill || primaryUri) &&
          (cipher.type === CipherType.Login ||
            cipher.type === CipherType.Card ||
            cipher.type === CipherType.Identity) &&
          cipher.id ? (
            <button
              type="button"
              onClick={handleOpenAndFill}
              className="ov-btn-autofill h-[26px] px-2.5 text-[11.5px] leading-none"
            >
              {isCurrentSiteMatch || !primaryUri ? "Điền ngay" : "Mở & Điền"}
            </button>
          ) : null}

          {/* Native Popover More Actions Button (modern-web-guidance: viewport-aware-dropdowns) */}
          <button
            type="button"
            popoverTarget={morePopoverId}
            className="ov-icon-btn w-7 h-7"
            title="Tùy chọn mục"
            aria-label="Tùy chọn mục"
          >
            <MoreVertical size={15} />
          </button>
          <div
            id={morePopoverId}
            ref={morePopoverRef}
            popover="auto"
            className="ov-popover ov-popover-end"
          >
            {!isTrashed ? (
              <>
                <button
                  type="button"
                  className="ov-popover-item"
                  onClick={() => {
                    closeMorePopover();
                    onEdit(cipher);
                  }}
                >
                  <Edit3 size={13} />
                  <span>Chỉnh sửa mục</span>
                </button>
                <button
                  type="button"
                  className="ov-popover-item"
                  onClick={() => {
                    closeMorePopover();
                    void onToggleFavorite(cipher);
                  }}
                >
                  <Star
                    size={13}
                    className={cipher.favorite ? "fill-[var(--warning)] text-[var(--warning)]" : ""}
                  />
                  <span>{cipher.favorite ? "Bỏ yêu thích" : "Thêm vào yêu thích"}</span>
                </button>
                {canAutofill && cipher.id ? (
                  <button
                    type="button"
                    className="ov-popover-item"
                    onClick={() => {
                      closeMorePopover();
                      void onAutofill(cipher.id!);
                    }}
                  >
                    <ExternalLink size={13} />
                    <span>Điền vào tab hiện tại</span>
                  </button>
                ) : null}
              </>
            ) : (
              <button
                type="button"
                className="ov-popover-item"
                onClick={() => {
                  closeMorePopover();
                  if (cipher.id) void onRestore(cipher.id);
                }}
              >
                <RotateCcw size={13} />
                <span>Khôi phục từ thùng rác</span>
              </button>
            )}

            <hr className="my-1 border-0 border-t border-[var(--border)]" />
            <button
              type="button"
              data-danger="true"
              className="ov-popover-item"
              onClick={() => {
                closeMorePopover();
                setConfirmDeleteOpen(true);
              }}
            >
              <Trash2 size={13} />
              <span>{isTrashed ? "Xóa vĩnh viễn" : "Chuyển vào thùng rác"}</span>
            </button>
          </div>
        </div>
      </div>

      {/* Scrollable Clean 1Password 8 Detail Body */}
      <div className="flex-1 overflow-y-auto ov-scroll px-5 py-4 flex flex-col gap-4">
        {/* Hero with large rounded-square icon + optional gold star corner badge */}
        <div className="flex items-center gap-3.5">
          <div className="relative shrink-0">
            <BrandAvatar
              name={cipher.name}
              type={cipher.type}
              uri={primaryUri ?? undefined}
              showFavicon={showFavicons}
              size="lg"
            />
            {cipher.favorite ? (
              <span
                className="absolute -bottom-1 -right-1 w-4 h-4 rounded-full bg-[var(--surface)] flex items-center justify-center shadow-sm"
                title="Yêu thích"
              >
                <Star size={11} className="fill-[var(--warning)] text-[var(--warning)]" />
              </span>
            ) : null}
          </div>
          <div className="min-w-0 flex-1">
            <h2 className="text-[19px] font-bold tracking-tight m-0 truncate text-[var(--text-primary)]">
              {cipher.name}
            </h2>
            {cipher.deletedDate ? (
              <span className="inline-block mt-0.5 text-[11px] font-semibold text-[var(--danger)]">
                Trong thùng rác
              </span>
            ) : null}
          </div>
        </div>

        {/* Exact Image 1 Grouped Bordered Field Card */}
        {cipher.type === CipherType.Login && cipher.login ? (
          <>
            {(cipher.login.username || cipher.login.password || cipher.login.totp) ? (
              <div className="ov-detail-card divide-y divide-[var(--border)]">
                {cipher.login.username ? (
                  <div className="px-3.5 py-2.5 group flex items-center justify-between gap-2 hover:bg-[var(--surface-subtle)] transition-colors">
                    <div
                      className="min-w-0 flex-1 cursor-pointer"
                      onClick={() => {
                        void onCopyText(cipher.login!.username!, "Đã sao chép tên đăng nhập");
                      }}
                    >
                      <div className="text-[11px] font-medium" style={{ color: "var(--field-label)" }}>
                        username
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
                      className="ov-icon-btn w-7 h-7 opacity-0 group-hover:opacity-100 focus:opacity-100 shrink-0"
                      title="Sao chép username"
                      aria-label="Sao chép username"
                    >
                      <Copy size={13} />
                    </button>
                  </div>
                ) : null}

                {cipher.login.password ? (
                  <div className="px-3.5 py-2.5 group hover:bg-[var(--surface-subtle)] transition-colors">
                    <div className="flex items-center justify-between gap-2">
                      <div className="text-[11px] font-medium" style={{ color: "var(--field-label)" }}>
                        password
                      </div>
                      <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity">
                        <button
                          type="button"
                          onClick={() => {
                            setRevealPassword(true);
                            setLargeType((v) => !v);
                          }}
                          className="ov-icon-btn w-6 h-6"
                          title="Phóng to mật khẩu"
                          aria-label="Phóng to mật khẩu"
                        >
                          <Maximize2 size={12} />
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setRevealPassword((v) => !v);
                            if (revealPassword) setLargeType(false);
                          }}
                          className="ov-icon-btn w-6 h-6"
                          title={revealPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                          aria-label={revealPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                        >
                          {revealPassword ? <EyeOff size={13} /> : <Eye size={13} />}
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            void onCopyText(cipher.login!.password!, "Đã sao chép mật khẩu");
                          }}
                          className="ov-icon-btn w-6 h-6"
                          title="Sao chép mật khẩu"
                          aria-label="Sao chép mật khẩu"
                        >
                          <Copy size={13} />
                        </button>
                      </div>
                    </div>
                    <div className="mt-1 flex items-center justify-between gap-2">
                      <div
                        className="min-w-0 flex-1 cursor-pointer"
                        onClick={() => {
                          void onCopyText(cipher.login!.password!, "Đã sao chép mật khẩu");
                        }}
                      >
                        <ColorPassword
                          value={cipher.login.password}
                          revealed={revealPassword}
                          large={largeType}
                        />
                      </div>
                      {strengthBadge ? (
                        <span
                          className="inline-flex items-center gap-1.5 text-[11.5px] font-medium shrink-0"
                          style={{ color: "var(--text-secondary)" }}
                        >
                          <span>{strengthBadge.label}</span>
                          <span className="ov-strength-ring" style={{ color: strengthBadge.color }} />
                        </span>
                      ) : null}
                    </div>
                  </div>
                ) : null}

                {cipher.login.totp ? (
                  <div className="px-3.5 py-2.5 hover:bg-[var(--surface-subtle)] transition-colors">
                    <div className="text-[11px] font-medium mb-0.5" style={{ color: "var(--field-label)" }}>
                      one-time password
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

            {/* Passkeys */}
            {cipher.login.fido2Credentials && cipher.login.fido2Credentials.length > 0 ? (
              <div className="flex flex-col gap-1.5">
                <div className="text-[11px] font-medium" style={{ color: "var(--field-label)" }}>
                  passkey
                </div>
                {cipher.login.fido2Credentials.map((cred) => (
                  <div
                    key={cred.credentialId}
                    className="flex items-center justify-between px-3 py-2 rounded-[9px]"
                    style={{ background: "var(--passkey-soft)" }}
                  >
                    <div className="flex items-center gap-2 min-w-0">
                      <Fingerprint size={15} style={{ color: "var(--passkey)" }} />
                      <div className="min-w-0">
                        <div className="text-[12.5px] font-semibold truncate">{cred.rpId}</div>
                        <div className="text-[11px] text-[var(--text-secondary)] truncate">
                          {cred.userName || cred.userDisplayName || "ECDSA P-256"}
                        </div>
                      </div>
                    </div>
                    <span
                      className="text-[10.5px] font-bold px-2 py-0.5 rounded-full text-white shrink-0"
                      style={{ background: "var(--passkey)" }}
                    >
                      Passkey
                    </span>
                  </div>
                ))}
              </div>
            ) : null}

            {/* Clean unboxed Website links below the card (exact Image 1!) */}
            {cipher.login.uris && cipher.login.uris.some((u) => u.uri) ? (
              <div className="flex flex-col gap-2 px-1">
                {cipher.login.uris
                  .filter((u) => u.uri)
                  .map((u, idx) => (
                    <div key={idx} className="flex items-center justify-between gap-2">
                      <div className="min-w-0 flex-1">
                        <div className="text-[11px] font-medium" style={{ color: "var(--field-label)" }}>
                          website{idx > 0 ? ` ${idx + 1}` : ""}
                        </div>
                        <button
                          type="button"
                          onClick={() => {
                            const target = /^https?:\/\//i.test(u.uri!)
                              ? u.uri!
                              : `https://${u.uri!}`;
                            void chrome.tabs.create({ url: target });
                          }}
                          className="text-[13px] font-medium text-[var(--accent)] hover:underline truncate max-w-full bg-transparent border-0 p-0 cursor-pointer text-left block mt-0.5"
                        >
                          {u.uri}
                        </button>
                      </div>
                      <button
                        type="button"
                        onClick={() => {
                          const target = /^https?:\/\//i.test(u.uri!)
                            ? u.uri!
                            : `https://${u.uri!}`;
                          void chrome.tabs.create({ url: target });
                        }}
                        className="ov-icon-btn w-7 h-7 shrink-0"
                        title="Mở trang web"
                        aria-label="Mở trang web"
                      >
                        <ExternalLink size={13} />
                      </button>
                    </div>
                  ))}
              </div>
            ) : null}
          </>
        ) : null}

        {/* Card fields */}
        {cipher.type === CipherType.Card && cipher.card ? (
          <div className="ov-detail-card divide-y divide-[var(--border)]">
            {[
              { label: "cardholder", value: cipher.card.cardholderName },
              { label: "brand", value: cipher.card.brand },
              { label: "number", value: cipher.card.number, mono: true },
              {
                label: "expiry",
                value:
                  cipher.card.expMonth || cipher.card.expYear
                    ? `${cipher.card.expMonth ?? "MM"}/${cipher.card.expYear ?? "YYYY"}`
                    : null,
              },
              { label: "verification code", value: cipher.card.code, mono: true },
            ]
              .filter((f) => Boolean(f.value))
              .map((field) => (
                <div
                  key={field.label}
                  className="px-3.5 py-2.5 group flex items-center justify-between gap-2 hover:bg-[var(--surface-subtle)]"
                >
                  <div
                    className="min-w-0 flex-1 cursor-pointer"
                    onClick={() => {
                      void onCopyText(field.value!, `Đã sao chép ${field.label}`);
                    }}
                  >
                    <div className="text-[11px] font-medium" style={{ color: "var(--field-label)" }}>
                      {field.label}
                    </div>
                    <div className={`text-[13.5px] font-medium mt-0.5 ${field.mono ? "font-mono-code" : ""}`}>
                      {field.value}
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      void onCopyText(field.value!, `Đã sao chép ${field.label}`);
                    }}
                    className="ov-icon-btn w-7 h-7 opacity-0 group-hover:opacity-100"
                    aria-label={`Sao chép ${field.label}`}
                  >
                    <Copy size={13} />
                  </button>
                </div>
              ))}
          </div>
        ) : null}

        {/* Identity fields */}
        {cipher.type === CipherType.Identity && cipher.identity ? (
          <div className="ov-detail-card divide-y divide-[var(--border)]">
            {[
              {
                label: "họ và tên",
                value: [
                  cipher.identity.firstName,
                  cipher.identity.middleName,
                  cipher.identity.lastName,
                ]
                  .filter(Boolean)
                  .join(" "),
              },
              { label: "email", value: cipher.identity.email },
              { label: "điện thoại", value: cipher.identity.phone },
              { label: "công ty", value: cipher.identity.company },
              {
                label: "địa chỉ",
                value: [
                  cipher.identity.address1,
                  cipher.identity.address2,
                  cipher.identity.city,
                  cipher.identity.state,
                  cipher.identity.postalCode,
                  cipher.identity.country,
                ]
                  .filter(Boolean)
                  .join(", "),
              },
            ]
              .filter((f) => Boolean(f.value))
              .map((field) => (
                <div
                  key={field.label}
                  className="px-3.5 py-2.5 group flex items-center justify-between gap-2 hover:bg-[var(--surface-subtle)]"
                >
                  <div
                    className="min-w-0 flex-1 cursor-pointer"
                    onClick={() => {
                      void onCopyText(field.value!, `Đã sao chép ${field.label}`);
                    }}
                  >
                    <div className="text-[11px] font-medium" style={{ color: "var(--field-label)" }}>
                      {field.label}
                    </div>
                    <div className="text-[13.5px] font-medium mt-0.5 break-words text-[var(--text-primary)]">
                      {field.value}
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      void onCopyText(field.value!, `Đã sao chép ${field.label}`);
                    }}
                    className="ov-icon-btn w-7 h-7 opacity-0 group-hover:opacity-100 shrink-0"
                    aria-label={`Sao chép ${field.label}`}
                  >
                    <Copy size={13} />
                  </button>
                </div>
              ))}
          </div>
        ) : null}

        {/* Notes */}
        {cipher.notes ? (
          <div className="px-1">
            <div className="flex items-center justify-between mb-1">
              <span className="text-[11px] font-medium" style={{ color: "var(--field-label)" }}>
                notes
              </span>
              <button
                type="button"
                onClick={() => {
                  void onCopyText(cipher.notes!, "Đã sao chép ghi chú");
                }}
                className="ov-icon-btn w-6 h-6"
                aria-label="Sao chép ghi chú"
              >
                <Copy size={12} />
              </button>
            </div>
            <p className="text-[13px] leading-relaxed whitespace-pre-wrap break-words m-0 text-[var(--text-primary)]">
              {cipher.notes}
            </p>
          </div>
        ) : null}

        {cipher.revisionDate ? (
          <div className="text-[11px] text-[var(--text-muted)] px-1 pt-1">
            Cập nhật: {new Date(cipher.revisionDate).toLocaleString("vi-VN")}
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

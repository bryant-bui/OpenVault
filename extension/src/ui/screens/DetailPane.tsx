import {
  CipherType,
  FieldType,
  SecureNoteType,
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
  Globe,
  Grid3X3,
  Maximize2,
  MoreVertical,
  RotateCcw,
  Star,
  Trash2,
} from "lucide-react";
import { useId, useRef, useState } from "react";
import { formatLocalTimestamp, getStrings, type AppLanguage } from "../../shared/i18n.js";
import { getVisibleCustomFields, normalizeAvatarDataUrl } from "../../shared/media.js";
import {
  countFilledRecoveryCodes,
  isRecoveryCodesField,
  parseRecoveryCodesToSlots,
} from "../../shared/recovery-codes.js";
import { BrandAvatar } from "../components/BrandAvatar.js";
import { ColorPassword } from "../components/ColorPassword.js";
import { ConfirmDialog } from "../components/ConfirmDialog.js";
import { TotpRing } from "../components/TotpRing.js";

interface DetailPaneProps {
  cipher: CipherPayload;
  accountEmail: string;
  accountAvatarBase64?: string | null;
  language?: AppLanguage;
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

const STRENGTH_COLOR: Record<number, string> = {
  0: "var(--danger)",
  1: "var(--warning)",
  2: "var(--accent)",
  3: "var(--success)",
  4: "var(--success)",
};

function getNoteCategoryLabel(noteType: number | undefined, s: ReturnType<typeof getStrings>): string {
  switch (noteType) {
    case SecureNoteType.Software:
      return s.categorySoftware;
    case SecureNoteType.Passport:
      return s.categoryPassport;
    case SecureNoteType.IdCard:
      return s.categoryIdCard;
    default:
      return s.categoryNotes;
  }
}

export function DetailPane({
  cipher,
  accountEmail,
  accountAvatarBase64,
  language = "en",
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
  const s = getStrings(language);
  const [revealPassword, setRevealPassword] = useState(false);
  const [largeType, setLargeType] = useState(false);
  const [revealedHiddenFields, setRevealedHiddenFields] = useState<Record<number, boolean>>({});
  const [revealedRecoveryFields, setRevealedRecoveryFields] = useState<Record<number, boolean>>({});
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);

  const morePopoverId = useId();
  const morePopoverRef = useRef<HTMLDivElement>(null);

  const isTrashed = Boolean(cipher.deletedDate);
  const accountLabel = accountEmail.split("@")[0] || s.personalVault;
  const avatarDataUrl = normalizeAvatarDataUrl(accountAvatarBase64);
  const primaryUri = cipher.login?.uris?.find((u) => u.uri)?.uri ?? null;
  const visibleCustomFields = getVisibleCustomFields(cipher.fields);
  const pwStrength = cipher.login?.password
    ? estimatePasswordStrength(cipher.login.password)
    : null;
  const strengthLabel = pwStrength ? s.strengthLabels[pwStrength.score] : null;
  const strengthColor = pwStrength ? STRENGTH_COLOR[pwStrength.score] : null;

  const hasPasskeys = Boolean(cipher.login?.fido2Credentials && cipher.login.fido2Credentials.length > 0);
  const totpSecret = cipher.login?.totp?.trim() || "";
  const hasTopLoginContent = Boolean(
    cipher.login?.username || cipher.login?.password || totpSecret || hasPasskeys,
  );
  const validUris = (cipher.login?.uris ?? []).filter((u) => Boolean(u.uri?.trim()));

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
    <div className="flex-1 flex flex-col min-h-0 bg-[var(--bg)]">
      {/* Detail Context Top Bar */}
      <div className="h-[44px] shrink-0 px-4 border-b border-[var(--border)] bg-[var(--surface)] flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          {showBackButton ? (
            <button
              type="button"
              onClick={onBack}
              className="ov-icon-btn w-8 h-8 shrink-0"
              aria-label={s.back}
            >
              <ArrowLeft size={16} strokeWidth={2} />
            </button>
          ) : null}

          <span className="inline-flex items-center gap-1.5 text-[13px] font-medium text-[var(--text-secondary)] truncate">
            {avatarDataUrl ? (
              <img
                src={avatarDataUrl}
                alt=""
                className="w-5 h-5 rounded-full object-cover shrink-0"
              />
            ) : (
              <span
                className="w-5 h-5 rounded-full text-[10px] font-bold text-white inline-flex items-center justify-center shrink-0"
                style={{ background: "linear-gradient(135deg, #0A84FF, #0455BF)" }}
              >
                {accountLabel[0]?.toUpperCase() ?? "O"}
              </span>
            )}
            <span className="truncate max-w-[96px]">{accountLabel}</span>
          </span>

          <span className="text-[var(--border-strong)] text-[12px]">|</span>

          <span className="inline-flex items-center gap-1.5 text-[13px] font-medium text-[var(--text-secondary)] truncate">
            <Folder size={14} strokeWidth={1.9} className="shrink-0 text-[var(--text-muted)]" />
            <span className="truncate max-w-[110px]">{folder?.name ?? s.personalVault}</span>
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
              className="ov-btn-autofill h-[30px] px-3 text-[12.5px] leading-none"
            >
              {isCurrentSiteMatch || !primaryUri ? s.fillNow : s.openAndFill}
            </button>
          ) : null}

          {!isTrashed ? (
            <button
              type="button"
              onClick={() => onEdit(cipher)}
              className="ov-icon-btn w-8 h-8"
              title={s.editItem}
              aria-label={s.editItem}
            >
              <Edit3 size={16} strokeWidth={1.9} />
            </button>
          ) : null}

          <button
            type="button"
            popoverTarget={morePopoverId}
            className="ov-icon-btn w-8 h-8"
            title={s.itemOptions}
            aria-label={s.itemOptions}
          >
            <MoreVertical size={17} strokeWidth={1.9} />
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
                  <Edit3 size={15} />
                  <span>{s.editItem}</span>
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
                    size={15}
                    className={cipher.favorite ? "fill-[var(--warning)] text-[var(--warning)]" : ""}
                  />
                  <span>{cipher.favorite ? s.removeFavorite : s.addFavorite}</span>
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
                    <ExternalLink size={15} />
                    <span>{s.fillCurrentTab}</span>
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
                <RotateCcw size={15} />
                <span>{s.restoreFromTrash}</span>
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
              <Trash2 size={15} />
              <span>{isTrashed ? s.deletePermanently : s.moveToTrash}</span>
            </button>
          </div>
        </div>
      </div>

      {/* Scrollable Detail Body (scroll wrapper has NO flex-col so flex children never shrink/clip!) */}
      <div className="flex-1 overflow-y-auto ov-scroll px-5 py-4">
        <div className="flex flex-col gap-4 pb-6">
          {/* Header Hero */}
          <div className="flex items-center gap-4">
            <div className="relative shrink-0">
              <BrandAvatar
                name={cipher.name}
                type={cipher.type}
                secureNoteType={cipher.secureNote?.type}
                uri={primaryUri ?? undefined}
                fields={cipher.fields}
                showFavicon={showFavicons}
                size="lg"
              />
              {cipher.favorite ? (
                <span
                  className="absolute -bottom-1 -right-1 w-5 h-5 rounded-full bg-[var(--surface)] border border-[var(--border)] flex items-center justify-center shadow-sm"
                  title={s.favorites}
                >
                  <Star size={12} className="fill-[var(--warning)] text-[var(--warning)]" />
                </span>
              ) : null}
            </div>
            <div className="min-w-0 flex-1">
              <h2 className="text-[21px] font-bold tracking-tight m-0 truncate text-[var(--text-primary)] leading-snug">
                {cipher.name}
              </h2>
              <div className="flex items-center gap-2 mt-1">
                {cipher.type === CipherType.SecureNote ? (
                  <span className="inline-block px-2 py-0.5 rounded-full text-[11.5px] font-semibold bg-[var(--accent-soft)] text-[var(--accent)]">
                    {getNoteCategoryLabel(cipher.secureNote?.type, s)}
                  </span>
                ) : null}
                {cipher.deletedDate ? (
                  <span className="inline-block text-[12px] font-semibold text-[var(--danger)]">
                    {s.inTrash}
                  </span>
                ) : null}
              </div>
            </div>
          </div>

          {/* Login Primary Card: Username + Password + Passkeys (top group!) + One-Time Password (TOTP) */}
          {cipher.type === CipherType.Login && cipher.login ? (
            <>
              {hasTopLoginContent ? (
                <div className="ov-detail-card divide-y divide-[var(--border)]">
                  {cipher.login.username ? (
                    <div className="px-4 py-3 group flex items-center justify-between gap-2 hover:bg-[var(--surface-subtle)] transition-colors">
                      <div
                        className="min-w-0 flex-1 cursor-pointer"
                        onClick={() => {
                          void onCopyText(
                            cipher.login!.username!,
                            s.isVi ? "Đã sao chép tên đăng nhập" : "Copied username",
                          );
                        }}
                      >
                        <div className="text-[12.5px] font-semibold" style={{ color: "var(--field-label)" }}>
                          {s.usernameLabel}
                        </div>
                        <div className="text-[15px] font-medium mt-0.5 break-all text-[var(--text-primary)]">
                          {cipher.login.username}
                        </div>
                      </div>
                      <button
                        type="button"
                        onClick={() => {
                          void onCopyText(
                            cipher.login!.username!,
                            s.isVi ? "Đã sao chép tên đăng nhập" : "Copied username",
                          );
                        }}
                        className="ov-icon-btn w-8 h-8 shrink-0"
                        title={s.copy}
                        aria-label={s.copy}
                      >
                        <Copy size={16} strokeWidth={1.9} />
                      </button>
                    </div>
                  ) : null}

                  {cipher.login.password ? (
                    <div className="px-4 py-3 group hover:bg-[var(--surface-subtle)] transition-colors">
                      <div className="flex items-center justify-between gap-2">
                        <div className="text-[12.5px] font-semibold" style={{ color: "var(--field-label)" }}>
                          {s.passwordLabel}
                        </div>
                        <div className="flex items-center gap-1">
                          <button
                            type="button"
                            onClick={() => {
                              setRevealPassword(true);
                              setLargeType((v) => !v);
                            }}
                            aria-pressed={largeType}
                            className="ov-icon-btn w-7 h-7"
                            title={s.largeType}
                            aria-label={s.largeType}
                          >
                            <Maximize2 size={15} strokeWidth={1.9} />
                          </button>
                          <button
                            type="button"
                            onClick={() => {
                              setRevealPassword((v) => !v);
                              if (revealPassword) setLargeType(false);
                            }}
                            aria-pressed={revealPassword}
                            className="ov-icon-btn w-7 h-7"
                            title={revealPassword ? s.hidePassword : s.showPassword}
                            aria-label={revealPassword ? s.hidePassword : s.showPassword}
                          >
                            {revealPassword ? <EyeOff size={16} strokeWidth={1.9} /> : <Eye size={16} strokeWidth={1.9} />}
                          </button>
                          <button
                            type="button"
                            onClick={() => {
                              void onCopyText(
                                cipher.login!.password!,
                                s.isVi ? "Đã sao chép mật khẩu" : "Copied password",
                              );
                            }}
                            className="ov-icon-btn w-7 h-7"
                            title={s.copy}
                            aria-label={s.copy}
                          >
                            <Copy size={16} strokeWidth={1.9} />
                          </button>
                        </div>
                      </div>
                      <div className="mt-1 flex items-center justify-between gap-2">
                        <div
                          className="min-w-0 flex-1 cursor-pointer"
                          onClick={() => {
                            void onCopyText(
                              cipher.login!.password!,
                              s.isVi ? "Đã sao chép mật khẩu" : "Copied password",
                            );
                          }}
                        >
                          <ColorPassword
                            value={cipher.login.password}
                            revealed={revealPassword}
                            large={largeType}
                          />
                        </div>
                        {strengthLabel && strengthColor ? (
                          <span
                            className="inline-flex items-center gap-1.5 text-[12.5px] font-semibold shrink-0"
                            style={{ color: "var(--text-secondary)" }}
                          >
                            <span>{strengthLabel}</span>
                            <span className="ov-strength-ring" style={{ color: strengthColor }} />
                          </span>
                        ) : null}
                      </div>
                    </div>
                  ) : null}

                  {/* Passkeys grouped right alongside Username & Password at top of Detail */}
                  {hasPasskeys ? (
                    <div className="px-4 py-3 flex flex-col gap-2">
                      <div className="text-[12.5px] font-semibold" style={{ color: "var(--field-label)" }}>
                        {s.passkeys}
                      </div>
                      {cipher.login.fido2Credentials!.map((cred) => (
                        <div
                          key={cred.credentialId}
                          className="flex items-center justify-between gap-2 px-3 py-2 rounded-[9px]"
                          style={{ background: "var(--passkey-soft)" }}
                        >
                          <div className="flex items-center gap-2.5 min-w-0">
                            <Fingerprint size={18} strokeWidth={2} style={{ color: "var(--passkey)" }} />
                            <div className="min-w-0">
                              <div className="text-[14px] font-semibold truncate text-[var(--text-primary)]">
                                {cred.userDisplayName || cred.userName || cred.rpId}
                              </div>
                              <div className="text-[12px] text-[var(--text-secondary)] truncate">
                                {cred.rpId}
                                {cred.userName && cred.userDisplayName && cred.userName !== cred.userDisplayName
                                  ? ` · ${cred.userName}`
                                  : ""}
                              </div>
                            </div>
                          </div>
                          <span
                            className="text-[11px] font-bold px-2.5 py-0.5 rounded-full text-white shrink-0"
                            style={{ background: "var(--passkey)" }}
                          >
                            Passkey
                          </span>
                        </div>
                      ))}
                    </div>
                  ) : null}

                  {/* Live 2FA TOTP Row (always rendered visibly when cipher.login.totp exists!) */}
                  {totpSecret ? (
                    <div className="px-4 py-3 hover:bg-[var(--surface-subtle)] transition-colors">
                      <div className="text-[12.5px] font-semibold mb-1" style={{ color: "var(--field-label)" }}>
                        {s.totpFieldShortLabel}
                      </div>
                      <TotpRing
                        secretOrUri={totpSecret}
                        onCopy={(code) => {
                          void onCopyText(code, s.isVi ? "Đã sao chép mã 2FA" : "Copied 2FA code");
                        }}
                      />
                    </div>
                  ) : null}
                </div>
              ) : null}

              {/* Websites: Link-Only rows WITHOUT any "Website" title label */}
              {validUris.length > 0 ? (
                <div className="ov-detail-card divide-y divide-[var(--border)]">
                  {validUris.map((u, idx) => {
                    const href = /^https?:\/\//i.test(u.uri!) ? u.uri! : `https://${u.uri!}`;
                    return (
                      <div
                        key={idx}
                        className="px-4 py-2.5 flex items-center justify-between gap-2.5 hover:bg-[var(--surface-subtle)] transition-colors"
                      >
                        <div className="flex items-center gap-2.5 min-w-0 flex-1">
                          <Globe size={16} strokeWidth={1.9} className="text-[var(--accent)] shrink-0" />
                          <a
                            href={href}
                            title={u.uri!}
                            onClick={(e) => {
                              e.preventDefault();
                              void chrome.tabs.create({ url: href });
                            }}
                            className="text-[14px] font-medium text-[var(--accent)] hover:underline truncate block"
                          >
                            {u.uri}
                          </a>
                        </div>
                        <div className="flex items-center gap-1 shrink-0">
                          <button
                            type="button"
                            onClick={() => {
                              void onCopyText(u.uri!, s.isVi ? "Đã sao chép đường dẫn" : "Copied link");
                            }}
                            className="ov-icon-btn w-7 h-7"
                            title={s.copy}
                            aria-label={s.copy}
                          >
                            <Copy size={15} strokeWidth={1.9} />
                          </button>
                          <button
                            type="button"
                            onClick={() => {
                              void chrome.tabs.create({ url: href });
                            }}
                            className="ov-icon-btn w-7 h-7"
                            title={s.openWebsite}
                            aria-label={s.openWebsite}
                          >
                            <ExternalLink size={15} strokeWidth={1.9} />
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              ) : null}
            </>
          ) : null}

          {/* Card fields */}
          {cipher.type === CipherType.Card && cipher.card ? (
            <div className="ov-detail-card divide-y divide-[var(--border)]">
              {[
                { label: s.isVi ? "Chủ Thẻ" : "Cardholder", value: cipher.card.cardholderName },
                { label: s.isVi ? "Thương Hiệu" : "Brand", value: cipher.card.brand },
                { label: s.isVi ? "Số Thẻ" : "Card Number", value: cipher.card.number, mono: true },
                {
                  label: s.isVi ? "Hết Hạn" : "Expiry",
                  value:
                    cipher.card.expMonth || cipher.card.expYear
                      ? `${cipher.card.expMonth ?? "MM"}/${cipher.card.expYear ?? "YYYY"}`
                      : null,
                },
                { label: s.isVi ? "Mã Bảo Mật (CVV)" : "Security Code", value: cipher.card.code, mono: true },
              ]
                .filter((f) => Boolean(f.value))
                .map((field) => (
                  <div
                    key={field.label}
                    className="px-4 py-3 group flex items-center justify-between gap-2 hover:bg-[var(--surface-subtle)]"
                  >
                    <div
                      className="min-w-0 flex-1 cursor-pointer"
                      onClick={() => {
                        void onCopyText(field.value!, `${s.copy}: ${field.label}`);
                      }}
                    >
                      <div className="text-[12.5px] font-semibold" style={{ color: "var(--field-label)" }}>
                        {field.label}
                      </div>
                      <div
                        className={`text-[15px] font-medium mt-0.5 text-[var(--text-primary)] ${
                          field.mono ? "font-mono-code" : ""
                        }`}
                      >
                        {field.value}
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        void onCopyText(field.value!, `${s.copy}: ${field.label}`);
                      }}
                      className="ov-icon-btn w-8 h-8 shrink-0"
                      aria-label={s.copy}
                    >
                      <Copy size={16} strokeWidth={1.9} />
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
                  label: s.isVi ? "Họ Và Tên" : "Full Name",
                  value: [
                    cipher.identity.firstName,
                    cipher.identity.middleName,
                    cipher.identity.lastName,
                  ]
                    .filter(Boolean)
                    .join(" "),
                },
                { label: "Email", value: cipher.identity.email },
                { label: s.isVi ? "Điện Thoại" : "Phone", value: cipher.identity.phone },
                { label: s.isVi ? "Công Ty" : "Company", value: cipher.identity.company },
                {
                  label: s.isVi ? "Địa Chỉ" : "Address",
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
                    className="px-4 py-3 group flex items-center justify-between gap-2 hover:bg-[var(--surface-subtle)]"
                  >
                    <div
                      className="min-w-0 flex-1 cursor-pointer"
                      onClick={() => {
                        void onCopyText(field.value!, `${s.copy}: ${field.label}`);
                      }}
                    >
                      <div className="text-[12.5px] font-semibold" style={{ color: "var(--field-label)" }}>
                        {field.label}
                      </div>
                      <div className="text-[15px] font-medium mt-0.5 break-words text-[var(--text-primary)]">
                        {field.value}
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        void onCopyText(field.value!, `${s.copy}: ${field.label}`);
                      }}
                      className="ov-icon-btn w-8 h-8 shrink-0"
                      aria-label={s.copy}
                    >
                      <Copy size={16} strokeWidth={1.9} />
                    </button>
                  </div>
                ))}
            </div>
          ) : null}

          {/* Recovery Codes Table Cards (4 columns x 5 rows = 20 cells) */}
          {visibleCustomFields.map((field, idx) => {
            if (!isRecoveryCodesField(field.name)) return null;
            const slots = parseRecoveryCodesToSlots(field.value);
            const filledCount = countFilledRecoveryCodes(slots);
            const isShown = Boolean(revealedRecoveryFields[idx]);
            return (
              <div key={`rec-${idx}`} className="ov-detail-card">
                <div className="px-4 py-2.5 flex items-center justify-between gap-2 bg-[var(--surface-subtle)]">
                  <div className="flex items-center gap-2 min-w-0">
                    <Grid3X3 size={16} strokeWidth={2} style={{ color: "var(--field-label)" }} />
                    <span className="text-[13px] font-bold truncate text-[var(--text-primary)]">
                      {field.name || s.recoveryCodesTitle}
                    </span>
                    <span className="px-2 py-0.5 rounded-full text-[11px] font-bold bg-[var(--accent-soft)] text-[var(--accent)] tabular-nums">
                      {filledCount}/20
                    </span>
                  </div>
                  <div className="flex items-center gap-1 shrink-0">
                    <button
                      type="button"
                      onClick={() =>
                        setRevealedRecoveryFields((prev) => ({ ...prev, [idx]: !prev[idx] }))
                      }
                      aria-pressed={isShown}
                      className="ov-icon-btn w-7 h-7"
                      title={isShown ? s.hideCodes : s.showCodes}
                      aria-label={isShown ? s.hideCodes : s.showCodes}
                    >
                      {isShown ? <EyeOff size={15} /> : <Eye size={15} />}
                    </button>
                    {field.value ? (
                      <button
                        type="button"
                        onClick={() => {
                          void onCopyText(field.value!, s.copyAll);
                        }}
                        className="ov-btn-ghost px-2.5 py-1 text-[11.5px] inline-flex items-center gap-1"
                      >
                        <Copy size={12} />
                        <span>{s.copyAll}</span>
                      </button>
                    ) : null}
                  </div>
                </div>

                <div className="ov-recovery-grid">
                  {slots.map((code, slotIdx) => {
                    const hasCode = code.length > 0;
                    const display = hasCode ? (isShown ? code : "••••") : "—";
                    return (
                      <button
                        key={slotIdx}
                        type="button"
                        disabled={!hasCode}
                        onClick={() => {
                          if (!hasCode) return;
                          void onCopyText(code, `#${slotIdx + 1}: ${code}`);
                        }}
                        className="ov-recovery-cell"
                        title={hasCode ? `${s.copy} #${slotIdx + 1}` : undefined}
                      >
                        <span className="text-[10.5px] font-semibold text-[var(--text-muted)] tabular-nums">
                          {String(slotIdx + 1).padStart(2, "0")}
                        </span>
                        <span
                          className={`font-mono-code text-[14.5px] font-bold tracking-[0.06em] ${
                            hasCode ? "text-[var(--text-primary)]" : "text-[var(--text-muted)]"
                          }`}
                        >
                          {display}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>
            );
          })}

          {/* Standard Visible Custom Fields (excluding internal `_ov_icon` and Recovery Codes tables above) */}
          {visibleCustomFields.some((f) => !isRecoveryCodesField(f.name)) ? (
            <div className="ov-detail-card divide-y divide-[var(--border)]">
              {visibleCustomFields.map((field, idx) => {
                if (isRecoveryCodesField(field.name)) return null;
                const isHidden = field.type === FieldType.Hidden;
                const isRevealed = Boolean(revealedHiddenFields[idx]);
                const val = field.value ?? "";
                const isOtp = val.toLowerCase().startsWith("otpauth://");
                return (
                  <div
                    key={`${field.name ?? "f"}-${idx}`}
                    className="px-4 py-3 group flex items-center justify-between gap-2 hover:bg-[var(--surface-subtle)]"
                  >
                    <div
                      className="min-w-0 flex-1 cursor-pointer"
                      onClick={() => {
                        if (!isOtp && val) {
                          void onCopyText(val, `${s.copy}: ${field.name || "Field"}`);
                        }
                      }}
                    >
                      <div className="text-[12.5px] font-semibold" style={{ color: "var(--field-label)" }}>
                        {field.name || "Field"}
                      </div>
                      {isOtp ? (
                        <div className="mt-1">
                          <TotpRing
                            secretOrUri={val}
                            compact
                            onCopy={(code) => {
                              void onCopyText(code, s.isVi ? "Đã sao chép mã 2FA" : "Copied 2FA code");
                            }}
                          />
                        </div>
                      ) : (
                        <div
                          className={`text-[15px] font-medium mt-0.5 break-words text-[var(--text-primary)] ${
                            isHidden ? "font-mono-code" : ""
                          }`}
                        >
                          {isHidden && !isRevealed ? "••••••••••••" : val}
                        </div>
                      )}
                    </div>
                    {!isOtp ? (
                      <div className="flex items-center gap-1 shrink-0">
                        {isHidden ? (
                          <button
                            type="button"
                            onClick={() =>
                              setRevealedHiddenFields((prev) => ({ ...prev, [idx]: !prev[idx] }))
                            }
                            aria-pressed={isRevealed}
                            className="ov-icon-btn w-7 h-7"
                            aria-label={isRevealed ? s.hidePassword : s.showPassword}
                          >
                            {isRevealed ? <EyeOff size={15} /> : <Eye size={15} />}
                          </button>
                        ) : null}
                        {val ? (
                          <button
                            type="button"
                            onClick={() => {
                              void onCopyText(val, `${s.copy}: ${field.name || "Field"}`);
                            }}
                            className="ov-icon-btn w-7 h-7"
                            aria-label={s.copy}
                          >
                            <Copy size={15} />
                          </button>
                        ) : null}
                      </div>
                    ) : null}
                  </div>
                );
              })}
            </div>
          ) : null}

          {/* Notes */}
          {cipher.notes ? (
            <div className="ov-detail-card px-4 py-3">
              <div className="flex items-center justify-between mb-1">
                <span className="text-[12.5px] font-semibold" style={{ color: "var(--field-label)" }}>
                  {s.notesLabel}
                </span>
                <button
                  type="button"
                  onClick={() => {
                    void onCopyText(cipher.notes!, s.isVi ? "Đã sao chép ghi chú" : "Copied notes");
                  }}
                  className="ov-icon-btn w-7 h-7"
                  aria-label={s.copy}
                >
                  <Copy size={15} />
                </button>
              </div>
              <p className="text-[14.5px] leading-relaxed whitespace-pre-wrap break-words m-0 text-[var(--text-primary)]">
                {cipher.notes}
              </p>
            </div>
          ) : null}

          {/* Local Device Timezone Timestamps */}
          {(cipher.revisionDate || cipher.creationDate) ? (
            <div className="text-[12px] text-[var(--text-muted)] px-1 pt-1 flex flex-col gap-0.5">
              {cipher.revisionDate ? (
                <div>
                  {s.lastEdited}: {formatLocalTimestamp(cipher.revisionDate, language)}
                </div>
              ) : null}
              {cipher.creationDate ? (
                <div>
                  {s.added}: {formatLocalTimestamp(cipher.creationDate, language)}
                </div>
              ) : null}
            </div>
          ) : null}
        </div>
      </div>

      <ConfirmDialog
        open={confirmDeleteOpen}
        title={isTrashed ? s.deletePermTitle : s.moveTrashTitle}
        description={isTrashed ? s.deletePermDesc(cipher.name) : s.moveTrashDesc(cipher.name)}
        confirmLabel={isTrashed ? s.deletePermanently : s.moveToTrash}
        onConfirm={() => {
          void handleConfirmDelete();
        }}
        onCancel={() => setConfirmDeleteOpen(false)}
      />
    </div>
  );
}

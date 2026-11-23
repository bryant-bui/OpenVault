import {
  CipherType,
  FieldType,
  generatePassword,
  type CipherField,
  type CipherPayload,
  type Fido2Credential,
  type FolderPayload,
} from "@openvault/crypto-core";
import {
  ArrowLeft,
  Camera,
  ChevronDown,
  Dices,
  Eye,
  EyeOff,
  Fingerprint,
  Plus,
  RotateCcw,
  Save,
  Trash2,
} from "lucide-react";
import { useId, useRef, useState } from "react";
import { extractDisplayDomain } from "../../shared/domain-match.js";
import { getStrings, type AppLanguage } from "../../shared/i18n.js";
import {
  OV_CUSTOM_ICON_FIELD,
  compressImageFileToBase64,
  getRawCustomIconBase64,
  getVisibleCustomFields,
} from "../../shared/media.js";
import { BrandAvatar } from "../components/BrandAvatar.js";
import { ConfirmDialog } from "../components/ConfirmDialog.js";
import { TotpRing } from "../components/TotpRing.js";

interface EditorScreenProps {
  initialCipher: CipherPayload | null;
  folders: FolderPayload[];
  currentTabUrl: string;
  language?: AppLanguage;
  showFavicons?: boolean;
  autoCopyGeneratedPassword?: boolean;
  onCancel: () => void;
  onSave: (cipher: CipherPayload) => Promise<void>;
  onCopyText: (text: string, label: string) => Promise<void>;
}

interface EditableCustomField {
  id: number;
  name: string;
  value: string;
  type: FieldType;
}

export function EditorScreen({
  initialCipher,
  folders,
  currentTabUrl,
  language = "en",
  showFavicons = true,
  autoCopyGeneratedPassword = false,
  onCancel,
  onSave,
  onCopyText,
}: EditorScreenProps) {
  const s = getStrings(language);
  const isEditing = Boolean(initialCipher?.id);
  const [type, setType] = useState<CipherType>(initialCipher?.type ?? CipherType.Login);
  const [name, setName] = useState(
    initialCipher?.name ?? (currentTabUrl ? extractDisplayDomain(currentTabUrl) : ""),
  );
  const [folderId, setFolderId] = useState<string>(initialCipher?.folderId ?? "");
  const [favorite, setFavorite] = useState<boolean>(initialCipher?.favorite ?? false);

  // Custom E2EE item icon (_ov_icon raw base64 WebP synced with Android!)
  const [customIconBase64, setCustomIconBase64] = useState<string | null>(() =>
    getRawCustomIconBase64(initialCipher?.fields),
  );
  const iconFileRef = useRef<HTMLInputElement>(null);

  // Login fields
  const [username, setUsername] = useState(initialCipher?.login?.username ?? "");
  const [password, setPassword] = useState(initialCipher?.login?.password ?? "");
  const [showPassword, setShowPassword] = useState(false);
  const [totp, setTotp] = useState(initialCipher?.login?.totp ?? "");
  const [uris, setUris] = useState<string[]>(() => {
    const existing = initialCipher?.login?.uris?.map((u) => u.uri ?? "").filter(Boolean) ?? [];
    if (existing.length > 0) return existing;
    if (!isEditing && currentTabUrl && /^https?:\/\//i.test(currentTabUrl)) {
      try {
        return [new URL(currentTabUrl).origin];
      } catch {
        return [""];
      }
    }
    return [""];
  });

  // Editable Passkey list (#6: delete passkeys directly in editor!)
  const [passkeys, setPasskeys] = useState<Fido2Credential[]>(
    () => initialCipher?.login?.fido2Credentials ?? [],
  );
  const [passkeyToDelete, setPasskeyToDelete] = useState<Fido2Credential | null>(null);

  // Deep Editable Custom Fields (#3 & #4)
  const nextFieldIdRef = useRef(1);
  const [customFields, setCustomFields] = useState<EditableCustomField[]>(() =>
    getVisibleCustomFields(initialCipher?.fields).map((f) => ({
      id: nextFieldIdRef.current++,
      name: f.name ?? "",
      value: f.value ?? "",
      type: f.type,
    })),
  );
  const addMorePopoverId = useId();
  const addMorePopoverRef = useRef<HTMLDivElement>(null);

  // Card fields
  const [cardholderName, setCardholderName] = useState(initialCipher?.card?.cardholderName ?? "");
  const [brand, setBrand] = useState(initialCipher?.card?.brand ?? "");
  const [cardNumber, setCardNumber] = useState(initialCipher?.card?.number ?? "");
  const [expMonth, setExpMonth] = useState(initialCipher?.card?.expMonth ?? "");
  const [expYear, setExpYear] = useState(initialCipher?.card?.expYear ?? "");
  const [cardCode, setCardCode] = useState(initialCipher?.card?.code ?? "");

  // Identity fields
  const [firstName, setFirstName] = useState(initialCipher?.identity?.firstName ?? "");
  const [middleName, setMiddleName] = useState(initialCipher?.identity?.middleName ?? "");
  const [lastName, setLastName] = useState(initialCipher?.identity?.lastName ?? "");
  const [idEmail, setIdEmail] = useState(initialCipher?.identity?.email ?? "");
  const [phone, setPhone] = useState(initialCipher?.identity?.phone ?? "");
  const [company, setCompany] = useState(initialCipher?.identity?.company ?? "");
  const [address1, setAddress1] = useState(initialCipher?.identity?.address1 ?? "");
  const [address2, setAddress2] = useState(initialCipher?.identity?.address2 ?? "");
  const [city, setCity] = useState(initialCipher?.identity?.city ?? "");
  const [stateRegion, setStateRegion] = useState(initialCipher?.identity?.state ?? "");
  const [postalCode, setPostalCode] = useState(initialCipher?.identity?.postalCode ?? "");
  const [country, setCountry] = useState(initialCipher?.identity?.country ?? "");

  // Notes
  const [notes, setNotes] = useState(initialCipher?.notes ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleGenerateInline = () => {
    const generated = generatePassword({
      length: 20,
      lowercase: true,
      uppercase: true,
      digits: true,
      symbols: true,
    });
    setPassword(generated);
    setShowPassword(true);
    if (autoCopyGeneratedPassword) {
      void onCopyText(
        generated,
        s.isVi ? "Đã tạo & sao chép mật khẩu mạnh" : "Generated & copied strong password",
      );
    }
  };

  const handleIconPick = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const b64 = await compressImageFileToBase64(file, 96, 0.82);
      setCustomIconBase64(b64);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not process image");
    } finally {
      if (iconFileRef.current) iconFileRef.current.value = "";
    }
  };

  const addCustomField = (defaultLabel: string, fieldType: FieldType, prefillValue = "") => {
    addMorePopoverRef.current?.hidePopover?.();
    setCustomFields((prev) => [
      ...prev,
      {
        id: nextFieldIdRef.current++,
        name: defaultLabel,
        value: prefillValue,
        type: fieldType,
      },
    ]);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError(s.isVi ? "Vui lòng nhập tên mục." : "Please enter an item title.");
      return;
    }

    setError(null);
    setSaving(true);
    try {
      const builtFields: CipherField[] = customFields
        .filter((f) => f.name.trim() || f.value.trim())
        .map((f) => ({
          name: f.name.trim() || "field",
          value: f.value,
          type: f.type,
          linkedId: null,
        }));

      if (customIconBase64) {
        builtFields.push({
          name: OV_CUSTOM_ICON_FIELD,
          value: customIconBase64,
          type: FieldType.Hidden,
          linkedId: null,
        });
      }

      const payload: CipherPayload = {
        id: initialCipher?.id,
        type,
        name: name.trim(),
        folderId: folderId || null,
        favorite,
        notes: notes.trim() || null,
        login:
          type === CipherType.Login
            ? {
                username: username.trim() || null,
                password: password || null,
                totp: totp.trim() || null,
                uris: uris
                  .map((u) => u.trim())
                  .filter(Boolean)
                  .map((u) => ({ uri: u, match: null })),
                fido2Credentials: passkeys.length > 0 ? passkeys : null,
              }
            : null,
        secureNote: type === CipherType.SecureNote ? { type: 0 } : null,
        card:
          type === CipherType.Card
            ? {
                cardholderName: cardholderName.trim() || null,
                brand: brand.trim() || null,
                number: cardNumber.trim() || null,
                expMonth: expMonth.trim() || null,
                expYear: expYear.trim() || null,
                code: cardCode.trim() || null,
              }
            : null,
        identity:
          type === CipherType.Identity
            ? {
                ...(initialCipher?.identity ?? {}),
                firstName: firstName.trim() || null,
                middleName: middleName.trim() || null,
                lastName: lastName.trim() || null,
                email: idEmail.trim() || null,
                phone: phone.trim() || null,
                company: company.trim() || null,
                address1: address1.trim() || null,
                address2: address2.trim() || null,
                city: city.trim() || null,
                state: stateRegion.trim() || null,
                postalCode: postalCode.trim() || null,
                country: country.trim() || null,
              }
            : null,
        fields: builtFields.length > 0 ? builtFields : null,
      };
      await onSave(payload);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save item.");
    } finally {
      setSaving(false);
    }
  };

  const previewIconDataUrl = customIconBase64
    ? `data:image/webp;base64,${customIconBase64}`
    : null;

  return (
    <form onSubmit={handleSubmit} className="flex-1 flex flex-col min-h-0">
      <header className="px-3 py-2.5 border-b border-[var(--border)] bg-[var(--surface)] flex items-center justify-between">
        <button
          type="button"
          onClick={onCancel}
          className="ov-icon-btn px-2 py-1.5 gap-1 text-[12.5px] font-semibold"
        >
          <ArrowLeft size={15} />
          <span>{s.cancel}</span>
        </button>

        <span className="text-[13.5px] font-bold">
          {isEditing ? s.editItemTitle : s.newItemTitle}
        </span>

        <button
          type="submit"
          disabled={saving}
          className="ov-btn-primary px-3 py-1.5 inline-flex items-center gap-1.5 text-[12.5px]"
        >
          <Save size={13} />
          <span>{saving ? s.saving : s.save}</span>
        </button>
      </header>

      <div className="flex-1 overflow-y-auto ov-scroll p-3.5 flex flex-col gap-3">
        {!isEditing ? (
          <div className="grid grid-cols-4 gap-1.5">
            {[
              { id: CipherType.Login, label: s.logins },
              { id: CipherType.Card, label: s.isVi ? "Thẻ" : "Card" },
              { id: CipherType.Identity, label: s.identities },
              { id: CipherType.SecureNote, label: s.isVi ? "Ghi chú" : "Note" },
            ].map((opt) => (
              <button
                key={opt.id}
                type="button"
                onClick={() => setType(opt.id)}
                className={`py-1.5 rounded-[10px] text-[11.5px] font-semibold border cursor-pointer ${
                  type === opt.id
                    ? "bg-[var(--accent-soft)] text-[var(--accent)] border-[var(--accent-border)]"
                    : "bg-[var(--surface)] text-[var(--text-secondary)] border-[var(--border)]"
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
        ) : null}

        {/* Title + Custom Item Icon Picker (#7) */}
        <div className="ov-card p-3 flex flex-col gap-3">
          <div className="flex items-center gap-3">
            <div className="flex flex-col items-center gap-1 shrink-0">
              <input
                ref={iconFileRef}
                type="file"
                accept="image/*"
                className="sr-only"
                onChange={(e) => {
                  void handleIconPick(e);
                }}
              />
              <button
                type="button"
                onClick={() => iconFileRef.current?.click()}
                className="relative group p-0 border-0 bg-transparent cursor-pointer rounded-[12px]"
                title={s.changeIcon}
              >
                <BrandAvatar
                  name={name || "O"}
                  type={type}
                  uri={uris[0]}
                  customIconDataUrl={previewIconDataUrl}
                  showFavicon={showFavicons}
                  size="lg"
                />
                <span className="absolute -bottom-1 -right-1 w-5 h-5 rounded-full bg-[var(--accent)] text-white flex items-center justify-center shadow-sm">
                  <Camera size={11} />
                </span>
              </button>
              {customIconBase64 ? (
                <button
                  type="button"
                  onClick={() => setCustomIconBase64(null)}
                  className="text-[10px] font-semibold text-[var(--text-muted)] hover:text-[var(--danger)] bg-transparent border-0 p-0 cursor-pointer inline-flex items-center gap-0.5"
                >
                  <RotateCcw size={9} />
                  <span>{s.resetIcon}</span>
                </button>
              ) : null}
            </div>

            <div className="flex-1 min-w-0">
              <label className="block text-[11px] font-semibold text-[var(--text-muted)] uppercase mb-1">
                {s.itemNameLabel}
              </label>
              <input
                type="text"
                className="ov-input"
                placeholder={s.itemNamePlaceholder}
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                autoFocus
              />
            </div>
          </div>

          <div className="flex items-center gap-3">
            {folders.length > 0 ? (
              <div className="flex-1">
                <label className="block text-[11px] font-semibold text-[var(--text-muted)] uppercase mb-1">
                  {s.folderLabel}
                </label>
                <select
                  value={folderId}
                  onChange={(e) => setFolderId(e.target.value)}
                  className="ov-input py-1.5 text-[12.5px]"
                >
                  <option value="">{s.noFolder}</option>
                  {folders.map((f) => (
                    <option key={f.id} value={f.id}>
                      {f.name}
                    </option>
                  ))}
                </select>
              </div>
            ) : null}

            <label className="inline-flex items-center gap-2 text-[12.5px] font-medium cursor-pointer pt-4">
              <input
                type="checkbox"
                checked={favorite}
                onChange={(e) => setFavorite(e.target.checked)}
              />
              <span>{s.favoriteLabel}</span>
            </label>
          </div>
        </div>

        {type === CipherType.Login ? (
          <>
            <div className="ov-card p-3 flex flex-col gap-3">
              <div>
                <label className="block text-[11px] font-semibold text-[var(--text-muted)] uppercase mb-1">
                  {s.usernameLabel}
                </label>
                <input
                  type="text"
                  className="ov-input"
                  placeholder="user@example.com"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                />
              </div>

              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="text-[11px] font-semibold text-[var(--text-muted)] uppercase">
                    {s.passwordLabel}
                  </label>
                  <button
                    type="button"
                    onClick={handleGenerateInline}
                    className="inline-flex items-center gap-1 text-[11.5px] font-semibold text-[var(--accent)] bg-transparent border-0 cursor-pointer p-0"
                  >
                    <Dices size={13} />
                    <span>{s.generateStrong}</span>
                  </button>
                </div>
                <div className="relative">
                  <input
                    type={showPassword ? "text" : "password"}
                    className="ov-input font-mono-code pr-9"
                    placeholder={s.passwordPlaceholder}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    className="ov-icon-btn w-7 h-7 absolute right-1.5 top-1/2 -translate-y-1/2"
                    aria-label={showPassword ? s.hidePassword : s.showPassword}
                  >
                    {showPassword ? <EyeOff size={14} /> : <Eye size={14} />}
                  </button>
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-semibold text-[var(--text-muted)] uppercase mb-1">
                  {s.totpLabel}
                </label>
                <input
                  type="text"
                  className="ov-input font-mono-code text-[12px]"
                  placeholder="JBSWY3DPEHPK3PXP or otpauth://totp/..."
                  value={totp}
                  onChange={(e) => setTotp(e.target.value)}
                />
                {totp.trim().length >= 8 ? (
                  <div className="mt-2 px-2 py-1 rounded-[10px] bg-[var(--surface-subtle)]">
                    <TotpRing
                      secretOrUri={totp}
                      compact
                      onCopy={(code) => {
                        void onCopyText(code, s.isVi ? "Đã sao chép mã 2FA" : "Copied 2FA code");
                      }}
                    />
                  </div>
                ) : null}
              </div>
            </div>

            {/* Passkey Management with Delete (#6) */}
            {passkeys.length > 0 ? (
              <div className="ov-card p-3 flex flex-col gap-2">
                <span className="text-[11px] font-semibold text-[var(--text-muted)] uppercase">
                  {s.passkeys}
                </span>
                {passkeys.map((cred) => (
                  <div
                    key={cred.credentialId}
                    className="flex items-center justify-between gap-2 px-2.5 py-2 rounded-[9px]"
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
                    <button
                      type="button"
                      onClick={() => setPasskeyToDelete(cred)}
                      className="ov-icon-btn w-7 h-7 shrink-0 hover:text-[var(--danger)]"
                      title={s.remove}
                      aria-label={s.remove}
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                ))}
              </div>
            ) : null}

            {/* Single-line Truncated Websites (#3) */}
            <div className="ov-card p-3 flex flex-col gap-2">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-semibold text-[var(--text-muted)] uppercase">
                  {s.websitesLabel}
                </span>
                <button
                  type="button"
                  onClick={() => setUris((prev) => [...prev, ""])}
                  className="inline-flex items-center gap-1 text-[11.5px] font-semibold text-[var(--accent)] bg-transparent border-0 cursor-pointer p-0"
                >
                  <Plus size={13} />
                  <span>{s.addWebsite}</span>
                </button>
              </div>
              {uris.map((u, idx) => (
                <div key={idx} className="flex items-center gap-1.5">
                  <input
                    type="text"
                    className="ov-input text-[12.5px] truncate"
                    placeholder="https://github.com"
                    value={u}
                    title={u}
                    onChange={(e) =>
                      setUris((prev) => prev.map((val, i) => (i === idx ? e.target.value : val)))
                    }
                  />
                  {uris.length > 1 ? (
                    <button
                      type="button"
                      onClick={() => setUris((prev) => prev.filter((_, i) => i !== idx))}
                      className="ov-icon-btn w-8 h-8 shrink-0 hover:text-[var(--danger)]"
                      aria-label={s.delete}
                    >
                      <Trash2 size={14} />
                    </button>
                  ) : null}
                </div>
              ))}
            </div>
          </>
        ) : null}

        {type === CipherType.Card ? (
          <div className="ov-card p-3 flex flex-col gap-2.5">
            <input
              type="text"
              className="ov-input"
              placeholder={s.isVi ? "Tên chủ thẻ" : "Cardholder name"}
              value={cardholderName}
              onChange={(e) => setCardholderName(e.target.value)}
            />
            <input
              type="text"
              className="ov-input"
              placeholder={s.isVi ? "Thương hiệu (Visa, Mastercard...)" : "Brand (Visa, Mastercard...)"}
              value={brand}
              onChange={(e) => setBrand(e.target.value)}
            />
            <input
              type="text"
              className="ov-input font-mono-code"
              placeholder={s.isVi ? "Số thẻ" : "Card number"}
              value={cardNumber}
              onChange={(e) => setCardNumber(e.target.value)}
            />
            <div className="grid grid-cols-3 gap-2">
              <input
                type="text"
                className="ov-input"
                placeholder="MM"
                value={expMonth}
                onChange={(e) => setExpMonth(e.target.value)}
              />
              <input
                type="text"
                className="ov-input"
                placeholder="YYYY"
                value={expYear}
                onChange={(e) => setExpYear(e.target.value)}
              />
              <input
                type="text"
                className="ov-input font-mono-code"
                placeholder="CVV"
                value={cardCode}
                onChange={(e) => setCardCode(e.target.value)}
              />
            </div>
          </div>
        ) : null}

        {type === CipherType.Identity ? (
          <div className="ov-card p-3 flex flex-col gap-2.5">
            <div className="grid grid-cols-3 gap-2">
              <input
                type="text"
                className="ov-input"
                placeholder={s.isVi ? "Họ" : "First name"}
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
              />
              <input
                type="text"
                className="ov-input"
                placeholder={s.isVi ? "Tên đệm" : "Middle"}
                value={middleName}
                onChange={(e) => setMiddleName(e.target.value)}
              />
              <input
                type="text"
                className="ov-input"
                placeholder={s.isVi ? "Tên" : "Last name"}
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
              />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <input
                type="email"
                className="ov-input"
                placeholder="Email"
                value={idEmail}
                onChange={(e) => setIdEmail(e.target.value)}
              />
              <input
                type="tel"
                className="ov-input"
                placeholder={s.isVi ? "Số điện thoại" : "Phone"}
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
              />
            </div>
            <input
              type="text"
              className="ov-input"
              placeholder={s.isVi ? "Công ty / Tổ chức" : "Company / Organization"}
              value={company}
              onChange={(e) => setCompany(e.target.value)}
            />
            <input
              type="text"
              className="ov-input"
              placeholder={s.isVi ? "Địa chỉ (Dòng 1)" : "Address line 1"}
              value={address1}
              onChange={(e) => setAddress1(e.target.value)}
            />
            <input
              type="text"
              className="ov-input"
              placeholder={s.isVi ? "Địa chỉ (Dòng 2)" : "Address line 2"}
              value={address2}
              onChange={(e) => setAddress2(e.target.value)}
            />
            <div className="grid grid-cols-2 gap-2">
              <input
                type="text"
                className="ov-input"
                placeholder={s.isVi ? "Thành phố" : "City"}
                value={city}
                onChange={(e) => setCity(e.target.value)}
              />
              <input
                type="text"
                className="ov-input"
                placeholder={s.isVi ? "Tỉnh / Bang" : "State / Province"}
                value={stateRegion}
                onChange={(e) => setStateRegion(e.target.value)}
              />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <input
                type="text"
                className="ov-input"
                placeholder={s.isVi ? "Mã bưu chính" : "Postal code"}
                value={postalCode}
                onChange={(e) => setPostalCode(e.target.value)}
              />
              <input
                type="text"
                className="ov-input"
                placeholder={s.isVi ? "Quốc gia" : "Country"}
                value={country}
                onChange={(e) => setCountry(e.target.value)}
              />
            </div>
          </div>
        ) : null}

        {/* Deep Editable Custom Fields + "+ Add more" Dropdown (#3 & #4) */}
        <div className="ov-card p-3 flex flex-col gap-2.5">
          <div className="flex items-center justify-between">
            <span className="text-[11px] font-semibold text-[var(--text-muted)] uppercase">
              {s.customFields}
            </span>
            <button
              type="button"
              popoverTarget={addMorePopoverId}
              className="inline-flex items-center gap-1 text-[11.5px] font-semibold text-[var(--accent)] bg-transparent border-0 cursor-pointer p-0"
            >
              <Plus size={13} />
              <span>{s.addMore}</span>
              <ChevronDown size={12} />
            </button>
            <div
              id={addMorePopoverId}
              ref={addMorePopoverRef}
              popover="auto"
              className="ov-popover ov-popover-end"
            >
              <button
                type="button"
                className="ov-popover-item"
                onClick={() =>
                  addCustomField(
                    s.addSecQuestion,
                    FieldType.Hidden,
                    generatePassword({ length: 16, lowercase: true, uppercase: true, digits: true, symbols: false }),
                  )
                }
              >
                <span>{s.addSecQuestion}</span>
              </button>
              <hr className="my-1 border-0 border-t border-[var(--border)]" />
              <button type="button" className="ov-popover-item" onClick={() => addCustomField(s.addText, FieldType.Text)}>
                <span>{s.addText}</span>
              </button>
              <button type="button" className="ov-popover-item" onClick={() => addCustomField(s.addUrl, FieldType.Text)}>
                <span>{s.addUrl}</span>
              </button>
              <button type="button" className="ov-popover-item" onClick={() => addCustomField(s.addEmail, FieldType.Text)}>
                <span>{s.addEmail}</span>
              </button>
              <button type="button" className="ov-popover-item" onClick={() => addCustomField(s.addAddress, FieldType.Text)}>
                <span>{s.addAddress}</span>
              </button>
              <button type="button" className="ov-popover-item" onClick={() => addCustomField(s.addDate, FieldType.Text)}>
                <span>{s.addDate}</span>
              </button>
              <button type="button" className="ov-popover-item" onClick={() => addCustomField(s.addOtp, FieldType.Hidden)}>
                <span>{s.addOtp}</span>
              </button>
              <button type="button" className="ov-popover-item" onClick={() => addCustomField(s.addPasswordField, FieldType.Hidden)}>
                <span>{s.addPasswordField}</span>
              </button>
              <button type="button" className="ov-popover-item" onClick={() => addCustomField(s.addPhone, FieldType.Text)}>
                <span>{s.addPhone}</span>
              </button>
            </div>
          </div>

          {customFields.length > 0 ? (
            <div className="flex flex-col gap-2">
              {customFields.map((field) => (
                <div
                  key={field.id}
                  className="p-2 rounded-[9px] bg-[var(--surface-subtle)] border border-[var(--border)] flex flex-col gap-1.5"
                >
                  <div className="flex items-center gap-1.5">
                    <input
                      type="text"
                      className="flex-1 bg-transparent border-0 border-b border-[var(--border)] px-1 py-0.5 text-[11.5px] font-semibold outline-none text-[var(--field-label)]"
                      placeholder={s.fieldLabelPlaceholder}
                      value={field.name}
                      onChange={(e) =>
                        setCustomFields((prev) =>
                          prev.map((f) => (f.id === field.id ? { ...f, name: e.target.value } : f)),
                        )
                      }
                    />
                    <button
                      type="button"
                      onClick={() =>
                        setCustomFields((prev) => prev.filter((f) => f.id !== field.id))
                      }
                      className="ov-icon-btn w-6 h-6 shrink-0 hover:text-[var(--danger)]"
                      aria-label={s.delete}
                    >
                      <Trash2 size={12} />
                    </button>
                  </div>
                  <input
                    type={field.type === FieldType.Hidden ? "text" : "text"}
                    className={`ov-input py-1.5 text-[12.5px] ${
                      field.type === FieldType.Hidden ? "font-mono-code" : ""
                    }`}
                    placeholder={s.fieldValuePlaceholder}
                    value={field.value}
                    onChange={(e) =>
                      setCustomFields((prev) =>
                        prev.map((f) => (f.id === field.id ? { ...f, value: e.target.value } : f)),
                      )
                    }
                  />
                </div>
              ))}
            </div>
          ) : null}
        </div>

        <div className="ov-card p-3">
          <label className="block text-[11px] font-semibold text-[var(--text-muted)] uppercase mb-1">
            {s.notesLabel}
          </label>
          <textarea
            rows={3}
            className="ov-input resize-y"
            placeholder={s.notesPlaceholder}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
        </div>

        {error ? (
          <div className="px-3 py-2 rounded-[10px] text-[12px] font-medium bg-[var(--danger-soft)] text-[var(--danger)]">
            {error}
          </div>
        ) : null}
      </div>

      <ConfirmDialog
        open={passkeyToDelete !== null}
        title={s.removePasskeyTitle}
        description={
          passkeyToDelete
            ? `${passkeyToDelete.rpId} (${passkeyToDelete.userName || "Passkey"}) — ${s.removePasskeyDesc}`
            : s.removePasskeyDesc
        }
        confirmLabel={s.remove}
        onConfirm={() => {
          if (passkeyToDelete) {
            setPasskeys((prev) =>
              prev.filter((p) => p.credentialId !== passkeyToDelete.credentialId),
            );
          }
          setPasskeyToDelete(null);
        }}
        onCancel={() => setPasskeyToDelete(null)}
      />
    </form>
  );
}

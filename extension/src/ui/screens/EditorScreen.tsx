import {
  CipherType,
  generatePassword,
  type CipherPayload,
  type FolderPayload,
} from "@openvault/crypto-core";
import { ArrowLeft, Dices, Eye, EyeOff, Plus, Save, Trash2 } from "lucide-react";
import { useState } from "react";
import { extractDisplayDomain } from "../../shared/domain-match.js";
import { TotpRing } from "../components/TotpRing.js";

interface EditorScreenProps {
  initialCipher: CipherPayload | null;
  folders: FolderPayload[];
  currentTabUrl: string;
  onCancel: () => void;
  onSave: (cipher: CipherPayload) => Promise<void>;
  onCopyText: (text: string, label: string) => Promise<void>;
}

export function EditorScreen({
  initialCipher,
  folders,
  currentTabUrl,
  onCancel,
  onSave,
  onCopyText,
}: EditorScreenProps) {
  const isEditing = Boolean(initialCipher?.id);
  const [type, setType] = useState<CipherType>(initialCipher?.type ?? CipherType.Login);
  const [name, setName] = useState(
    initialCipher?.name ?? (currentTabUrl ? extractDisplayDomain(currentTabUrl) : ""),
  );
  const [folderId, setFolderId] = useState<string>(initialCipher?.folderId ?? "");
  const [favorite, setFavorite] = useState<boolean>(initialCipher?.favorite ?? false);

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

  // Card fields
  const [cardholderName, setCardholderName] = useState(initialCipher?.card?.cardholderName ?? "");
  const [brand, setBrand] = useState(initialCipher?.card?.brand ?? "");
  const [cardNumber, setCardNumber] = useState(initialCipher?.card?.number ?? "");
  const [expMonth, setExpMonth] = useState(initialCipher?.card?.expMonth ?? "");
  const [expYear, setExpYear] = useState(initialCipher?.card?.expYear ?? "");
  const [cardCode, setCardCode] = useState(initialCipher?.card?.code ?? "");

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
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError("Vui lòng nhập tên mục.");
      return;
    }

    setError(null);
    setSaving(true);
    try {
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
                fido2Credentials: initialCipher?.login?.fido2Credentials ?? null,
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
        identity: initialCipher?.identity ?? null,
        fields: initialCipher?.fields ?? null,
      };
      await onSave(payload);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Không thể lưu mục.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="flex-1 flex flex-col min-h-0">
      <header className="px-3 py-2.5 border-b border-[var(--border)] bg-[var(--surface)] flex items-center justify-between">
        <button
          type="button"
          onClick={onCancel}
          className="ov-icon-btn px-2 py-1.5 gap-1 text-[12.5px] font-semibold"
        >
          <ArrowLeft size={15} />
          <span>Hủy</span>
        </button>

        <span className="text-[13.5px] font-bold">
          {isEditing ? "Chỉnh sửa mục" : "Thêm mục mới"}
        </span>

        <button
          type="submit"
          disabled={saving}
          className="ov-btn-primary px-3 py-1.5 inline-flex items-center gap-1.5 text-[12.5px]"
        >
          <Save size={13} />
          <span>{saving ? "Đang lưu..." : "Lưu"}</span>
        </button>
      </header>

      <div className="flex-1 overflow-y-auto ov-scroll p-3.5 flex flex-col gap-3">
        {!isEditing ? (
          <div className="grid grid-cols-3 gap-1.5">
            {[
              { id: CipherType.Login, label: "Đăng nhập" },
              { id: CipherType.SecureNote, label: "Ghi chú" },
              { id: CipherType.Card, label: "Thẻ" },
            ].map((opt) => (
              <button
                key={opt.id}
                type="button"
                onClick={() => setType(opt.id)}
                className={`py-1.5 rounded-[10px] text-[12px] font-semibold border cursor-pointer ${
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

        <div className="ov-card p-3 flex flex-col gap-3">
          <div>
            <label className="block text-[11px] font-semibold text-[var(--text-muted)] uppercase mb-1">
              Tên mục *
            </label>
            <input
              type="text"
              className="ov-input"
              placeholder="Ví dụ: GitHub, Gmail, Vietcombank..."
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              autoFocus
            />
          </div>

          <div className="flex items-center gap-3">
            {folders.length > 0 ? (
              <div className="flex-1">
                <label className="block text-[11px] font-semibold text-[var(--text-muted)] uppercase mb-1">
                  Thư mục
                </label>
                <select
                  value={folderId}
                  onChange={(e) => setFolderId(e.target.value)}
                  className="ov-input py-1.5 text-[12.5px]"
                >
                  <option value="">Không có thư mục</option>
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
              <span>Yêu thích</span>
            </label>
          </div>
        </div>

        {type === CipherType.Login ? (
          <>
            <div className="ov-card p-3 flex flex-col gap-3">
              <div>
                <label className="block text-[11px] font-semibold text-[var(--text-muted)] uppercase mb-1">
                  Tên đăng nhập / Email
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
                    Mật khẩu
                  </label>
                  <button
                    type="button"
                    onClick={handleGenerateInline}
                    className="inline-flex items-center gap-1 text-[11.5px] font-semibold text-[var(--accent)] bg-transparent border-0 cursor-pointer p-0"
                  >
                    <Dices size={13} />
                    <span>Tạo mật khẩu mạnh</span>
                  </button>
                </div>
                <div className="relative">
                  <input
                    type={showPassword ? "text" : "password"}
                    className="ov-input font-mono-code pr-9"
                    placeholder="Nhập hoặc tạo mật khẩu"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    className="ov-icon-btn w-7 h-7 absolute right-1.5 top-1/2 -translate-y-1/2"
                    aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                  >
                    {showPassword ? <EyeOff size={14} /> : <Eye size={14} />}
                  </button>
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-semibold text-[var(--text-muted)] uppercase mb-1">
                  Khóa xác thực 2FA (TOTP Secret hoặc otpauth://)
                </label>
                <input
                  type="text"
                  className="ov-input font-mono-code text-[12px]"
                  placeholder="JBSWY3DPEHPK3PXP hoặc otpauth://totp/..."
                  value={totp}
                  onChange={(e) => setTotp(e.target.value)}
                />
                {totp.trim().length >= 8 ? (
                  <div className="mt-2 px-2 py-1 rounded-[10px] bg-[var(--surface-elevated)]">
                    <TotpRing
                      secretOrUri={totp}
                      compact
                      onCopy={(code) => {
                        void onCopyText(code, "Đã sao chép mã 2FA");
                      }}
                    />
                  </div>
                ) : null}
              </div>
            </div>

            <div className="ov-card p-3 flex flex-col gap-2">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-semibold text-[var(--text-muted)] uppercase">
                  Trang web (URL)
                </span>
                <button
                  type="button"
                  onClick={() => setUris((prev) => [...prev, ""])}
                  className="inline-flex items-center gap-1 text-[11.5px] font-semibold text-[var(--accent)] bg-transparent border-0 cursor-pointer p-0"
                >
                  <Plus size={13} />
                  <span>Thêm URL</span>
                </button>
              </div>
              {uris.map((u, idx) => (
                <div key={idx} className="flex items-center gap-1.5">
                  <input
                    type="text"
                    className="ov-input text-[12.5px]"
                    placeholder="https://github.com"
                    value={u}
                    onChange={(e) =>
                      setUris((prev) => prev.map((val, i) => (i === idx ? e.target.value : val)))
                    }
                  />
                  {uris.length > 1 ? (
                    <button
                      type="button"
                      onClick={() => setUris((prev) => prev.filter((_, i) => i !== idx))}
                      className="ov-icon-btn w-8 h-8 shrink-0 hover:text-[var(--danger)]"
                      aria-label="Xóa URL"
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
              placeholder="Tên chủ thẻ"
              value={cardholderName}
              onChange={(e) => setCardholderName(e.target.value)}
            />
            <input
              type="text"
              className="ov-input"
              placeholder="Thương hiệu (Visa, Mastercard...)"
              value={brand}
              onChange={(e) => setBrand(e.target.value)}
            />
            <input
              type="text"
              className="ov-input font-mono-code"
              placeholder="Số thẻ"
              value={cardNumber}
              onChange={(e) => setCardNumber(e.target.value)}
            />
            <div className="grid grid-cols-3 gap-2">
              <input
                type="text"
                className="ov-input"
                placeholder="Tháng (MM)"
                value={expMonth}
                onChange={(e) => setExpMonth(e.target.value)}
              />
              <input
                type="text"
                className="ov-input"
                placeholder="Năm (YYYY)"
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

        <div className="ov-card p-3">
          <label className="block text-[11px] font-semibold text-[var(--text-muted)] uppercase mb-1">
            Ghi chú bảo mật
          </label>
          <textarea
            rows={3}
            className="ov-input resize-y"
            placeholder="Mã khôi phục, câu hỏi bảo mật..."
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
    </form>
  );
}

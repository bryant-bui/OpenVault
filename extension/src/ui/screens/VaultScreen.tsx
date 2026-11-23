import { CipherType, type CipherPayload, type FolderPayload } from "@openvault/crypto-core";
import {
  Copy,
  KeyRound,
  Lock,
  PanelRightOpen,
  Plus,
  RefreshCw,
  Search,
  Shield,
  Sparkles,
  Star,
  Trash2,
  X,
} from "lucide-react";
import { useMemo, useRef, useState } from "react";
import { extractDisplayDomain, findMatchingLogins } from "../../shared/domain-match.js";
import type { PendingSaveCredential } from "../../shared/types.js";
import { BrandAvatar } from "../components/BrandAvatar.js";

type FilterCategory = "all" | "login" | "totp" | "passkey" | "note" | "card" | "trash";

interface VaultScreenProps {
  ciphers: CipherPayload[];
  folders: FolderPayload[];
  currentTabUrl: string;
  isPopup: boolean;
  syncing: boolean;
  pendingSave: PendingSaveCredential | null;
  onSelectCipher: (cipher: CipherPayload) => void;
  onNewCipher: () => void;
  onAutofill: (cipherId: string) => Promise<void>;
  onCopyText: (text: string, label: string) => Promise<void>;
  onSync: () => Promise<void>;
  onLock: () => Promise<void>;
  onOpenSidePanel: () => Promise<void>;
  onAcceptPendingSave: () => Promise<void>;
  onDismissPendingSave: () => Promise<void>;
}

const FILTER_TABS: { id: FilterCategory; label: string }[] = [
  { id: "all", label: "Tất cả" },
  { id: "login", label: "Đăng nhập" },
  { id: "totp", label: "2FA" },
  { id: "passkey", label: "Passkey" },
  { id: "note", label: "Ghi chú" },
  { id: "card", label: "Thẻ" },
  { id: "trash", label: "Thùng rác" },
];

export function VaultScreen({
  ciphers,
  folders,
  currentTabUrl,
  isPopup,
  syncing,
  pendingSave,
  onSelectCipher,
  onNewCipher,
  onAutofill,
  onCopyText,
  onSync,
  onLock,
  onOpenSidePanel,
  onAcceptPendingSave,
  onDismissPendingSave,
}: VaultScreenProps) {
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState<FilterCategory>("all");
  const [selectedFolderId, setSelectedFolderId] = useState<string>("all");
  const searchRef = useRef<HTMLInputElement>(null);

  const matchingLogins = useMemo(
    () => (currentTabUrl ? findMatchingLogins(ciphers, currentTabUrl) : []),
    [ciphers, currentTabUrl],
  );
  const matchingIds = useMemo(() => new Set(matchingLogins.map((m) => m.id)), [matchingLogins]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return ciphers
      .filter((c) => {
        if (category === "trash") return Boolean(c.deletedDate);
        if (c.deletedDate) return false;
        if (selectedFolderId !== "all" && (c.folderId ?? "none") !== selectedFolderId) return false;

        if (category === "login" && c.type !== CipherType.Login) return false;
        if (category === "totp" && !c.login?.totp) return false;
        if (category === "passkey" && !(c.login?.fido2Credentials && c.login.fido2Credentials.length > 0))
          return false;
        if (category === "note" && c.type !== CipherType.SecureNote) return false;
        if (category === "card" && c.type !== CipherType.Card) return false;

        if (!q) return true;
        const haystack = [
          c.name,
          c.login?.username ?? "",
          ...(c.login?.uris?.map((u) => u.uri ?? "") ?? []),
          c.card?.brand ?? "",
          c.notes ?? "",
        ]
          .join(" ")
          .toLowerCase();
        return haystack.includes(q);
      })
      .sort((a, b) => {
        if (a.favorite !== b.favorite) return a.favorite ? -1 : 1;
        return a.name.localeCompare(b.name);
      });
  }, [ciphers, category, selectedFolderId, query]);

  const favorites = useMemo(
    () => filtered.filter((c) => c.favorite && !query && category === "all"),
    [filtered, query, category],
  );
  const regularItems = useMemo(
    () =>
      !query && category === "all"
        ? filtered.filter((c) => !c.favorite)
        : filtered,
    [filtered, query, category],
  );

  const renderRow = (item: CipherPayload, showAutofillButton = false) => {
    const subtitle =
      item.type === CipherType.Login
        ? item.login?.username || extractDisplayDomain(item.login?.uris?.[0]?.uri) || "Tài khoản đăng nhập"
        : item.type === CipherType.Card
          ? item.card?.brand || "Thẻ thanh toán"
          : item.type === CipherType.SecureNote
            ? "Ghi chú bảo mật"
            : "Thông tin định danh";

    const hasTotp = Boolean(item.login?.totp);
    const hasPasskey = Boolean(item.login?.fido2Credentials?.length);

    return (
      <div
        key={item.id ?? item.name}
        className="group flex items-center gap-2.5 px-2.5 py-2 rounded-[12px] hover:bg-[var(--surface-elevated)] transition-colors cursor-pointer"
        onClick={() => onSelectCipher(item)}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            onSelectCipher(item);
          }
        }}
      >
        <BrandAvatar name={item.name} type={item.type} size="md" />

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5">
            <span className="text-[13.5px] font-semibold truncate text-[var(--text-primary)]">
              {item.name}
            </span>
            {item.favorite ? (
              <Star size={11} className="shrink-0 fill-[var(--warning)] text-[var(--warning)]" />
            ) : null}
          </div>
          <div className="flex items-center gap-1.5 mt-0.5">
            <span className="text-[12px] text-[var(--text-secondary)] truncate">{subtitle}</span>
            {hasTotp ? (
              <span className="shrink-0 px-1.5 py-0.2 rounded-full text-[9.5px] font-bold bg-[var(--accent-soft)] text-[var(--accent)]">
                2FA
              </span>
            ) : null}
            {hasPasskey ? (
              <span className="shrink-0 px-1.5 py-0.2 rounded-full text-[9.5px] font-bold bg-[var(--success-soft)] text-[var(--success)]">
                Passkey
              </span>
            ) : null}
          </div>
        </div>

        <div
          className="flex items-center gap-1 shrink-0"
          onClick={(e) => e.stopPropagation()}
        >
          {showAutofillButton && item.id ? (
            <button
              type="button"
              onClick={() => {
                void onAutofill(item.id!);
              }}
              className="px-2.5 py-1 rounded-[8px] text-[11.5px] font-semibold bg-[var(--accent)] text-white border-0 cursor-pointer hover:bg-[var(--accent-hover)]"
            >
              Điền
            </button>
          ) : item.login?.password ? (
            <button
              type="button"
              onClick={() => {
                void onCopyText(item.login!.password!, "Đã sao chép mật khẩu");
              }}
              className="ov-icon-btn w-7 h-7 opacity-0 group-hover:opacity-100 focus:opacity-100"
              title="Sao chép mật khẩu"
              aria-label={`Sao chép mật khẩu của ${item.name}`}
            >
              <Copy size={14} />
            </button>
          ) : null}
        </div>
      </div>
    );
  };

  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* Top 1Password-style sticky header */}
      <header className="px-3.5 pt-3 pb-2.5 border-b border-[var(--border)] bg-[var(--surface)]">
        <div className="flex items-center gap-2">
          <div className="relative flex-1">
            <Search
              size={14}
              className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--text-muted)] pointer-events-none"
            />
            <input
              ref={searchRef}
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Tìm mật khẩu, trang web, 2FA..."
              className="ov-input pl-8 pr-7 py-2 text-[12.5px]"
              aria-label="Tìm kiếm trong OpenVault"
            />
            {query ? (
              <button
                type="button"
                onClick={() => setQuery("")}
                className="ov-icon-btn w-6 h-6 absolute right-1.5 top-1/2 -translate-y-1/2"
                aria-label="Xóa từ khóa tìm kiếm"
              >
                <X size={13} />
              </button>
            ) : null}
          </div>

          <button
            type="button"
            onClick={onNewCipher}
            className="ov-btn-primary w-8 h-8 flex items-center justify-center shrink-0"
            title="Thêm mục mới"
            aria-label="Thêm mục mới"
          >
            <Plus size={16} />
          </button>

          <button
            type="button"
            onClick={() => {
              void onSync();
            }}
            className="ov-icon-btn w-8 h-8 shrink-0"
            title="Đồng bộ với Cloudflare D1"
            aria-label="Đồng bộ"
          >
            <RefreshCw size={15} className={syncing ? "animate-spin text-[var(--accent)]" : ""} />
          </button>

          {isPopup ? (
            <button
              type="button"
              onClick={() => {
                void onOpenSidePanel();
              }}
              className="ov-icon-btn w-8 h-8 shrink-0"
              title="Mở rộng sang Side Panel"
              aria-label="Mở Side Panel"
            >
              <PanelRightOpen size={15} />
            </button>
          ) : null}

          <button
            type="button"
            onClick={() => {
              void onLock();
            }}
            className="ov-icon-btn w-8 h-8 shrink-0"
            title="Khóa OpenVault ngay"
            aria-label="Khóa OpenVault"
          >
            <Lock size={15} />
          </button>
        </div>

        {/* Category Filter Pills */}
        <div className="flex items-center gap-1.5 mt-2.5 overflow-x-auto pb-0.5 ov-scroll">
          {FILTER_TABS.map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setCategory(tab.id)}
              className={`px-2.5 py-1 rounded-full text-[11.5px] font-semibold border whitespace-nowrap cursor-pointer transition-colors ${
                category === tab.id
                  ? "bg-[var(--accent-soft)] text-[var(--accent)] border-[var(--accent-border)]"
                  : "bg-transparent text-[var(--text-secondary)] border-transparent hover:bg-[var(--surface-elevated)]"
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {folders.length > 0 && category !== "trash" ? (
          <div className="mt-2">
            <select
              value={selectedFolderId}
              onChange={(e) => setSelectedFolderId(e.target.value)}
              className="ov-input py-1 text-[11.5px]"
              aria-label="Lọc theo thư mục"
            >
              <option value="all">Mọi thư mục ({folders.length})</option>
              <option value="none">Không thuộc thư mục</option>
              {folders.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.name}
                </option>
              ))}
            </select>
          </div>
        ) : null}
      </header>

      {/* Scrollable Vault Content */}
      <div className="flex-1 overflow-y-auto ov-scroll px-2.5 py-2.5 flex flex-col gap-3">
        {/* Pending Save Banner */}
        {pendingSave ? (
          <div className="ov-card p-3 border-[var(--accent-border)] bg-[var(--accent-soft)]">
            <div className="flex items-center gap-2 text-[12.5px] font-bold text-[var(--accent)]">
              <Sparkles size={14} />
              <span>Phát hiện đăng nhập mới</span>
            </div>
            <p className="text-[12px] text-[var(--text-secondary)] mt-1 mb-2.5 truncate">
              {pendingSave.username || "(Không rõ tên)"} · {extractDisplayDomain(pendingSave.url)}
            </p>
            <div className="flex gap-2 justify-end">
              <button
                type="button"
                onClick={() => {
                  void onDismissPendingSave();
                }}
                className="ov-btn-ghost px-2.5 py-1 text-[11.5px]"
              >
                Bỏ qua
              </button>
              <button
                type="button"
                onClick={() => {
                  void onAcceptPendingSave();
                }}
                className="ov-btn-primary px-3 py-1 text-[11.5px]"
              >
                Lưu vào Vault
              </button>
            </div>
          </div>
        ) : null}

        {/* Suggested for current tab */}
        {!query && category === "all" && matchingLogins.length > 0 ? (
          <section>
            <div className="px-2 mb-1 flex items-center justify-between">
              <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--accent)]">
                Gợi ý cho {extractDisplayDomain(currentTabUrl)}
              </span>
              <span className="text-[11px] font-semibold text-[var(--text-muted)]">
                {matchingLogins.length}
              </span>
            </div>
            <div className="ov-card p-1">
              {matchingLogins.map((match) => {
                const full = ciphers.find((c) => c.id === match.id);
                return full ? renderRow(full, true) : null;
              })}
            </div>
          </section>
        ) : null}

        {/* Favorites */}
        {favorites.length > 0 ? (
          <section>
            <div className="px-2 mb-1 text-[11px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
              Yêu thích
            </div>
            <div className="ov-card p-1">
              {favorites.map((item) => renderRow(item, Boolean(item.id && matchingIds.has(item.id))))}
            </div>
          </section>
        ) : null}

        {/* All / Filtered Items */}
        {regularItems.length > 0 ? (
          <section>
            <div className="px-2 mb-1 flex items-center justify-between">
              <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
                {category === "trash" ? "Thùng rác" : query ? "Kết quả tìm kiếm" : "Tất cả mục"}
              </span>
              <span className="text-[11px] text-[var(--text-muted)]">{regularItems.length}</span>
            </div>
            <div className="ov-card p-1">
              {regularItems.map((item) =>
                renderRow(item, Boolean(item.id && matchingIds.has(item.id))),
              )}
            </div>
          </section>
        ) : null}

        {/* Empty State */}
        {filtered.length === 0 ? (
          <div className="flex-1 flex flex-col items-center justify-center text-center py-10 px-4">
            <div className="w-12 h-12 rounded-[14px] bg-[var(--surface-elevated)] flex items-center justify-center text-[var(--text-muted)] mb-3">
              {category === "trash" ? <Trash2 size={22} /> : query ? <Search size={22} /> : <KeyRound size={22} />}
            </div>
            <p className="text-[14px] font-semibold m-0 text-[var(--text-primary)]">
              {query
                ? "Không tìm thấy mục phù hợp"
                : category === "trash"
                  ? "Thùng rác trống"
                  : "Kho mật khẩu của bạn đang trống"}
            </p>
            <p className="text-[12px] text-[var(--text-secondary)] mt-1 mb-4">
              {query
                ? "Thử tìm bằng tên miền hoặc tên tài khoản khác."
                : "Thêm tài khoản mới hoặc Import file JSON từ Bitwarden trong mục Cài đặt."}
            </p>
            {!query && category !== "trash" ? (
              <button
                type="button"
                onClick={onNewCipher}
                className="ov-btn-primary px-4 py-2 inline-flex items-center gap-1.5"
              >
                <Shield size={14} />
                <span>Thêm tài khoản đầu tiên</span>
              </button>
            ) : null}
          </div>
        ) : null}
      </div>
    </div>
  );
}

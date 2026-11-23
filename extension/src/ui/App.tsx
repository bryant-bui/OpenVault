import {
  CipherType,
  estimatePasswordStrength,
  type CipherPayload,
  type FolderPayload,
} from "@openvault/crypto-core";
import {
  ChevronDown,
  ChevronRight,
  CreditCard,
  Dices,
  FileText,
  Fingerprint,
  Folder,
  KeyRound,
  Lock,
  PanelLeft,
  PanelRightOpen,
  Plus,
  RefreshCw,
  Search,
  Settings,
  Shield,
  ShieldCheck,
  Sparkles,
  Star,
  Trash2,
  X,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { DEFAULT_SERVER_URL, DEFAULT_SETTINGS } from "../shared/constants.js";
import {
  extractDisplayDomain,
  findMatchingLogins,
  scoreUriMatch,
} from "../shared/domain-match.js";
import type {
  DecryptedVault,
  ExtensionSettings,
  VaultStatusResponse,
} from "../shared/types.js";
import { BrandAvatar } from "./components/BrandAvatar.js";
import {
  getActiveTab,
  openSidePanelFromCurrentWindow,
  sendBg,
} from "./components/bridge.js";
import { Toast, type ToastMessage } from "./components/Toast.js";
import { AuthScreen } from "./screens/AuthScreen.js";
import { DetailPane } from "./screens/DetailPane.js";
import { EditorScreen } from "./screens/EditorScreen.js";
import { GeneratorScreen } from "./screens/GeneratorScreen.js";
import { SettingsScreen } from "./screens/SettingsScreen.js";
import { UnlockScreen } from "./screens/UnlockScreen.js";
import { WatchtowerPanel } from "./screens/WatchtowerPanel.js";

type FilterKind = "all" | "favorites" | "login" | "totp" | "passkey" | "weak" | "card" | "note" | "trash";
type RightPanelMode = "detail" | "editor" | "generator" | "watchtower" | "settings";

export function App() {
  const isPopup = document.body.dataset.surface !== "sidepanel";

  const [booting, setBooting] = useState(true);
  const [status, setStatus] = useState<VaultStatusResponse>({
    hasAccount: false,
    unlocked: false,
    email: null,
    serverUrl: DEFAULT_SERVER_URL,
    lastSyncAt: null,
    settings: { ...DEFAULT_SETTINGS },
    pendingSave: null,
  });

  const [ciphers, setCiphers] = useState<CipherPayload[]>([]);
  const [folders, setFolders] = useState<FolderPayload[]>([]);
  const [syncing, setSyncing] = useState(false);

  const [query, setQuery] = useState("");
  const [filterKind, setFilterKind] = useState<FilterKind>("all");
  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [favoritesOpen, setFavoritesOpen] = useState(true);

  const [selectedCipherId, setSelectedCipherId] = useState<string | null>(null);
  const [rightMode, setRightMode] = useState<RightPanelMode>("detail");
  const [editingCipher, setEditingCipher] = useState<CipherPayload | null>(null);
  const [narrowWidth, setNarrowWidth] = useState(() => window.innerWidth < 540);
  const [mobileShowDetail, setMobileShowDetail] = useState(false);

  const [currentTabId, setCurrentTabId] = useState<number | null>(null);
  const [currentTabUrl, setCurrentTabUrl] = useState<string>("");

  const [toast, setToast] = useState<ToastMessage | null>(null);
  const toastTimerRef = useRef<number | null>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const onResize = () => setNarrowWidth(window.innerWidth < 540);
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        searchRef.current?.focus();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  const showToast = useCallback((text: string, tone: "info" | "success" | "error" = "info") => {
    setToast({ id: Date.now(), text, tone });
    if (toastTimerRef.current !== null) {
      window.clearTimeout(toastTimerRef.current);
    }
    toastTimerRef.current = window.setTimeout(() => {
      setToast(null);
    }, 2600);
  }, []);

  const refreshActiveTab = useCallback(async () => {
    try {
      const tab = await getActiveTab();
      setCurrentTabId(tab?.id ?? null);
      setCurrentTabUrl(tab?.url ?? "");
    } catch {
      setCurrentTabId(null);
      setCurrentTabUrl("");
    }
  }, []);

  const loadVault = useCallback(
    async (forceSync = false) => {
      setSyncing(true);
      try {
        const vault = await sendBg<DecryptedVault>({ type: "GET_VAULT", forceSync });
        setCiphers(vault.ciphers);
        setFolders(vault.folders);
        setStatus((prev) => ({ ...prev, lastSyncAt: vault.lastSyncAt }));
      } catch (err) {
        showToast(err instanceof Error ? err.message : "Không thể đồng bộ Vault", "error");
      } finally {
        setSyncing(false);
      }
    },
    [showToast],
  );

  const loadStatus = useCallback(async () => {
    try {
      const next = await sendBg<VaultStatusResponse>({ type: "GET_STATUS" });
      setStatus(next);
      if (next.hasAccount && next.unlocked) {
        await loadVault(false);
      } else {
        setCiphers([]);
        setFolders([]);
        setSelectedCipherId(null);
        setRightMode("detail");
      }
    } catch (err) {
      showToast(err instanceof Error ? err.message : "Lỗi khởi động OpenVault", "error");
    } finally {
      setBooting(false);
    }
  }, [loadVault, showToast]);

  useEffect(() => {
    void refreshActiveTab();
    void loadStatus();
  }, [refreshActiveTab, loadStatus]);

  const matchingLogins = useMemo(
    () => (currentTabUrl ? findMatchingLogins(ciphers, currentTabUrl) : []),
    [ciphers, currentTabUrl],
  );

  const weakOrReusedIds = useMemo(() => {
    const activeLogins = ciphers.filter((c) => !c.deletedDate && c.type === CipherType.Login);
    const pwCounts = new Map<string, number>();
    for (const c of activeLogins) {
      const pw = c.login?.password ?? "";
      if (pw) pwCounts.set(pw, (pwCounts.get(pw) ?? 0) + 1);
    }
    const flagged = new Set<string>();
    for (const c of activeLogins) {
      if (!c.id) continue;
      const pw = c.login?.password ?? "";
      if (pw && (estimatePasswordStrength(pw).score <= 1 || (pwCounts.get(pw) ?? 0) > 1)) {
        flagged.add(c.id);
      }
    }
    return flagged;
  }, [ciphers]);

  const filteredCiphers = useMemo(() => {
    const q = query.trim().toLowerCase();
    return ciphers
      .filter((c) => {
        if (filterKind === "trash") return Boolean(c.deletedDate);
        if (c.deletedDate) return false;
        if (selectedFolderId !== null && c.folderId !== selectedFolderId) return false;

        if (filterKind === "favorites" && !c.favorite) return false;
        if (filterKind === "login" && c.type !== CipherType.Login) return false;
        if (filterKind === "totp" && !c.login?.totp) return false;
        if (filterKind === "passkey" && !(c.login?.fido2Credentials && c.login.fido2Credentials.length > 0))
          return false;
        if (filterKind === "weak" && !(c.id && weakOrReusedIds.has(c.id))) return false;
        if (filterKind === "card" && c.type !== CipherType.Card) return false;
        if (filterKind === "note" && c.type !== CipherType.SecureNote) return false;

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
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [ciphers, filterKind, selectedFolderId, query, weakOrReusedIds]);

  // Auto-select first matching login or first visible item in wide Master-Detail view
  useEffect(() => {
    if (rightMode !== "detail") return;
    if (selectedCipherId && filteredCiphers.some((c) => c.id === selectedCipherId)) return;
    const preferred =
      (!query && filterKind === "all" && matchingLogins[0]?.id) ||
      filteredCiphers.find((c) => c.favorite)?.id ||
      filteredCiphers[0]?.id ||
      null;
    setSelectedCipherId(preferred);
  }, [filteredCiphers, matchingLogins, query, filterKind, selectedCipherId, rightMode]);

  const handleCopyText = useCallback(
    async (text: string, label: string) => {
      try {
        await navigator.clipboard.writeText(text);
        showToast(label, "success");
        void sendBg({ type: "TOUCH_ACTIVITY" }).catch(() => undefined);
      } catch {
        showToast("Không thể ghi vào bộ nhớ tạm", "error");
      }
    },
    [showToast],
  );

  const handleAutofill = useCallback(
    async (cipherId: string) => {
      if (currentTabId === null) {
        showToast("Không tìm thấy tab đang mở", "error");
        return;
      }
      try {
        const res = await sendBg<{ filled: boolean }>({
          type: "AUTOFILL_IN_TAB",
          tabId: currentTabId,
          cipherId,
        });
        if (res.filled) {
          showToast("Đã tự động điền vào trang web", "success");
          if (isPopup) window.close();
        } else {
          showToast("Không tìm thấy ô nhập mật khẩu trên trang này", "error");
        }
      } catch (err) {
        showToast(err instanceof Error ? err.message : "Tự động điền thất bại", "error");
      }
    },
    [currentTabId, isPopup, showToast],
  );

  const handleSaveCipher = useCallback(
    async (cipher: CipherPayload) => {
      const saved = await sendBg<CipherPayload>({ type: "SAVE_CIPHER", cipher });
      await loadVault(false);
      setEditingCipher(null);
      setSelectedCipherId(saved.id ?? null);
      setRightMode("detail");
      showToast("Đã mã hóa và đồng bộ lên Cloudflare D1", "success");
    },
    [loadVault, showToast],
  );

  const handleToggleFavorite = useCallback(
    async (cipher: CipherPayload) => {
      await sendBg({
        type: "SAVE_CIPHER",
        cipher: { ...cipher, favorite: !cipher.favorite },
      });
      await loadVault(false);
    },
    [loadVault],
  );

  const handleDeleteCipher = useCallback(
    async (id: string, permanent: boolean) => {
      await sendBg({ type: "DELETE_CIPHER", id, permanent });
      setSelectedCipherId(null);
      setMobileShowDetail(false);
      await loadVault(false);
      showToast(permanent ? "Đã xóa vĩnh viễn" : "Đã chuyển vào Thùng rác", "info");
    },
    [loadVault, showToast],
  );

  const handleRestoreCipher = useCallback(
    async (id: string) => {
      await sendBg({ type: "RESTORE_CIPHER", id });
      await loadVault(false);
      showToast("Đã khôi phục mục từ Thùng rác", "success");
    },
    [loadVault, showToast],
  );

  const handleLock = useCallback(async () => {
    await sendBg({ type: "LOCK" });
    await loadStatus();
  }, [loadStatus]);

  const handleLogout = useCallback(
    async (allDevices: boolean) => {
      await sendBg({ type: "LOGOUT", allDevices });
      await loadStatus();
      showToast("Đã đăng xuất khỏi OpenVault", "info");
    },
    [loadStatus, showToast],
  );

  const handleOpenSidePanel = useCallback(async () => {
    try {
      await openSidePanelFromCurrentWindow();
      if (isPopup) window.close();
    } catch {
      showToast("Không thể mở Side Panel trên cửa sổ này", "error");
    }
  }, [isPopup, showToast]);

  const handleUpdateSettings = useCallback(
    async (partial: Partial<ExtensionSettings>) => {
      const next = await sendBg<ExtensionSettings>({ type: "UPDATE_SETTINGS", settings: partial });
      setStatus((prev) => ({ ...prev, settings: next }));
      showToast("Đã lưu cài đặt", "success");
    },
    [showToast],
  );

  const handleAcceptPendingSave = useCallback(async () => {
    await sendBg({ type: "SAVE_PENDING_CREDENTIAL" });
    setStatus((prev) => ({ ...prev, pendingSave: null }));
    await loadVault(false);
    showToast("Đã lưu tài khoản mới vào OpenVault!", "success");
  }, [loadVault, showToast]);

  const handleDismissPendingSave = useCallback(async () => {
    await sendBg({ type: "DISMISS_PENDING_SAVE" });
    setStatus((prev) => ({ ...prev, pendingSave: null }));
  }, []);

  if (booting) {
    return (
      <div className="flex-1 flex items-center justify-center text-[13px] text-[var(--text-secondary)]">
        Đang khởi động OpenVault...
      </div>
    );
  }

  if (!status.hasAccount) {
    return (
      <div className="flex-1 flex flex-col bg-[var(--bg)] overflow-hidden">
        <div className="flex-1 flex flex-col max-w-[400px] w-full mx-auto min-h-0">
          <AuthScreen
            initialServerUrl={status.serverUrl}
            onAuthenticated={loadStatus}
            onToast={showToast}
          />
        </div>
        <Toast toast={toast} onDismiss={() => setToast(null)} />
      </div>
    );
  }

  if (!status.unlocked) {
    return (
      <div className="flex-1 flex flex-col bg-[var(--bg)] overflow-hidden">
        <div className="flex-1 flex flex-col max-w-[390px] w-full mx-auto min-h-0">
          <UnlockScreen
            email={status.email ?? ""}
            serverUrl={status.serverUrl}
            onUnlocked={loadStatus}
            onLoggedOut={loadStatus}
          />
        </div>
        <Toast toast={toast} onDismiss={() => setToast(null)} />
      </div>
    );
  }

  const selectedCipher = selectedCipherId
    ? ciphers.find((c) => c.id === selectedCipherId) ?? null
    : null;

  const selectedFolder = selectedCipher?.folderId
    ? folders.find((f) => f.id === selectedCipher.folderId) ?? null
    : null;

  const canAutofillSelected = Boolean(
    selectedCipher?.login?.uris?.some((u) => scoreUriMatch(currentTabUrl, u.uri) > 0) ||
      (currentTabUrl.startsWith("http") && selectedCipher?.type === CipherType.Login),
  );

  const favoriteItems =
    !query && filterKind === "all" && selectedFolderId === null
      ? filteredCiphers.filter((c) => c.favorite)
      : [];
  const otherItems =
    !query && filterKind === "all" && selectedFolderId === null
      ? filteredCiphers.filter((c) => !c.favorite)
      : filteredCiphers;

  const selectItem = (cipher: CipherPayload) => {
    setSelectedCipherId(cipher.id ?? null);
    setRightMode("detail");
    setMobileShowDetail(true);
  };

  const renderListRow = (item: CipherPayload) => {
    const isSelected = rightMode === "detail" && item.id === selectedCipherId;
    const subtitle =
      item.type === CipherType.Login
        ? item.login?.username || extractDisplayDomain(item.login?.uris?.[0]?.uri) || "Login"
        : item.type === CipherType.Card
          ? item.card?.number
            ? `•••• ${item.card.number.slice(-4)}`
            : item.card?.brand || "Thẻ thanh toán"
          : item.type === CipherType.SecureNote
            ? "Ghi chú bảo mật"
            : "Định danh";

    return (
      <button
        key={item.id ?? item.name}
        type="button"
        data-selected={isSelected}
        onClick={() => selectItem(item)}
        className="ov-list-row"
      >
        <BrandAvatar
          name={item.name}
          type={item.type}
          uri={item.login?.uris?.[0]?.uri}
          showFavicon={status.settings.showFavicons}
          size="md"
          inverted={isSelected}
        />
        <div className="min-w-0 flex-1">
          <div className="text-[13px] font-semibold truncate leading-tight">{item.name}</div>
          <div className="ov-row-sub text-[11.5px] text-[var(--text-secondary)] truncate mt-0.5 leading-tight">
            {subtitle}
          </div>
        </div>
        {item.login?.totp ? (
          <span
            className="text-[9.5px] font-bold px-1.5 py-0.5 rounded-md shrink-0"
            style={{
              background: isSelected ? "rgba(255,255,255,0.22)" : "var(--accent-soft)",
              color: isSelected ? "#ffffff" : "var(--accent)",
            }}
          >
            2FA
          </span>
        ) : null}
      </button>
    );
  };

  const isOverlayPanel =
    rightMode === "editor" ||
    rightMode === "generator" ||
    rightMode === "watchtower" ||
    rightMode === "settings";

  const showLeftPane = !narrowWidth || (!mobileShowDetail && !isOverlayPanel);
  const showRightPane = !narrowWidth || mobileShowDetail || isOverlayPanel;

  return (
    <div className="flex-1 flex flex-col min-h-0 bg-[var(--surface)] relative">
      {/* Authentic 1Password Single Top Control Bar (ZERO bottom tabs!) */}
      <header className="h-[48px] shrink-0 px-3 border-b border-[var(--border)] bg-[var(--surface)] flex items-center gap-2">
        {/* Brand Emblem + Filter Sidebar Toggle */}
        <button
          type="button"
          onClick={() => setSidebarOpen((v) => !v)}
          data-active={sidebarOpen}
          className="ov-icon-btn h-8 px-1.5 gap-1.5 shrink-0"
          title="Danh mục & Thư mục"
          aria-label="Mở bộ lọc danh mục"
        >
          <img
            src="/icons/icon-32.png"
            alt="OpenVault"
            className="w-5 h-5 rounded-[5px]"
          />
          <PanelLeft size={14} />
        </button>

        {/* Recessed 1Password Search Bar */}
        <div className="relative flex-1 min-w-0">
          <Search
            size={13}
            className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--text-muted)] pointer-events-none"
          />
          <input
            ref={searchRef}
            type="search"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              if (rightMode !== "detail") setRightMode("detail");
            }}
            placeholder="Tìm trong OpenVault..."
            className="ov-search-input"
            aria-label="Tìm trong OpenVault"
          />
          {query ? (
            <button
              type="button"
              onClick={() => setQuery("")}
              className="ov-icon-btn w-5 h-5 absolute right-1.5 top-1/2 -translate-y-1/2"
              aria-label="Xóa tìm kiếm"
            >
              <X size={12} />
            </button>
          ) : null}
        </div>

        {/* Utility Tool Icons (Watchtower, Generator, Sync, Settings, Side Panel, Lock) */}
        <div className="flex items-center gap-0.5 shrink-0">
          <button
            type="button"
            data-active={rightMode === "watchtower"}
            onClick={() =>
              setRightMode((m) => (m === "watchtower" ? "detail" : "watchtower"))
            }
            className="ov-icon-btn w-7 h-7"
            title="Watchtower — Điểm bảo mật"
            aria-label="Watchtower"
          >
            <ShieldCheck size={15} />
          </button>

          <button
            type="button"
            data-active={rightMode === "generator"}
            onClick={() =>
              setRightMode((m) => (m === "generator" ? "detail" : "generator"))
            }
            className="ov-icon-btn w-7 h-7"
            title="Password Generator"
            aria-label="Tạo mật khẩu mạnh"
          >
            <Dices size={15} />
          </button>

          <button
            type="button"
            onClick={() => {
              void loadVault(true);
            }}
            className="ov-icon-btn w-7 h-7"
            title="Đồng bộ Cloudflare D1"
            aria-label="Đồng bộ"
          >
            <RefreshCw size={14} className={syncing ? "animate-spin text-[var(--accent)]" : ""} />
          </button>

          <button
            type="button"
            data-active={rightMode === "settings"}
            onClick={() =>
              setRightMode((m) => (m === "settings" ? "detail" : "settings"))
            }
            className="ov-icon-btn w-7 h-7"
            title="Cài đặt, Backup R2 & Import Bitwarden"
            aria-label="Cài đặt"
          >
            <Settings size={15} />
          </button>

          {isPopup ? (
            <button
              type="button"
              onClick={() => {
                void handleOpenSidePanel();
              }}
              className="ov-icon-btn w-7 h-7"
              title="Mở ở Side Panel"
              aria-label="Mở Side Panel"
            >
              <PanelRightOpen size={14} />
            </button>
          ) : null}

          <button
            type="button"
            onClick={() => {
              void handleLock();
            }}
            className="ov-icon-btn w-7 h-7"
            title="Khóa OpenVault"
            aria-label="Khóa OpenVault"
          >
            <Lock size={14} />
          </button>
        </div>

        {/* Signature 1Password Primary Blue "+ New Item" CTA */}
        <button
          type="button"
          onClick={() => {
            setEditingCipher(null);
            setRightMode("editor");
          }}
          className="ov-btn-primary h-[30px] px-3 inline-flex items-center gap-1 shrink-0"
        >
          <Plus size={14} strokeWidth={2.6} />
          <span>Mới</span>
        </button>
      </header>

      {/* Main Split Workspace */}
      <div className="flex-1 flex min-h-0 relative overflow-hidden">
        {/* Slide-over Category & Folder Sidebar (1Password-style collapsible filter rail) */}
        {sidebarOpen ? (
          <aside className="w-[172px] shrink-0 border-r border-[var(--border)] bg-[var(--bg)] overflow-y-auto ov-scroll p-2 flex flex-col gap-0.5 z-20">
            {[
              { id: "all" as const, label: "Tất cả mục", icon: KeyRound },
              { id: "favorites" as const, label: "Yêu thích", icon: Star },
              { id: "login" as const, label: "Đăng nhập", icon: Shield },
              { id: "totp" as const, label: "Mã 2FA (TOTP)", icon: ShieldCheck },
              { id: "passkey" as const, label: "Passkeys", icon: Fingerprint },
              { id: "card" as const, label: "Thẻ thanh toán", icon: CreditCard },
              { id: "note" as const, label: "Ghi chú bảo mật", icon: FileText },
              { id: "trash" as const, label: "Thùng rác", icon: Trash2 },
            ].map((cat) => {
              const Icon = cat.icon;
              const active = filterKind === cat.id && selectedFolderId === null;
              return (
                <button
                  key={cat.id}
                  type="button"
                  onClick={() => {
                    setFilterKind(cat.id);
                    setSelectedFolderId(null);
                    setRightMode("detail");
                    setSidebarOpen(false);
                  }}
                  className={`w-full flex items-center gap-2 px-2.5 py-1.5 rounded-[8px] border-0 text-[12.5px] font-medium cursor-pointer text-left transition-colors ${
                    active
                      ? "bg-[var(--accent-soft)] text-[var(--accent)] font-semibold"
                      : "bg-transparent text-[var(--text-secondary)] hover:bg-[var(--surface-hover)] hover:text-[var(--text-primary)]"
                  }`}
                >
                  <Icon size={14} />
                  <span className="truncate">{cat.label}</span>
                </button>
              );
            })}

            {folders.length > 0 ? (
              <>
                <div className="px-2.5 pt-3 pb-1 text-[10.5px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
                  Thư mục
                </div>
                {folders.map((folder) => {
                  const active = selectedFolderId === folder.id;
                  return (
                    <button
                      key={folder.id}
                      type="button"
                      onClick={() => {
                        setFilterKind("all");
                        setSelectedFolderId(folder.id ?? null);
                        setRightMode("detail");
                        setSidebarOpen(false);
                      }}
                      className={`w-full flex items-center gap-2 px-2.5 py-1.5 rounded-[8px] border-0 text-[12.5px] font-medium cursor-pointer text-left ${
                        active
                          ? "bg-[var(--accent-soft)] text-[var(--accent)] font-semibold"
                          : "bg-transparent text-[var(--text-secondary)] hover:bg-[var(--surface-hover)]"
                      }`}
                    >
                      <Folder size={13} />
                      <span className="truncate">{folder.name}</span>
                    </button>
                  );
                })}
              </>
            ) : null}
          </aside>
        ) : null}

        {/* Left Master Item List Pane */}
        {showLeftPane ? (
          <div
            className={`${
              narrowWidth ? "flex-1" : "w-[248px] shrink-0 border-r border-[var(--border)]"
            } flex flex-col min-h-0 bg-[var(--surface)]`}
          >
            {/* Active filter chip when filtered */}
            {(filterKind !== "all" || selectedFolderId !== null) ? (
              <div className="px-2.5 py-1.5 border-b border-[var(--border)] flex items-center justify-between bg-[var(--bg)]">
                <span className="text-[11.5px] font-semibold text-[var(--accent)]">
                  {selectedFolderId
                    ? folders.find((f) => f.id === selectedFolderId)?.name ?? "Thư mục"
                    : filterKind === "weak"
                      ? "Mật khẩu yếu / trùng"
                      : filterKind === "totp"
                        ? "Mã 2FA (TOTP)"
                        : filterKind === "passkey"
                          ? "Passkeys"
                          : filterKind === "trash"
                            ? "Thùng rác"
                            : filterKind === "favorites"
                              ? "Yêu thích"
                              : filterKind === "card"
                                ? "Thẻ thanh toán"
                                : filterKind === "note"
                                  ? "Ghi chú"
                                  : "Đăng nhập"}
                </span>
                <button
                  type="button"
                  onClick={() => {
                    setFilterKind("all");
                    setSelectedFolderId(null);
                  }}
                  className="ov-icon-btn w-5 h-5"
                  aria-label="Bỏ lọc"
                >
                  <X size={12} />
                </button>
              </div>
            ) : null}

            <div className="flex-1 overflow-y-auto ov-scroll p-2 flex flex-col gap-2">
              {/* Pending Save Banner */}
              {status.pendingSave ? (
                <div className="p-2.5 rounded-[10px] border border-[var(--accent-border)] bg-[var(--accent-soft)]">
                  <div className="flex items-center gap-1.5 text-[11.5px] font-bold text-[var(--accent)]">
                    <Sparkles size={13} />
                    <span>Lưu đăng nhập mới?</span>
                  </div>
                  <div className="text-[11px] text-[var(--text-secondary)] truncate mt-0.5 mb-2">
                    {status.pendingSave.username || extractDisplayDomain(status.pendingSave.url)}
                  </div>
                  <div className="flex gap-1.5 justify-end">
                    <button
                      type="button"
                      onClick={() => {
                        void handleDismissPendingSave();
                      }}
                      className="ov-btn-ghost px-2 py-1 text-[11px]"
                    >
                      Bỏ
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        void handleAcceptPendingSave();
                      }}
                      className="ov-btn-primary px-2.5 py-1 text-[11px]"
                    >
                      Lưu
                    </button>
                  </div>
                </div>
              ) : null}

              {/* Suggested for current website */}
              {!query && filterKind === "all" && selectedFolderId === null && matchingLogins.length > 0 ? (
                <div>
                  <div className="px-2 py-1 text-[11px] font-semibold text-[var(--accent)] flex items-center justify-between">
                    <span className="truncate">Gợi ý · {extractDisplayDomain(currentTabUrl)}</span>
                    <span>{matchingLogins.length}</span>
                  </div>
                  <div className="flex flex-col gap-0.5">
                    {matchingLogins.map((m) => {
                      const full = ciphers.find((c) => c.id === m.id);
                      return full ? renderListRow(full) : null;
                    })}
                  </div>
                </div>
              ) : null}

              {/* Collapsible ★ Favorites section (Slide 2 exact styling) */}
              {favoriteItems.length > 0 ? (
                <div>
                  <button
                    type="button"
                    onClick={() => setFavoritesOpen((v) => !v)}
                    className="w-full px-2 py-1 flex items-center justify-between text-[11.5px] font-semibold text-[var(--text-secondary)] bg-transparent border-0 cursor-pointer"
                  >
                    <span className="inline-flex items-center gap-1.5">
                      <Star size={12} className="fill-[var(--warning)] text-[var(--warning)]" />
                      <span>Yêu thích</span>
                    </span>
                    {favoritesOpen ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
                  </button>
                  {favoritesOpen ? (
                    <div className="flex flex-col gap-0.5 mt-0.5">
                      {favoriteItems.map(renderListRow)}
                    </div>
                  ) : null}
                </div>
              ) : null}

              {/* All / Remaining Items */}
              {otherItems.length > 0 ? (
                <div>
                  {favoriteItems.length > 0 || matchingLogins.length > 0 ? (
                    <div className="px-2 py-1 text-[11px] font-semibold text-[var(--text-muted)]">
                      {query ? "Kết quả" : "Tất cả mục"}
                    </div>
                  ) : null}
                  <div className="flex flex-col gap-0.5">{otherItems.map(renderListRow)}</div>
                </div>
              ) : null}

              {filteredCiphers.length === 0 ? (
                <div className="flex-1 flex flex-col items-center justify-center text-center px-4 py-10">
                  <div className="w-10 h-10 rounded-[12px] bg-[var(--surface-subtle)] flex items-center justify-center text-[var(--text-muted)] mb-2.5">
                    <KeyRound size={18} />
                  </div>
                  <div className="text-[13px] font-semibold text-[var(--text-primary)]">
                    {query ? "Không tìm thấy mục nào" : "Chưa có mục nào"}
                  </div>
                  <p className="text-[11.5px] text-[var(--text-secondary)] mt-1 mb-3">
                    {query
                      ? "Thử từ khóa khác."
                      : "Nhấn + Mới hoặc mở Cài đặt để Import từ Bitwarden."}
                  </p>
                  {!query && filterKind !== "trash" ? (
                    <button
                      type="button"
                      onClick={() => {
                        setEditingCipher(null);
                        setRightMode("editor");
                      }}
                      className="ov-btn-primary px-3 py-1.5 text-[12px]"
                    >
                      + Thêm tài khoản
                    </button>
                  ) : null}
                </div>
              ) : null}
            </div>
          </div>
        ) : null}

        {/* Right Live Detail / Tool Pane */}
        {showRightPane ? (
          <main className="flex-1 flex flex-col min-w-0 min-h-0 bg-[var(--surface)]">
            {rightMode === "editor" ? (
              <EditorScreen
                initialCipher={editingCipher}
                folders={folders}
                currentTabUrl={currentTabUrl}
                onCancel={() => {
                  setEditingCipher(null);
                  setRightMode("detail");
                }}
                onSave={handleSaveCipher}
                onCopyText={handleCopyText}
              />
            ) : rightMode === "generator" ? (
              <GeneratorScreen
                onCopyText={handleCopyText}
                onClose={() => setRightMode("detail")}
              />
            ) : rightMode === "watchtower" ? (
              <WatchtowerPanel
                ciphers={ciphers}
                onFilterWeak={() => {
                  setFilterKind("weak");
                  setSelectedFolderId(null);
                  setRightMode("detail");
                  setMobileShowDetail(false);
                }}
                onFilter2FA={() => {
                  setFilterKind("totp");
                  setSelectedFolderId(null);
                  setRightMode("detail");
                  setMobileShowDetail(false);
                }}
                onClose={() => setRightMode("detail")}
              />
            ) : rightMode === "settings" ? (
              <SettingsScreen
                email={status.email ?? ""}
                serverUrl={status.serverUrl}
                lastSyncAt={status.lastSyncAt}
                settings={status.settings}
                isPopup={isPopup}
                onUpdateSettings={handleUpdateSettings}
                onImported={async () => {
                  await loadVault(true);
                  setRightMode("detail");
                }}
                onLock={handleLock}
                onLogout={handleLogout}
                onOpenSidePanel={handleOpenSidePanel}
                onClose={() => setRightMode("detail")}
                onToast={showToast}
              />
            ) : selectedCipher ? (
              <DetailPane
                cipher={selectedCipher}
                accountEmail={status.email ?? ""}
                folder={selectedFolder}
                showFavicons={status.settings.showFavicons}
                canAutofill={canAutofillSelected}
                showBackButton={narrowWidth}
                onBack={() => setMobileShowDetail(false)}
                onEdit={(cipher) => {
                  setEditingCipher(cipher);
                  setRightMode("editor");
                }}
                onToggleFavorite={handleToggleFavorite}
                onAutofill={handleAutofill}
                onDelete={handleDeleteCipher}
                onRestore={handleRestoreCipher}
                onCopyText={handleCopyText}
              />
            ) : (
              <div className="flex-1 flex flex-col items-center justify-center text-center p-6 bg-[var(--bg)]">
                <img
                  src="/icons/icon-128.png"
                  alt=""
                  className="w-14 h-14 rounded-[16px] shadow-md mb-3 opacity-90"
                />
                <h2 className="text-[15px] font-bold m-0 text-[var(--text-primary)]">
                  Chào mừng đến OpenVault
                </h2>
                <p className="text-[12.5px] text-[var(--text-secondary)] max-w-[260px] mt-1 mb-4">
                  Chọn một mục bên trái để xem chi tiết, hoặc thêm tài khoản mới được mã hóa Zero-Knowledge.
                </p>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => {
                      setEditingCipher(null);
                      setRightMode("editor");
                    }}
                    className="ov-btn-primary px-3.5 py-2 inline-flex items-center gap-1.5"
                  >
                    <Plus size={14} />
                    <span>Thêm mục mới</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setRightMode("settings")}
                    className="ov-btn-ghost px-3.5 py-2"
                  >
                    Import Proton / Bitwarden
                  </button>
                </div>
              </div>
            )}
          </main>
        ) : null}
      </div>

      <Toast toast={toast} onDismiss={() => setToast(null)} />
    </div>
  );
}

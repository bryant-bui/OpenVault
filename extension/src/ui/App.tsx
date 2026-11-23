import {
  CipherType,
  estimatePasswordStrength,
  type CipherPayload,
  type FolderPayload,
} from "@openvault/crypto-core";
import {
  ChevronDown,
  ChevronRight,
  ChevronsUpDown,
  CreditCard,
  Dices,
  FileText,
  Fingerprint,
  Folder,
  KeyRound,
  LayoutGrid,
  Lock,
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
  UserSquare2,
  X,
} from "lucide-react";
import { useCallback, useEffect, useId, useMemo, useRef, useState } from "react";
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

type FilterKind =
  | "all"
  | "favorites"
  | "login"
  | "totp"
  | "passkey"
  | "weak"
  | "card"
  | "identity"
  | "note"
  | "trash";
type RightPanelMode = "detail" | "editor" | "watchtower" | "settings";

const FILTER_CATEGORIES: Array<{ id: FilterKind; label: string; icon: typeof KeyRound }> = [
  { id: "all", label: "Tất cả mục", icon: LayoutGrid },
  { id: "favorites", label: "Yêu thích", icon: Star },
  { id: "login", label: "Đăng nhập", icon: Shield },
  { id: "totp", label: "Mã 2FA (TOTP)", icon: ShieldCheck },
  { id: "passkey", label: "Passkeys", icon: Fingerprint },
  { id: "card", label: "Thẻ tín dụng", icon: CreditCard },
  { id: "identity", label: "Danh tính", icon: UserSquare2 },
  { id: "note", label: "Ghi chú bảo mật", icon: FileText },
  { id: "trash", label: "Thùng rác", icon: Trash2 },
];

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
    pinEnabled: false,
    pinAvailableNow: false,
    pinRequireMasterOnRestart: true,
    pinRemainingAttempts: 5,
    pinMaxAttempts: 5,
    pinLength: 6,
  });

  const [ciphers, setCiphers] = useState<CipherPayload[]>([]);
  const [folders, setFolders] = useState<FolderPayload[]>([]);
  const [syncing, setSyncing] = useState(false);

  const [query, setQuery] = useState("");
  const [filterKind, setFilterKind] = useState<FilterKind>("all");
  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(null);
  const [favoritesOpen, setFavoritesOpen] = useState(true);
  const [generatorDrawerOpen, setGeneratorDrawerOpen] = useState(false);

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

  const accountPopoverId = useId();
  const filterPopoverId = useId();
  const accountPopoverRef = useRef<HTMLDivElement>(null);
  const filterPopoverRef = useRef<HTMLDivElement>(null);

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
      } else if (e.key === "Escape" && generatorDrawerOpen) {
        setGeneratorDrawerOpen(false);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [generatorDrawerOpen]);

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
        setGeneratorDrawerOpen(false);
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
        if (filterKind === "identity" && c.type !== CipherType.Identity) return false;
        if (filterKind === "note" && c.type !== CipherType.SecureNote) return false;

        if (!q) return true;
        const haystack = [
          c.name,
          c.login?.username ?? "",
          ...(c.login?.uris?.map((u) => u.uri ?? "") ?? []),
          c.card?.brand ?? "",
          c.identity?.firstName ?? "",
          c.identity?.lastName ?? "",
          c.identity?.email ?? "",
          c.identity?.phone ?? "",
          c.notes ?? "",
        ]
          .join(" ")
          .toLowerCase();
        return haystack.includes(q);
      })
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [ciphers, filterKind, selectedFolderId, query, weakOrReusedIds]);

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
        void sendBg({ type: "SCHEDULE_CLIPBOARD_CLEAR" }).catch(() => undefined);
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

  // Keep the beloved atmospheric 3 + 3 auto-unlock PIN UnlockScreen untouched!
  if (!status.unlocked) {
    return (
      <div className="flex-1 flex flex-col bg-[var(--bg)] overflow-hidden">
        <UnlockScreen
          email={status.email ?? ""}
          serverUrl={status.serverUrl}
          pinEnabled={status.pinEnabled}
          pinAvailableNow={status.pinAvailableNow}
          pinRemainingAttempts={status.pinRemainingAttempts}
          pinLength={status.pinLength}
          onUnlocked={loadStatus}
          onLoggedOut={loadStatus}
        />
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

  const isCurrentSiteMatch = Boolean(
    selectedCipher?.login?.uris?.some((u) => scoreUriMatch(currentTabUrl, u.uri) > 0),
  );

  const canAutofillSelected = Boolean(
    isCurrentSiteMatch ||
      (currentTabUrl.startsWith("http") &&
        (selectedCipher?.type === CipherType.Login ||
          selectedCipher?.type === CipherType.Card ||
          selectedCipher?.type === CipherType.Identity)),
  );

  const currentFilterLabel = selectedFolderId
    ? folders.find((f) => f.id === selectedFolderId)?.name ?? "Thư mục"
    : filterKind === "weak"
      ? "Mật khẩu yếu / trùng"
      : FILTER_CATEGORIES.find((c) => c.id === filterKind)?.label ?? "Tất cả mục";

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
            : item.identity?.email ||
              item.identity?.phone ||
              [item.identity?.firstName, item.identity?.lastName].filter(Boolean).join(" ") ||
              "Danh tính";

    return (
      <button
        key={item.id ?? item.name}
        type="button"
        data-selected={isSelected}
        onClick={() => selectItem(item)}
        className="ov-list-row"
      >
        <div className="relative shrink-0">
          <BrandAvatar
            name={item.name}
            type={item.type}
            uri={item.login?.uris?.[0]?.uri}
            showFavicon={status.settings.showFavicons}
            size="md"
            inverted={isSelected}
          />
          {item.favorite ? (
            <span className="absolute -bottom-0.5 -right-0.5 w-3.5 h-3.5 rounded-full bg-[var(--surface)] flex items-center justify-center">
              <Star size={9} className="fill-[var(--warning)] text-[var(--warning)]" />
            </span>
          ) : null}
        </div>
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
    rightMode === "editor" || rightMode === "watchtower" || rightMode === "settings";
  const showLeftPane = !narrowWidth || (!mobileShowDetail && !isOverlayPanel);
  const showRightPane = !narrowWidth || mobileShowDetail || isOverlayPanel;
  const accountLabel = status.email?.split("@")[0] || "Tài khoản";

  return (
    <div className="flex-1 flex flex-col min-h-0 bg-[var(--surface)] relative">
      {/* Exact Image 1 1Password 8 Clean Top Bar (Left Account Picker | Search + Watchtower + Generator + '+ Mục mới') */}
      <header className="h-[46px] shrink-0 border-b border-[var(--border)] bg-[var(--surface)] flex items-center">
        {/* Left Account Selector matching the 228px Master Column */}
        <div
          className={`${
            narrowWidth ? "px-2.5" : "w-[228px] shrink-0 px-3 border-r border-[var(--border)]"
          } h-full flex items-center`}
        >
          <button
            type="button"
            popoverTarget={accountPopoverId}
            className="h-8 px-1.5 rounded-[8px] border-0 bg-transparent hover:bg-[var(--surface-subtle)] text-[var(--text-primary)] inline-flex items-center gap-2 cursor-pointer transition-colors max-w-full"
            title="Tài khoản, Cài đặt & Công cụ"
          >
            <img src="/icons/icon-32.png" alt="OpenVault" className="w-5 h-5 rounded-full shrink-0" />
            {!narrowWidth ? (
              <span className="text-[12.5px] font-semibold truncate">{accountLabel}</span>
            ) : null}
            <ChevronDown size={13} className="text-[var(--text-muted)] shrink-0" />
          </button>

          <div
            id={accountPopoverId}
            ref={accountPopoverRef}
            popover="auto"
            className="ov-popover"
          >
            <div className="px-2.5 py-1.5 border-b border-[var(--border)] mb-1">
              <div className="text-[12px] font-semibold text-[var(--text-primary)] truncate">
                {status.email}
              </div>
              <div className="text-[10.5px] text-[var(--text-muted)] truncate">
                {status.serverUrl.replace(/^https?:\/\//, "")}
              </div>
            </div>

            <button
              type="button"
              className="ov-popover-item"
              onClick={() => {
                accountPopoverRef.current?.hidePopover?.();
                void loadVault(true);
              }}
            >
              <RefreshCw size={13} className={syncing ? "animate-spin text-[var(--accent)]" : ""} />
              <span>Đồng bộ Cloudflare D1</span>
            </button>
            <button
              type="button"
              className="ov-popover-item"
              onClick={() => {
                accountPopoverRef.current?.hidePopover?.();
                setGeneratorDrawerOpen(true);
              }}
            >
              <Dices size={13} />
              <span>Trình tạo mật khẩu</span>
            </button>
            <button
              type="button"
              className="ov-popover-item"
              onClick={() => {
                accountPopoverRef.current?.hidePopover?.();
                setRightMode("watchtower");
              }}
            >
              <ShieldCheck size={13} />
              <span>Watchtower ({weakOrReusedIds.size} cảnh báo)</span>
            </button>
            {isPopup ? (
              <button
                type="button"
                className="ov-popover-item"
                onClick={() => {
                  accountPopoverRef.current?.hidePopover?.();
                  void handleOpenSidePanel();
                }}
              >
                <PanelRightOpen size={13} />
                <span>Mở ở Side Panel</span>
              </button>
            ) : null}
            <button
              type="button"
              className="ov-popover-item"
              onClick={() => {
                accountPopoverRef.current?.hidePopover?.();
                setRightMode("settings");
              }}
            >
              <Settings size={13} />
              <span>Cài đặt & Import</span>
            </button>

            <hr className="my-1 border-0 border-t border-[var(--border)]" />
            <button
              type="button"
              className="ov-popover-item"
              onClick={() => {
                accountPopoverRef.current?.hidePopover?.();
                void handleLock();
              }}
            >
              <Lock size={13} />
              <span>Khóa OpenVault</span>
            </button>
          </div>
        </div>

        {/* Right Top Bar: Clean Search + Watchtower Badge + Generator + "+ Mục mới" */}
        <div className="flex-1 min-w-0 px-3 flex items-center gap-2">
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
              placeholder="Tìm trong OpenVault"
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

          {/* Watchtower button with notification dot (exact Image 1!) */}
          <button
            type="button"
            data-active={rightMode === "watchtower"}
            onClick={() => setRightMode((m) => (m === "watchtower" ? "detail" : "watchtower"))}
            className="ov-icon-btn w-7 h-7 relative shrink-0"
            title="Watchtower — Điểm bảo mật"
            aria-label="Watchtower"
          >
            <ShieldCheck size={16} />
            {weakOrReusedIds.size > 0 ? (
              <span
                className="w-2 h-2 rounded-full absolute top-1 right-1"
                style={{ background: "var(--danger)" }}
              />
            ) : null}
          </button>

          {/* Password Generator Slide-over Drawer button (exact Image 2!) */}
          <button
            type="button"
            data-active={generatorDrawerOpen}
            onClick={() => setGeneratorDrawerOpen((v) => !v)}
            className="ov-icon-btn w-7 h-7 shrink-0"
            title="Trình tạo mật khẩu"
            aria-label="Trình tạo mật khẩu"
          >
            <Dices size={15} />
          </button>

          {/* Signature 1Password Primary Blue "+ Mục mới" CTA */}
          <button
            type="button"
            onClick={() => {
              setEditingCipher(null);
              setRightMode("editor");
            }}
            className="ov-btn-primary h-[30px] px-3 inline-flex items-center gap-1 shrink-0"
          >
            <Plus size={14} strokeWidth={2.6} />
            <span>Mục mới</span>
          </button>
        </div>
      </header>

      {/* Main Split Workspace */}
      <div className="flex-1 flex min-h-0 relative overflow-hidden">
        {/* Left Master Item List Pane */}
        {showLeftPane ? (
          <div
            className={`${
              narrowWidth ? "flex-1" : "w-[228px] shrink-0 border-r border-[var(--border)]"
            } flex flex-col min-h-0 bg-[var(--surface)]`}
          >
            {/* Exact Image 1 "All Items ↕" Category/Folder Selector Row (38px aligned with DetailPane header!) */}
            <div className="h-[38px] shrink-0 px-2.5 border-b border-[var(--border)] flex items-center justify-between gap-1">
              <button
                type="button"
                popoverTarget={filterPopoverId}
                className="flex-1 min-w-0 h-7 px-2 rounded-[7px] border-0 bg-transparent hover:bg-[var(--surface-subtle)] text-[var(--text-primary)] flex items-center justify-between gap-1.5 cursor-pointer transition-colors"
              >
                <span className="inline-flex items-center gap-2 min-w-0">
                  <LayoutGrid size={13} className="text-[var(--text-secondary)] shrink-0" />
                  <span className="text-[12.5px] font-semibold truncate">{currentFilterLabel}</span>
                </span>
                <ChevronsUpDown size={13} className="text-[var(--text-muted)] shrink-0" />
              </button>

              <div
                id={filterPopoverId}
                ref={filterPopoverRef}
                popover="auto"
                className="ov-popover"
              >
                {FILTER_CATEGORIES.map((cat) => {
                  const Icon = cat.icon;
                  const active = filterKind === cat.id && selectedFolderId === null;
                  return (
                    <button
                      key={cat.id}
                      type="button"
                      data-active={active}
                      className="ov-popover-item"
                      onClick={() => {
                        setFilterKind(cat.id);
                        setSelectedFolderId(null);
                        setRightMode("detail");
                        filterPopoverRef.current?.hidePopover?.();
                      }}
                    >
                      <Icon size={13} />
                      <span className="truncate">{cat.label}</span>
                    </button>
                  );
                })}

                {folders.length > 0 ? (
                  <>
                    <hr className="my-1 border-0 border-t border-[var(--border)]" />
                    <div className="px-2 py-1 text-[10px] font-bold uppercase tracking-wider text-[var(--text-muted)]">
                      Thư mục
                    </div>
                    {folders.map((folder) => {
                      const active = selectedFolderId === folder.id;
                      return (
                        <button
                          key={folder.id}
                          type="button"
                          data-active={active}
                          className="ov-popover-item"
                          onClick={() => {
                            setFilterKind("all");
                            setSelectedFolderId(folder.id ?? null);
                            setRightMode("detail");
                            filterPopoverRef.current?.hidePopover?.();
                          }}
                        >
                          <Folder size={13} />
                          <span className="truncate">{folder.name}</span>
                        </button>
                      );
                    })}
                  </>
                ) : null}
              </div>
            </div>

            <div className="flex-1 overflow-y-auto ov-scroll p-1.5 flex flex-col gap-1.5">
              {/* Pending Save Banner */}
              {status.pendingSave ? (
                <div className="p-2.5 rounded-[9px] border border-[var(--accent-border)] bg-[var(--accent-soft)]">
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

              {/* Collapsible Favorites */}
              {favoriteItems.length > 0 ? (
                <div>
                  <button
                    type="button"
                    onClick={() => setFavoritesOpen((v) => !v)}
                    className="w-full px-2 py-1 flex items-center justify-between text-[11px] font-semibold text-[var(--text-secondary)] bg-transparent border-0 cursor-pointer"
                  >
                    <span className="inline-flex items-center gap-1.5">
                      <Star size={11} className="fill-[var(--warning)] text-[var(--warning)]" />
                      <span>Yêu thích</span>
                    </span>
                    {favoritesOpen ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
                  </button>
                  {favoritesOpen ? (
                    <div className="flex flex-col gap-0.5 mt-0.5">{favoriteItems.map(renderListRow)}</div>
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
                <div className="flex-1 flex flex-col items-center justify-center text-center px-3 py-10">
                  <div className="w-10 h-10 rounded-[10px] bg-[var(--surface-subtle)] flex items-center justify-center text-[var(--text-muted)] mb-2.5">
                    <KeyRound size={18} />
                  </div>
                  <div className="text-[13px] font-semibold text-[var(--text-primary)]">
                    {query ? "Không tìm thấy mục nào" : "Chưa có mục nào"}
                  </div>
                  <p className="text-[11.5px] text-[var(--text-secondary)] mt-1 mb-3">
                    {query
                      ? "Thử từ khóa khác."
                      : "Nhấn + Mục mới hoặc mở Cài đặt để Import dữ liệu."}
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
                autoCopyGeneratedPassword={status.settings.autoCopyGeneratedPassword}
                onCancel={() => {
                  setEditingCipher(null);
                  setRightMode("detail");
                }}
                onSave={handleSaveCipher}
                onCopyText={handleCopyText}
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
                pinEnabled={status.pinEnabled}
                pinRequireMasterOnRestart={status.pinRequireMasterOnRestart}
                pinMaxAttempts={status.pinMaxAttempts}
                isPopup={isPopup}
                onUpdateSettings={handleUpdateSettings}
                onPinUpdated={loadStatus}
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
                isCurrentSiteMatch={isCurrentSiteMatch}
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

        {/* Exact Image 2: Right Slide-Over Password Generator Drawer dimming the Vault */}
        {generatorDrawerOpen ? (
          <div
            className="ov-gen-backdrop"
            onClick={(e) => {
              if (e.target === e.currentTarget) setGeneratorDrawerOpen(false);
            }}
          >
            <div className="ov-gen-drawer" role="dialog" aria-label="Trình tạo mật khẩu">
              <GeneratorScreen
                autoCopyOnGenerate={status.settings.autoCopyGeneratedPassword}
                onCopyText={handleCopyText}
                onClose={() => setGeneratorDrawerOpen(false)}
              />
            </div>
          </div>
        ) : null}
      </div>

      <Toast toast={toast} onDismiss={() => setToast(null)} />
    </div>
  );
}

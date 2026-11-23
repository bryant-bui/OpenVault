import type { CipherPayload, FolderPayload } from "@openvault/crypto-core";
import { Dices, KeyRound, Settings, ShieldCheck } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { DEFAULT_SERVER_URL, DEFAULT_SETTINGS } from "../shared/constants.js";
import { scoreUriMatch } from "../shared/domain-match.js";
import type {
  DecryptedVault,
  ExtensionSettings,
  VaultStatusResponse,
} from "../shared/types.js";
import {
  getActiveTab,
  openSidePanelFromCurrentWindow,
  sendBg,
} from "./components/bridge.js";
import { Toast, type ToastMessage } from "./components/Toast.js";
import { AuthScreen } from "./screens/AuthScreen.js";
import { DetailScreen } from "./screens/DetailScreen.js";
import { EditorScreen } from "./screens/EditorScreen.js";
import { GeneratorScreen } from "./screens/GeneratorScreen.js";
import { SettingsScreen } from "./screens/SettingsScreen.js";
import { TotpScreen } from "./screens/TotpScreen.js";
import { UnlockScreen } from "./screens/UnlockScreen.js";
import { VaultScreen } from "./screens/VaultScreen.js";

type MainTab = "vault" | "totp" | "generator" | "settings";

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

  const [activeTab, setActiveTab] = useState<MainTab>("vault");
  const [selectedCipherId, setSelectedCipherId] = useState<string | null>(null);
  const [editingCipher, setEditingCipher] = useState<CipherPayload | null | undefined>(undefined);

  const [currentTabId, setCurrentTabId] = useState<number | null>(null);
  const [currentTabUrl, setCurrentTabUrl] = useState<string>("");

  const [toast, setToast] = useState<ToastMessage | null>(null);
  const toastTimerRef = useRef<number | null>(null);

  const showToast = useCallback((text: string, tone: "info" | "success" | "error" = "info") => {
    setToast({ id: Date.now(), text, tone });
    if (toastTimerRef.current !== null) {
      window.clearTimeout(toastTimerRef.current);
    }
    toastTimerRef.current = window.setTimeout(() => {
      setToast(null);
    }, 2800);
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

  const loadVault = useCallback(async (forceSync = false) => {
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
  }, [showToast]);

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
        setEditingCipher(undefined);
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
      setEditingCipher(undefined);
      setSelectedCipherId(saved.id ?? null);
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
      <>
        <AuthScreen
          initialServerUrl={status.serverUrl}
          onAuthenticated={loadStatus}
          onToast={showToast}
        />
        <Toast toast={toast} onDismiss={() => setToast(null)} />
      </>
    );
  }

  if (!status.unlocked) {
    return (
      <>
        <UnlockScreen
          email={status.email ?? ""}
          serverUrl={status.serverUrl}
          onUnlocked={loadStatus}
          onLoggedOut={loadStatus}
        />
        <Toast toast={toast} onDismiss={() => setToast(null)} />
      </>
    );
  }

  const selectedCipher = selectedCipherId
    ? ciphers.find((c) => c.id === selectedCipherId) ?? null
    : null;

  const canAutofillSelected = Boolean(
    selectedCipher?.login?.uris?.some((u) => scoreUriMatch(currentTabUrl, u.uri) > 0) ||
      (currentTabUrl.startsWith("http") && selectedCipher?.type === 1),
  );

  const inOverlayView = editingCipher !== undefined || selectedCipher !== null;

  return (
    <div className="flex-1 flex flex-col min-h-0">
      {editingCipher !== undefined ? (
        <EditorScreen
          initialCipher={editingCipher}
          folders={folders}
          currentTabUrl={currentTabUrl}
          onCancel={() => setEditingCipher(undefined)}
          onSave={handleSaveCipher}
          onCopyText={handleCopyText}
        />
      ) : selectedCipher ? (
        <DetailScreen
          cipher={selectedCipher}
          canAutofill={canAutofillSelected}
          onBack={() => setSelectedCipherId(null)}
          onEdit={(cipher) => setEditingCipher(cipher)}
          onToggleFavorite={handleToggleFavorite}
          onAutofill={handleAutofill}
          onDelete={handleDeleteCipher}
          onRestore={handleRestoreCipher}
          onCopyText={handleCopyText}
        />
      ) : activeTab === "vault" ? (
        <VaultScreen
          ciphers={ciphers}
          folders={folders}
          currentTabUrl={currentTabUrl}
          isPopup={isPopup}
          syncing={syncing}
          pendingSave={status.pendingSave}
          onSelectCipher={(c) => setSelectedCipherId(c.id ?? null)}
          onNewCipher={() => setEditingCipher(null)}
          onAutofill={handleAutofill}
          onCopyText={handleCopyText}
          onSync={() => loadVault(true)}
          onLock={handleLock}
          onOpenSidePanel={handleOpenSidePanel}
          onAcceptPendingSave={handleAcceptPendingSave}
          onDismissPendingSave={handleDismissPendingSave}
        />
      ) : activeTab === "totp" ? (
        <TotpScreen
          ciphers={ciphers}
          onSelectCipher={(c) => setSelectedCipherId(c.id ?? null)}
          onCopyText={handleCopyText}
        />
      ) : activeTab === "generator" ? (
        <GeneratorScreen onCopyText={handleCopyText} />
      ) : (
        <SettingsScreen
          email={status.email ?? ""}
          serverUrl={status.serverUrl}
          lastSyncAt={status.lastSyncAt}
          settings={status.settings}
          isPopup={isPopup}
          onUpdateSettings={handleUpdateSettings}
          onImported={() => loadVault(true)}
          onLock={handleLock}
          onLogout={handleLogout}
          onOpenSidePanel={handleOpenSidePanel}
          onToast={showToast}
        />
      )}

      {/* Bottom 1Password-style Navigation Bar */}
      {!inOverlayView ? (
        <nav
          aria-label="Điều hướng chính"
          className="h-[54px] shrink-0 grid grid-cols-4 border-t border-[var(--border)] bg-[var(--surface)] px-1"
        >
          {[
            { id: "vault" as const, label: "Kho", icon: KeyRound },
            { id: "totp" as const, label: "Mã 2FA", icon: ShieldCheck },
            { id: "generator" as const, label: "Tạo MK", icon: Dices },
            { id: "settings" as const, label: "Cài đặt", icon: Settings },
          ].map((item) => {
            const Icon = item.icon;
            const active = activeTab === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => setActiveTab(item.id)}
                className={`flex flex-col items-center justify-center gap-0.5 border-0 bg-transparent cursor-pointer transition-colors ${
                  active ? "text-[var(--accent)]" : "text-[var(--text-muted)] hover:text-[var(--text-primary)]"
                }`}
              >
                <Icon size={18} strokeWidth={active ? 2.4 : 1.9} />
                <span className="text-[10.5px] font-semibold">{item.label}</span>
              </button>
            );
          })}
        </nav>
      ) : null}

      <Toast toast={toast} onDismiss={() => setToast(null)} />
    </div>
  );
}

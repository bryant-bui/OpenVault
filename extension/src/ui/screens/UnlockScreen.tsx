import { Eye, EyeOff, KeyRound, LogOut, Shield } from "lucide-react";
import { useState } from "react";
import { sendBg } from "../components/bridge.js";
import { ConfirmDialog } from "../components/ConfirmDialog.js";

interface UnlockScreenProps {
  email: string;
  serverUrl: string;
  onUnlocked: () => Promise<void>;
  onLoggedOut: () => Promise<void>;
}

export function UnlockScreen({ email, serverUrl, onUnlocked, onLoggedOut }: UnlockScreenProps) {
  const [masterPassword, setMasterPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmLogout, setConfirmLogout] = useState(false);

  const handleUnlock = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!masterPassword) return;
    setError(null);
    setLoading(true);
    try {
      await sendBg({ type: "UNLOCK", masterPassword });
      setMasterPassword("");
      await onUnlocked();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Không thể mở khóa Vault.");
    } finally {
      setLoading(false);
    }
  };

  const handleConfirmLogout = async () => {
    setConfirmLogout(false);
    await sendBg({ type: "LOGOUT" });
    await onLoggedOut();
  };

  return (
    <div className="flex-1 flex flex-col justify-between p-6">
      <div className="flex-1 flex flex-col justify-center">
        <div className="flex flex-col items-center text-center mb-6">
          <div
            className="w-16 h-16 rounded-[20px] flex items-center justify-center text-white shadow-lg mb-3.5"
            style={{ background: "linear-gradient(135deg, #0A84FF, #0455BF)" }}
          >
            <Shield size={32} strokeWidth={2.1} />
          </div>
          <h1 className="text-[19px] font-bold tracking-tight m-0">OpenVault đang khóa</h1>
          <div className="mt-2 inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[var(--surface)] border border-[var(--border)] text-[12px] font-medium text-[var(--text-secondary)]">
            <span className="w-2 h-2 rounded-full bg-[var(--accent)]" />
            <span>{email}</span>
          </div>
        </div>

        <form onSubmit={handleUnlock} className="flex flex-col gap-3">
          <div className="relative">
            <input
              type={showPassword ? "text" : "password"}
              className="ov-input pr-10 py-2.5 text-[13.5px]"
              placeholder="Nhập Master Password để mở khóa"
              value={masterPassword}
              onChange={(e) => setMasterPassword(e.target.value)}
              autoFocus
              autoComplete="current-password"
            />
            <button
              type="button"
              onClick={() => setShowPassword((v) => !v)}
              className="ov-icon-btn w-8 h-8 absolute right-1.5 top-1/2 -translate-y-1/2"
              aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
            >
              {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
            </button>
          </div>

          {error ? (
            <div className="px-3 py-2 rounded-[10px] text-[12px] font-medium bg-[var(--danger-soft)] text-[var(--danger)]">
              {error}
            </div>
          ) : null}

          <button
            type="submit"
            disabled={loading || !masterPassword}
            className="ov-btn-primary w-full py-2.5 flex items-center justify-center gap-2"
          >
            <KeyRound size={15} />
            <span>{loading ? "Đang giải mã khóa..." : "Mở khóa OpenVault"}</span>
          </button>
        </form>
      </div>

      <div className="pt-4 border-t border-[var(--border)] flex items-center justify-between text-[11.5px] text-[var(--text-muted)]">
        <span className="truncate max-w-[220px]">{serverUrl.replace(/^https?:\/\//, "")}</span>
        <button
          type="button"
          onClick={() => setConfirmLogout(true)}
          className="inline-flex items-center gap-1 text-[var(--text-secondary)] hover:text-[var(--danger)] bg-transparent border-0 cursor-pointer font-semibold p-0"
        >
          <LogOut size={13} />
          <span>Đăng xuất</span>
        </button>
      </div>

      <ConfirmDialog
        open={confirmLogout}
        title="Đăng xuất khỏi OpenVault?"
        description="Bộ nhớ đệm mã hóa cục bộ trên trình duyệt này sẽ bị xóa sạch. Dữ liệu trên Cloudflare D1 vẫn an toàn."
        confirmLabel="Đăng xuất"
        onConfirm={() => {
          void handleConfirmLogout();
        }}
        onCancel={() => setConfirmLogout(false)}
      />
    </div>
  );
}

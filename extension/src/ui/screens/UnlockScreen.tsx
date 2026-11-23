import { Eye, EyeOff, Grid3x3, KeyRound, LogOut, Shield } from "lucide-react";
import { useEffect, useState } from "react";
import { sendBg } from "../components/bridge.js";
import { ConfirmDialog } from "../components/ConfirmDialog.js";

interface UnlockScreenProps {
  email: string;
  serverUrl: string;
  pinEnabled: boolean;
  pinAvailableNow: boolean;
  pinRemainingAttempts: number;
  onUnlocked: () => Promise<void>;
  onLoggedOut: () => Promise<void>;
}

export function UnlockScreen({
  email,
  serverUrl,
  pinEnabled,
  pinAvailableNow,
  pinRemainingAttempts,
  onUnlocked,
  onLoggedOut,
}: UnlockScreenProps) {
  const [pinAvailable, setPinAvailable] = useState(pinAvailableNow);
  const [pinWiped, setPinWiped] = useState(false);
  const [remainingAttempts, setRemainingAttempts] = useState(pinRemainingAttempts);
  const [usePin, setUsePin] = useState(pinAvailableNow);
  const [secret, setSecret] = useState("");
  const [showSecret, setShowSecret] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmLogout, setConfirmLogout] = useState(false);

  useEffect(() => {
    setPinAvailable(pinAvailableNow);
    setPinWiped(false);
    setUsePin(pinAvailableNow);
    setRemainingAttempts(pinRemainingAttempts);
  }, [pinAvailableNow, pinRemainingAttempts]);

  const handleUnlock = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!secret) return;
    setError(null);
    setLoading(true);
    try {
      if (usePin) {
        await sendBg({ type: "UNLOCK_WITH_PIN", pin: secret });
      } else {
        await sendBg({ type: "UNLOCK", masterPassword: secret });
      }
      setSecret("");
      await onUnlocked();
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Không thể mở khóa Vault.";
      setError(msg);
      if (usePin) {
        if (msg.includes("hủy mã PIN")) {
          setPinAvailable(false);
          setPinWiped(true);
          setRemainingAttempts(0);
          setUsePin(false);
          setSecret("");
        } else {
          setRemainingAttempts((prev) => Math.max(0, prev - 1));
        }
      }
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
        <div className="flex flex-col items-center text-center mb-5">
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

        {pinEnabled && !pinAvailable && !pinWiped ? (
          <div className="mb-3 px-3 py-2 rounded-[10px] text-[11.5px] bg-[var(--accent-soft)] text-[var(--accent)] text-center leading-snug">
            Trình duyệt vừa khởi động lại — nhập Master Password 1 lần, sau đó mã PIN sẽ tự kích hoạt lại cho phiên làm việc này.
          </div>
        ) : null}

        <form onSubmit={handleUnlock} className="flex flex-col gap-3">
          <div className="relative">
            <input
              type={showSecret ? "text" : "password"}
              inputMode={usePin ? "numeric" : "text"}
              className={`ov-input pr-10 py-2.5 ${
                usePin ? "font-mono-code text-center tracking-[0.28em] text-[16px]" : "text-[13.5px]"
              }`}
              placeholder={usePin ? "Nhập mã PIN" : "Nhập Master Password"}
              value={secret}
              onChange={(e) => setSecret(e.target.value)}
              autoFocus
              autoComplete="current-password"
            />
            <button
              type="button"
              onClick={() => setShowSecret((v) => !v)}
              className="ov-icon-btn w-8 h-8 absolute right-1.5 top-1/2 -translate-y-1/2"
              aria-label={showSecret ? "Ẩn" : "Hiện"}
            >
              {showSecret ? <EyeOff size={15} /> : <Eye size={15} />}
            </button>
          </div>

          {error ? (
            <div className="px-3 py-2 rounded-[10px] text-[12px] font-medium bg-[var(--danger-soft)] text-[var(--danger)]">
              {error}
            </div>
          ) : null}

          <button
            type="submit"
            disabled={loading || !secret}
            className="ov-btn-primary w-full py-2.5 flex items-center justify-center gap-2"
          >
            {usePin ? <Grid3x3 size={15} /> : <KeyRound size={15} />}
            <span>
              {loading
                ? "Đang giải mã khóa..."
                : usePin
                  ? "Mở khóa bằng mã PIN"
                  : "Mở khóa bằng Master Password"}
            </span>
          </button>

          {pinAvailable ? (
            <button
              type="button"
              onClick={() => {
                setUsePin((v) => !v);
                setSecret("");
                setError(null);
              }}
              className="text-[12px] font-semibold text-[var(--accent)] hover:underline bg-transparent border-0 cursor-pointer py-1"
            >
              {usePin
                ? "Mở khóa bằng Master Password"
                : `Quay lại mở khóa bằng mã PIN (còn ${remainingAttempts} lần thử)`}
            </button>
          ) : null}
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

import { estimatePasswordStrength } from "@openvault/crypto-core";
import { Eye, EyeOff, Globe, Lock } from "lucide-react";
import { useState } from "react";
import { DEFAULT_SERVER_URL } from "../../shared/constants.js";
import type { AppLanguage } from "../../shared/i18n.js";
import { OpenVaultLogo } from "../components/OpenVaultLogo.js";
import { sendBg } from "../components/bridge.js";

interface AuthScreenProps {
  initialServerUrl: string;
  language?: AppLanguage;
  onAuthenticated: () => Promise<void>;
  onToast: (text: string, tone?: "info" | "success" | "error") => void;
}

export function AuthScreen({
  initialServerUrl,
  language = "en",
  onAuthenticated,
  onToast,
}: AuthScreenProps) {
  const isVi = language === "vi";
  const [mode, setMode] = useState<"login" | "register">("login");
  const [serverUrl, setServerUrl] = useState(initialServerUrl || DEFAULT_SERVER_URL);
  const [showServerConfig, setShowServerConfig] = useState(false);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [masterPassword, setMasterPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [hint, setHint] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const strength = estimatePasswordStrength(masterPassword);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!email.trim() || !masterPassword) {
      setError(
        isVi
          ? "Vui lòng nhập đầy đủ email và Master Password."
          : "Please enter both email and Master Password.",
      );
      return;
    }

    if (mode === "register") {
      if (masterPassword.length < 10) {
        setError(
          isVi
            ? "Master Password nên dài ít nhất 10 ký tự để đảm bảo an toàn."
            : "Master Password must be at least 10 characters long.",
        );
        return;
      }
      if (masterPassword !== confirmPassword) {
        setError(isVi ? "Mật khẩu xác nhận không khớp." : "Passwords do not match.");
        return;
      }
    }

    setSubmitting(true);
    try {
      if (mode === "register") {
        await sendBg({
          type: "REGISTER",
          serverUrl,
          email: email.trim(),
          name: name.trim(),
          masterPassword,
          masterPasswordHint: hint.trim() || undefined,
        });
        onToast(
          isVi
            ? "Đã tạo tài khoản OpenVault (Argon2id Zero-Knowledge)!"
            : "Created OpenVault account (Argon2id Zero-Knowledge)!",
          "success",
        );
      } else {
        await sendBg({
          type: "LOGIN",
          serverUrl,
          email: email.trim(),
          masterPassword,
        });
        onToast(
          isVi ? "Đăng nhập và đồng bộ Vault thành công!" : "Signed in and synced vault!",
          "success",
        );
      }
      await onAuthenticated();
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : isVi
            ? "Không thể kết nối máy chủ."
            : "Unable to reach server.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex-1 flex flex-col overflow-y-auto ov-scroll px-5 py-6">
      <div className="flex flex-col items-center text-center mb-5">
        <div className="mb-3 shadow-md rounded-[15px]">
          <OpenVaultLogo variant="crest" size={56} />
        </div>
        <h1 className="text-[19px] font-bold tracking-tight m-0">OpenVault</h1>
        <p className="text-[12.5px] text-[var(--text-secondary)] mt-1 mb-0">
          {isVi
            ? "Kho mật khẩu Zero-Knowledge · Argon2id · AES-256"
            : "Zero-Knowledge Password Manager · Argon2id · AES-256"}
        </p>
      </div>

      {/* Mode segmented switch */}
      <div className="grid grid-cols-2 p-1 rounded-[11px] bg-[var(--surface-subtle)] border border-[var(--border)] mb-4">
        <button
          type="button"
          onClick={() => {
            setMode("login");
            setError(null);
          }}
          className={`py-1.5 text-[12.5px] font-semibold rounded-[8px] border-0 cursor-pointer transition-all ${
            mode === "login"
              ? "bg-[var(--surface)] text-[var(--text-primary)] shadow-xs"
              : "bg-transparent text-[var(--text-secondary)]"
          }`}
        >
          {isVi ? "Đăng nhập" : "Sign In"}
        </button>
        <button
          type="button"
          onClick={() => {
            setMode("register");
            setError(null);
          }}
          className={`py-1.5 text-[12.5px] font-semibold rounded-[8px] border-0 cursor-pointer transition-all ${
            mode === "register"
              ? "bg-[var(--surface)] text-[var(--text-primary)] shadow-xs"
              : "bg-transparent text-[var(--text-secondary)]"
          }`}
        >
          {isVi ? "Tạo tài khoản mới" : "Create Account"}
        </button>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        {mode === "register" ? (
          <div>
            <label className="block text-[11.5px] font-semibold text-[var(--text-secondary)] mb-1">
              {isVi ? "Tên hiển thị" : "Display Name"}
            </label>
            <input
              type="text"
              className="ov-input"
              placeholder="Alex Morgan"
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoComplete="name"
            />
          </div>
        ) : null}

        <div>
          <label className="block text-[11.5px] font-semibold text-[var(--text-secondary)] mb-1">
            Email
          </label>
          <input
            type="email"
            className="ov-input"
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="username"
            required
          />
        </div>

        <div>
          <label className="block text-[11.5px] font-semibold text-[var(--text-secondary)] mb-1">
            Master Password
          </label>
          <div className="relative">
            <input
              type={showPassword ? "text" : "password"}
              className="ov-input pr-9"
              placeholder={isVi ? "Nhập Master Password" : "Enter Master Password"}
              value={masterPassword}
              onChange={(e) => setMasterPassword(e.target.value)}
              autoComplete={mode === "register" ? "new-password" : "current-password"}
              required
            />
            <button
              type="button"
              onClick={() => setShowPassword((v) => !v)}
              className="ov-icon-btn w-7 h-7 absolute right-1.5 top-1/2 -translate-y-1/2"
              aria-label={
                showPassword
                  ? isVi
                    ? "Ẩn mật khẩu"
                    : "Hide password"
                  : isVi
                    ? "Hiện mật khẩu"
                    : "Show password"
              }
            >
              {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
            </button>
          </div>

          {mode === "register" && masterPassword.length > 0 ? (
            <div className="mt-1.5 flex items-center gap-2">
              <div className="flex-1 h-1.5 rounded-full bg-[var(--surface-subtle)] overflow-hidden">
                <div
                  className="h-full rounded-full transition-all duration-200"
                  style={{
                    width: `${Math.min(100, (strength.score + 1) * 20)}%`,
                    background:
                      strength.score <= 1
                        ? "var(--danger)"
                        : strength.score === 2
                          ? "var(--warning)"
                          : "var(--success)",
                  }}
                />
              </div>
              <span className="text-[11px] font-medium text-[var(--text-secondary)]">
                {strength.entropyBits} bit
              </span>
            </div>
          ) : null}
        </div>

        {mode === "register" ? (
          <>
            <div>
              <label className="block text-[11.5px] font-semibold text-[var(--text-secondary)] mb-1">
                {isVi ? "Xác nhận Master Password" : "Confirm Master Password"}
              </label>
              <input
                type={showPassword ? "text" : "password"}
                className="ov-input"
                placeholder={isVi ? "Nhập lại Master Password" : "Re-enter Master Password"}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                autoComplete="new-password"
                required
              />
            </div>

            <div>
              <label className="block text-[11.5px] font-semibold text-[var(--text-secondary)] mb-1">
                {isVi ? "Gợi ý mật khẩu (tùy chọn)" : "Password Hint (optional)"}
              </label>
              <input
                type="text"
                className="ov-input"
                placeholder={isVi ? "Gợi ý không chứa mật khẩu" : "Optional hint without password"}
                value={hint}
                onChange={(e) => setHint(e.target.value)}
              />
            </div>
          </>
        ) : null}

        {/* Server URL accordion */}
        <div className="ov-card p-2.5">
          <button
            type="button"
            onClick={() => setShowServerConfig((v) => !v)}
            className="w-full flex items-center justify-between text-[12px] text-[var(--text-secondary)] bg-transparent border-0 cursor-pointer p-0"
          >
            <span className="flex items-center gap-1.5 font-medium truncate">
              <Globe size={13} className="text-[var(--accent)] shrink-0" />
              <span className="truncate">{serverUrl.replace(/^https?:\/\//, "")}</span>
            </span>
            <span className="text-[11px] font-semibold text-[var(--accent)] shrink-0 ml-2">
              {showServerConfig ? (isVi ? "Ẩn" : "Hide") : isVi ? "Đổi server" : "Change"}
            </span>
          </button>
          {showServerConfig ? (
            <div className="mt-2">
              <input
                type="url"
                className="ov-input text-[12px]"
                value={serverUrl}
                onChange={(e) => setServerUrl(e.target.value)}
                placeholder="https://openvault-api.your-account.workers.dev"
                required
              />
            </div>
          ) : null}
        </div>

        {error ? (
          <div className="px-3 py-2 rounded-[10px] text-[12px] font-medium bg-[var(--danger-soft)] text-[var(--danger)]">
            {error}
          </div>
        ) : null}

        <button
          type="submit"
          disabled={submitting}
          className="ov-btn-primary w-full py-2.5 mt-1 flex items-center justify-center gap-2"
        >
          <Lock size={14} />
          <span>
            {submitting
              ? mode === "register"
                ? isVi
                  ? "Đang sinh khóa Argon2id..."
                  : "Deriving Argon2id keys..."
                : isVi
                  ? "Đang giải mã & đăng nhập..."
                  : "Signing in & decrypting..."
              : mode === "register"
                ? isVi
                  ? "Khởi tạo Kho Mật Khẩu"
                  : "Create OpenVault"
                : isVi
                  ? "Mở Kho Mật Khẩu"
                  : "Unlock Vault"}
          </span>
        </button>
      </form>
    </div>
  );
}

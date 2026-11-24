import { ArrowRight, Eye, EyeOff, Loader2 } from "lucide-react";
import { Fragment, useCallback, useEffect, useId, useRef, useState } from "react";
import { getStrings, type AppLanguage } from "../../shared/i18n.js";
import { sendBg } from "../components/bridge.js";
import { ConfirmDialog } from "../components/ConfirmDialog.js";
import { OpenVaultLogo } from "../components/OpenVaultLogo.js";

interface UnlockScreenProps {
  email?: string;
  accountAvatarBase64?: string | null;
  language?: AppLanguage;
  pinEnabled: boolean;
  pinAvailableNow: boolean;
  pinRemainingAttempts: number;
  pinLength: number;
  onUnlocked: () => Promise<void>;
  onLoggedOut: () => Promise<void>;
}

/**
 * Split Metallic Vault-Door Unlock Screen synchronized with Android `UnlockScreen.kt`:
 * - Top & bottom vault plates separated by a horizontal bevel seam
 * - Centered 90px Cobalt Keyhole Dial straddling the seam (rotates 90° clockwise & splits doors on unlock)
 * - Clean 3+3 PIN slots or single-row Master Password bar
 */
export function UnlockScreen({
  language = "en",
  pinEnabled,
  pinAvailableNow,
  pinRemainingAttempts,
  pinLength,
  onUnlocked,
  onLoggedOut,
}: UnlockScreenProps) {
  const s = getStrings(language);
  const safeSlotCount = pinLength >= 4 && pinLength <= 8 ? pinLength : 6;
  const useSegmentedBoxes = pinLength >= 4 && pinLength <= 8;

  const [pinAvailable, setPinAvailable] = useState(pinAvailableNow);
  const [pinWiped, setPinWiped] = useState(false);
  const [remainingAttempts, setRemainingAttempts] = useState(pinRemainingAttempts);
  const [usePin, setUsePin] = useState(pinAvailableNow);

  const [pin, setPin] = useState("");
  const [pinFocused, setPinFocused] = useState(false);
  const [shakeKey, setShakeKey] = useState(0);
  const [unlockPhase, setUnlockPhase] = useState<"idle" | "rotating" | "splitting">("idle");

  const [masterPassword, setMasterPassword] = useState("");
  const [showMasterPassword, setShowMasterPassword] = useState(false);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmLogout, setConfirmLogout] = useState(false);

  const pinInputRef = useRef<HTMLInputElement>(null);
  const masterInputRef = useRef<HTMLInputElement>(null);
  const submittingRef = useRef(false);
  const errorId = useId();
  const pinInputId = useId();
  const unlocking = unlockPhase !== "idle";

  useEffect(() => {
    setPinAvailable(pinAvailableNow);
    setPinWiped(false);
    setUsePin(pinAvailableNow);
    setRemainingAttempts(pinRemainingAttempts);
  }, [pinAvailableNow, pinRemainingAttempts]);

  useEffect(() => {
    if (usePin) {
      pinInputRef.current?.focus();
    } else {
      masterInputRef.current?.focus();
    }
  }, [usePin]);

  const playKeyholeUnlockAndProceed = useCallback(async () => {
    const reduced = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    if (reduced) {
      await onUnlocked();
      return;
    }
    setUnlockPhase("rotating");
    await new Promise((r) => window.setTimeout(r, 270));
    setUnlockPhase("splitting");
    await new Promise((r) => window.setTimeout(r, 300));
    await onUnlocked();
  }, [onUnlocked]);

  const submitPin = useCallback(
    async (candidatePin: string) => {
      if (!candidatePin || submittingRef.current) return;
      submittingRef.current = true;
      setError(null);
      setLoading(true);
      try {
        await sendBg({ type: "UNLOCK_WITH_PIN", pin: candidatePin });
        setPin("");
        await playKeyholeUnlockAndProceed();
      } catch (err) {
        const msg =
          err instanceof Error
            ? err.message
            : s.isVi
              ? "Mã PIN không chính xác."
              : "Incorrect PIN.";
        setError(msg);
        setShakeKey((k) => k + 1);
        setPin("");
        if (msg.includes("hủy mã PIN") || msg.toLowerCase().includes("wiped")) {
          setPinAvailable(false);
          setPinWiped(true);
          setRemainingAttempts(0);
          setUsePin(false);
        } else {
          setRemainingAttempts((prev) => Math.max(0, prev - 1));
          window.requestAnimationFrame(() => {
            pinInputRef.current?.focus();
          });
        }
      } finally {
        setLoading(false);
        submittingRef.current = false;
      }
    },
    [playKeyholeUnlockAndProceed, s.isVi],
  );

  const handlePinChange = (raw: string) => {
    if (loading || unlocking) return;
    setError(null);

    if (useSegmentedBoxes) {
      const cleaned = raw.replace(/\s+/g, "").slice(0, safeSlotCount);
      setPin(cleaned);
      if (cleaned.length === safeSlotCount) {
        void submitPin(cleaned);
      }
    } else {
      setPin(raw);
    }
  };

  const handlePinFormSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (pin.length >= 4) {
      void submitPin(pin);
    }
  };

  const handleMasterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!masterPassword || loading || unlocking) return;
    setError(null);
    setLoading(true);
    try {
      await sendBg({ type: "UNLOCK", masterPassword });
      setMasterPassword("");
      await playKeyholeUnlockAndProceed();
    } catch (err) {
      const msg =
        err instanceof Error
          ? err.message
          : s.isVi
            ? "Mật khẩu chính không đúng."
            : "Incorrect Master Password.";
      setError(msg);
      setShakeKey((k) => k + 1);
      window.requestAnimationFrame(() => {
        masterInputRef.current?.focus();
      });
    } finally {
      setLoading(false);
    }
  };

  const handleConfirmLogout = async () => {
    setConfirmLogout(false);
    await sendBg({ type: "LOGOUT" });
    await onLoggedOut();
  };

  const splitAfterIndex = safeSlotCount === 6 ? 2 : safeSlotCount === 8 ? 3 : -1;

  return (
    <div className="ov-vault-door-shell" data-phase={unlockPhase}>
      {/* Top Vault Door Plate (glides UP on unlock) */}
      <div className="ov-vault-door-top" aria-hidden="true" />

      {/* Centered Rotating Keyhole Dial right on the Horizontal Seam */}
      <div className="ov-vault-door-dial" aria-hidden="true">
        <OpenVaultLogo variant="dial" size={90} rotated={unlockPhase !== "idle"} />
      </div>

      {/* Bottom Vault Door Plate (glides DOWN on unlock) */}
      <div className="ov-vault-door-bottom px-6 pb-6 pt-[72px]">
        <div className="flex-1 flex flex-col items-center w-full max-w-[360px] mx-auto">
          {pinEnabled && !pinAvailable && !pinWiped ? (
            <div className="mb-3 px-3.5 py-2 rounded-[10px] text-[11.5px] bg-[var(--accent-soft)] text-[var(--accent)] text-center leading-snug w-full">
              {s.pinRestartNotice}
            </div>
          ) : null}

          {usePin ? (
            <form
              onSubmit={handlePinFormSubmit}
              className="flex flex-col items-center w-full gap-3.5"
              noValidate
            >
              <div className="text-[13px] font-medium text-[var(--text-secondary)] text-center">
                {loading || unlocking
                  ? s.unlocking
                  : `${s.enterPinPrompt} (${safeSlotCount})`}
              </div>

              {useSegmentedBoxes ? (
                <div className="ov-pin-wrapper" onClick={() => pinInputRef.current?.focus()}>
                  <label htmlFor={pinInputId} className="sr-only">
                    {s.enterPinPrompt}
                  </label>
                  <input
                    ref={pinInputRef}
                    id={pinInputId}
                    type="password"
                    inputMode="numeric"
                    autoComplete="off"
                    maxLength={safeSlotCount}
                    value={pin}
                    disabled={loading || unlocking}
                    autoFocus
                    aria-invalid={error ? "true" : "false"}
                    aria-describedby={error ? errorId : undefined}
                    onFocus={() => setPinFocused(true)}
                    onBlur={() => setPinFocused(false)}
                    onChange={(e) => handlePinChange(e.target.value)}
                    className="ov-pin-native-input"
                  />
                  <div
                    key={shakeKey}
                    className={`ov-pin-slots ${shakeKey > 0 && error ? "ov-pin-shake" : ""}`}
                    aria-hidden="true"
                  >
                    {Array.from({ length: safeSlotCount }).map((_, idx) => {
                      const isFilled = idx < pin.length;
                      const isActive =
                        pinFocused && !loading && idx === Math.min(pin.length, safeSlotCount - 1);
                      return (
                        <Fragment key={idx}>
                          <div
                            className="ov-pin-slot"
                            data-active={isActive ? "true" : "false"}
                            data-filled={isFilled ? "true" : "false"}
                            data-error={error ? "true" : "false"}
                            data-loading={loading || unlocking ? "true" : "false"}
                          >
                            {isFilled ? (
                              <span className="ov-pin-dot" />
                            ) : isActive ? (
                              <span className="ov-pin-caret" />
                            ) : null}
                          </div>
                          {idx === splitAfterIndex ? <div className="ov-pin-gap" /> : null}
                        </Fragment>
                      );
                    })}
                  </div>
                </div>
              ) : (
                <div className="w-full flex flex-col gap-2.5">
                  <input
                    ref={pinInputRef}
                    id={pinInputId}
                    type="password"
                    inputMode="numeric"
                    autoComplete="off"
                    placeholder={s.enterPinPrompt}
                    value={pin}
                    disabled={loading || unlocking}
                    autoFocus
                    aria-invalid={error ? "true" : "false"}
                    aria-describedby={error ? errorId : undefined}
                    onChange={(e) => handlePinChange(e.target.value)}
                    className="ov-input font-mono-code text-center tracking-[0.25em] text-[15px] py-2.5"
                  />
                  <button
                    type="submit"
                    disabled={loading || unlocking || pin.length < 4}
                    className="ov-btn-primary w-full py-2.5"
                  >
                    {loading || unlocking ? s.unlocking : s.unlockHeading}
                  </button>
                </div>
              )}

              <div className="min-h-[20px] flex items-center justify-center text-center" aria-live="polite">
                {loading || unlocking ? (
                  <span className="inline-flex items-center gap-1.5 text-[12px] font-medium text-[var(--accent)]">
                    <Loader2 size={13} className="animate-spin" />
                    <span>{s.unlocking}</span>
                  </span>
                ) : error ? (
                  <span id={errorId} className="text-[12px] font-medium text-[var(--danger)]">
                    {error}
                  </span>
                ) : null}
              </div>

              <div className="flex items-center gap-3 text-[12.5px] font-semibold">
                <button
                  type="button"
                  onClick={() => {
                    setUsePin(false);
                    setPin("");
                    setError(null);
                  }}
                  className="text-[var(--accent)] hover:underline bg-transparent border-0 cursor-pointer p-0"
                >
                  {s.useMasterPassword}
                </button>
                <span className="text-[var(--border-strong)]" aria-hidden="true">
                  ·
                </span>
                <button
                  type="button"
                  onClick={() => setConfirmLogout(true)}
                  className="text-[var(--text-secondary)] hover:text-[var(--danger)] bg-transparent border-0 cursor-pointer p-0 transition-colors"
                >
                  {s.signOut}
                </button>
              </div>
            </form>
          ) : (
            <form
              onSubmit={handleMasterSubmit}
              className="flex flex-col items-center gap-3.5 w-full"
            >
              {/* Single-row Master Password Bar with vertical divider + Cobalt arrow submit */}
              <div
                key={shakeKey}
                data-error={error ? "true" : "false"}
                className={`ov-master-unlock-bar ${shakeKey > 0 && error ? "ov-pin-shake" : ""}`}
              >
                <input
                  ref={masterInputRef}
                  type={showMasterPassword ? "text" : "password"}
                  className="flex-1 min-w-0 h-full bg-transparent border-0 outline-none px-4 text-[14.5px] text-[var(--text-primary)] placeholder:text-[var(--text-muted)]"
                  placeholder={s.enterMasterPrompt}
                  value={masterPassword}
                  disabled={loading || unlocking}
                  onChange={(e) => {
                    setMasterPassword(e.target.value);
                    if (error) setError(null);
                  }}
                  autoFocus
                  autoComplete="current-password"
                  aria-invalid={error ? "true" : "false"}
                  aria-describedby={error ? errorId : undefined}
                />
                {masterPassword ? (
                  <button
                    type="button"
                    onClick={() => setShowMasterPassword((v) => !v)}
                    className="ov-icon-btn w-8 h-8 mr-1 shrink-0"
                    aria-label={showMasterPassword ? s.hidePassword : s.showPassword}
                  >
                    {showMasterPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                ) : null}
                <div className="w-[1px] h-6 bg-[var(--border-strong)] shrink-0" aria-hidden="true" />
                <button
                  type="submit"
                  disabled={loading || unlocking || !masterPassword}
                  aria-label={s.unlockHeading}
                  className="w-12 h-full bg-transparent border-0 flex items-center justify-center cursor-pointer shrink-0 disabled:cursor-default"
                >
                  {loading || unlocking ? (
                    <Loader2 size={18} className="animate-spin text-[var(--accent)]" />
                  ) : (
                    <ArrowRight
                      size={20}
                      className={masterPassword ? "text-[var(--accent)]" : "text-[var(--text-muted)]"}
                    />
                  )}
                </button>
              </div>

              <div className="min-h-[20px] text-center" aria-live="polite">
                {error ? (
                  <span id={errorId} className="text-[12.5px] font-medium text-[var(--danger)]">
                    {error}
                  </span>
                ) : (
                  <span className="text-[13px] text-[var(--text-secondary)]">
                    {s.isVi ? "OpenVault đang khóa." : "OpenVault is locked."}
                  </span>
                )}
              </div>

              <div className="flex items-center justify-center gap-3 text-[12.5px] font-semibold">
                {pinAvailable ? (
                  <>
                    <button
                      type="button"
                      onClick={() => {
                        setUsePin(true);
                        setMasterPassword("");
                        setError(null);
                      }}
                      className="text-[var(--accent)] hover:underline bg-transparent border-0 cursor-pointer p-0"
                    >
                      {s.usePin(remainingAttempts)}
                    </button>
                    <span className="text-[var(--border-strong)]" aria-hidden="true">
                      ·
                    </span>
                  </>
                ) : null}
                <button
                  type="button"
                  onClick={() => setConfirmLogout(true)}
                  className="text-[var(--text-secondary)] hover:text-[var(--danger)] bg-transparent border-0 cursor-pointer p-0 transition-colors"
                >
                  {s.signOut}
                </button>
              </div>
            </form>
          )}
        </div>
      </div>

      <ConfirmDialog
        open={confirmLogout}
        title={s.signOutTitle}
        description={s.signOutDesc}
        confirmLabel={s.signOut}
        onConfirm={() => {
          void handleConfirmLogout();
        }}
        onCancel={() => setConfirmLogout(false)}
      />
    </div>
  );
}

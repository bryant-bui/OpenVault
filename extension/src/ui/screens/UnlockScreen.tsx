import { Eye, EyeOff, KeyRound, Loader2 } from "lucide-react";
import { Fragment, useCallback, useEffect, useId, useRef, useState } from "react";
import { getStrings, type AppLanguage } from "../../shared/i18n.js";
import { normalizeAvatarDataUrl } from "../../shared/media.js";
import { sendBg } from "../components/bridge.js";
import { ConfirmDialog } from "../components/ConfirmDialog.js";

interface UnlockScreenProps {
  email: string;
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
 * Atmospheric 3 + 3 Auto-Unlock PIN / Master Password Screen
 * with rotating vault keyhole unlock animation (#13) & clean footer without server URL (#17).
 */
export function UnlockScreen({
  email,
  accountAvatarBase64,
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
  const [unlockingDoors, setUnlockingDoors] = useState(false);

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

  const avatarDataUrl = normalizeAvatarDataUrl(accountAvatarBase64);

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
    setUnlockingDoors(true);
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
        const msg = err instanceof Error ? err.message : s.isVi ? "Mã PIN không chính xác." : "Incorrect PIN.";
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
    if (loading || unlockingDoors) return;
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
    if (!masterPassword || loading || unlockingDoors) return;
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
            ? "Master Password không chính xác."
            : "Incorrect Master Password.";
      setError(msg);
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
    <div
      className={`ov-unlock-canvas flex-1 flex flex-col justify-between px-6 pb-5 pt-20 ${
        unlockingDoors ? "ov-unlock-opening" : ""
      }`}
    >
      {/* Peeking top OpenVault gradient emblem silhouette */}
      <div className="ov-unlock-emblem" aria-hidden="true" />

      <div className="flex-1 flex flex-col items-center justify-center relative z-10 w-full max-w-[360px] mx-auto">
        {/* Rotating Vault Keyhole Dial (rotates 90° on unlock!) */}
        <div className="flex items-center justify-center gap-3 mb-3">
          <div className={`ov-keyhole-dial ${unlockingDoors ? "ov-keyhole-rotated" : ""}`} aria-hidden="true">
            <div className="ov-keyhole-inner">
              <div className="ov-keyhole-slot" />
            </div>
          </div>
          {avatarDataUrl ? (
            <img
              src={avatarDataUrl}
              alt=""
              className="w-10 h-10 rounded-[12px] object-cover border border-[var(--border)] shadow-sm"
            />
          ) : null}
        </div>

        <h1 className="text-[22px] font-bold tracking-tight m-0 text-[var(--text-primary)] text-center">
          {s.unlockHeading}{" "}
          <span className="font-extrabold bg-gradient-to-r from-[#2f85ff] to-[#818cf8] bg-clip-text text-transparent">
            OpenVault
          </span>
        </h1>

        <p className="text-[13px] font-medium text-[var(--text-secondary)] mt-1.5 mb-5 text-center">
          {usePin ? s.enterPinPrompt : s.enterMasterPrompt}
        </p>

        {pinEnabled && !pinAvailable && !pinWiped ? (
          <div className="mb-4 px-3.5 py-2 rounded-[10px] text-[11.5px] bg-[var(--accent-soft)] text-[var(--accent)] text-center leading-snug w-full">
            {s.pinRestartNotice}
          </div>
        ) : null}

        {usePin ? (
          <form
            onSubmit={handlePinFormSubmit}
            className="flex flex-col items-center w-full gap-4"
            noValidate
          >
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
                  disabled={loading || unlockingDoors}
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
                          data-loading={loading || unlockingDoors ? "true" : "false"}
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
                  disabled={loading || unlockingDoors}
                  autoFocus
                  aria-invalid={error ? "true" : "false"}
                  aria-describedby={error ? errorId : undefined}
                  onChange={(e) => handlePinChange(e.target.value)}
                  className="ov-input font-mono-code text-center tracking-[0.25em] text-[15px] py-2.5"
                />
                <button
                  type="submit"
                  disabled={loading || unlockingDoors || pin.length < 4}
                  className="ov-btn-primary w-full py-2.5"
                >
                  {loading || unlockingDoors ? s.unlocking : s.unlockHeading}
                </button>
              </div>
            )}

            <div className="min-h-[20px] flex items-center justify-center text-center" aria-live="polite">
              {loading || unlockingDoors ? (
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

            <div className="flex items-center gap-4 mt-1 text-[12.5px] font-semibold">
              <button
                type="button"
                onClick={() => {
                  setUsePin(false);
                  setPin("");
                  setError(null);
                }}
                className="text-[var(--text-secondary)] hover:text-[var(--text-primary)] bg-transparent border-0 cursor-pointer p-0 transition-colors"
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
          <form onSubmit={handleMasterSubmit} className="flex flex-col gap-3 w-full">
            <div className="relative">
              <input
                ref={masterInputRef}
                type={showMasterPassword ? "text" : "password"}
                className="ov-input pr-10 py-2.5 text-[13.5px]"
                placeholder={s.enterMasterPrompt}
                value={masterPassword}
                onChange={(e) => {
                  setMasterPassword(e.target.value);
                  if (error) setError(null);
                }}
                autoFocus
                autoComplete="current-password"
                aria-invalid={error ? "true" : "false"}
                aria-describedby={error ? errorId : undefined}
              />
              <button
                type="button"
                onClick={() => setShowMasterPassword((v) => !v)}
                className="ov-icon-btn w-8 h-8 absolute right-1.5 top-1/2 -translate-y-1/2"
                aria-label={showMasterPassword ? s.hidePassword : s.showPassword}
              >
                {showMasterPassword ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>

            <div aria-live="polite">
              {error ? (
                <div
                  id={errorId}
                  className="px-3 py-2 rounded-[10px] text-[12px] font-medium bg-[var(--danger-soft)] text-[var(--danger)] text-center"
                >
                  {error}
                </div>
              ) : null}
            </div>

            <button
              type="submit"
              disabled={loading || unlockingDoors || !masterPassword}
              className="ov-btn-primary w-full py-2.5 flex items-center justify-center gap-2"
            >
              {loading || unlockingDoors ? <Loader2 size={15} className="animate-spin" /> : <KeyRound size={15} />}
              <span>{loading || unlockingDoors ? s.unlocking : `${s.unlockHeading} OpenVault`}</span>
            </button>

            <div className="flex items-center justify-center gap-4 mt-1 text-[12.5px] font-semibold">
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

      {/* Clean email-only footer without server URL clutter (#17) */}
      <div className="relative z-10 text-center text-[11px] text-[var(--text-muted)] truncate">
        {email}
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

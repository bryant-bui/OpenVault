import {
  CipherType,
  estimatePasswordStrength,
  type CipherPayload,
} from "@openvault/crypto-core";
import { Fingerprint, Lock, ShieldCheck, X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { getStrings, type AppLanguage } from "../../shared/i18n.js";

interface WatchtowerPanelProps {
  ciphers: CipherPayload[];
  language?: AppLanguage;
  onFilterWeak: () => void;
  onFilter2FA: () => void;
  onClose: () => void;
}

/**
 * 1Password Watchtower local zero-knowledge security health dashboard
 * with geometrically pure circular arc gauge and smooth animated count-up (#12).
 */
export function WatchtowerPanel({
  ciphers,
  language = "en",
  onFilterWeak,
  onFilter2FA,
  onClose,
}: WatchtowerPanelProps) {
  const s = getStrings(language);

  const stats = useMemo(() => {
    const activeLogins = ciphers.filter((c) => !c.deletedDate && c.type === CipherType.Login);
    const pwCounts = new Map<string, number>();

    let weakCount = 0;
    let totpCount = 0;
    let passkeyCount = 0;

    for (const c of activeLogins) {
      const pw = c.login?.password ?? "";
      if (pw) {
        pwCounts.set(pw, (pwCounts.get(pw) ?? 0) + 1);
        if (estimatePasswordStrength(pw).score <= 1) {
          weakCount++;
        }
      }
      if (c.login?.totp) totpCount++;
      if (c.login?.fido2Credentials && c.login.fido2Credentials.length > 0) passkeyCount++;
    }

    let reusedCount = 0;
    for (const c of activeLogins) {
      const pw = c.login?.password ?? "";
      if (pw && (pwCounts.get(pw) ?? 0) > 1) reusedCount++;
    }

    const total = activeLogins.length;
    if (total === 0) {
      return {
        score: 1000,
        label: s.isVi ? "SẴN SÀNG" : "READY",
        total: 0,
        weakCount: 0,
        reusedCount: 0,
        totpCount: 0,
        passkeyCount: 0,
      };
    }

    const weakPenalty = (weakCount / total) * 340;
    const reusePenalty = (reusedCount / total) * 260;
    const bonus = Math.min(80, ((totpCount + passkeyCount) / total) * 80);
    const score = Math.round(Math.max(150, Math.min(1000, 920 - weakPenalty - reusePenalty + bonus)));

    const label = s.isVi
      ? score >= 850
        ? "TUYỆT VỜI"
        : score >= 700
          ? "TỐT"
          : score >= 500
            ? "TRUNG BÌNH"
            : "CẦN CẢI THIỆN"
      : score >= 850
        ? "FANTASTIC"
        : score >= 700
          ? "VERY GOOD"
          : score >= 500
            ? "GOOD"
            : "NEEDS WORK";

    return { score, label, total, weakCount, reusedCount, totpCount, passkeyCount };
  }, [ciphers, s.isVi]);

  // Smooth animated count-up & arc sweep (0 -> target score over 800ms)
  const [animProgress, setAnimProgress] = useState(0);
  useEffect(() => {
    let rafId = 0;
    const start = performance.now();
    const duration = 820;
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration);
      // Cubic ease-out
      const eased = 1 - Math.pow(1 - t, 3);
      setAnimProgress(eased);
      if (t < 1) {
        rafId = window.requestAnimationFrame(tick);
      }
    };
    rafId = window.requestAnimationFrame(tick);
    return () => window.cancelAnimationFrame(rafId);
  }, [stats.score]);

  const displayedScore = Math.round(stats.score * animProgress);
  const progress = Math.max(0.02, Math.min(1, (stats.score / 1000) * animProgress));
  const arcRadius = 46;
  const arcLength = Math.PI * arcRadius;
  const dashOffset = arcLength * (1 - progress);
  const gaugeColor =
    stats.score >= 800
      ? "#16A34A"
      : stats.score >= 600
        ? "#D97706"
        : "#E11D48";

  return (
    <div className="flex-1 flex flex-col min-h-0 bg-[var(--bg)]">
      <div className="px-4 py-2.5 border-b border-[var(--border)] bg-[var(--surface)] flex items-center justify-between">
        <div className="flex items-center gap-2">
          <ShieldCheck size={16} className="text-[var(--accent)]" />
          <h2 className="text-[14px] font-bold m-0">{s.watchtowerTitle}</h2>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="ov-icon-btn w-7 h-7"
          aria-label={s.close}
        >
          <X size={15} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto ov-scroll p-4 flex flex-col gap-3">
        <div className="ov-card p-4 flex items-center justify-between gap-4">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-1.5 text-[14.5px] font-bold text-[var(--text-primary)]">
              <ShieldCheck size={16} className="text-[var(--success)]" />
              <span>{s.vaultHealthScore}</span>
            </div>
            <p className="text-[12px] text-[var(--text-secondary)] leading-relaxed mt-1 mb-0">
              {s.watchtowerLocalNote(stats.total)}
            </p>
          </div>

          <div className="relative w-[124px] h-[78px] flex flex-col items-center justify-end shrink-0">
            <svg width="116" height="66" viewBox="0 0 116 66" className="overflow-visible">
              <defs>
                <linearGradient id="ovWatchtowerGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                  <stop offset="0%" stopColor="#10B981" />
                  <stop offset="55%" stopColor="#2DD4BF" />
                  <stop offset="100%" stopColor={gaugeColor === "#16A34A" ? "#2563EB" : gaugeColor} />
                </linearGradient>
              </defs>
              <path
                d="M 12 58 A 46 46 0 0 1 104 58"
                fill="none"
                stroke="var(--border)"
                strokeWidth="10"
                strokeLinecap="round"
              />
              <path
                d="M 12 58 A 46 46 0 0 1 104 58"
                fill="none"
                stroke={stats.score >= 800 ? "url(#ovWatchtowerGrad)" : gaugeColor}
                strokeWidth="10"
                strokeLinecap="round"
                strokeDasharray={arcLength}
                strokeDashoffset={dashOffset}
              />
            </svg>
            <div className="absolute bottom-0 text-center">
              <div className="text-[24px] font-extrabold leading-none tracking-tight tabular-nums text-[var(--text-primary)]">
                {displayedScore}
              </div>
              <div className="text-[9px] font-bold tracking-wider uppercase text-[var(--text-muted)] mt-0.5">
                {stats.label}
              </div>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <button
            type="button"
            onClick={onFilterWeak}
            className="ov-card p-4 text-left cursor-pointer hover:border-[var(--border-strong)] transition-colors flex flex-col gap-2"
          >
            <div className="flex items-start justify-between">
              <span className="text-[28px] font-extrabold leading-none text-[var(--text-primary)]">
                {stats.weakCount + stats.reusedCount}
              </span>
              <span
                className="w-9 h-9 rounded-[10px] flex items-center justify-center"
                style={{ background: "var(--danger-soft)", color: "var(--danger)" }}
              >
                <Lock size={17} />
              </span>
            </div>
            <div>
              <div className="text-[13px] font-bold text-[var(--text-primary)]">
                {s.weakAndReusedCard}
              </div>
              <p className="text-[11.5px] text-[var(--text-secondary)] m-0 mt-0.5 leading-snug">
                {s.weakReusedSummary(stats.weakCount, stats.reusedCount)}
              </p>
            </div>
          </button>

          <button
            type="button"
            onClick={onFilter2FA}
            className="ov-card p-4 text-left cursor-pointer hover:border-[var(--border-strong)] transition-colors flex flex-col gap-2"
          >
            <div className="flex items-start justify-between">
              <span className="text-[28px] font-extrabold leading-none text-[var(--text-primary)]">
                {stats.totpCount + stats.passkeyCount}
              </span>
              <span
                className="w-9 h-9 rounded-[10px] flex items-center justify-center"
                style={{ background: "var(--passkey-soft)", color: "var(--passkey)" }}
              >
                <Fingerprint size={18} />
              </span>
            </div>
            <div>
              <div className="text-[13px] font-bold text-[var(--text-primary)]">
                {s.twoFaAndPasskeyCard}
              </div>
              <p className="text-[11.5px] text-[var(--text-secondary)] m-0 mt-0.5 leading-snug">
                {s.twoFaPasskeySummary(stats.totpCount, stats.passkeyCount)}
              </p>
            </div>
          </button>
        </div>
      </div>
    </div>
  );
}

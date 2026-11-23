import { generateTotp, type TotpCodeResult } from "@openvault/crypto-core";
import { Check, Copy } from "lucide-react";
import { useEffect, useState } from "react";

interface TotpRingProps {
  secretOrUri: string;
  onCopy: (code: string) => void;
  compact?: boolean;
}

function formatCode(code: string): string {
  if (code.length === 6) return `${code.slice(0, 3)} ${code.slice(3)}`;
  if (code.length === 8) return `${code.slice(0, 4)} ${code.slice(4)}`;
  return code;
}

export function TotpRing({ secretOrUri, onCopy, compact = false }: TotpRingProps) {
  const [result, setResult] = useState<TotpCodeResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    let active = true;
    const tick = async () => {
      try {
        const res = await generateTotp(secretOrUri, Date.now());
        if (active) {
          setResult(res);
          setError(null);
        }
      } catch {
        if (active) setError("Khóa TOTP không hợp lệ");
      }
    };
    void tick();
    const timer = window.setInterval(() => {
      void tick();
    }, 1000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [secretOrUri]);

  if (error) {
    return <span className="text-[12px] text-[var(--danger)]">{error}</span>;
  }

  const remaining = result?.remainingSeconds ?? 30;
  const period = result?.period ?? 30;
  const progress = Math.max(0, Math.min(1, remaining / period));

  const radius = compact ? 11 : 14;
  const stroke = compact ? 2.5 : 3;
  const size = (radius + stroke) * 2;
  const circumference = 2 * Math.PI * radius;
  const dashOffset = circumference * (1 - progress);

  const ringColor =
    remaining <= 5
      ? "var(--danger)"
      : remaining <= 10
        ? "var(--warning)"
        : "var(--accent)";

  const handleCopy = () => {
    if (!result) return;
    onCopy(result.code);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1500);
  };

  return (
    <button
      type="button"
      onClick={handleCopy}
      className={`group flex items-center justify-between w-full text-left rounded-[10px] transition-colors cursor-pointer ${
        compact ? "gap-2.5 py-1" : "gap-3 px-2.5 py-2 hover:bg-[var(--surface-elevated)]"
      }`}
      title="Bấm để sao chép mã 2FA"
    >
      <div className="flex items-center gap-3 min-w-0">
        <div className="relative flex items-center justify-center shrink-0" style={{ width: size, height: size }}>
          <svg width={size} height={size} className="-rotate-90">
            <circle
              cx={size / 2}
              cy={size / 2}
              r={radius}
              fill="none"
              stroke="var(--border)"
              strokeWidth={stroke}
            />
            <circle
              cx={size / 2}
              cy={size / 2}
              r={radius}
              fill="none"
              stroke={ringColor}
              strokeWidth={stroke}
              strokeLinecap="round"
              strokeDasharray={circumference}
              strokeDashoffset={dashOffset}
              style={{ transition: "stroke-dashoffset 0.9s linear, stroke 0.25s ease" }}
            />
          </svg>
          <span
            className="absolute text-[9.5px] font-bold tabular-nums"
            style={{ color: ringColor }}
          >
            {remaining}
          </span>
        </div>

        <span
          className={`font-mono-code font-bold tracking-[0.12em] tabular-nums ${
            compact ? "text-[16px]" : "text-[21px]"
          }`}
          style={{ color: remaining <= 5 ? "var(--danger)" : "var(--text-primary)" }}
        >
          {result ? formatCode(result.code) : "••• •••"}
        </span>
      </div>

      <span className="ov-icon-btn w-7 h-7 shrink-0">
        {copied ? (
          <Check size={15} className="text-[var(--success)]" />
        ) : (
          <Copy size={14} className="opacity-70 group-hover:opacity-100" />
        )}
      </span>
    </button>
  );
}

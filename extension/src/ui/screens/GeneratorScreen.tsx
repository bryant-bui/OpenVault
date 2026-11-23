import {
  estimatePasswordStrength,
  generatePassphrase,
  generatePassword,
} from "@openvault/crypto-core";
import { ChevronRight, Copy, History, RotateCw, X } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { getStrings, type AppLanguage } from "../../shared/i18n.js";
import { ColorPassword } from "../components/ColorPassword.js";

interface GeneratorScreenProps {
  language?: AppLanguage;
  autoCopyOnGenerate?: boolean;
  onCopyText: (text: string, label: string) => Promise<void>;
  onClose?: () => void;
}

type GenType = "random" | "passphrase";

export function GeneratorScreen({
  language = "en",
  autoCopyOnGenerate = false,
  onCopyText,
  onClose,
}: GeneratorScreenProps) {
  const s = getStrings(language);
  const [genType, setGenType] = useState<GenType>("random");
  const [length, setLength] = useState(20);
  const [wordCount, setWordCount] = useState(4);
  const [digits, setDigits] = useState(true);
  const [symbols, setSymbols] = useState(true);
  const [avoidAmbiguous, setAvoidAmbiguous] = useState(false);
  const [password, setPassword] = useState("");
  const [history, setHistory] = useState<string[]>([]);
  const [showHistory, setShowHistory] = useState(false);

  const regenerate = useCallback(
    (userTriggered = false) => {
      const next =
        genType === "random"
          ? generatePassword({
              length,
              lowercase: true,
              uppercase: true,
              digits,
              symbols,
              avoidAmbiguous,
            })
          : generatePassphrase({
              wordCount,
              capitalize: true,
              includeNumber: digits,
              separator: symbols ? "-" : ".",
            });

      setPassword(next);
      setHistory((prev) => [next, ...prev.filter((p) => p !== next)].slice(0, 10));
      if (userTriggered && autoCopyOnGenerate) {
        void onCopyText(next, "Đã tạo & sao chép mật khẩu mới");
      }
    },
    [genType, length, wordCount, digits, symbols, avoidAmbiguous, autoCopyOnGenerate, onCopyText],
  );

  useEffect(() => {
    regenerate(false);
  }, [regenerate]);

  const strength = estimatePasswordStrength(password);

  return (
    <div className="flex-1 flex flex-col min-h-0 bg-[var(--surface)]">
      <div className="px-4 py-3 flex items-center justify-between">
        <h2 className="text-[15.5px] font-bold m-0 tracking-tight text-[var(--text-primary)]">
          {s.passwordGenerator}
        </h2>
        {onClose ? (
          <button
            type="button"
            onClick={onClose}
            className="ov-icon-btn w-7 h-7"
            aria-label={s.close}
          >
            <X size={16} />
          </button>
        ) : null}
      </div>

      <div className="flex-1 overflow-y-auto ov-scroll px-4 pb-4 flex flex-col gap-3">
        {/* Image 2 3-action top row: Copy | Refresh | Use */}
        <div className="flex items-center justify-between gap-2">
          <button
            type="button"
            onClick={() => {
              void onCopyText(password, s.isVi ? "Đã sao chép mật khẩu" : "Copied password");
            }}
            className="ov-btn-ghost px-3.5 py-1.5 text-[12.5px]"
          >
            {s.copy}
          </button>

          <button
            type="button"
            onClick={() => regenerate(true)}
            className="ov-icon-btn w-8 h-8"
            title={s.isVi ? "Tạo mật khẩu khác" : "Regenerate"}
            aria-label={s.isVi ? "Tạo lại mật khẩu" : "Regenerate"}
          >
            <RotateCw size={16} />
          </button>

          <button
            type="button"
            onClick={() => {
              void onCopyText(password, s.isVi ? "Đã sao chép mật khẩu mới" : "Copied new password");
              onClose?.();
            }}
            className="ov-btn-primary px-3.5 py-1.5 text-[12.5px]"
          >
            {s.isVi ? "Dùng mật khẩu" : "Use password"}
          </button>
        </div>

        {/* Crisp Bordered Password Box + Clean Strength Underline (exact Image 2!) */}
        <div>
          <div className="ov-gen-strip px-3.5 py-2.5 text-left">
            <ColorPassword value={password} revealed />
          </div>
          <div className="mt-2 h-[2.5px] rounded-full bg-[var(--surface-subtle)] overflow-hidden">
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
        </div>

        {/* Hairline-divided Controls */}
        <div className="flex flex-col divide-y divide-[var(--border)]">
          <div className="py-2.5 flex items-center justify-between gap-3">
            <label htmlFor="gen-type" className="text-[13px] font-medium text-[var(--text-secondary)]">
              {s.isVi ? "Loại" : "Type"}
            </label>
            <select
              id="gen-type"
              value={genType}
              onChange={(e) => setGenType(e.target.value as GenType)}
              className="ov-input w-auto py-1 text-[12.5px] font-medium"
            >
              <option value="random">{s.isVi ? "Mật khẩu ngẫu nhiên" : "Random Password"}</option>
              <option value="passphrase">{s.isVi ? "Cụm từ dễ nhớ" : "Memorable Passphrase"}</option>
            </select>
          </div>

          <div className="py-2.5 flex items-center justify-between gap-3">
            <label htmlFor="gen-len" className="text-[13px] font-medium text-[var(--text-secondary)] shrink-0">
              {genType === "random"
                ? s.isVi
                  ? "Số ký tự"
                  : "Characters"
                : s.isVi
                  ? "Số từ"
                  : "Words"}
            </label>
            <div className="flex items-center gap-2.5 flex-1 max-w-[195px]">
              <input
                id="gen-len"
                type="range"
                min={genType === "random" ? 8 : 3}
                max={genType === "random" ? 64 : 8}
                value={genType === "random" ? length : wordCount}
                onChange={(e) =>
                  genType === "random"
                    ? setLength(Number(e.target.value))
                    : setWordCount(Number(e.target.value))
                }
                className="flex-1 cursor-pointer"
              />
              <span className="w-10 text-center py-0.5 rounded-[7px] border border-[var(--border)] bg-[var(--surface)] font-mono-code text-[12.5px] font-semibold tabular-nums">
                {genType === "random" ? length : wordCount}
              </span>
            </div>
          </div>

          <div className="py-2.5 flex items-center justify-between">
            <span className="text-[13px] font-medium text-[var(--text-secondary)]">
              {s.isVi ? "Chữ số" : "Numbers"}
            </span>
            <label className="ov-switch">
              <input
                type="checkbox"
                checked={digits}
                onChange={(e) => setDigits(e.target.checked)}
              />
              <span className="ov-switch-track" />
            </label>
          </div>

          <div className="py-2.5 flex items-center justify-between">
            <span className="text-[13px] font-medium text-[var(--text-secondary)]">
              {genType === "random"
                ? s.isVi
                  ? "Ký tự đặc biệt"
                  : "Symbols"
                : s.isVi
                  ? "Dấu gạch ngang (-)"
                  : "Hyphen separator (-)"}
            </span>
            <label className="ov-switch">
              <input
                type="checkbox"
                checked={symbols}
                onChange={(e) => setSymbols(e.target.checked)}
              />
              <span className="ov-switch-track" />
            </label>
          </div>

          {genType === "random" ? (
            <div className="py-2.5 flex items-center justify-between">
              <span className="text-[13px] font-medium text-[var(--text-secondary)]">
                {s.isVi ? "Tránh ký tự dễ nhầm" : "Avoid ambiguous characters"}
              </span>
              <label className="ov-switch">
                <input
                  type="checkbox"
                  checked={avoidAmbiguous}
                  onChange={(e) => setAvoidAmbiguous(e.target.checked)}
                />
                <span className="ov-switch-track" />
              </label>
            </div>
          ) : null}

          <div className="pt-2.5">
            <button
              type="button"
              onClick={() => setShowHistory((v) => !v)}
              className="w-full flex items-center justify-between text-[12.5px] font-medium text-[var(--text-secondary)] hover:text-[var(--text-primary)] bg-transparent border-0 p-0 cursor-pointer"
            >
              <span className="inline-flex items-center gap-2">
                <History size={14} />
                <span>
                  {s.isVi ? "Lịch sử tạo mật khẩu" : "Generator history"} ({history.length})
                </span>
              </span>
              <ChevronRight
                size={14}
                className={`transition-transform ${showHistory ? "rotate-90" : ""}`}
              />
            </button>

            {showHistory ? (
              <div className="mt-2 flex flex-col gap-1 max-h-[115px] overflow-y-auto ov-scroll">
                {history.map((item, idx) => (
                  <button
                    key={`${item}-${idx}`}
                    type="button"
                    onClick={() => {
                      void onCopyText(item, s.isVi ? "Đã sao chép từ lịch sử" : "Copied from history");
                    }}
                    className="w-full flex items-center justify-between gap-2 px-2.5 py-1.5 rounded-[7px] bg-[var(--surface-subtle)] hover:bg-[var(--surface-hover)] border-0 cursor-pointer text-left"
                  >
                    <span className="font-mono-code text-[11.5px] truncate text-[var(--text-primary)]">
                      {item}
                    </span>
                    <Copy size={11} className="shrink-0 text-[var(--text-muted)]" />
                  </button>
                ))}
              </div>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
}

import {
  estimatePasswordStrength,
  generatePassphrase,
  generatePassword,
} from "@openvault/crypto-core";
import { ChevronRight, Copy, History, RotateCw, X } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { ColorPassword } from "../components/ColorPassword.js";

interface GeneratorScreenProps {
  onCopyText: (text: string, label: string) => Promise<void>;
  onClose?: () => void;
}

type GenType = "random" | "passphrase";

/**
 * 1Password Slide-4 Password Generator Panel:
 * - 3-action top bar (Copy | Regenerate | Use/Copy)
 * - Pop-out syntax-colored password strip + green strength bar
 * - Hairline-divided Type select, Characters slider + numeric badge, iOS toggle switches
 * - Collapsible Password Generator History
 */
export function GeneratorScreen({ onCopyText, onClose }: GeneratorScreenProps) {
  const [genType, setGenType] = useState<GenType>("random");
  const [length, setLength] = useState(20);
  const [wordCount, setWordCount] = useState(4);
  const [digits, setDigits] = useState(true);
  const [symbols, setSymbols] = useState(true);
  const [avoidAmbiguous, setAvoidAmbiguous] = useState(false);
  const [password, setPassword] = useState("");
  const [history, setHistory] = useState<string[]>([]);
  const [showHistory, setShowHistory] = useState(false);

  const regenerate = useCallback(() => {
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
  }, [genType, length, wordCount, digits, symbols, avoidAmbiguous]);

  useEffect(() => {
    regenerate();
  }, [regenerate]);

  const strength = estimatePasswordStrength(password);

  return (
    <div className="flex-1 flex flex-col min-h-0 bg-[var(--bg)]">
      <div className="px-4 py-2.5 border-b border-[var(--border)] bg-[var(--surface)] flex items-center justify-between">
        <h2 className="text-[14px] font-bold m-0">Password Generator</h2>
        {onClose ? (
          <button
            type="button"
            onClick={onClose}
            className="ov-icon-btn w-7 h-7"
            aria-label="Đóng Password Generator"
          >
            <X size={15} />
          </button>
        ) : null}
      </div>

      <div className="flex-1 overflow-y-auto ov-scroll p-4 flex justify-center">
        <div className="w-full max-w-[380px] ov-card p-4 flex flex-col gap-3 self-start">
          {/* Slide-4 3-action top row: Copy | Rotate | Copy & Close */}
          <div className="flex items-center justify-between gap-2">
            <button
              type="button"
              onClick={() => {
                void onCopyText(password, "Đã sao chép mật khẩu");
              }}
              className="ov-btn-ghost px-3 py-1.5 text-[12px] inline-flex items-center gap-1.5"
            >
              <Copy size={12} />
              <span>Copy</span>
            </button>

            <button
              type="button"
              onClick={regenerate}
              className="ov-icon-btn w-8 h-8"
              title="Tạo mật khẩu khác"
              aria-label="Tạo lại mật khẩu"
            >
              <RotateCw size={15} />
            </button>

            <button
              type="button"
              onClick={() => {
                void onCopyText(password, "Đã sao chép mật khẩu mới");
                onClose?.();
              }}
              className="ov-btn-primary px-3.5 py-1.5 text-[12px]"
            >
              Dùng mật khẩu
            </button>
          </div>

          {/* Elevated Pop-out Password Strip + Green Strength Bar */}
          <div>
            <div className="ov-gen-strip px-3.5 py-3 text-center">
              <ColorPassword value={password} revealed large />
            </div>
            <div className="mt-2 h-1 rounded-full bg-[var(--surface-subtle)] overflow-hidden">
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

          {/* Hairline-divided controls (Slide 4 exact layout) */}
          <div className="flex flex-col divide-y divide-[var(--border)]">
            <div className="py-2.5 flex items-center justify-between gap-3">
              <label htmlFor="gen-type" className="text-[12.5px] font-medium text-[var(--text-secondary)]">
                Loại
              </label>
              <select
                id="gen-type"
                value={genType}
                onChange={(e) => setGenType(e.target.value as GenType)}
                className="ov-input w-auto py-1 text-[12px] font-medium"
              >
                <option value="random">Mật khẩu ngẫu nhiên</option>
                <option value="passphrase">Cụm từ dễ nhớ</option>
              </select>
            </div>

            <div className="py-2.5 flex items-center justify-between gap-3">
              <label htmlFor="gen-len" className="text-[12.5px] font-medium text-[var(--text-secondary)] shrink-0">
                {genType === "random" ? "Ký tự" : "Số từ"}
              </label>
              <div className="flex items-center gap-2.5 flex-1 max-w-[205px]">
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
                <span className="w-9 text-center py-0.5 rounded-[6px] border border-[var(--border)] bg-[var(--surface-subtle)] font-mono-code text-[12px] font-semibold tabular-nums">
                  {genType === "random" ? length : wordCount}
                </span>
              </div>
            </div>

            <div className="py-2.5 flex items-center justify-between">
              <span className="text-[12.5px] font-medium text-[var(--text-secondary)]">
                Chữ số (0-9)
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
              <span className="text-[12.5px] font-medium text-[var(--text-secondary)]">
                {genType === "random" ? "Ký tự đặc biệt (!@#$)" : "Dùng dấu gạch ngang (-)"}
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
                <span className="text-[12.5px] font-medium text-[var(--text-secondary)]">
                  Tránh ký tự dễ nhầm (l, I, O, 0, 1)
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
                className="w-full flex items-center justify-between text-[12px] font-semibold text-[var(--text-secondary)] hover:text-[var(--text-primary)] bg-transparent border-0 p-0 cursor-pointer"
              >
                <span className="inline-flex items-center gap-1.5">
                  <History size={13} />
                  <span>Lịch sử tạo mật khẩu ({history.length})</span>
                </span>
                <ChevronRight
                  size={14}
                  className={`transition-transform ${showHistory ? "rotate-90" : ""}`}
                />
              </button>

              {showHistory ? (
                <div className="mt-2 flex flex-col gap-1 max-h-[120px] overflow-y-auto ov-scroll">
                  {history.map((item, idx) => (
                    <button
                      key={`${item}-${idx}`}
                      type="button"
                      onClick={() => {
                        void onCopyText(item, "Đã sao chép từ lịch sử");
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
    </div>
  );
}

import { estimatePasswordStrength, generatePassword } from "@openvault/crypto-core";
import { Copy, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { ColorPassword } from "../components/ColorPassword.js";

interface GeneratorScreenProps {
  onCopyText: (text: string, label: string) => Promise<void>;
}

const STRENGTH_LABELS: Record<string, string> = {
  weak: "Yếu",
  fair: "Trung bình",
  good: "Khá mạnh",
  strong: "Mạnh",
  excellent: "Rất mạnh",
};

export function GeneratorScreen({ onCopyText }: GeneratorScreenProps) {
  const [length, setLength] = useState(20);
  const [lowercase, setLowercase] = useState(true);
  const [uppercase, setUppercase] = useState(true);
  const [digits, setDigits] = useState(true);
  const [symbols, setSymbols] = useState(true);
  const [avoidAmbiguous, setAvoidAmbiguous] = useState(false);
  const [password, setPassword] = useState("");

  const regenerate = useCallback(() => {
    const safeLower = lowercase || (!uppercase && !digits && !symbols);
    setPassword(
      generatePassword({
        length,
        lowercase: safeLower,
        uppercase,
        digits,
        symbols,
        avoidAmbiguous,
      }),
    );
  }, [length, lowercase, uppercase, digits, symbols, avoidAmbiguous]);

  useEffect(() => {
    regenerate();
  }, [regenerate]);

  const strength = estimatePasswordStrength(password);

  return (
    <div className="flex-1 flex flex-col min-h-0">
      <header className="px-3.5 py-3 border-b border-[var(--border)] bg-[var(--surface)]">
        <h1 className="text-[15px] font-bold m-0">Trình tạo mật khẩu mạnh</h1>
      </header>

      <div className="flex-1 overflow-y-auto ov-scroll p-3.5 flex flex-col gap-3">
        {/* Preview Card */}
        <div className="ov-card p-4 flex flex-col gap-3">
          <div className="min-h-[60px] flex items-center justify-center text-center px-2 py-2 rounded-[10px] bg-[var(--surface-elevated)]">
            <ColorPassword value={password} revealed large />
          </div>

          <div className="flex items-center justify-between text-[12px]">
            <div className="flex items-center gap-2 flex-1 mr-3">
              <div className="flex-1 h-1.5 rounded-full bg-[var(--surface-elevated)] overflow-hidden">
                <div
                  className="h-full rounded-full transition-all duration-200"
                  style={{
                    width: `${(strength.score + 1) * 20}%`,
                    background:
                      strength.score <= 1
                        ? "var(--danger)"
                        : strength.score === 2
                          ? "var(--warning)"
                          : "var(--success)",
                  }}
                />
              </div>
              <span className="font-semibold text-[var(--text-secondary)] whitespace-nowrap">
                {STRENGTH_LABELS[strength.label]} · {strength.entropyBits} bit
              </span>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={regenerate}
              className="ov-btn-ghost py-2 inline-flex items-center justify-center gap-1.5"
            >
              <RefreshCw size={14} />
              <span>Tạo lại</span>
            </button>
            <button
              type="button"
              onClick={() => {
                void onCopyText(password, "Đã sao chép mật khẩu mới");
              }}
              className="ov-btn-primary py-2 inline-flex items-center justify-center gap-1.5"
            >
              <Copy size={14} />
              <span>Sao chép</span>
            </button>
          </div>
        </div>

        {/* Options */}
        <div className="ov-card p-3.5 flex flex-col gap-3.5">
          <div>
            <div className="flex items-center justify-between text-[12.5px] font-semibold mb-1.5">
              <span>Độ dài mật khẩu</span>
              <span className="font-mono-code text-[var(--accent)]">{length} ký tự</span>
            </div>
            <input
              type="range"
              min={8}
              max={64}
              value={length}
              onChange={(e) => setLength(Number(e.target.value))}
              className="w-full cursor-pointer"
              aria-label="Độ dài mật khẩu"
            />
          </div>

          <div className="grid grid-cols-2 gap-2.5 text-[12.5px]">
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={lowercase}
                onChange={(e) => setLowercase(e.target.checked)}
              />
              <span>Chữ thường (a-z)</span>
            </label>
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={uppercase}
                onChange={(e) => setUppercase(e.target.checked)}
              />
              <span>Chữ hoa (A-Z)</span>
            </label>
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={digits}
                onChange={(e) => setDigits(e.target.checked)}
              />
              <span>Chữ số (0-9)</span>
            </label>
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={symbols}
                onChange={(e) => setSymbols(e.target.checked)}
              />
              <span>Ký tự đặc biệt</span>
            </label>
          </div>

          <label className="flex items-center gap-2 text-[12.5px] pt-2 border-t border-[var(--border)] cursor-pointer">
            <input
              type="checkbox"
              checked={avoidAmbiguous}
              onChange={(e) => setAvoidAmbiguous(e.target.checked)}
            />
            <span>Tránh ký tự dễ nhầm lẫn (l, I, O, 0, 1)</span>
          </label>
        </div>
      </div>
    </div>
  );
}

import type { CipherPayload } from "@openvault/crypto-core";
import { Search, ShieldCheck } from "lucide-react";
import { useMemo, useState } from "react";
import { BrandAvatar } from "../components/BrandAvatar.js";
import { TotpRing } from "../components/TotpRing.js";

interface TotpScreenProps {
  ciphers: CipherPayload[];
  onSelectCipher: (cipher: CipherPayload) => void;
  onCopyText: (text: string, label: string) => Promise<void>;
}

export function TotpScreen({ ciphers, onSelectCipher, onCopyText }: TotpScreenProps) {
  const [query, setQuery] = useState("");

  const items = useMemo(() => {
    const q = query.trim().toLowerCase();
    return ciphers
      .filter((c) => !c.deletedDate && Boolean(c.login?.totp))
      .filter((c) => {
        if (!q) return true;
        return `${c.name} ${c.login?.username ?? ""}`.toLowerCase().includes(q);
      })
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [ciphers, query]);

  return (
    <div className="flex-1 flex flex-col min-h-0">
      <header className="px-3.5 pt-3 pb-2.5 border-b border-[var(--border)] bg-[var(--surface)]">
        <div className="flex items-center justify-between mb-2">
          <h1 className="text-[15px] font-bold m-0">Trình xác thực 2FA (TOTP)</h1>
          <span className="text-[11.5px] font-semibold text-[var(--accent)]">
            {items.length} mã
          </span>
        </div>
        <div className="relative">
          <Search
            size={14}
            className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--text-muted)] pointer-events-none"
          />
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Lọc mã 2FA..."
            className="ov-input pl-8 py-1.5 text-[12.5px]"
          />
        </div>
      </header>

      <div className="flex-1 overflow-y-auto ov-scroll p-3 flex flex-col gap-2.5">
        {items.length === 0 ? (
          <div className="flex-1 flex flex-col items-center justify-center text-center px-6 py-10">
            <div className="w-12 h-12 rounded-[14px] bg-[var(--surface-elevated)] flex items-center justify-center text-[var(--text-muted)] mb-3">
              <ShieldCheck size={22} />
            </div>
            <p className="text-[14px] font-semibold m-0">Chưa có mã TOTP nào</p>
            <p className="text-[12px] text-[var(--text-secondary)] mt-1">
              Mở hoặc tạo tài khoản đăng nhập rồi dán khóa Base32 hoặc liên kết `otpauth://` vào ô 2FA.
            </p>
          </div>
        ) : (
          items.map((item) => (
            <div key={item.id ?? item.name} className="ov-card p-3 flex flex-col gap-1.5">
              <button
                type="button"
                onClick={() => onSelectCipher(item)}
                className="flex items-center gap-2.5 bg-transparent border-0 p-0 text-left cursor-pointer"
              >
                <BrandAvatar name={item.name} type={item.type} size="sm" />
                <div className="min-w-0 flex-1">
                  <div className="text-[13px] font-bold truncate text-[var(--text-primary)]">
                    {item.name}
                  </div>
                  <div className="text-[11.5px] text-[var(--text-secondary)] truncate">
                    {item.login?.username || "2FA Authenticator"}
                  </div>
                </div>
              </button>
              <TotpRing
                secretOrUri={item.login!.totp!}
                onCopy={(code) => {
                  void onCopyText(code, `Đã sao chép mã 2FA của ${item.name}`);
                }}
              />
            </div>
          ))
        )}
      </div>
    </div>
  );
}

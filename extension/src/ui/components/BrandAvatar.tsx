import { CipherType } from "@openvault/crypto-core";
import { CreditCard, FileText, UserSquare2 } from "lucide-react";

const PALETTE: [string, string][] = [
  ["#0A84FF", "#0455BF"],
  ["#6366F1", "#4338CA"],
  ["#0EA5E9", "#0369A1"],
  ["#10B981", "#047857"],
  ["#F59E0B", "#B45309"],
  ["#EC4899", "#BE185D"],
  ["#8B5CF6", "#6D28D9"],
  ["#14B8A6", "#0F766E"],
];

function hashIndex(text: string): number {
  let hash = 0;
  for (let i = 0; i < text.length; i++) {
    hash = (hash * 31 + text.charCodeAt(i)) >>> 0;
  }
  return hash % PALETTE.length;
}

interface BrandAvatarProps {
  name: string;
  type: CipherType;
  size?: "sm" | "md" | "lg";
}

export function BrandAvatar({ name, type, size = "md" }: BrandAvatarProps) {
  const [from, to] = PALETTE[hashIndex(name || "O")]!;
  const dims =
    size === "sm"
      ? "w-8 h-8 rounded-[9px] text-[13px]"
      : size === "lg"
        ? "w-12 h-12 rounded-[14px] text-[19px]"
        : "w-9 h-9 rounded-[11px] text-[14px]";

  const iconSize = size === "lg" ? 20 : size === "sm" ? 15 : 17;

  return (
    <div
      className={`${dims} shrink-0 flex items-center justify-center font-bold text-white select-none shadow-xs`}
      style={{ background: `linear-gradient(135deg, ${from}, ${to})` }}
      aria-hidden="true"
    >
      {type === CipherType.SecureNote ? (
        <FileText size={iconSize} strokeWidth={2.2} />
      ) : type === CipherType.Card ? (
        <CreditCard size={iconSize} strokeWidth={2.2} />
      ) : type === CipherType.Identity ? (
        <UserSquare2 size={iconSize} strokeWidth={2.2} />
      ) : (
        (name.trim()[0] ?? "O").toUpperCase()
      )}
    </div>
  );
}

import { CipherType } from "@openvault/crypto-core";
import { CreditCard, FileText, UserSquare2 } from "lucide-react";
import { useState } from "react";
import { parseHostname } from "../../shared/domain-match.js";

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
  uri?: string | null;
  showFavicon?: boolean;
  size?: "sm" | "md" | "lg";
  inverted?: boolean;
}

export function BrandAvatar({
  name,
  type,
  uri,
  showFavicon = true,
  size = "md",
  inverted = false,
}: BrandAvatarProps) {
  const [faviconFailed, setFaviconFailed] = useState(false);

  const dims =
    size === "sm"
      ? "w-7 h-7 rounded-[7px] text-[12px]"
      : size === "lg"
        ? "w-11 h-11 rounded-[12px] text-[18px]"
        : "w-8 h-8 rounded-[8px] text-[13px]";

  const iconSize = size === "lg" ? 20 : size === "sm" ? 14 : 16;
  const host = type === CipherType.Login && showFavicon ? parseHostname(uri) : null;
  const faviconUrl =
    host && host !== "localhost" && !/^\d+\.\d+\.\d+\.\d+$/.test(host)
      ? `https://icons.duckduckgo.com/ip3/${encodeURIComponent(host)}.ico`
      : null;

  if (faviconUrl && !faviconFailed) {
    return (
      <div
        className={`${dims} shrink-0 flex items-center justify-center overflow-hidden border select-none`}
        style={{
          background: "#ffffff",
          borderColor: inverted ? "rgba(255,255,255,0.35)" : "var(--border)",
        }}
        aria-hidden="true"
      >
        <img
          src={faviconUrl}
          alt=""
          className={size === "lg" ? "w-7 h-7 object-contain" : "w-5 h-5 object-contain"}
          loading="lazy"
          referrerPolicy="no-referrer"
          onError={() => setFaviconFailed(true)}
        />
      </div>
    );
  }

  const [from, to] = PALETTE[hashIndex(name || "O")]!;

  return (
    <div
      className={`${dims} shrink-0 flex items-center justify-center font-bold text-white select-none`}
      style={{
        background: inverted
          ? "rgba(255, 255, 255, 0.2)"
          : `linear-gradient(135deg, ${from}, ${to})`,
      }}
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

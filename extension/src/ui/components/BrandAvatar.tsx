import { CipherType, SecureNoteType, type CipherField } from "@openvault/crypto-core";
import { BookOpen, CreditCard, FileText, IdCard, Package, UserSquare2 } from "lucide-react";
import { useEffect, useState } from "react";
import { parseHostname } from "../../shared/domain-match.js";
import { getCustomIconDataUrl } from "../../shared/media.js";

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
  secureNoteType?: number | null;
  uri?: string | null;
  fields?: CipherField[] | null;
  customIconDataUrl?: string | null;
  showFavicon?: boolean;
  size?: "sm" | "md" | "lg";
  inverted?: boolean;
}

/**
 * 3-Tier OpenVault Item Icon (synchronized with Android v0.7.0+):
 * 1. Custom E2EE `_ov_icon` image uploaded by user (96x96 WebP synced across Android & Chrome)
 * 2. High-resolution Google S2 website favicon (128px, rejecting 16x16 generic globe fallbacks)
 * 3. Crisp gradient category / monogram fallback
 */
export function BrandAvatar({
  name,
  type,
  secureNoteType = 0,
  uri,
  fields,
  customIconDataUrl,
  showFavicon = true,
  size = "md",
  inverted = false,
}: BrandAvatarProps) {
  const [faviconFailed, setFaviconFailed] = useState(false);

  const resolvedCustomIcon = customIconDataUrl ?? getCustomIconDataUrl(fields);
  const host = showFavicon ? parseHostname(uri) : null;
  const faviconUrl =
    host && host !== "localhost" && !/^\d+\.\d+\.\d+\.\d+$/.test(host)
      ? `https://www.google.com/s2/favicons?domain=${encodeURIComponent(host)}&sz=128`
      : null;

  useEffect(() => {
    setFaviconFailed(false);
  }, [faviconUrl]);

  const dims =
    size === "sm"
      ? "w-8 h-8 rounded-[8px] text-[13px]"
      : size === "lg"
        ? "w-12 h-12 rounded-[13px] text-[20px]"
        : "w-9 h-9 rounded-[9px] text-[14.5px]";

  const iconSize = size === "lg" ? 22 : size === "sm" ? 15 : 18;

  if (resolvedCustomIcon) {
    return (
      <div
        className={`${dims} shrink-0 flex items-center justify-center overflow-hidden border select-none`}
        style={{
          background: "var(--surface-subtle)",
          borderColor: inverted ? "rgba(255,255,255,0.35)" : "var(--border)",
        }}
        aria-hidden="true"
      >
        <img
          src={resolvedCustomIcon}
          alt=""
          className="w-full h-full object-cover"
          loading="lazy"
        />
      </div>
    );
  }

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
          onLoad={(e) => {
            const el = e.currentTarget;
            // Reject 16x16 generic globe fallback so our crisp monogram renders instead
            if (el.naturalWidth <= 16 && el.naturalHeight <= 16) {
              setFaviconFailed(true);
            }
          }}
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
        secureNoteType === SecureNoteType.Software ? (
          <Package size={iconSize} strokeWidth={2.1} />
        ) : secureNoteType === SecureNoteType.Passport ? (
          <BookOpen size={iconSize} strokeWidth={2.1} />
        ) : secureNoteType === SecureNoteType.IdCard ? (
          <IdCard size={iconSize} strokeWidth={2.1} />
        ) : (
          <FileText size={iconSize} strokeWidth={2.1} />
        )
      ) : type === CipherType.Card ? (
        <CreditCard size={iconSize} strokeWidth={2.1} />
      ) : type === CipherType.Identity ? (
        <UserSquare2 size={iconSize} strokeWidth={2.1} />
      ) : (
        (name.trim()[0] ?? "O").toUpperCase()
      )}
    </div>
  );
}

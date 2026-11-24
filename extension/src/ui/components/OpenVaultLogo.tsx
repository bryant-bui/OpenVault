import { useId } from "react";

interface OpenVaultLogoProps {
  /**
   * - `"crest"`: Canonical Cobalt squircle (`#0C74F2 -> #0558C9`) wrapping the precision Vault Dial.
   * - `"dial"`: Circular metallic silver-bevel Cobalt Vault Dial (for the split Vault-Door Unlock screen).
   */
  variant?: "crest" | "dial";
  size?: number;
  /** Rotates the inner keyhole bolt clockwise 90deg on unlock animation. */
  rotated?: boolean;
  className?: string;
}

/**
 * Canonical OpenVault Brand Emblem synchronized identically across
 * Chrome Extension (React + PNG extension icons + Content Script) and Android (Jetpack Compose + XML drawables).
 */
export function OpenVaultLogo({
  variant = "crest",
  size = 40,
  rotated = false,
  className = "",
}: OpenVaultLogoProps) {
  const uid = useId().replace(/[^a-zA-Z0-9_-]/g, "");
  const squircleGradId = `ov-sq-${uid}`;
  const silverGradId = `ov-ag-${uid}`;
  const cobaltGradId = `ov-co-${uid}`;
  const centerGradId = `ov-ct-${uid}`;

  if (variant === "dial") {
    return (
      <svg
        width={size}
        height={size}
        viewBox="0 0 100 100"
        fill="none"
        className={className}
        aria-hidden="true"
      >
        <defs>
          <linearGradient id={silverGradId} x1="8" y1="8" x2="92" y2="92" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="#F8FAFC" />
            <stop offset="50%" stopColor="#CBD5E1" />
            <stop offset="100%" stopColor="#E2E8F0" />
          </linearGradient>
          <linearGradient id={cobaltGradId} x1="50" y1="10" x2="50" y2="90" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="#1A56DB" />
            <stop offset="52%" stopColor="#0C74F2" />
            <stop offset="100%" stopColor="#60A5FA" />
          </linearGradient>
          <linearGradient id={centerGradId} x1="32" y1="32" x2="68" y2="68" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="#FFFFFF" />
            <stop offset="100%" stopColor="#E2E8F0" />
          </linearGradient>
        </defs>

        {/* Outer polished silver/white ring */}
        <circle cx="50" cy="50" r="46" fill={`url(#${silverGradId})`} />
        {/* Inner Cobalt-to-Sky bevel ring */}
        <circle cx="50" cy="50" r="38" fill={`url(#${cobaltGradId})`} />
        {/* Subtle sky halo ring inside Cobalt */}
        <circle cx="50" cy="50" r="31.5" stroke="#93C5FD" strokeOpacity="0.45" strokeWidth="2.8" />
        {/* Recessed bright center cylinder disc */}
        <circle cx="50" cy="50" r="26.5" fill={`url(#${centerGradId})`} />

        {/* Rotating notched dark-navy keyhole bolt (0deg -> 90deg clockwise on unlock) */}
        <g
          style={{
            transformOrigin: "50px 50px",
            transform: rotated ? "rotate(90deg)" : "rotate(0deg)",
            transition: "transform 270ms cubic-bezier(0.22, 1, 0.36, 1)",
          }}
        >
          <rect x="44" y="33" width="12" height="34" rx="3.4" fill="#0F172A" />
          {/* Signature lock notches (upper-left & lower-right) */}
          <path d="M43.5 44.5 L49.5 48.5 L43.5 52 Z" fill={`url(#${centerGradId})`} />
          <path d="M56.5 49 L50.5 53 L56.5 56.5 Z" fill={`url(#${centerGradId})`} />
        </g>
      </svg>
    );
  }

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 100 100"
      fill="none"
      className={className}
      aria-hidden="true"
    >
      <defs>
        <linearGradient id={squircleGradId} x1="0" y1="0" x2="100" y2="100" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#0C74F2" />
          <stop offset="100%" stopColor="#0558C9" />
        </linearGradient>
        <linearGradient id={silverGradId} x1="16" y1="16" x2="84" y2="84" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#FFFFFF" />
          <stop offset="55%" stopColor="#E2E8F0" />
          <stop offset="100%" stopColor="#F1F5F9" />
        </linearGradient>
        <linearGradient id={cobaltGradId} x1="50" y1="22" x2="50" y2="78" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#1A56DB" />
          <stop offset="55%" stopColor="#0C74F2" />
          <stop offset="100%" stopColor="#60A5FA" />
        </linearGradient>
        <linearGradient id={centerGradId} x1="36" y1="36" x2="64" y2="64" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#FFFFFF" />
          <stop offset="100%" stopColor="#E5ECF6" />
        </linearGradient>
      </defs>

      {/* Canonical Cobalt Squircle */}
      <rect x="4" y="4" width="92" height="92" rx="24" fill={`url(#${squircleGradId})`} />
      {/* Precision Vault Dial */}
      <circle cx="50" cy="50" r="34" fill={`url(#${silverGradId})`} />
      <circle cx="50" cy="50" r="27.5" fill={`url(#${cobaltGradId})`} />
      <circle cx="50" cy="50" r="22.8" stroke="#93C5FD" strokeOpacity="0.45" strokeWidth="2.1" />
      <circle cx="50" cy="50" r="19.2" fill={`url(#${centerGradId})`} />

      {/* Notched dark-navy keyhole bolt */}
      <g
        style={{
          transformOrigin: "50px 50px",
          transform: rotated ? "rotate(90deg)" : "rotate(0deg)",
          transition: "transform 270ms cubic-bezier(0.22, 1, 0.36, 1)",
        }}
      >
        <rect x="45.6" y="37.6" width="8.8" height="24.8" rx="2.5" fill="#0F172A" />
        <path d="M45.2 46 L49.5 48.8 L45.2 51.5 Z" fill={`url(#${centerGradId})`} />
        <path d="M54.8 49.2 L50.5 52 L54.8 54.6 Z" fill={`url(#${centerGradId})`} />
      </g>
    </svg>
  );
}

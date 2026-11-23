interface ColorPasswordProps {
  value: string;
  revealed: boolean;
  large?: boolean;
}

export function ColorPassword({ value, revealed, large = false }: ColorPasswordProps) {
  if (!revealed) {
    return (
      <span className="font-mono-code tracking-[0.18em] text-[13px] select-none text-[var(--text-secondary)]">
        ••••••••••••
      </span>
    );
  }

  return (
    <span
      className={`font-mono-code break-all ${
        large ? "text-[19px] leading-relaxed tracking-[0.06em]" : "text-[13.5px] leading-snug"
      }`}
    >
      {Array.from(value).map((ch, idx) => {
        if (/[0-9]/.test(ch)) {
          return (
            <span key={idx} className="pw-digit">
              {ch}
            </span>
          );
        }
        if (/[^a-zA-Z0-9]/.test(ch)) {
          return (
            <span key={idx} className="pw-symbol">
              {ch}
            </span>
          );
        }
        return <span key={idx}>{ch}</span>;
      })}
    </span>
  );
}

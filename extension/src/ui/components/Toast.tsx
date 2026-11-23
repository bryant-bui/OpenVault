import { CheckCircle2, AlertCircle, X } from "lucide-react";

export interface ToastMessage {
  id: number;
  text: string;
  tone: "info" | "success" | "error";
}

interface ToastProps {
  toast: ToastMessage | null;
  onDismiss: () => void;
}

export function Toast({ toast, onDismiss }: ToastProps) {
  return (
    <div
      role="region"
      aria-label="Thông báo"
      className="pointer-events-none fixed inset-x-3 bottom-3 z-50 flex justify-center"
    >
      <p className="sr-only">Thông báo trạng thái OpenVault</p>
      <div aria-live="polite" className="w-full max-w-[356px]">
        {toast ? (
          <div
            className="pointer-events-auto flex items-center gap-2.5 px-3.5 py-2.5 rounded-[12px] border shadow-lg text-[12.5px] font-medium"
            style={{
              background: "var(--surface)",
              borderColor:
                toast.tone === "error"
                  ? "var(--danger)"
                  : toast.tone === "success"
                    ? "var(--success)"
                    : "var(--border-strong)",
            }}
          >
            {toast.tone === "error" ? (
              <AlertCircle size={15} className="shrink-0 text-[var(--danger)]" />
            ) : (
              <CheckCircle2 size={15} className="shrink-0 text-[var(--success)]" />
            )}
            <span className="flex-1 text-[var(--text-primary)] leading-snug">{toast.text}</span>
            <button
              type="button"
              onClick={onDismiss}
              className="ov-icon-btn w-6 h-6 shrink-0"
              aria-label="Đóng thông báo"
            >
              <X size={13} />
            </button>
          </div>
        ) : null}
      </div>
    </div>
  );
}

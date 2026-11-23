import { useEffect, useId, useRef } from "react";

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  cancelLabel?: string;
  destructive?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Native <dialog> modal following modern-web-guidance `dialog`:
 * - Uses showModal() (no redundant role="dialog" / aria-modal)
 * - Safe action (Cancel) receives initial autofocus for destructive prompts
 * - Reads dialog.returnValue on close and immediately resets it to ""
 */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  cancelLabel = "Hủy",
  destructive = true,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    const handleClose = () => {
      const value = dialog.returnValue;
      dialog.returnValue = "";
      if (value === "confirm") {
        onConfirm();
      } else {
        onCancel();
      }
    };

    dialog.addEventListener("close", handleClose);
    return () => dialog.removeEventListener("close", handleClose);
  }, [onConfirm, onCancel]);

  return (
    <dialog ref={dialogRef} className="ov-dialog" aria-labelledby={titleId}>
      <div className="p-5 flex flex-col gap-3">
        <h1 id={titleId} className="text-[15px] font-bold m-0 text-[var(--text-primary)]">
          {title}
        </h1>
        <p className="text-[13px] leading-relaxed m-0 text-[var(--text-secondary)]">
          {description}
        </p>
        <form method="dialog" className="flex items-center justify-end gap-2 mt-2">
          <button
            type="submit"
            value="cancel"
            autoFocus
            className="ov-btn-ghost px-3.5 py-2"
          >
            {cancelLabel}
          </button>
          <button
            type="submit"
            value="confirm"
            className={`px-3.5 py-2 rounded-[10px] font-semibold text-[13px] border-0 cursor-pointer text-white ${
              destructive
                ? "bg-[var(--danger)] hover:opacity-90"
                : "bg-[var(--accent)] hover:bg-[var(--accent-hover)]"
            }`}
          >
            {confirmLabel}
          </button>
        </form>
      </div>
    </dialog>
  );
}

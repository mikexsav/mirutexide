import { useEffect, useRef, type ReactNode } from "react";
import { X } from "lucide-react";
export default function StudioDialog({
  title,
  close,
  children,
  wide = false,
}: {
  title: string;
  close: () => void;
  children: ReactNode;
  wide?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    ref.current?.showModal();
    ref.current
      ?.querySelector<HTMLInputElement>('input:not([type="radio"]),textarea')
      ?.focus();
  }, []);
  return (
    <dialog
      ref={ref}
      className={`studio-dialog ${wide ? "wide" : ""}`}
      onCancel={close}
      aria-label={title}
      onClick={(e) => {
        if (e.target === e.currentTarget) close();
      }}
    >
      <header>
        <h2>{title}</h2>
        <button title="Закрыть окно" onClick={close}>
          <X size={20} />
        </button>
      </header>
      {children}
    </dialog>
  );
}

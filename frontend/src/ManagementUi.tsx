import { useEffect, useId, useRef, type ReactNode } from "react";
export function ManagementDialog({
  title,
  children,
  close,
}: {
  title: string;
  children: ReactNode;
  close: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null),
    id = useId();
  useEffect(() => {
    const previous = document.activeElement as HTMLElement;
    const dialog = ref.current;
    dialog?.showModal();
    return () => {
      dialog?.close();
      previous?.focus();
    };
  }, []);
  return (
    <dialog
      className="lab-dialog"
      ref={ref}
      aria-labelledby={id}
      onCancel={(e) => {
        e.preventDefault();
        close();
      }}
    >
      <h2 id={id}>{title}</h2>
      {children}
      <button type="button" className="lab-btn lab-secondary" onClick={close}>
        Chiudi
      </button>
    </dialog>
  );
}
export function Feedback({ error, notice }: { error: string; notice: string }) {
  return (
    <>
      {error && (
        <p role="alert" className="lab-error">
          {error}
        </p>
      )}
      {notice && (
        <p role="status" className="lab-notice">
          {notice}
        </p>
      )}
    </>
  );
}
export type AccountRow = {
  id: string;
  workspace_id: string;
  name: string;
  email: string;
  role: string;
  status: string;
  workspace_status: string;
  verified: boolean;
};
export function Credentials({
  value,
  close,
}: {
  value: { email: string; temporaryPassword: string };
  close: () => void;
}) {
  return (
    <ManagementDialog
      title="Credenziali da consegnare all’agente"
      close={close}
    >
      <p>
        La password viene mostrata soltanto in questa schermata. L’agente può
        cambiarla dal proprio profilo.
      </p>
      <dl className="lab-credentials">
        <dt>Email</dt>
        <dd>{value.email}</dd>
        <dt>Password iniziale</dt>
        <dd>
          <code>{value.temporaryPassword}</code>
        </dd>
      </dl>
      <p>Se la perdi, usa «Reset password» per generarne una nuova.</p>
    </ManagementDialog>
  );
}

// Report categories only: no form values, passwords, query strings or exception text.
export function watchClientActivity(send: (type: string) => void) {
  let last = 0;
  function report(type: string) {
    const now = Date.now();
    if (now - last < 1000) return;
    last = now;
    send(type);
  }
  const invalid = () => report("VALIDATION"),
    error = () => report("UI_ERROR"),
    rejection = () => report("UI_ERROR");
  window.addEventListener("invalid", invalid, true);
  window.addEventListener("error", error);
  window.addEventListener("unhandledrejection", rejection);
  return () => {
    window.removeEventListener("invalid", invalid, true);
    window.removeEventListener("error", error);
    window.removeEventListener("unhandledrejection", rejection);
  };
}

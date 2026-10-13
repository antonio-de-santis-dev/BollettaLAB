import { useEffect, useState } from "react";
import { NavLink } from "react-router-dom";
import { users, payments, useTask, errorMessage } from "./platform";
import { Feedback, ManagementDialog, type AccountRow } from "./ManagementUi";
function AdminNav() {
  return (
    <nav className="lab-admin-tabs" aria-label="Amministrazione">
      <NavLink end to="/app/admin">
        Profili utenti
      </NavLink>
      <NavLink to="/app/admin/log">Registro attività</NavLink>
    </nav>
  );
}
export function AdminUsers() {
  const task = useTask(),
    [rows, setRows] = useState<AccountRow[]>([]),
    [wallets, setWallets] = useState<
      {
        workspace_id: string;
        monthly_remaining: number;
        extra_remaining: number;
      }[]
    >([]),
    [search, setSearch] = useState(""),
    [role, setRole] = useState(""),
    [selected, setSelected] = useState<AccountRow | null>(null),
    [delta, setDelta] = useState(""),
    [reason, setReason] = useState(""),
    [remove, setRemove] = useState<AccountRow | null>(null),
    [resetLink, setResetLink] = useState("");
  async function load() {
    const [u, w] = await Promise.all([
      users.get("/admin/users"),
      payments.get("/admin/wallets"),
    ]);
    setRows(u.data);
    setWallets(w.data);
  }
  useEffect(() => {
    void task.run(load);
  }, []);
  const filtered = rows.filter(
    (u) =>
      `${u.name} ${u.email}`.toLowerCase().includes(search.toLowerCase()) &&
      (!role || role === u.role),
  );
  return (
    <main className="lab-page">
      <p className="lab-eyebrow">AMMINISTRAZIONE</p>
      <h1>Profili utenti.</h1>
      <AdminNav />
      <p>
        Gestisci accessi e quote. Per cercare azioni ed errori apri il Registro
        attività.
      </p>
      <Feedback error={task.error} notice={task.notice} />
      {resetLink && (
        <p className="lab-notice">
          Ambiente di prova:{" "}
          <a href={resetLink}>Apri il link di reimpostazione password</a>
        </p>
      )}
      <div className="lab-form-grid">
        <label className="lab-field">
          <span>Cerca nome o email</span>
          <input value={search} onChange={(e) => setSearch(e.target.value)} />
        </label>
        <label className="lab-field">
          <span>Ruolo dell’account</span>
          <select value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="">Tutti i ruoli</option>
            <option value="OWNER">Titolare impresa</option>
            <option value="PRIVATE">Privato</option>
            <option value="AGENT">Agente</option>
            <option value="ADMIN">Amministratore</option>
          </select>
        </label>
      </div>
      {task.busy && !rows.length ? (
        <p role="status">Caricamento utenti…</p>
      ) : (
        <div className="lab-table-wrap">
          <table className="lab-table">
            <thead>
              <tr>
                <th>Utente</th>
                <th>Ruolo</th>
                <th>Stato</th>
                <th>Saldo simulazioni</th>
                <th>Gestione</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((u) => {
                const w = wallets.find(
                  (x) => x.workspace_id === u.workspace_id,
                );
                return (
                  <tr key={u.id}>
                    <td>
                      <strong>{u.name}</strong>
                      <small>{u.email}</small>
                    </td>
                    <td>
                      {{
                        OWNER: "Titolare",
                        PRIVATE: "Privato",
                        AGENT: "Agente",
                        ADMIN: "Amministratore",
                      }[u.role] ?? u.role}
                    </td>
                    <td>
                      {u.status === "ACTIVE"
                        ? "Attivo"
                        : u.status === "BLOCKED"
                          ? "Bloccato"
                          : u.status}
                      <small>
                        Spazio:{" "}
                        {u.workspace_status === "ACTIVE"
                          ? "attivo"
                          : u.workspace_status}
                      </small>
                    </td>
                    <td>
                      {w
                        ? Number(w.monthly_remaining) +
                          Number(w.extra_remaining)
                        : "—"}
                    </td>
                    <td>
                      {u.role !== "ADMIN" && (
                        <div className="lab-actions">
                          <button
                            className="lab-btn lab-secondary"
                            disabled={task.busy}
                            onClick={() =>
                              task.run(async () => {
                                await users.patch("/admin/users/" + u.id, {
                                  blocked: u.status === "ACTIVE",
                                });
                                await load();
                              })
                            }
                          >
                            {u.status === "ACTIVE" ? "Blocca" : "Sblocca"}
                          </button>
                          <button
                            className="lab-btn lab-secondary"
                            onClick={() => setSelected(u)}
                          >
                            Quote
                          </button>
                          <button
                            className="lab-btn lab-secondary"
                            disabled={task.busy}
                            onClick={() =>
                              task.run(async () => {
                                const r = await users.post(
                                  "/admin/users/" + u.id + "/reset-password",
                                );
                                setResetLink(r.data.resetUrl ?? "");
                                task.setNotice(r.data.message);
                              })
                            }
                          >
                            Reset password
                          </button>
                          <button
                            className="lab-btn lab-destructive"
                            onClick={() => setRemove(u)}
                          >
                            Elimina
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          {!filtered.length && (
            <p className="lab-empty">Nessun account corrisponde ai filtri.</p>
          )}
        </div>
      )}
      {selected && (
        <ManagementDialog
          title={"Modifica quote · " + selected.name}
          close={() => setSelected(null)}
        >
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void task.run(async () => {
                await payments.post("/admin/wallets/" + selected.workspace_id, {
                  delta: Number(delta),
                  reason,
                });
                setSelected(null);
                setDelta("");
                setReason("");
                await load();
              });
            }}
          >
            <label className="lab-field">
              <span>Variazione simulazioni (positiva o negativa)</span>
              <input
                required
                type="number"
                step="1"
                value={delta}
                onChange={(e) => setDelta(e.target.value)}
              />
            </label>
            <label className="lab-field">
              <span>Motivazione</span>
              <input
                required
                maxLength={180}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </label>
            <button disabled={task.busy} className="lab-btn">
              Applica variazione
            </button>
          </form>
        </ManagementDialog>
      )}
      {remove && (
        <ManagementDialog
          title="Eliminare questo profilo?"
          close={() => setRemove(null)}
        >
          <p>
            {remove.email}. Per un titolare o privato saranno eliminati anche i
            dati del relativo account nei cinque servizi. L’operazione è
            definitiva.
          </p>
          <button
            disabled={task.busy}
            className="lab-btn lab-destructive"
            onClick={() =>
              task.run(async () => {
                await users.delete("/admin/users/" + remove.id);
                setRemove(null);
                await load();
              })
            }
          >
            Elimina definitivamente
          </button>
        </ManagementDialog>
      )}
    </main>
  );
}
type Log = {
  id: string;
  account_name: string;
  account_email: string;
  action: string;
  description: string;
  subject_id: string;
  status: "COMPLETED" | "WARNING" | "ERROR";
  service: string;
  created_at: string;
  request_id?: string;
  http_status?: number;
};
type Filters = {
  search: string;
  status: string;
  service: string;
  from: string;
  to: string;
};
const empty: Filters = {
  search: "",
  status: "",
  service: "",
  from: "",
  to: "",
};
const states = { COMPLETED: "Completato", WARNING: "Avviso", ERROR: "Errore" };
const actions: Record<string, string> = {
  LOGIN: "Accesso account",
  REGISTER: "Registrazione account",
  CREATE_AGENT: "Creazione agente",
  INVITE_AGENT: "Invito agente",
  OWNER_RESET_AGENT_PASSWORD: "Reset password agente",
  ADMIN_RESET_PASSWORD: "Reset password da admin",
  RESET_PASSWORD: "Password aggiornata",
  UPDATE_PROFILE: "Modifica profilo",
  BLOCK_USER: "Blocco account",
  UNBLOCK_USER: "Sblocco account",
  DELETE_AGENT: "Eliminazione agente",
  DELETE_WORKSPACE: "Eliminazione spazio account",
};
export function AdminLogs() {
  const [filters, setFilters] = useState<Filters>(empty),
    [applied, setApplied] = useState<Filters>(empty),
    [page, setPage] = useState(0),
    [reload, setReload] = useState(0),
    [data, setData] = useState<{ items: Log[]; total: number }>({
      items: [],
      total: 0,
    }),
    [loading, setLoading] = useState(true),
    [error, setError] = useState("");
  useEffect(() => {
    const c = new AbortController();
    setLoading(true);
    setError("");
    users
      .get("/admin/logs", {
        params: { ...applied, page, size: 30 },
        signal: c.signal,
      })
      .then((r) => {
        if (!c.signal.aborted) setData(r.data);
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(errorMessage(e));
      })
      .finally(() => {
        if (!c.signal.aborted) setLoading(false);
      });
    return () => c.abort();
  }, [applied, page, reload]);
  const change = (key: keyof Filters, value: string) =>
    setFilters({ ...filters, [key]: value });
  return (
    <main className="lab-page">
      <p className="lab-eyebrow">AMMINISTRAZIONE</p>
      <h1>Registro attività.</h1>
      <AdminNav />
      <p>
        Azioni ed errori dei servizi, con account, esito e data. Le credenziali
        e i token non sono registrati.
      </p>
      <form
        className="lab-card lab-log-filters"
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          setApplied({ ...filters });
        }}
      >
        <div className="lab-form-grid">
          <label className="lab-field">
            <span>Cerca account, azione o descrizione</span>
            <input
              maxLength={200}
              value={filters.search}
              onChange={(e) => change("search", e.target.value)}
            />
          </label>
          <label className="lab-field">
            <span>Stato dell’operazione</span>
            <select
              value={filters.status}
              onChange={(e) => change("status", e.target.value)}
            >
              <option value="">Tutti gli stati</option>
              {Object.entries(states).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          <label className="lab-field">
            <span>Servizio</span>
            <select
              value={filters.service}
              onChange={(e) => change("service", e.target.value)}
            >
              <option value="">Tutti i servizi</option>
              {["utenti", "pagamento", "luce", "gas", "luce-business"].map(
                (s) => (
                  <option key={s}>{s}</option>
                ),
              )}
            </select>
          </label>
          <label className="lab-field">
            <span>Data iniziale</span>
            <input
              type="date"
              value={filters.from}
              onChange={(e) => change("from", e.target.value)}
            />
          </label>
          <label className="lab-field">
            <span>Data finale</span>
            <input
              type="date"
              min={filters.from || undefined}
              value={filters.to}
              onChange={(e) => change("to", e.target.value)}
            />
          </label>
        </div>
        <div className="lab-actions">
          <button className="lab-btn">Applica filtri</button>
          <button
            type="button"
            className="lab-btn lab-secondary"
            onClick={() => {
              setFilters(empty);
              setApplied(empty);
              setPage(0);
              setReload((n) => n + 1);
            }}
          >
            Azzera filtri
          </button>
          <button
            type="button"
            className="lab-btn lab-secondary"
            onClick={() => setReload((n) => n + 1)}
          >
            Aggiorna registro
          </button>
        </div>
      </form>
      {error && (
        <p role="alert" className="lab-error">
          {error}
        </p>
      )}
      <p role="status">
        {loading ? "Caricamento attività…" : `${data.total} attività trovate`}
      </p>
      <div className="lab-table-wrap">
        <table className="lab-table lab-log-table">
          <thead>
            <tr>
              <th>Account</th>
              <th>Stato</th>
              <th>Azione</th>
              <th>Descrizione</th>
              <th>Data e ora</th>
            </tr>
          </thead>
          <tbody>
            {!loading &&
              data.items.map((l) => (
                <tr key={l.id}>
                  <td>
                    <strong>{l.account_name}</strong>
                    <small>{l.account_email}</small>
                  </td>
                  <td>
                    <span
                      className={`lab-state lab-state-${l.status.toLowerCase()}`}
                    >
                      {states[l.status]}
                    </span>
                  </td>
                  <td>
                    {actions[l.action] ?? l.action}
                    <small>{l.service}</small>
                  </td>
                  <td>
                    {actions[l.action] ??
                      l.description ??
                      l.subject_id ??
                      "Operazione registrata"}
                    {l.request_id && (
                      <details>
                        <summary>Riferimento tecnico</summary>
                        <small>
                          Richiesta: {l.request_id} · HTTP {l.http_status}
                        </small>
                      </details>
                    )}
                  </td>
                  <td>{new Date(l.created_at).toLocaleString("it-IT")}</td>
                </tr>
              ))}
          </tbody>
        </table>
        {!loading && !data.items.length && (
          <p className="lab-empty">Nessuna attività corrisponde ai filtri.</p>
        )}
      </div>
      <div className="lab-actions lab-pagination">
        <button
          className="lab-btn lab-secondary"
          disabled={loading || page === 0}
          onClick={() => setPage((p) => p - 1)}
        >
          Precedente
        </button>
        <span>
          Pagina {page + 1} di {Math.max(1, Math.ceil(data.total / 30))}
        </span>
        <button
          className="lab-btn lab-secondary"
          disabled={loading || (page + 1) * 30 >= data.total}
          onClick={() => setPage((p) => p + 1)}
        >
          Successiva
        </button>
      </div>
    </main>
  );
}

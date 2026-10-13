import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { users, payments, useAuth, useTask } from "./platform";
import {
  Feedback,
  Credentials,
  ManagementDialog,
  type AccountRow,
} from "./ManagementUi";
type Invitation = { name: string; email: string; expires_at: string };
type Secret = { email: string; temporaryPassword: string };
export default function CompanyPage() {
  const { wallet } = useAuth(),
    task = useTask(),
    [data, setData] = useState<{ agents: AccountRow[]; invites: Invitation[] }>(
      { agents: [], invites: [] },
    ),
    [usage, setUsage] = useState<{ user_id: string; simulations: number }[]>(
      [],
    ),
    [fields, setFields] = useState({ name: "", email: "" }),
    [credentials, setCredentials] = useState<Secret | null>(null),
    [reset, setReset] = useState<AccountRow | null>(null);
  async function load() {
    const [a, u] = await Promise.all([
      users.get("/company/agents"),
      payments.get("/company/usage"),
    ]);
    setData(a.data);
    setUsage(u.data);
  }
  useEffect(() => {
    void task.run(load);
  }, []);
  async function create(values: { name: string; email: string }) {
    const r = await users.post("/company/agents/create", values);
    setCredentials(r.data);
    setFields({ name: "", email: "" });
    task.setNotice(r.data.message);
    await load();
  }
  return (
    <main className="lab-page">
      <p className="lab-eyebrow">GESTIONE IMPRESA</p>
      <h1>Il tuo gruppo di lavoro.</h1>
      <p>
        {data.agents.length + data.invites.length} posti occupati su{" "}
        {wallet?.agentSeats ?? 2}. Gli agenti creati possono accedere subito; le
        simulazioni sono condivise con il titolare.
      </p>
      <Feedback error={task.error} notice={task.notice} />
      <div className="lab-table-wrap">
        <table className="lab-table">
          <thead>
            <tr>
              <th>Agente</th>
              <th>Email</th>
              <th>Stato</th>
              <th>Simulazioni salvate</th>
              <th>Gestione</th>
            </tr>
          </thead>
          <tbody>
            {data.agents.map((a) => (
              <tr key={a.id}>
                <td>{a.name}</td>
                <td>{a.email}</td>
                <td>
                  <span className="lab-state">
                    {a.status === "ACTIVE" ? "Attivo" : "Bloccato"}
                  </span>
                </td>
                <td>
                  {usage.find((u) => u.user_id === a.id)?.simulations ?? 0}
                </td>
                <td>
                  <div className="lab-actions">
                    <button
                      className="lab-btn lab-secondary"
                      disabled={task.busy}
                      onClick={() =>
                        task.run(async () => {
                          await users.patch("/company/agents/" + a.id, {
                            blocked: a.status === "ACTIVE",
                          });
                          await load();
                        })
                      }
                    >
                      {a.status === "ACTIVE" ? "Blocca" : "Sblocca"}
                    </button>
                    <button
                      className="lab-btn lab-secondary"
                      disabled={task.busy}
                      onClick={() => setReset(a)}
                    >
                      Reset password
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {data.invites.map((i) => (
              <tr key={i.email}>
                <td>{i.name}</td>
                <td>{i.email}</td>
                <td>Invito in attesa</td>
                <td>—</td>
                <td>
                  <button
                    className="lab-btn lab-secondary"
                    disabled={task.busy}
                    onClick={() => task.run(() => create(i))}
                  >
                    Attiva e genera password
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!data.agents.length && !data.invites.length && (
          <p className="lab-empty">
            Nessun agente presente. Crea il primo account qui sotto.
          </p>
        )}
      </div>
      <form
        className="lab-card lab-invite"
        onSubmit={(e) => {
          e.preventDefault();
          void task.run(() => create(fields));
        }}
      >
        <h2>Crea un account agente</h2>
        <p>
          Occupa un solo posto. Dopo la creazione riceverai email e password
          iniziale da consegnare all’agente.
        </p>
        <div className="lab-form-grid">
          <label className="lab-field">
            <span>Nome e cognome</span>
            <input
              required
              maxLength={200}
              value={fields.name}
              onChange={(e) => setFields({ ...fields, name: e.target.value })}
            />
          </label>
          <label className="lab-field">
            <span>Email dell’agente</span>
            <input
              type="email"
              required
              maxLength={254}
              value={fields.email}
              onChange={(e) => setFields({ ...fields, email: e.target.value })}
            />
          </label>
        </div>
        <button className="lab-btn" disabled={task.busy}>
          Crea agente e genera password
        </button>
      </form>
      <Link to="/app/storico">Consulta lo storico degli agenti →</Link>
      {credentials && (
        <Credentials value={credentials} close={() => setCredentials(null)} />
      )}{" "}
      {reset && (
        <ManagementDialog
          title={"Reimpostare la password di " + reset.name + "?"}
          close={() => setReset(null)}
        >
          <p>
            L’agente dovrà usare la nuova password. Le sessioni aperte saranno
            chiuse.
          </p>
          <button
            className="lab-btn"
            disabled={task.busy}
            onClick={() =>
              task.run(async () => {
                const r = await users.post(
                  "/company/agents/" + reset.id + "/reset-password",
                );
                setReset(null);
                setCredentials(r.data);
                task.setNotice(r.data.message);
              })
            }
          >
            Genera nuova password
          </button>
        </ManagementDialog>
      )}
    </main>
  );
}

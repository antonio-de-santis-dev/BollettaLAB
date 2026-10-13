import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { users, useAuth, useTask } from "./platform";
import { Feedback } from "./ManagementUi";
type Profile = {
  name: string;
  email: string;
  role: string;
  companyName: string;
  vatNumber: string;
  address: string;
};
export default function ProfilePage() {
  const task = useTask(),
    { refresh } = useAuth(),
    navigate = useNavigate();
  const [data, setData] = useState<Profile | null>(null),
    [currentPassword, setCurrent] = useState(""),
    [newPassword, setNext] = useState(""),
    [verification, setVerification] = useState("");
  useEffect(() => {
    void task.run(async () =>
      setData((await users.get<Profile>("/profile")).data),
    );
  }, []);
  function field(key: keyof Profile, label: string, type = "text", max = 200) {
    return (
      <label className="lab-field">
        <span>{label}</span>
        <input
          type={type}
          required
          maxLength={max}
          value={data?.[key] ?? ""}
          onChange={(e) =>
            setData(data ? { ...data, [key]: e.target.value } : null)
          }
        />
      </label>
    );
  }
  return (
    <main className="lab-page">
      <Link className="lab-back" to="/app">
        ← Torna alla dashboard
      </Link>
      <p className="lab-eyebrow">IL TUO ACCOUNT</p>
      <h1>Il tuo profilo.</h1>
      <p>
        Aggiorna i dati personali e la password. Per cambiare email o password
        serve la password attuale.
      </p>
      <Feedback error={task.error} notice={task.notice} />
      {verification && (
        <p className="lab-notice">
          <a href={verification}>Verifica la nuova email</a>
        </p>
      )}
      {!data ? (
        <p role="status">
          {task.busy ? "Caricamento profilo…" : "Profilo non disponibile."}
        </p>
      ) : (
        <form
          className="lab-card"
          onSubmit={(e) => {
            e.preventDefault();
            void task.run(async () => {
              const { data: r } = await users.put("/profile", {
                ...data,
                currentPassword,
                newPassword,
              });
              setCurrent("");
              setNext("");
              setVerification(r.verificationUrl ?? "");
              if (r.loginRequired) {
                await refresh();
                navigate("/accedi");
              } else {
                await refresh();
                task.setNotice(r.message);
              }
            });
          }}
        >
          <h2>Dati personali</h2>
          <div className="lab-form-grid">
            {field("name", "Nome e cognome")}
            {field("email", "Email", "email", 254)}
          </div>
          {data.role === "OWNER" && (
            <>
              <h2>Dati dell’impresa</h2>
              <div className="lab-form-grid">
                {field("companyName", "Ragione sociale")}
                {field("vatNumber", "Partita IVA", "text", 11)}
                {field("address", "Indirizzo dell’impresa", "text", 300)}
              </div>
            </>
          )}
          <fieldset className="lab-profile-password">
            <legend>Accesso e password</legend>
            <p>
              Lascia vuota la nuova password se vuoi mantenere quella attuale.
            </p>
            <div className="lab-form-grid">
              <label className="lab-field">
                <span>Password attuale</span>
                <input
                  type="password"
                  autoComplete="current-password"
                  value={currentPassword}
                  onChange={(e) => setCurrent(e.target.value)}
                  required={!!newPassword}
                />
              </label>
              <label className="lab-field">
                <span>Nuova password</span>
                <input
                  type="password"
                  autoComplete="new-password"
                  minLength={10}
                  maxLength={72}
                  value={newPassword}
                  onChange={(e) => setNext(e.target.value)}
                />
                <small>
                  Da 10 caratteri, massimo 72 byte. Dopo la modifica dovrai
                  accedere di nuovo.
                </small>
              </label>
            </div>
          </fieldset>
          <button className="lab-btn" disabled={task.busy}>
            {task.busy ? "Salvataggio…" : "Salva profilo"}
          </button>
        </form>
      )}
    </main>
  );
}

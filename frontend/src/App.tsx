import { lazy, Suspense, useEffect, useRef, useState, ReactNode } from "react";
import {
  Link,
  Navigate,
  Route,
  Routes,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import {
  Zap,
  Flame,
  Building2,
  ArrowUpRight,
  LogOut,
  History,
  Users,
  ShieldCheck,
  ArrowLeft,
} from "lucide-react";
import Welcome from "./Welcome";
import { AccountForm, Billing } from "./AccountPages";
import { useAuth, useTask, users, payments, errorMessage } from "./platform";
import axios from "axios";
const AdminUsers = lazy(() =>
    import("./AdminPages").then((m) => ({ default: m.AdminUsers })),
  ),
  AdminLogs = lazy(() =>
    import("./AdminPages").then((m) => ({ default: m.AdminLogs })),
  ),
  CompanyPage = lazy(() => import("./CompanyPage")),
  ProfilePage = lazy(() => import("./ProfilePage"));
const Luce = lazy(() => import("./modules/luce/App")),
  Business = lazy(() => import("./modules/luce-business/App")),
  Gas = lazy(() => import("./modules/gas/App"));
const ResultLuce = lazy(
    () => import("./modules/luce/components/RisultatoView"),
  ),
  ResultBusiness = lazy(
    () => import("./modules/luce-business/components/RisultatoView"),
  ),
  ResultGas = lazy(() =>
    import("./modules/gas/pages/GasPages").then((m) => ({
      default: m.Risultato,
    })),
  ),
  ResultAdvanced = lazy(
    () => import("./modules/luce-business/business/Risultato"),
  );
const simulatorNames: Record<string, string> = {
  luce: "Progetto Luce",
  gas: "Progetto Gas",
  "luce-business": "Progetto Luce Business",
};
export function Modal({
  title,
  children,
  close,
}: {
  title: string;
  children: ReactNode;
  close: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement;
    ref.current?.showModal();
    return () => {
      ref.current?.close();
      previous?.focus();
    };
  }, []);
  return (
    <dialog
      className="lab-dialog"
      ref={ref}
      onCancel={(e) => {
        e.preventDefault();
        close();
      }}
    >
      <h2>{title}</h2>
      {children}
      <button className="lab-btn lab-secondary" onClick={close}>
        Chiudi
      </button>
    </dialog>
  );
}
function Guard({
  children,
  role,
  simulation = false,
}: {
  children: ReactNode;
  role?: string;
  simulation?: boolean;
}) {
  const { user, wallet, loading, walletError, refreshWallet } = useAuth();
  if (loading)
    return (
      <p className="lab-loading" role="status">
        Caricamento del tuo spazio…
      </p>
    );
  if (!user) return <Navigate to="/accedi" replace />;
  if (role && user.role !== role) return <Navigate to="/app" replace />;
  if (simulation && user.role !== "ADMIN" && !wallet)
    return (
      <main className="lab-page">
        <p role="status">{walletError || "Caricamento del piano…"}</p>
        {walletError && (
          <button className="lab-btn" onClick={() => refreshWallet()}>
            Riprova
          </button>
        )}
      </main>
    );
  if (simulation && user.role !== "ADMIN" && !wallet?.active)
    return <Navigate to="/app/piano" replace />;
  return <>{children}</>;
}
function Layout({ children }: { children: ReactNode }) {
  const { user, wallet, refresh } = useAuth(),
    navigate = useNavigate(),
    [quota, setQuota] = useState(false);
  useEffect(() => {
    const show = () => setQuota(true);
    window.addEventListener("lab:credits-empty", show);
    return () => window.removeEventListener("lab:credits-empty", show);
  }, []);
  async function logout() {
    try {
      await users.post("/auth/logout");
      await refresh();
      navigate("/");
    } catch (e) {
      window.alert(errorMessage(e));
    }
  }
  return (
    <>
      <header className="lab-app-nav">
        <Link className="lab-logo" to="/app">
          bolletta<span>LAB</span>
          <i />
        </Link>
        <nav aria-label="Il tuo spazio">
          <Link to="/app">Dashboard</Link>
          <Link to="/app/storico">
            <History size={16} />
            <span>Storico</span>
          </Link>
          {user?.role === "OWNER" && (
            <Link to="/app/impresa">
              <Users size={16} />
              <span>Impresa</span>
            </Link>
          )}
          {user?.role === "ADMIN" && (
            <Link to="/app/admin">
              <ShieldCheck size={16} />
              <span>Admin</span>
            </Link>
          )}
        </nav>
        <div className="lab-app-user">
          {wallet && (
            <Link className="lab-quota" to="/app/piano">
              {wallet.remaining} <span>simulazioni</span>
            </Link>
          )}
          <Link className="lab-user-name" to="/app/profilo">
            {user?.name}
          </Link>
          <button className="lab-icon" onClick={logout} aria-label="Esci">
            <LogOut size={19} />
          </button>
        </div>
      </header>
      <Suspense
        fallback={
          <p className="lab-loading" role="status">
            Caricamento pagina…
          </p>
        }
      >
        {children}
      </Suspense>
      {quota && (
        <Modal
          title="Hai esaurito le simulazioni"
          close={() => setQuota(false)}
        >
          <p>
            Lo storico e i PDF restano disponibili.{" "}
            {user?.role === "AGENT"
              ? "Chiedi al titolare di aggiornare il piano dell’impresa."
              : "Modifica il piano o acquista simulazioni aggiuntive per continuare."}
          </p>
          <Link
            className="lab-btn"
            to="/app/piano"
            onClick={() => setQuota(false)}
          >
            Modifica piano o compra simulazioni
          </Link>
        </Modal>
      )}
    </>
  );
}
function Dashboard() {
  const { user, wallet } = useAuth();
  return (
    <main className="lab-page">
      <p className="lab-eyebrow">
        {user?.companyName ?? "IL TUO SPAZIO DI CONSULENZA"}
      </p>
      <h1>
        Ciao, {user?.name.split(" ")[0]}.<br />
        <em>Da dove cominciamo?</em>
      </h1>
      <p>
        Un unico spazio per i tuoi confronti. Scegli il simulatore e lavora sui
        consumi reali.
      </p>
      {user?.role !== "ADMIN" && (
        <div className="lab-balance">
          <div>
            <span>Simulazioni disponibili</span>
            <strong>{wallet?.remaining ?? "—"}</strong>
          </div>
          <p>
            {wallet?.active
              ? `${wallet.monthlyRemaining} mensili + ${wallet.extraRemaining} aggiuntive. Rinnovo da effettuare alla scadenza del periodo.`
              : "Completa la verifica email e attiva il piano per iniziare."}
          </p>
          <Link className="lab-btn lab-secondary" to="/app/piano">
            Gestisci piano
          </Link>
        </div>
      )}
      <div className="lab-grid3">
        {[
          {
            id: "luce",
            Icon: Zap,
            desc: "Offerte, bollette, parametri e confronti per la casa.",
          },
          {
            id: "gas",
            Icon: Flame,
            desc: "Consumi Smc, tariffe e confronti gas domestico.",
          },
          {
            id: "luce-business",
            Icon: Building2,
            desc: "La consulenza energetica per attività e imprese.",
          },
        ].map(({ id, Icon, desc }) => (
          <Link className="lab-product-card" key={id} to={"/simulatori/" + id}>
            <div>
              <Icon size={28} />
              <ArrowUpRight size={20} />
            </div>
            <h2>{simulatorNames[id]}</h2>
            <p>{desc}</p>
            <span className="lab-text-link">Apri simulatore</span>
          </Link>
        ))}
      </div>
      <div className="lab-dashboard-foot">
        <p>
          Ogni nuovo confronto salvato usa una simulazione. Riaprire un
          risultato o scaricare il PDF non riduce il saldo.
        </p>
        <Link to="/app/storico">Ritrova le tue simulazioni →</Link>
      </div>
    </main>
  );
}
type Row = Record<string, any>;
function HistoryPage() {
  const [rows, setRows] = useState<Row[]>([]),
    [search, setSearch] = useState(""),
    task = useTask();
  useEffect(() => {
    void task.run(async () => setRows((await payments.get("/history")).data));
  }, []);
  return (
    <main className="lab-page">
      <p className="lab-eyebrow">TUTTI I TUOI CONFRONTI</p>
      <h1>Il lavoro resta qui.</h1>
      <p>Riapri una simulazione e scarica il PDF senza consumare crediti.</p>
      <label className="lab-field">
        <span>Cerca per simulatore o autore</span>
        <input value={search} onChange={(e) => setSearch(e.target.value)} />
      </label>
      {task.error && (
        <p role="alert" className="lab-error">
          {task.error}
        </p>
      )}
      {task.busy ? (
        <p role="status">Caricamento…</p>
      ) : (
        <div className="lab-table-wrap">
          <table className="lab-table">
            <thead>
              <tr>
                <th>Simulatore</th>
                <th>Autore</th>
                <th>Impresa</th>
                <th>Data</th>
                <th>Risultato</th>
              </tr>
            </thead>
            <tbody>
              {rows
                .filter((r) =>
                  JSON.stringify(r)
                    .toLowerCase()
                    .includes(search.toLowerCase()),
                )
                .map((r) => (
                  <tr key={r.id}>
                    <td>{simulatorNames[r.simulator]}</td>
                    <td>{r.author_name}</td>
                    <td>{r.company_name || "—"}</td>
                    <td>{new Date(r.created_at).toLocaleString("it-IT")}</td>
                    <td>
                      <Link
                        to={`/app/storico/${r.simulator}/${r.result_id}?kind=${r.operation_kind}`}
                      >
                        Apri #{r.result_id}
                      </Link>
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
          {!rows.length && (
            <p className="lab-empty">
              Nessuna simulazione salvata. Inizia da uno dei tre simulatori.
            </p>
          )}
        </div>
      )}
    </main>
  );
}
function Detail() {
  const [params] = useSearchParams(),
    { service, id } = useParams(),
    [result, setResult] = useState<any>(null),
    [error, setError] = useState(""),
    [loading, setLoading] = useState(true),
    [retry, setRetry] = useState(0);
  const kind = params.get("kind"),
    from = params.get("from"),
    advanced = kind === "business" && service === "luce-business";
  useEffect(() => {
    const c = new AbortController();
    setResult(null);
    setError("");
    setLoading(true);
    if (!service || !simulatorNames[service] || !id || !/^\d+$/.test(id)) {
      setError("Confronto non valido");
      setLoading(false);
      return;
    }
    const api = axios.create({ baseURL: "/api/" + service, timeout: 30000 });
    api
      .get((advanced ? "/business/simulazioni/" : "/confronti/") + id, {
        signal: c.signal,
      })
      .then((r) => {
        if (!c.signal.aborted) setResult(r.data);
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(errorMessage(e));
      })
      .finally(() => {
        if (!c.signal.aborted) setLoading(false);
      });
    return () => c.abort();
  }, [service, id, advanced, retry]);
  const back =
    from === "advanced" && service === "luce-business"
      ? "/simulatori/luce-business/storico-simulazioni"
      : from === service
        ? `/simulatori/${service}/storico`
        : "/app/storico";
  return (
    <main className={"lab-detail lab-module-" + service}>
      <Link className="lab-back" to={back}>
        <ArrowLeft size={17} />
        Torna allo storico simulazioni
      </Link>
      {loading && <p role="status">Caricamento confronto…</p>}
      {error && (
        <>
          <p role="alert" className="lab-error">
            {error}
          </p>
          <button
            className="lab-btn lab-secondary"
            onClick={() => setRetry((n) => n + 1)}
          >
            Riprova
          </button>
        </>
      )}
      <Suspense fallback={<p role="status">Caricamento risultato…</p>}>
        {result &&
          (service === "gas" ? (
            <ResultGas saved={result} />
          ) : service === "luce-business" ? (
            advanced ? (
              <ResultAdvanced simulazione={result} />
            ) : (
              <ResultBusiness confronto={result} />
            )
          ) : (
            <ResultLuce confronto={result} />
          ))}
      </Suspense>
    </main>
  );
}
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Welcome />} />
      {(
        ["login", "register", "forgot", "reset", "invite", "verify"] as const
      ).map((mode, index) => (
        <Route
          key={mode}
          path={
            [
              "/accedi",
              "/registrati",
              "/recupera-password",
              "/reimposta-password",
              "/invito",
              "/verifica-email",
            ][index]
          }
          element={<AccountForm mode={mode} />}
        />
      ))}
      <Route
        path="/app"
        element={
          <Guard>
            <Layout>
              <Dashboard />
            </Layout>
          </Guard>
        }
      />
      <Route
        path="/app/profilo"
        element={
          <Guard>
            <Layout>
              <ProfilePage />
            </Layout>
          </Guard>
        }
      />
      <Route
        path="/app/admin/log"
        element={
          <Guard role="ADMIN">
            <Layout>
              <AdminLogs />
            </Layout>
          </Guard>
        }
      />
      <Route
        path="/app/piano"
        element={
          <Guard>
            <Layout>
              <Billing />
            </Layout>
          </Guard>
        }
      />
      <Route
        path="/app/storico"
        element={
          <Guard>
            <Layout>
              <HistoryPage />
            </Layout>
          </Guard>
        }
      />
      <Route
        path="/app/storico/:service/:id"
        element={
          <Guard>
            <Layout>
              <Detail />
            </Layout>
          </Guard>
        }
      />
      <Route
        path="/app/impresa"
        element={
          <Guard role="OWNER">
            <Layout>
              <CompanyPage />
            </Layout>
          </Guard>
        }
      />
      <Route
        path="/app/admin"
        element={
          <Guard role="ADMIN">
            <Layout>
              <AdminUsers />
            </Layout>
          </Guard>
        }
      />
      {[
        { path: "luce", Component: Luce },
        { path: "gas", Component: Gas },
        { path: "luce-business", Component: Business },
      ].map(({ path, Component }) => (
        <Route
          key={path}
          path={"/simulatori/" + path + "/*"}
          element={
            <Guard simulation>
              <Layout>
                <Suspense
                  fallback={
                    <p className="lab-loading">Caricamento simulatore…</p>
                  }
                >
                  <Component />
                </Suspense>
              </Layout>
            </Guard>
          }
        />
      ))}
      <Route
        path="*"
        element={
          <main className="lab-page">
            <h1>Pagina non trovata</h1>
            <Link to="/">Torna alla Welcome</Link>
          </main>
        }
      />
    </Routes>
  );
}

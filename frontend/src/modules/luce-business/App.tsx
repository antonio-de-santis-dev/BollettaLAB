import "./styles.css";
import { useEffect, useState } from "react";
import { NavLink, Routes, Route, useLocation, Link } from "react-router-dom";
import {
  Zap,
  ArrowLeftRight,
  Tags,
  FileText,
  SlidersHorizontal,
  History,
  Menu,
  X,
  ArrowUpRight,
} from "lucide-react";
import "./business/business.css";
import Simulatore from "./business/Simulatore";
import Tariffe from "./business/Tariffe";
import StoricoBusiness from "./business/Storico";
import ConfrontoPage from "./pages/ConfrontoPage";
import OffertePage from "./pages/OffertePage";
import BollettePage from "./pages/BollettePage";
import ParametriPage from "./pages/ParametriPage";
import FontiPage from "./pages/FontiPage";
import StoricoPage from "./pages/StoricoPage";
import ImpostazioniPdfPage from "./pages/ImpostazioniPdfPage";

const nav = [
  { to: "/", label: "Confronto", icon: ArrowLeftRight },
  { to: "/bollette", label: "Bollette clienti", icon: FileText },
  { to: "/offerte", label: "Offerte", icon: Tags },
  { to: "/parametri", label: "Parametri gestore", icon: SlidersHorizontal },
  { to: "/fonti", label: "Fonti ufficiali", icon: SlidersHorizontal },
  { to: "/impostazioni-pdf", label: "Impostazioni PDF", icon: FileText },
  { to: "/storico", label: "Storico", icon: History },
];
export default function App() {
  const [open, setOpen] = useState(false);
  const actualLocation=useLocation(); const location={...actualLocation,pathname:actualLocation.pathname.slice(25) || "/"};
  useEffect(() => {
    setOpen(false);
    document.title = `${nav.find((n) => n.to === location.pathname)?.label ?? "Pagina non trovata"} · Progetto Luce Business`;
    document.querySelector<HTMLElement>("#main-content")?.focus();
    window.scrollTo(0, 0);
  }, [location.pathname]);
  return (
    <div className="lab-module-luce-business app business-shell">
      <a href="#main-content" className="skip-link">
        Vai al contenuto
      </a>
      <aside
        id="business-navigation"
        className={`sidebar ${open ? "open" : ""}`}
      >
        {open && (
          <button
            className="icon-button business-nav-close"
            aria-label="Chiudi menu"
            onClick={() => setOpen(false)}
          >
            <X size={20} />
          </button>
        )}
        <Link to="/simulatori/luce-business" className="brand" aria-label="Progetto Luce Business">
          <span>
            <Zap size={23} fill="currentColor" />
          </span>
          <div>
            progetto
            <strong>
              luce<span>.</span>
            </strong>
          </div>
        </Link>
        <span className="business-tag">BUSINESS</span>
        <p className="nav-caption">PROGETTO LUCE BUSINESS</p>
        <nav aria-label="Navigazione principale">
          {nav.map((n) => (
            <NavLink
              key={n.to}
              to={"/simulatori/luce-business"+(n.to === "/" ? "" : n.to)}
              end
              className={({ isActive }) =>
                isActive ? "nav-link active" : "nav-link"
              }
            >
              <n.icon size={19} />
              <span>{n.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div className="mini-sun" aria-hidden="true">
            ✳
          </div>
          <h3>
            Ogni scelta
            <br />
            merita chiarezza.
          </h3>
          <p>
            Consumi reali.
            <br />
            Calcoli trasparenti.
          </p>
          <span>PROGETTO LUCE BUSINESS</span>
        </div>
      </aside>
      {open && (
        <button
          className="nav-scrim"
          aria-label="Chiudi navigazione"
          onClick={() => setOpen(false)}
        />
      )}
      <div
        className={
          location.pathname === "/impostazioni-pdf"
            ? "workspace workspace-pdf"
            : "workspace"
        }
      >
        <header className="topbar">
          <div className="breadcrumb">
            <button
              className="icon-button mobile-menu"
              aria-label="Apri menu"
              aria-controls="business-navigation"
              aria-expanded={open}
              onClick={() => setOpen(!open)}
            >
              {open ? <X size={21} /> : <Menu size={21} />}
            </button>
            <span>Business</span>
            <span className="slash">/</span>
            <strong>
              {nav.find((n) => n.to === location.pathname)?.label ?? "404"}
            </strong>
          </div>
          <div className="topbar-right">
            <span className="topbar-label">
              <span className="status-dot" />
              Consulenza alle imprese
            </span>
            <div className="avatar" aria-label="Area consulente">
              PL
            </div>
          </div>
        </header>
        <main id="main-content" tabIndex={-1}>
          <Routes>
            <Route index element={<ConfrontoPage />} />
            <Route path="offerte" element={<OffertePage />} />
            <Route path="bollette" element={<BollettePage />} />
            <Route path="parametri" element={<ParametriPage />} />
            <Route path="impostazioni-pdf" element={<ImpostazioniPdfPage />} />
            <Route path="fonti" element={<FontiPage />} />
            <Route path="storico" element={<StoricoPage />} />
            <Route path="simulatore" element={<Simulatore />} />
            <Route path="tariffe" element={<Tariffe />} />
            <Route path="storico-simulazioni" element={<StoricoBusiness />} />
            <Route
              path="*"
              element={
                <div className="empty">
                  <h1>Pagina non trovata</h1>
                  <Link to="/simulatori/luce-business" className="button primary">
                    Torna al confronto
                    <ArrowUpRight size={18} />
                  </Link>
                </div>
              }
            />
          </Routes>
        </main>
        <footer className="workspace-footer">
          <span>
            Progetto Luce Business · Strumenti per consulenti energetici
          </span>
          <span>Calcolo parametrico · Prima versione</span>
        </footer>
      </div>
    </div>
  );
}

import "../fonti.css";
import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  ArrowRight,
  FileText,
  Tags,
  SlidersHorizontal,
  ArrowLeftRight,
  LoaderCircle,
} from "lucide-react";
import { api, messaggioErrore, useLista } from "../api";
import {
  consumo,
  numero,
  type Bolletta,
  type Offerta,
  type Confronto,
} from "../domain";
import { Titolo, Caricamento, Errore } from "../components/ui";
import RisultatoView from "../components/RisultatoView";

export default function ConfrontoPage() {
  const bollette = useLista<Bolletta>("/bollette"),
    offerte = useLista<Offerta>("/offerte");
  const [bolletta, setBolletta] = useState(""),
    [offerta, setOfferta] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [risultato, setRisultato] = useState<Confronto | null>(null);
  const attive = offerte.data.filter((o) => o.attiva),
    selected = bollette.data.find((b) => String(b.id) === bolletta);
  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setRisultato(null);
    try {
      const { data } = await api.post<Confronto>("/confronti", {
        bollettaId: bolletta,
        offertaId: offerta,
        usaFontiUfficiali: false,
      });
      setRisultato(data);
    } catch (e) {
      setError(messaggioErrore(e));
    } finally {
      setBusy(false);
    }
  }
  const steps = [
    {
      icon: FileText,
      n: "01",
      title: "La bolletta del cliente",
      text: "Inserisci consumi e importo fatturato.",
      link: "/simulatori/luce-business/bollette",
      cta: "Gestisci bollette",
      ready: bollette.data.length > 0,
    },
    {
      icon: Tags,
      n: "02",
      title: "L’offerta da proporre",
      text: "Configura prezzi, spread e PCV.",
      link: "/simulatori/luce-business/offerte",
      cta: "Gestisci offerte",
      ready: attive.length > 0,
    },
    {
      icon: SlidersHorizontal,
      n: "03",
      title: "I parametri del gestore",
      text: "Definisci le componenti del calcolo.",
      link: "/simulatori/luce-business/parametri",
      cta: "Configura parametri",
      ready: false,
    },
  ];
  return (
    <>
      <Titolo
        eyebrow="Il tuo spazio di consulenza"
        title="Il risparmio della tua impresa, nero su bianco."
        description="Confronta la bolletta del cliente con la tua offerta. Stessi consumi, ogni voce spiegata."
      />
      <div className="intro-banner">
        <div className="sun-disc">
          <ArrowLeftRight size={34} />
        </div>
        <div>
          <span className="eyebrow">DAI NUMERI A UNA SCELTA CONSAPEVOLE</span>
          <h2>
            Un confronto trasparente.
            <br />
            Una proposta più semplice.
          </h2>
          <p>
            Due sorgenti separate: dati reali del cliente e condizioni della tua
            offerta.
          </p>
        </div>
        <span className="banner-stamp">
          PROGETTO
          <br />
          <strong>LUCE BUSINESS</strong>
        </span>
      </div>
      {bollette.loading || offerte.loading ? (
        <Caricamento />
      ) : bollette.error || offerte.error ? (
        <Errore
          message={bollette.error || offerte.error}
          onRetry={() => {
            bollette.reload();
            offerte.reload();
          }}
        />
      ) : (
        <>
          <div className="steps">
            {steps.map((s) => (
              <Link key={s.n} to={s.link} className="step-card">
                <div className="step-top">
                  <s.icon size={22} />
                  <span>{s.n}</span>
                </div>
                <h3>{s.title}</h3>
                <p>{s.text}</p>
                <div className="step-link">
                  {s.cta}
                  <ArrowRight size={16} />
                </div>
                {s.ready && (
                  <span className="badge green">Dati disponibili</span>
                )}
              </Link>
            ))}
          </div>
          <form className="panel compare-form" onSubmit={submit}>
            <div className="panel-top">
              <div>
                <h2>Avvia un nuovo confronto</h2>
                <p className="muted">
                  Il risultato verrà salvato nello storico.
                </p>
              </div>
              <span className="badge">Luce</span>
            </div>
            <div className="compare-inputs">
              <label className="field">
                <span>Bolletta del cliente</span>
                <select
                  required
                  value={bolletta}
                  onChange={(e) => {
                    setBolletta(e.target.value);
                    setRisultato(null);
                  }}
                >
                  <option value="">Seleziona una bolletta</option>
                  {bollette.data.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.dati.cliente} · {b.dati.mesi[0].mese}
                    </option>
                  ))}
                </select>
              </label>
              <div className="compare-symbol" aria-hidden="true">
                <ArrowLeftRight size={22} />
              </div>
              <label className="field">
                <span>Offerta proposta</span>
                <select
                  required
                  value={offerta}
                  onChange={(e) => {
                    setOfferta(e.target.value);
                    setRisultato(null);
                  }}
                >
                  <option value="">Seleziona un’offerta attiva</option>
                  {attive.map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.nomeOfferta} · {o.nomeFornitore}
                    </option>
                  ))}
                </select>
              </label>
              <button className="button primary" type="submit" disabled={busy}>
                {busy ? (
                  <LoaderCircle size={18} className="spin" />
                ) : (
                  <ArrowRight size={18} />
                )}{" "}
                {busy ? "Calcolo…" : "Calcola confronto"}
              </button>
            </div>
            <p className="help">
              Il confronto business usa i parametri gestore configurati e il PUN
              mensile della bolletta.{" "}
              <Link to="/simulatori/luce-business/fonti">Consulta le fonti ufficiali</Link>.
            </p>
            {selected && (
              <p className="help">
                {numero(consumo(selected.dati), 3)} kWh ·{" "}
                {selected.dati.mesi.length} mesi · {selected.dati.potenzaKw} kW
              </p>
            )}
            {(bollette.data.length === 0 || attive.length === 0) && (
              <p className="help">
                Prima di procedere, aggiungi almeno una bolletta e un’offerta
                attiva.
              </p>
            )}
            {error && <Errore message={error} />}
          </form>
        </>
      )}
      <p className="help">
        <Link to="/simulatori/luce-business/simulatore">
          Simulazione dettagliata con tariffe mensili e IVA per voce
        </Link>
      </p>
      {risultato && <RisultatoView confronto={risultato} />}
    </>
  );
}

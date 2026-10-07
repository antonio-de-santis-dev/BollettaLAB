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
  const [usaFonti, setUsaFonti] = useState(false),
    [categoria, setCategoria] = useState("DOMESTICO_RESIDENTE"),
    [conferma, setConferma] = useState(false);
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
        usaFontiUfficiali: usaFonti,
        categoria: usaFonti ? categoria : null,
        confermaStandard: usaFonti && conferma,
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
      link: "/simulatori/luce/bollette",
      cta: "Gestisci bollette",
      ready: bollette.data.length > 0,
    },
    {
      icon: Tags,
      n: "02",
      title: "L’offerta da proporre",
      text: "Configura prezzi, spread e PCV.",
      link: "/simulatori/luce/offerte",
      cta: "Gestisci offerte",
      ready: attive.length > 0,
    },
    {
      icon: SlidersHorizontal,
      n: "03",
      title: "I parametri del gestore",
      text: "Definisci le componenti del calcolo.",
      link: "/simulatori/luce/parametri",
      cta: "Configura parametri",
      ready: false,
    },
  ];
  return (
    <>
      <Titolo
        eyebrow="Il tuo spazio di consulenza"
        title="Il risparmio, nero su bianco."
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
          <strong>LUCE</strong>
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
            <details className="fonti-choice">
              <summary>Parametri del confronto</summary>
              <label>
                <input
                  type="checkbox"
                  checked={usaFonti}
                  onChange={(e) => {
                    setUsaFonti(e.target.checked);
                    setRisultato(null);
                  }}
                />{" "}
                Usa i parametri mensili delle fonti ufficiali e le mie
                correzioni
              </label>
              {usaFonti && (
                <>
                  <label className="field">
                    <span>Profilo della fornitura</span>
                    <select
                      value={categoria}
                      onChange={(e) => {
                        setCategoria(e.target.value);
                        setRisultato(null);
                      }}
                    >
                      <option value="DOMESTICO_RESIDENTE">
                        Domestico residente
                      </option>
                      <option value="DOMESTICO_NON_RESIDENTE">
                        Domestico non residente
                      </option>
                    </select>
                  </label>
                  <label>
                    <input
                      type="checkbox"
                      required
                      checked={conferma}
                      onChange={(e) => setConferma(e.target.checked)}
                    />{" "}
                    Ho verificato che il dispacciamento standard CdispD sia
                    compatibile con l’offerta e non sia già incluso nel prezzo.
                    Ho verificato che IVA e accisa siano applicabili a questo
                    cliente e ai suoi consumi.
                  </label>
                  <p className="help">
                    I dati devono essere disponibili per ogni mese. Per
                    residenti fino a 3 kW verifica l’accisa.{" "}
                    <Link to="/simulatori/luce/fonti">Controlla e modifica le fonti</Link>.
                  </p>
                </>
              )}
              {!usaFonti && (
                <p className="help">
                  Il confronto usa il profilo manuale e il PUN della bolletta.
                </p>
              )}
            </details>
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
      {risultato && <RisultatoView confronto={risultato} />}
    </>
  );
}

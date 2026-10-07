import {useAuth} from "../../../platform";
import "../fonti.css";
import { useEffect, useState, type FormEvent } from "react";
import { RefreshCw, Save, RotateCcw, History } from "lucide-react";
import { api, messaggioErrore, useLista } from "../api";
import { Titolo, Errore, Caricamento } from "../components/ui";
import { numero } from "../domain";
interface Stato {
  automatico: boolean;
  gmeConfigurato: boolean;
  inCorso: boolean;
  periodi: string[];
  esiti: {
    creatoIl: string;
    fonte: string;
    successo: boolean;
    messaggio: string;
  }[];
}
interface Revisione {
  id: number;
  creatoIl: string;
  motivo: string;
  contenuto: string;
}
import { nomi, type DatoFonte } from "../fonti";
export type { DatoFonte } from "../fonti";
export default function FontiPage() {
 const {user}=useAuth();const canEdit=user?.role==="ADMIN";
  const [stato, setStato] = useState<Stato | null>(null),
    [periodo, setPeriodo] = useState(
      new Date()
        .toLocaleDateString("sv-SE", { timeZone: "Europe/Rome" })
        .slice(0, 7),
    ),
    [categoria, setCategoria] = useState("INDICE"),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [busy, setBusy] = useState(false),
    [edit, setEdit] = useState<DatoFonte | null>(null),
    [valore, setValore] = useState(""),
    [motivo, setMotivo] = useState(""),
    [codice, setCodice] = useState("PUN_F0"),
    [revisioni, setRevisioni] = useState<Revisione[] | null>(null),
    [titoloStorico, setTitoloStorico] = useState(""),
    [periodoGme, setPeriodoGme] = useState("");
  const lista = useLista<DatoFonte>(
    `/fonti/dati?periodo=${periodo}&categoria=${categoria}`,
  );
  useEffect(() => {
    const c = new AbortController();
    api
      .get<Stato>("/fonti", { signal: c.signal })
      .then((r) => setStato(r.data))
      .catch((e) => {
        if (!c.signal.aborted) setError(messaggioErrore(e));
      });
    return () => c.abort();
  }, []);
  useEffect(() => {
    if (lista.loading || categoria === "ORIGINALE") return;
    const disponibili = Object.keys(nomi).filter(
      (k) =>
        (categoria === "INDICE"
          ? k.startsWith("PUN_")
          : !k.startsWith("PUN_")) && !lista.data.some((d) => d.codice === k),
    );
    setCodice((c) => (disponibili.includes(c) ? c : (disponibili[0] ?? "")));
  }, [lista.data, lista.loading, categoria]);
  function reset() {
    setEdit(null);
    setValore("");
    setMotivo("");
    setRevisioni(null);
    setError("");
    setNotice("");
  }
  async function sincronizza() {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const { data } = await api.post<Stato>("/fonti/sincronizza", null, {
        params: periodoGme ? { periodoGme } : undefined,
        timeout: 120000,
      });
      setStato(data);
      lista.reload();
      setNotice(
        data.inCorso
          ? "Un aggiornamento è già in corso. Ricarica tra poco."
          : "Aggiornamento concluso. Controlla gli esiti delle fonti qui sotto.",
      );
    } catch (e) {
      setError(messaggioErrore(e));
    } finally {
      setBusy(false);
    }
  }
  async function salva(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await api.put("/fonti/dati", {
        codice: edit?.codice ?? codice,
        periodo,
        categoria,
        valore: valore.replace(",", "."),
        versione: edit?.versione ?? null,
        motivo,
      });
      setEdit(null);
      setValore("");
      setMotivo("");
      lista.reload();
      setNotice(
        "Correzione salvata. Sarà usata nei nuovi confronti con questo profilo e mese.",
      );
      setStato((await api.get<Stato>("/fonti")).data);
    } catch (e) {
      setError(messaggioErrore(e));
    } finally {
      setBusy(false);
    }
  }
  async function ripristina() {
    if (!edit) return;
    setBusy(true);
    setError("");
    try {
      await api.post(`/fonti/dati/${edit.id}/ripristina`, {
        versione: edit.versione,
        motivo,
      });
      setEdit(null);
      setValore("");
      setMotivo("");
      lista.reload();
      setNotice("Valore ufficiale ripristinato.");
    } catch (e) {
      setError(messaggioErrore(e));
    } finally {
      setBusy(false);
    }
  }
  async function storico(d: DatoFonte) {
    setBusy(true);
    setError("");
    try {
      setRevisioni(
        (await api.get<Revisione[]>(`/fonti/dati/${d.id}/revisioni`)).data,
      );
      setTitoloStorico(nomi[d.codice] ?? d.codice);
    } catch (e) {
      setError(messaggioErrore(e));
    } finally {
      setBusy(false);
    }
  }
  const selezionabili = Object.keys(nomi).filter((k) =>
    categoria === "INDICE" ? k.startsWith("PUN_") : !k.startsWith("PUN_"),
  );
  return (
    <div className="fonti-page">
      <p className="notice">
        Business: consulta gli indici PUN e i riferimenti pubblicati. I profili
        domestici non vengono applicati al confronto; trasporto, oneri e
        fiscalità si configurano in Parametri gestore per la fornitura
        dell’impresa.
      </p>
      <Titolo
        eyebrow="Dati aggiornati, scelte trasparenti"
        title="Fonti ufficiali"
        description="Importa i parametri pubblicati, verifica il mese e conserva le tue correzioni manuali."
      />
      <section className="panel fonti-sync">
        <div className="panel-top">
          <div>
            <h2>Aggiornamento dei dati</h2>
            <p className="muted">
              {stato
                ? `Automatico: ${stato.automatico ? "ogni 24 ore e all’avvio" : "disattivato"} · GME: ${stato.gmeConfigurato ? "credenziali configurate" : "da configurare sul server"}`
                : "Caricamento dello stato…"}
            </p>
          </div>
          <button
            className="button primary"
            disabled={busy || !canEdit}
            onClick={sincronizza}
          >
            <RefreshCw size={18} className={busy ? "spin" : ""} />
            {busy ? "Operazione in corso…" : "Aggiorna adesso"}
          </button>
        </div>
        <p>
          Il Portale Offerte fornisce i parametri pubblicati del mese e lo
          storico PUN F0 disponibile. GME aggiunge F1, F2, F3 e F23 dopo la
          configurazione delle credenziali. I mesi mancanti restano da
          completare.
        </p>
        {stato?.gmeConfigurato && (
          <label className="field">
            <span>Mese GME da importare (facoltativo)</span>
            <input
              type="month"
              value={periodoGme}
              onChange={(e) => setPeriodoGme(e.target.value)}
            />
            <small>Se vuoto, importa l’ultimo mese concluso.</small>
          </label>
        )}
        {stato?.esiti.length ? (
          <details>
            <summary>Ultimi aggiornamenti</summary>
            <ul className="fonti-esiti">
              {stato.esiti.map((e, i) => (
                <li key={i}>
                  <strong>
                    {e.fonte} · {e.successo ? "Completato" : "Da verificare"}
                  </strong>
                  <span>{new Date(e.creatoIl).toLocaleString("it-IT")}</span>
                  <p>{e.messaggio}</p>
                </li>
              ))}
            </ul>
          </details>
        ) : (
          <p className="help">
            Nessun aggiornamento registrato. Premi “Aggiorna adesso” per
            iniziare.
          </p>
        )}
      </section>
      {error && <Errore message={error} />}{" "}
      {notice && (
        <p role="status" className="fonti-notice">
          {notice}
        </p>
      )}
      <section className="panel fonti-panel">
        <div className="panel-top">
          <div>
            <h2>Parametri per mese</h2>
            <p className="muted">
              Le correzioni prevalgono sui valori importati. Lo storico dei
              confronti conserva i dati usati.
            </p>
          </div>
        </div>
        <div className="form-grid">
          <label className="field">
            <span>Mese</span>
            <input
              aria-label="Mese"
              type="month"
              disabled={busy}
              value={periodo}
              required
              onChange={(e) => {
                setPeriodo(e.target.value);
                reset();
              }}
            />
            <small>
              Disponibili: {stato?.periodi.slice(0, 6).join(", ") || "nessuno"}
            </small>
          </label>
          <label className="field">
            <span>Profilo</span>
            <select
              aria-label="Profilo"
              value={categoria}
              onChange={(e) => {
                setCategoria(e.target.value);
                setCodice(
                  e.target.value === "INDICE"
                    ? "PUN_F0"
                    : "coefficientePerdite",
                );
                reset();
              }}
              disabled={busy}
            >
              <option value="INDICE">Indici PUN</option>
              <option value="ORIGINALE">
                Tutti i valori pubblicati (sola lettura)
              </option>
            </select>
          </label>
        </div>
        {categoria.startsWith("DOMESTICO") && (
          <p className="help">
            Dispacciamento: standard CdispD del Portale Offerte, da verificare
            con il contratto. Per residenti fino a 3 kW l’accisa richiede una
            correzione motivata per esenzioni e scaglioni. Perdite e IVA sono
            frazioni: 0,10 = 10%.
          </p>
        )}
        {lista.loading ? (
          <Caricamento />
        ) : lista.error ? (
          <Errore message={lista.error} onRetry={lista.reload} />
        ) : lista.data.length === 0 ? (
          <p className="empty">
            Nessun dato per questo mese e profilo. Importa i dati o aggiungi i
            valori manualmente.
          </p>
        ) : (
          <div className="table-scroll">
            <table className="fonti-table">
              <thead>
                <tr>
                  <th>Parametro</th>
                  <th>Valore usato</th>
                  <th>Valore ufficiale</th>
                  <th>Fonte</th>
                  <th>Azioni</th>
                </tr>
              </thead>
              <tbody>
                {lista.data.map((d) => (
                  <tr key={d.id}>
                    <td data-label="Parametro">
                      <strong>{nomi[d.codice] ?? d.codice}</strong>
                      <small>{d.unita}</small>
                    </td>
                    <td data-label="Valore usato">
                      {numero(d.valore, 8)}{" "}
                      {d.valoreManuale !== null && (
                        <span className="badge">Manuale</span>
                      )}
                    </td>
                    <td data-label="Valore ufficiale">
                      {d.valoreUfficiale === null
                        ? "—"
                        : numero(d.valoreUfficiale, 8)}
                    </td>
                    <td data-label="Fonte">
                      {d.url ? (
                        <a href={d.url} target="_blank" rel="noreferrer">
                          {d.fonte === "PORTALE_OFFERTE"
                            ? "Portale Offerte"
                            : d.fonte}
                        </a>
                      ) : (
                        "Manuale"
                      )}
                      <small>
                        {d.pubblicatoIl ? `Pubblicato ${d.pubblicatoIl}` : ""}
                      </small>
                    </td>
                    <td data-label="Azioni">
                      <div className="fonti-actions">
                        {canEdit && categoria !== "ORIGINALE" && (
                          <button
                            className="button secondary"
                            disabled={busy}
                            onClick={() => {
                              setEdit(d);
                              setValore(d.valore);
                              setMotivo("");
                              setNotice("");
                            }}
                          >
                            Modifica
                          </button>
                        )}
                        <button
                          className="icon-button"
                          aria-label={`Storico ${nomi[d.codice] ?? d.codice}`}
                          disabled={busy}
                          onClick={() => storico(d)}
                        >
                          <History size={18} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      {canEdit && categoria !== "ORIGINALE" && (
        <form className="panel fonti-panel fonti-form" onSubmit={salva}>
          <div className="panel-top">
            <h2>
              {edit
                ? `Modifica: ${nomi[edit.codice]}`
                : "Aggiungi un parametro manuale"}
            </h2>
            {edit && (
              <button
                type="button"
                className="button secondary"
                onClick={reset}
                disabled={busy}
              >
                Annulla modifica
              </button>
            )}
          </div>
          <div className="form-grid">
            {!edit && (
              <label className="field">
                <span>Parametro</span>
                <select
                  aria-label="Parametro"
                  value={codice}
                  onChange={(e) => setCodice(e.target.value)}
                >
                  {selezionabili
                    .filter((c) => !lista.data.some((d) => d.codice === c))
                    .map((c) => (
                      <option key={c} value={c}>
                        {nomi[c]}
                      </option>
                    ))}
                </select>
              </label>
            )}
            <label className="field">
              <span>Valore {edit ? `(${edit.unita})` : ""}</span>
              <input
                required
                inputMode="decimal"
                value={valore}
                onChange={(e) => setValore(e.target.value)}
                pattern="[0-9]+([.,][0-9]{1,8})?"
              />
            </label>
            <label className="field">
              <span>Motivo della correzione / fonte verificata</span>
              <input
                required
                maxLength={500}
                value={motivo}
                onChange={(e) => setMotivo(e.target.value)}
                placeholder="Es. aliquota effettiva verificata sulla bolletta"
              />
            </label>
          </div>
          <div className="fonti-actions">
            <button
              className="button primary"
              disabled={
                busy ||
                lista.loading ||
                (!edit &&
                  (!codice || lista.data.some((d) => d.codice === codice)))
              }
            >
              <Save size={18} />
              Salva valore manuale
            </button>
            {edit?.valoreManuale !== null && edit?.valoreUfficiale != null && (
              <button
                type="button"
                className="button secondary"
                disabled={busy || !motivo.trim()}
                onClick={ripristina}
              >
                <RotateCcw size={18} />
                Ripristina valore ufficiale
              </button>
            )}
          </div>
        </form>
      )}
      {revisioni && (
        <section className="panel fonti-panel">
          <div className="panel-top">
            <h2>Storico · {titoloStorico}</h2>
            <button
              className="button secondary"
              onClick={() => setRevisioni(null)}
            >
              Chiudi
            </button>
          </div>
          {revisioni.length === 0 ? (
            <p>Nessuna revisione.</p>
          ) : (
            <ul className="fonti-esiti">
              {revisioni.map((r) => {
                const d = JSON.parse(r.contenuto) as DatoFonte;
                return (
                  <li key={r.id}>
                    <strong>{r.motivo}</strong>
                    <span>{new Date(r.creatoIl).toLocaleString("it-IT")}</span>
                    <p>
                      Valore usato: {numero(d.valore, 8)} ·{" "}
                      {d.valoreManuale !== null
                        ? "Correzione manuale"
                        : "Valore ufficiale"}{" "}
                      · {d.fonte}
                    </p>
                  </li>
                );
              })}
            </ul>
          )}
        </section>
      )}
    </div>
  );
}

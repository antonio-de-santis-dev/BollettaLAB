import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, Plus, Trash2, Building2 } from "lucide-react";
import { api, messaggioErrore, useLista } from "../api";
import { Campo, Decimale, Errore, Caricamento, Titolo } from "../components/ui";
import {
  nuovoInput,
  nuovoMese,
  meseSuccessivo,
  consumo,
  type Input,
  type Mese,
  type Profilo,
  type Simulazione,
} from "./model";
import Risultato from "./Risultato";
export default function Simulatore() {
  const profili = useLista<Profilo>("/business/profili");
  const [form, setForm] = useState<Input>(nuovoInput);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [risultato, setRisultato] = useState<Simulazione | null>(null);
  const set = <K extends keyof Input>(k: K, v: Input[K]) => {
    setForm((f) => ({ ...f, [k]: v }));
    setRisultato(null);
  };
  const mese = <K extends keyof Mese>(index: number, k: K, v: Mese[K]) =>
    set(
      "mesi",
      form.mesi.map((m, i) => (i === index ? { ...m, [k]: v } : m)),
    );
  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setRisultato(null);
    try {
      const { data } = await api.post<Simulazione>(
        "/business/simulazioni",
        form,
      );
      setRisultato(data);
      window.setTimeout(
        () =>
          document
            .querySelector("#business-result")
            ?.scrollIntoView({ behavior: "smooth", block: "start" }),
        20,
      );
    } catch (e) {
      setError(messaggioErrore(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Titolo
        eyebrow="Energia per la tua impresa"
        title="I costi, voce per voce."
        description="Simula la fornitura business sugli stessi consumi. Confronta gli importi e conserva ogni scenario."
      />
      <div className="business-hero">
        <div>
          <span className="eyebrow">DAL CONSUMO ALLA DECISIONE</span>
          <h2>
            Una bolletta più leggibile.
            <br />
            Una scelta più consapevole.
          </h2>
          <p>
            Quote energia, potenza, oneri e IVA. Ogni componente ha la sua
            regola.
          </p>
        </div>
        <Building2 size={66} strokeWidth={1} />
      </div>
      {profili.loading ? (
        <Caricamento />
      ) : profili.error ? (
        <Errore message={profili.error} onRetry={profili.reload} />
      ) : !profili.data.length ? (
        <div className="empty">
          <h2>Comincia dalle condizioni della fornitura.</h2>
          <p>
            Crea un profilo con prezzi, fonte, decorrenza e aliquote IVA.
            Nessuna tariffa di mercato è precompilata.
          </p>
          <Link className="button primary" to="/simulatori/luce-business/tariffe">
            Configura le tariffe <ArrowRight size={18} />
          </Link>
        </div>
      ) : (
        <form onSubmit={submit}>
          <section className="panel business-section">
            <div className="panel-top">
              <h2>
                <span className="step-number">01</span> Impresa e confronto
              </h2>
              <span className="badge">Dati osservati</span>
            </div>
            <div className="business-fields form-grid three">
              <Campo
                label="Ragione sociale"
                required
                maxLength={150}
                value={form.ragioneSociale}
                onChange={(e) => set("ragioneSociale", e.target.value)}
              />
              <Campo
                label="POD"
                required
                maxLength={30}
                value={form.pod}
                onChange={(e) => set("pod", e.target.value)}
              />
              <Campo
                label="Partita IVA"
                pattern="[0-9]{11}|"
                maxLength={11}
                hint="Facoltativa · 11 cifre"
                value={form.partitaIva}
                onChange={(e) => set("partitaIva", e.target.value)}
              />
              <Campo
                label="Data di riferimento"
                required
                type="date"
                value={form.dataRiferimento}
                onChange={(e) => set("dataRiferimento", e.target.value)}
              />
              <Decimale
                label="Potenza impegnata (kW)"
                required
                step="0.0001"
                min="0.0001"
                value={form.potenzaKw}
                onChange={(e) => set("potenzaKw", e.target.value)}
              />
              <Decimale
                label="Fattura precedente (€)"
                required
                step="0.01"
                hint="Importo relativo agli stessi mesi e consumi"
                value={form.fatturaPrecedente}
                onChange={(e) => set("fatturaPrecedente", e.target.value)}
              />
            </div>
          </section>
          <section className="panel business-section">
            <div className="panel-top">
              <h2>
                <span className="step-number">02</span> Consumi e profili
                mensili
              </h2>
              <span className="muted">Da 1 a 12 mesi consecutivi</span>
            </div>
            <div className="business-fields">
              {form.mesi.map((m, index) => {
                const p = profili.data.find((p) => p.id === m.profiloId);
                const indicizzato = p?.dati.voci.some((v) => v.indicizzata);
                return (
                  <fieldset className="business-month" key={index}>
                    <legend>Mese {index + 1}</legend>
                    <div className="form-grid three">
                      <Campo
                        label={`Periodo mese ${index + 1}`}
                        type="month"
                        required
                        value={m.mese}
                        onChange={(e) => mese(index, "mese", e.target.value)}
                      />
                      <label className="field">
                        <span>Profilo tariffario mese {index + 1} *</span>
                        <select
                          required
                          value={m.profiloId || ""}
                          onChange={(e) =>
                            mese(index, "profiloId", Number(e.target.value))
                          }
                        >
                          <option value="">Scegli il profilo</option>
                          {profili.data.map((p) => (
                            <option value={p.id} key={p.id}>
                              {p.dati.nome} · v{p.versione}
                              {p.dati.verificato ? "" : " · bozza"}
                            </option>
                          ))}
                        </select>
                      </label>
                      <div className="business-profile-hint">
                        {p ? (
                          <>
                            <strong>
                              {p.dati.verificato
                                ? "Verificato internamente"
                                : "Profilo in bozza"}
                            </strong>
                            <small>
                              Dal {p.dati.dal}
                              {p.dati.al ? ` al ${p.dati.al}` : ""} · Perdite{" "}
                              {p.dati.perdite}
                            </small>
                          </>
                        ) : (
                          <span>Puoi usare profili diversi per ogni mese.</span>
                        )}
                      </div>
                    </div>
                    <div className="form-grid three">
                      {(["f1", "f2", "f3"] as const).map((f) => (
                        <Decimale
                          key={f}
                          required
                          step="0.000001"
                          label={`${f.toUpperCase()} mese ${index + 1} (kWh)`}
                          value={m[f]}
                          onChange={(e) => mese(index, f, e.target.value)}
                        />
                      ))}
                    </div>
                    <label className="check">
                      <input
                        type="checkbox"
                        checked={m.quoteFisse === 1}
                        onChange={(e) =>
                          mese(index, "quoteFisse", e.target.checked ? 1 : 0)
                        }
                      />
                      Applica le quote fisse e di potenza a questo mese
                    </label>
                    <p className="help">
                      Consumi: {consumo(m)} kWh. Disattiva le quote solo se non
                      dovute; consumo zero non significa automaticamente quota
                      zero. Non si calcolano periodi parziali per giorni.
                    </p>
                    {indicizzato && (
                      <div className="business-pun">
                        <h3>PUN mensile · €/kWh</h3>
                        <p className="help">
                          Inserisci le fasce usate dalle voci indicizzate.
                          Nessun indice viene presunto.
                        </p>
                        <div className="form-grid three">
                          {(["f0", "f1", "f2", "f3", "f23"] as const).map(
                            (f) => (
                              <Decimale
                                key={f}
                                label={`PUN ${f.toUpperCase()} mese ${index + 1}`}
                                step="0.00000001"
                                value={m.pun?.[f] ?? ""}
                                onChange={(e) =>
                                  mese(index, "pun", {
                                    ...m.pun,
                                    [f]: e.target.value || null,
                                  })
                                }
                              />
                            ),
                          )}
                        </div>
                      </div>
                    )}
                    {form.mesi.length > 1 && (
                      <button
                        type="button"
                        className="text-button danger"
                        onClick={() =>
                          set(
                            "mesi",
                            form.mesi.filter((_, i) => i !== index),
                          )
                        }
                      >
                        <Trash2 size={16} />
                        Rimuovi mese {index + 1}
                      </button>
                    )}
                  </fieldset>
                );
              })}
              {form.mesi.length < 12 && (
                <button
                  type="button"
                  className="button secondary"
                  onClick={() => {
                    const last = form.mesi.at(-1)!;
                    set("mesi", [
                      ...form.mesi,
                      nuovoMese(last.profiloId, meseSuccessivo(last.mese)),
                    ]);
                  }}
                >
                  <Plus size={18} />
                  Aggiungi mese
                </button>
              )}
            </div>
          </section>
          <section className="panel business-section">
            <div className="panel-top">
              <h2>
                <span className="step-number">03</span> Altre partite
              </h2>
              <span className="muted">Importi del periodo proposto</span>
            </div>
            <div className="business-fields">
              <p className="help">
                Inserisci solo importi applicabili alla proposta. Puoi
                distinguere imponibili, aliquota zero ed esenti; gli accrediti
                possono essere negativi.
              </p>
              {form.altrePartite.map((a, index) => (
                <div className="business-extra" key={index}>
                  <Campo
                    label={`Descrizione partita ${index + 1}`}
                    required
                    maxLength={150}
                    value={a.descrizione}
                    onChange={(e) =>
                      set(
                        "altrePartite",
                        form.altrePartite.map((v, i) =>
                          i === index
                            ? { ...v, descrizione: e.target.value }
                            : v,
                        ),
                      )
                    }
                  />
                  <Decimale
                    label={`Importo partita ${index + 1} (€)`}
                    required
                    min={undefined}
                    step="0.01"
                    value={a.importo}
                    onChange={(e) =>
                      set(
                        "altrePartite",
                        form.altrePartite.map((v, i) =>
                          i === index ? { ...v, importo: e.target.value } : v,
                        ),
                      )
                    }
                  />
                  <Decimale
                    label={`IVA partita ${index + 1} (frazione)`}
                    disabled={a.esente}
                    required
                    max="1"
                    step="0.0001"
                    value={a.aliquotaIva}
                    onChange={(e) =>
                      set(
                        "altrePartite",
                        form.altrePartite.map((v, i) =>
                          i === index
                            ? { ...v, aliquotaIva: e.target.value }
                            : v,
                        ),
                      )
                    }
                  />
                  <label className="check">
                    <input
                      type="checkbox"
                      checked={a.esente}
                      onChange={(e) =>
                        set(
                          "altrePartite",
                          form.altrePartite.map((v, i) =>
                            i === index
                              ? {
                                  ...v,
                                  esente: e.target.checked,
                                  aliquotaIva: e.target.checked ? "0" : "0.22",
                                }
                              : v,
                          ),
                        )
                      }
                    />
                    Esente IVA
                  </label>
                  <button
                    className="icon-button danger"
                    type="button"
                    aria-label={`Rimuovi partita ${index + 1}`}
                    onClick={() =>
                      set(
                        "altrePartite",
                        form.altrePartite.filter((_, i) => i !== index),
                      )
                    }
                  >
                    <Trash2 size={17} />
                  </button>
                </div>
              ))}
              {form.altrePartite.length < 30 && (
                <button
                  type="button"
                  className="button secondary"
                  onClick={() =>
                    set("altrePartite", [
                      ...form.altrePartite,
                      {
                        descrizione: "",
                        importo: "0",
                        esente: false,
                        aliquotaIva: "0.22",
                      },
                    ])
                  }
                >
                  <Plus size={18} />
                  Aggiungi partita
                </button>
              )}
            </div>
          </section>
          <section className="business-submit">
            <label className="check">
              <input
                type="checkbox"
                required
                checked={form.confermaConfrontabilita}
                onChange={(e) =>
                  set("confermaConfrontabilita", e.target.checked)
                }
              />
              La fattura precedente riguarda gli stessi consumi, periodo e
              servizi. Ho controllato l’applicabilità delle condizioni e delle
              imposte.
            </label>
            {error && <Errore message={error} />}
            <button className="button primary" disabled={busy}>
              {busy ? "Calcolo in corso…" : "Calcola e salva simulazione"}
              <ArrowRight size={18} />
            </button>
            <small>
              Il risultato conserva gli input e tutte le versioni delle tariffe.
            </small>
          </section>
        </form>
      )}
      {risultato && <Risultato simulazione={risultato} />}
    </>
  );
}

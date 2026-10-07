import { useState, type FormEvent } from "react";
import { Plus, Pencil, Trash2, FileText } from "lucide-react";
import { api, messaggioErrore, useLista } from "../api";
import {
  euro,
  numero,
  consumo,
  nuovaBolletta,
  meseVuoto,
  type Bolletta,
  type DatiBolletta,
  type Mese,
  type Prezzi,
} from "../domain";
import {
  Campo,
  Decimale,
  Dialogo,
  Errore,
  Caricamento,
  Salva,
  Titolo,
  Vuoto,
} from "../components/ui";

function BollettaForm({
  initial,
  id,
  onClose,
  onSaved,
}: {
  initial: DatiBolletta;
  id?: number;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState(initial),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const set = <K extends keyof DatiBolletta>(key: K, value: DatiBolletta[K]) =>
    setForm({ ...form, [key]: value });
  const mese = (index: number, key: keyof Mese, value: Mese[keyof Mese]) =>
    set(
      "mesi",
      form.mesi.map((m, i) => (i === index ? { ...m, [key]: value } : m)),
    );
  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (id) await api.put(`/bollette/${id}`, form);
      else await api.post("/bollette", form);
      onSaved();
      onClose();
    } catch (e) {
      setError(messaggioErrore(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <form onSubmit={submit}>
      {error && <Errore message={error} />}
      <p className="form-intro">
        Inserisci i dati della bolletta reale. I mesi devono essere interi,
        consecutivi e senza duplicati.
      </p>
      <div className="form-grid">
        <Campo
          label="Cliente"
          required
          maxLength={150}
          value={form.cliente}
          onChange={(e) => set("cliente", e.target.value)}
        />
        <Campo
          label="POD"
          required
          maxLength={30}
          value={form.pod}
          onChange={(e) => set("pod", e.target.value)}
        />
        <Campo
          label="Fornitore attuale"
          required
          maxLength={150}
          value={form.fornitore}
          onChange={(e) => set("fornitore", e.target.value)}
        />
        <Decimale
          label="Potenza impegnata (kW)"
          required
          min="0.0001"
          step="0.0001"
          value={form.potenzaKw}
          onChange={(e) => set("potenzaKw", e.target.value)}
        />
        <Decimale
          label="Totale fatturato (€)"
          required
          step="0.01"
          value={form.totaleFatturato}
          onChange={(e) => set("totaleFatturato", e.target.value)}
        />
        <Decimale
          label="Aliquota IVA (frazione)"
          hint="0,10 = 10% · 0,22 = 22%"
          required
          max="1"
          step="0.0001"
          value={form.aliquotaIva}
          onChange={(e) => set("aliquotaIva", e.target.value)}
        />
        <Decimale
          label="Altre partite imponibili (€)"
          hint="Importi negativi ammessi per accrediti."
          required
          min={undefined}
          step="0.01"
          value={form.altrePartiteImponibili}
          onChange={(e) => set("altrePartiteImponibili", e.target.value)}
        />
        <Decimale
          label="Altre partite esenti IVA (€)"
          required
          min={undefined}
          step="0.01"
          value={form.altrePartiteEsenti}
          onChange={(e) => set("altrePartiteEsenti", e.target.value)}
        />
      </div>
      <h3 className="section-heading">Consumi mensili</h3>
      {form.mesi.map((m, index) => (
        <fieldset className="month-card" key={index}>
          <legend>Mese {index + 1}</legend>
          <div className="form-grid four">
            <Campo
              label="Mese di riferimento"
              required
              type="month"
              value={m.mese}
              onChange={(e) => mese(index, "mese", e.target.value)}
            />
            {(["f1", "f2", "f3"] as const).map((f) => (
              <Decimale
                key={f}
                label={`${f.toUpperCase()} (kWh)`}
                required
                step="0.000001"
                value={m[f]}
                onChange={(e) => mese(index, f, e.target.value)}
              />
            ))}
          </div>
          <label className="check">
            <input
              type="checkbox"
              checked={m.pun !== null}
              onChange={(e) => mese(index, "pun", e.target.checked ? {} : null)}
            />
            Inserisci PUN mensile per offerte indicizzate
          </label>
          {m.pun !== null && (
            <>
              <p className="help">
                Inserisci solo le fasce richieste dall'offerta. Valori in €/kWh,
                non €/MWh. Ogni fascia ha il suo indice esplicito.
              </p>
              <div className="form-grid three">
                {(["f0", "f1", "f23", "f2", "f3"] as (keyof Prezzi)[]).map(
                  (f) => (
                    <Decimale
                      key={f}
                      label={`PUN ${f.toUpperCase()} (€/kWh)`}
                      step="0.00000001"
                      value={m.pun?.[f] ?? ""}
                      onChange={(e) =>
                        mese(index, "pun", {
                          ...m.pun,
                          [f]: e.target.value === "" ? null : e.target.value,
                        })
                      }
                    />
                  ),
                )}
              </div>
            </>
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
      ))}
      {form.mesi.length < 12 && (
        <button
          type="button"
          className="button secondary"
          onClick={() => set("mesi", [...form.mesi, meseVuoto()])}
        >
          <Plus size={18} />
          Aggiungi mese
        </button>
      )}
      <p className="help">
        Le altre partite vengono riportate anche sul preventivo del gestore. La
        versione iniziale non gestisce periodi parziali, esenzioni o scaglioni
        automatici.
      </p>
      <Salva busy={busy} onCancel={onClose} />
    </form>
  );
}
export default function BollettePage() {
  const { data, loading, error, reload } = useLista<Bolletta>("/bollette");
  const [edit, setEdit] = useState<{ id?: number; dati: DatiBolletta } | null>(
      null,
    ),
    [remove, setRemove] = useState<Bolletta | null>(null),
    [busy, setBusy] = useState(false),
    [actionError, setActionError] = useState(""),
    [notice, setNotice] = useState("");
  async function elimina() {
    if (!remove) return;
    setBusy(true);
    setActionError("");
    try {
      await api.delete(`/bollette/${remove.id}`);
      setRemove(null);
      reload();
      setNotice("Bolletta eliminata.");
    } catch (e) {
      setActionError(messaggioErrore(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Titolo
        eyebrow="Dati del cliente"
        title="Bollette da confrontare"
        description="La bolletta reale è il punto di partenza. Consumi e importi restano separati dalle offerte."
        action={
          <button
            className="button primary"
            onClick={() => setEdit({ dati: nuovaBolletta() })}
          >
            <Plus size={18} />
            Nuova bolletta
          </button>
        }
      />
      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}
      {loading ? (
        <Caricamento />
      ) : error ? (
        <Errore message={error} onRetry={reload} />
      ) : data.length === 0 ? (
        <Vuoto
          title="Ogni confronto parte da una bolletta."
          text="Inserisci cliente, consumi per fascia e totale fatturato dal fornitore attuale."
          action={
            <button
              className="button primary"
              onClick={() => setEdit({ dati: nuovaBolletta() })}
            >
              <FileText size={18} />
              Inserisci bolletta
            </button>
          }
        />
      ) : (
        <section className="panel">
          <div className="panel-top">
            <h2>
              Archivio bollette <span className="count">{data.length}</span>
            </h2>
            <span className="muted">Dati inseriti manualmente</span>
          </div>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Cliente / POD</th>
                  <th>Periodo</th>
                  <th>Consumi</th>
                  <th>Totale fatturato</th>
                  <th>Fornitore</th>
                  <th>
                    <span className="sr-only">Azioni</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.map((b) => (
                  <tr key={b.id}>
                    <td>
                      <strong>{b.dati.cliente}</strong>
                      <small>{b.dati.pod}</small>
                    </td>
                    <td>
                      {b.dati.mesi[0].mese} → {b.dati.mesi.at(-1)?.mese}
                    </td>
                    <td>{numero(consumo(b.dati), 3)} kWh</td>
                    <td>{euro(b.dati.totaleFatturato)}</td>
                    <td>{b.dati.fornitore}</td>
                    <td>
                      <div className="row-actions">
                        <button
                          className="icon-button"
                          aria-label={`Modifica bolletta ${b.dati.cliente}`}
                          onClick={() => setEdit(b)}
                        >
                          <Pencil size={17} />
                        </button>
                        <button
                          className="icon-button danger"
                          aria-label={`Elimina bolletta ${b.dati.cliente}`}
                          onClick={() => {
                            setRemove(b);
                            setActionError("");
                          }}
                        >
                          <Trash2 size={17} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
      {edit && (
        <Dialogo
          title={edit.id ? "Modifica bolletta" : "Nuova bolletta"}
          onClose={() => setEdit(null)}
        >
          <BollettaForm
            initial={edit.dati}
            id={edit.id}
            onClose={() => setEdit(null)}
            onSaved={() => {
              reload();
              setNotice("Bolletta salvata correttamente.");
            }}
          />
        </Dialogo>
      )}
      {remove && (
        <Dialogo title="Eliminare la bolletta?" onClose={() => setRemove(null)}>
          <p>
            Eliminare la bolletta di <strong>{remove.dati.cliente}</strong>? I
            confronti storici restano disponibili.
          </p>
          {actionError && <Errore message={actionError} />}
          <div className="form-actions">
            <button
              className="button secondary"
              onClick={() => setRemove(null)}
            >
              Annulla
            </button>
            <button
              className="button destructive"
              disabled={busy}
              onClick={elimina}
            >
              Elimina bolletta
            </button>
          </div>
        </Dialogo>
      )}
    </>
  );
}

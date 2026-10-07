import { useState, type FormEvent } from "react";
import { Plus, Pencil, Trash2, Search, CheckCircle2 } from "lucide-react";
import { api, messaggioErrore, useLista } from "../api";
import {
  euro,
  fasce,
  normalizzaOfferta,
  nuovaOfferta,
  type Offerta,
  type Tariffa,
  type Tipo,
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

export function OffertaForm({
  initial,
  onSaved,
  onClose,
}: {
  initial: Offerta;
  onSaved: () => void;
  onClose: () => void;
}) {
  const [form, setForm] = useState<Offerta>(initial),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const valori = form.tipoOfferta === "PREZZO_FISSO" ? "prezzi" : "spread";
  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (form.id)
        await api.put(`/offerte/${form.id}`, normalizzaOfferta(form));
      else await api.post("/offerte", normalizzaOfferta(form));
      onSaved();
      onClose();
    } catch (e) {
      setError(messaggioErrore(e));
    } finally {
      setBusy(false);
    }
  }
  function tariffa(value: Tariffa) {
    setForm({ ...form, tipoTariffa: value });
  }
  return (
    <form onSubmit={submit}>
      <p className="form-intro">
        Configura i corrispettivi commerciali. Le componenti del gestore si
        impostano nella sezione Parametri.
      </p>
      {error && <Errore message={error} />}
      <div className="form-grid">
        <Campo
          label="Nome offerta"
          required
          maxLength={150}
          value={form.nomeOfferta}
          onChange={(e) => setForm({ ...form, nomeOfferta: e.target.value })}
        />
        <Campo
          label="Fornitore"
          required
          maxLength={150}
          value={form.nomeFornitore}
          onChange={(e) => setForm({ ...form, nomeFornitore: e.target.value })}
        />
        <label className="field">
          <span>Tipo di offerta</span>
          <select
            value={form.tipoOfferta}
            onChange={(e) =>
              setForm({ ...form, tipoOfferta: e.target.value as Tipo })
            }
          >
            <option value="PREZZO_FISSO">Prezzo fisso</option>
            <option value="INDICIZZATA_PUN">Indicizzata PUN</option>
          </select>
        </label>
        <label className="field">
          <span>Fasce orarie</span>
          <select
            value={form.tipoTariffa}
            onChange={(e) => tariffa(e.target.value as Tariffa)}
          >
            <option value="MONORARIA">Monoraria · F0</option>
            <option value="BIORARIA">Bioraria · F1 / F23</option>
            <option value="TRIORARIA">Trioraria · F1 / F2 / F3</option>
          </select>
        </label>
      </div>
      <fieldset>
        <legend>
          {form.tipoOfferta === "PREZZO_FISSO"
            ? "Prezzi energia"
            : "Spread sul PUN"}{" "}
          <small>€/kWh · perdite escluse</small>
        </legend>
        <div className="form-grid three">
          {fasce(form.tipoTariffa).map((f) => (
            <Decimale
              key={`${valori}-${f}`}
              label={`${form.tipoOfferta === "PREZZO_FISSO" ? "Prezzo" : "Spread"} ${f.toUpperCase()}`}
              step="0.00000001"
              required
              value={form[valori]?.[f] ?? ""}
              onChange={(e) =>
                setForm({
                  ...form,
                  [valori]: { ...form[valori], [f]: e.target.value } as Prezzi,
                })
              }
            />
          ))}
          <Decimale
            label="PCV annuo (€)"
            required
            step="0.00000001"
            value={form.pcvAnnuo}
            onChange={(e) => setForm({ ...form, pcvAnnuo: e.target.value })}
          />
        </div>
        {form.tipoOfferta === "INDICIZZATA_PUN" && (
          <p className="help">
            Il PUN di ogni mese e fascia si inserisce nella bolletta, in €/kWh.
            Il confronto usa PUN + spread e applica le perdite alla quantità.
          </p>
        )}
      </fieldset>
      <label className="field">
        <span>Note commerciali</span>
        <textarea
          rows={3}
          maxLength={2000}
          value={form.note ?? ""}
          onChange={(e) => setForm({ ...form, note: e.target.value })}
        />
      </label>
      <label className="check">
        <input
          type="checkbox"
          checked={form.attiva}
          onChange={(e) => setForm({ ...form, attiva: e.target.checked })}
        />
        Offerta attiva e disponibile per il confronto
      </label>
      <Salva busy={busy} onCancel={onClose} />
    </form>
  );
}

export default function OffertePage() {
  const { data, loading, error, reload } = useLista<Offerta>("/offerte");
  const [edit, setEdit] = useState<Offerta | null>(null),
    [search, setSearch] = useState(""),
    [notice, setNotice] = useState(""),
    [remove, setRemove] = useState<Offerta | null>(null),
    [busy, setBusy] = useState(false),
    [actionError, setActionError] = useState("");
  const filtered = data.filter((o) =>
    `${o.nomeOfferta} ${o.nomeFornitore}`
      .toLocaleLowerCase("it")
      .includes(search.toLocaleLowerCase("it")),
  );
  async function elimina() {
    if (!remove) return;
    setBusy(true);
    setActionError("");
    try {
      await api.delete(`/offerte/${remove.id}`);
      setRemove(null);
      reload();
      setNotice("Offerta eliminata. I confronti storici restano disponibili.");
    } catch (e) {
      setActionError(messaggioErrore(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Titolo
        eyebrow="Configurazione commerciale"
        title="Le tue offerte"
        description="Prezzi chiari, fasce corrette. Tutto pronto per il prossimo confronto."
        action={
          <button
            className="button primary"
            onClick={() => setEdit(nuovaOfferta())}
          >
            <Plus size={18} />
            Nuova offerta
          </button>
        }
      />
      {notice && (
        <p className="notice" role="status">
          <CheckCircle2 size={18} />
          {notice}
        </p>
      )}
      {loading ? (
        <Caricamento />
      ) : error ? (
        <Errore message={error} onRetry={reload} />
      ) : data.length === 0 ? (
        <Vuoto
          title="La tua prima offerta, da qui."
          text="Aggiungi un'offerta fissa o indicizzata e configura i prezzi per fascia."
          action={
            <button
              className="button primary"
              onClick={() => setEdit(nuovaOfferta())}
            >
              <Plus size={18} />
              Crea un'offerta
            </button>
          }
        />
      ) : (
        <section className="panel">
          <div className="panel-top">
            <h2>
              Catalogo offerte <span className="count">{data.length}</span>
            </h2>
            <label className="search">
              <Search size={18} />
              <input
                aria-label="Cerca offerte"
                placeholder="Cerca nome o fornitore…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </label>
          </div>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Offerta / fornitore</th>
                  <th>Formula</th>
                  <th>Tariffa</th>
                  <th>PCV annuo</th>
                  <th>Stato</th>
                  <th>
                    <span className="sr-only">Azioni</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((o) => (
                  <tr key={o.id}>
                    <td>
                      <strong>{o.nomeOfferta}</strong>
                      <small>{o.nomeFornitore}</small>
                    </td>
                    <td>
                      {o.tipoOfferta === "PREZZO_FISSO"
                        ? "Prezzo fisso"
                        : "PUN + spread"}
                    </td>
                    <td>{o.tipoTariffa.toLowerCase()}</td>
                    <td>{euro(o.pcvAnnuo)}</td>
                    <td>
                      <span className={`badge ${o.attiva ? "green" : ""}`}>
                        {o.attiva ? "Attiva" : "Disattivata"}
                      </span>
                    </td>
                    <td>
                      <div className="row-actions">
                        <button
                          className="icon-button"
                          aria-label={`Modifica ${o.nomeOfferta}`}
                          onClick={() => setEdit(o)}
                        >
                          <Pencil size={17} />
                        </button>
                        <button
                          className="icon-button danger"
                          aria-label={`Elimina ${o.nomeOfferta}`}
                          onClick={() => {
                            setRemove(o);
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
          {filtered.length === 0 && (
            <p className="help">Nessuna offerta corrisponde alla ricerca.</p>
          )}
        </section>
      )}
      <div className="tip">
        <span className="tip-label">COME FUNZIONA</span>
        <p>
          Ogni offerta genera automaticamente le voci energia e PCV. Le tariffe
          salvate vengono ricaricate dal database e usate nel confronto.
        </p>
      </div>
      {edit && (
        <Dialogo
          title={edit.id ? "Modifica offerta" : "Nuova offerta"}
          onClose={() => setEdit(null)}
        >
          <OffertaForm
            initial={edit}
            onClose={() => setEdit(null)}
            onSaved={() => {
              reload();
              setNotice("Offerta salvata correttamente.");
            }}
          />
        </Dialogo>
      )}
      {remove && (
        <Dialogo
          title="Eliminare questa offerta?"
          onClose={() => setRemove(null)}
        >
          <p>
            Stai eliminando <strong>{remove.nomeOfferta}</strong>. I risultati
            già salvati nello storico restano disponibili.
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
              {busy ? "Eliminazione…" : "Elimina offerta"}
            </button>
          </div>
        </Dialogo>
      )}
    </>
  );
}

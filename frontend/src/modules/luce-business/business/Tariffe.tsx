import { useState, type FormEvent } from "react";
import { Plus, Pencil, History, LockKeyhole, Trash2 } from "lucide-react";
import { api, messaggioErrore, useLista } from "../api";
import {
  Campo,
  Decimale,
  Dialogo,
  Titolo,
  Errore,
  Caricamento,
  Salva,
} from "../components/ui";
import {
  basi,
  categorie,
  nuovoProfilo,
  nuovaVoce,
  payloadProfilo,
  type Profilo,
  type ProfiloInput,
  type Voce,
  type Categoria,
  type Base,
  type Fascia,
} from "./model";
function Editor({
  initial,
  onClose,
  onSaved,
}: {
  initial: Profilo | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<ProfiloInput>(() =>
    structuredClone(initial?.dati ?? nuovoProfilo()),
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const set = <K extends keyof ProfiloInput>(k: K, v: ProfiloInput[K]) =>
    setForm((f) => ({ ...f, [k]: v }));
  const voce = <K extends keyof Voce>(index: number, k: K, v: Voce[K]) =>
    set(
      "voci",
      form.voci.map((r, i) => (i === index ? { ...r, [k]: v } : r)),
    );
  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const payload = payloadProfilo(form, initial?.versione);

      if (initial) await api.put(`/business/profili/${initial.id}`, payload);
      else await api.post("/business/profili", payload);
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
      <p className="form-intro">
        Prezzi configurabili, senza tariffe presunte. Le modifiche creano una
        revisione; le simulazioni già salvate conservano i valori precedenti.
      </p>
      {error && <Errore message={error} />}
      <div className="form-grid">
        <Campo
          label="Nome offerta / profilo"
          required
          maxLength={150}
          value={form.nome}
          onChange={(e) => set("nome", e.target.value)}
        />
        <Campo
          label="Fonte dei corrispettivi"
          required
          maxLength={500}
          value={form.fonte}
          onChange={(e) => set("fonte", e.target.value)}
        />
        <Campo
          label="Valido dal"
          type="month"
          required
          value={form.dal}
          onChange={(e) => set("dal", e.target.value)}
        />
        <Campo
          label="Valido fino al"
          type="month"
          value={form.al ?? ""}
          onChange={(e) => set("al", e.target.value || null)}
        />
        <Decimale
          label="Coefficiente perdite (frazione)"
          required
          max="1"
          step="0.00000001"
          value={form.perdite}
          onChange={(e) => set("perdite", e.target.value)}
        />
        <label className="check">
          <input
            type="checkbox"
            checked={form.arrotondaPerdite}
            onChange={(e) => set("arrotondaPerdite", e.target.checked)}
          />
          Arrotonda le perdite a kWh interi per fascia, come ROUND Excel
        </label>
      </div>
      <div className="business-draft-note">
        Il modello iniziale propone aliquota 0,22 e perdite 0,10 come
        impostazioni modificabili. Tutti i corrispettivi partono da zero:
        inserisci e verifica quelli della fornitura.
      </div>
      <h3 className="section-heading">Componenti della tariffa</h3>
      <p className="help">
        Quote annuali divise per 12. Energia e perdite devono avere i prezzi
        previsti dal contratto. Evita perdite separate se sono già incluse in
        un’altra voce.
      </p>
      {form.voci.map((v, index) => (
        <fieldset className="business-voice" key={index}>
          <legend>
            Voce {index + 1} · {v.descrizione}
          </legend>
          <div className="form-grid three">
            <Campo
              label={`Descrizione voce ${index + 1}`}
              required
              maxLength={150}
              value={v.descrizione}
              onChange={(e) => voce(index, "descrizione", e.target.value)}
            />
            <Campo
              label={`Codice voce ${index + 1}`}
              required
              pattern="[A-Za-z0-9_-]{1,40}"
              maxLength={40}
              value={v.codice}
              onChange={(e) => voce(index, "codice", e.target.value)}
            />
            <label className="field">
              <span>Categoria voce {index + 1}</span>
              <select
                value={v.categoria}
                onChange={(e) =>
                  voce(index, "categoria", e.target.value as Categoria)
                }
              >
                {Object.entries(categorie).map(([k, label]) => (
                  <option key={k} value={k}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>Base e unità voce {index + 1}</span>
              <select
                value={v.base}
                onChange={(e) => {
                  const base = e.target.value as Base;
                  set(
                    "voci",
                    form.voci.map((r, i) =>
                      i === index
                        ? {
                            ...r,
                            base,
                            fascia: ["KWH_FASCIA", "PERDITE_FASCIA"].includes(
                              base,
                            )
                              ? (r.fascia ?? "F0")
                              : null,
                            indicizzata:
                              ["KWH_FASCIA", "PERDITE_FASCIA"].includes(base) &&
                              r.indicizzata,
                          }
                        : r,
                    ),
                  );
                }}
              >
                {Object.entries(basi).map(([k, label]) => (
                  <option key={k} value={k}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            {["KWH_FASCIA", "PERDITE_FASCIA"].includes(v.base) && (
              <label className="field">
                <span>Fascia voce {index + 1}</span>
                <select
                  value={v.fascia ?? "F0"}
                  onChange={(e) =>
                    voce(index, "fascia", e.target.value as Fascia)
                  }
                >
                  {["F0", "F1", "F2", "F3", "F23"].map((f) => (
                    <option key={f}>{f}</option>
                  ))}
                </select>
              </label>
            )}
            <Decimale
              label={`${v.indicizzata ? "Spread" : "Corrispettivo"} voce ${index + 1}`}
              required
              step="0.00000001"
              value={v.corrispettivo}
              onChange={(e) => voce(index, "corrispettivo", e.target.value)}
            />
            <Decimale
              label={`IVA voce ${index + 1} (frazione)`}
              required
              max="1"
              step="0.0001"
              hint="0,22 = 22% · 0,10 = 10%"
              value={v.aliquotaIva}
              onChange={(e) => voce(index, "aliquotaIva", e.target.value)}
            />
          </div>
          <div className="business-voice-actions">
            {["KWH_FASCIA", "PERDITE_FASCIA"].includes(v.base) && (
              <label className="check">
                <input
                  type="checkbox"
                  checked={v.indicizzata}
                  onChange={(e) => voce(index, "indicizzata", e.target.checked)}
                />
                Prezzo indicizzato: PUN mensile della fascia + spread
              </label>
            )}
            <button
              type="button"
              className="text-button danger"
              disabled={form.voci.length === 1}
              onClick={() =>
                set(
                  "voci",
                  form.voci.filter((_, i) => i !== index),
                )
              }
            >
              <Trash2 size={16} />
              Rimuovi voce {index + 1}
            </button>
          </div>
        </fieldset>
      ))}
      {form.voci.length < 60 && (
        <button
          type="button"
          className="button secondary"
          onClick={() => {
            let index = form.voci.length;
            while (form.voci.some((v) => v.codice === `VOCE_${index + 1}`))
              index++;
            set("voci", [...form.voci, nuovaVoce(index)]);
          }}
        >
          <Plus size={18} />
          Aggiungi componente
        </button>
      )}
      <fieldset className="business-voice">
        <legend>Stato della verifica</legend>
        <label className="check">
          <input
            type="checkbox"
            checked={form.verificato}
            onChange={(e) => set("verificato", e.target.checked)}
          />
          Ho verificato tariffe, unità, decorrenza e fiscalità per l’uso
          previsto
        </label>
        <Campo
          label="Nota di verifica"
          required={form.verificato}
          maxLength={1000}
          hint="Chi, quando e su quali riferimenti. Non costituisce certificazione normativa."
          value={form.notaVerifica ?? ""}
          onChange={(e) => set("notaVerifica", e.target.value)}
        />
      </fieldset>
      <Salva busy={busy} onCancel={onClose} label="Salva profilo tariffario" />
    </form>
  );
}
export default function Tariffe() {
  const lista = useLista<Profilo>("/business/profili");
  const [edit, setEdit] = useState<Profilo | null | undefined>(undefined);
  const [revisioni, setRevisioni] = useState<Profilo[] | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [search, setSearch] = useState("");
  async function storico(p: Profilo) {
    setError("");
    try {
      setRevisioni(
        (await api.get<Profilo[]>(`/business/profili/${p.id}/revisioni`)).data,
      );
    } catch (e) {
      setError(messaggioErrore(e));
    }
  }
  return (
    <>
      <Titolo
        eyebrow="Condizioni della fornitura"
        title="Offerte e tariffe business"
        description="Un profilo per ogni condizione commerciale. Prezzi, quote di potenza e imposte con fonte e decorrenza."
        action={
          <button className="button primary" onClick={() => setEdit(null)}>
            <Plus size={18} />
            Nuovo profilo
          </button>
        }
      />
      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}
      {error && <Errore message={error} />}
      {lista.loading ? (
        <Caricamento />
      ) : lista.error ? (
        <Errore message={lista.error} onRetry={lista.reload} />
      ) : (
        <>
          <label className="field business-search">
            <span>Cerca un profilo</span>
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Nome o fonte"
            />
          </label>
          {!lista.data.length ? (
            <div className="empty">
              <h2>Nessun profilo configurato.</h2>
              <p>
                Crea le condizioni dalla tua offerta o dal listino verificato. I
                corrispettivi non sono ricostruibili dal solo PDF.
              </p>
            </div>
          ) : (
            <div className="business-profiles">
              {lista.data
                .filter((p) =>
                  `${p.dati.nome} ${p.dati.fonte}`
                    .toLocaleLowerCase("it")
                    .includes(search.toLocaleLowerCase("it")),
                )
                .map((p) => (
                  <section className="panel business-profile" key={p.id}>
                    <div className="business-profile-top">
                      <span
                        className={`badge ${p.dati.verificato ? "green" : ""}`}
                      >
                        {p.dati.verificato
                          ? "Verificato internamente"
                          : "Bozza"}
                      </span>
                      <small>v{p.versione}</small>
                    </div>
                    <h2>{p.dati.nome}</h2>
                    <p>{p.dati.fonte}</p>
                    <dl>
                      <div>
                        <dt>Decorrenza</dt>
                        <dd>
                          {p.dati.dal} → {p.dati.al ?? "aperta"}
                        </dd>
                      </div>
                      <div>
                        <dt>Componenti</dt>
                        <dd>{p.dati.voci.length} voci</dd>
                      </div>
                      <div>
                        <dt>Perdite</dt>
                        <dd>
                          {p.dati.perdite} ·{" "}
                          {p.dati.arrotondaPerdite
                            ? "interi per fascia"
                            : "decimali"}
                        </dd>
                      </div>
                    </dl>
                    <details>
                      <summary>Consulta corrispettivi e aliquote</summary>
                      {p.dati.voci.map((v) => (
                        <div className="business-rate" key={v.codice}>
                          <span>
                            {v.descrizione}
                            <small>
                              {basi[v.base]}
                              {v.fascia ? ` · ${v.fascia}` : ""}
                            </small>
                          </span>
                          <strong>
                            {v.indicizzata ? "PUN + " : ""}
                            {v.corrispettivo}
                            <small>IVA {v.aliquotaIva}</small>
                          </strong>
                        </div>
                      ))}
                      {p.dati.notaVerifica && (
                        <p className="help">Verifica: {p.dati.notaVerifica}</p>
                      )}
                    </details>
                    <div className="business-profile-actions">
                      <button
                        className="button secondary compact"
                        onClick={() => setEdit(p)}
                      >
                        <Pencil size={16} />
                        Modifica
                      </button>
                      <button
                        className="button secondary compact"
                        onClick={() => storico(p)}
                      >
                        <History size={16} />
                        Revisioni
                      </button>
                    </div>
                  </section>
                ))}
            </div>
          )}
        </>
      )}
      {edit !== undefined && (
        <Dialogo
          title={
            edit ? "Modifica profilo tariffario" : "Nuovo profilo tariffario"
          }
          className="business-profile-dialog"
          onClose={() => setEdit(undefined)}
        >
          <Editor
            initial={edit}
            onClose={() => setEdit(undefined)}
            onSaved={() => {
              lista.reload();
              setNotice(
                "Profilo salvato. I risultati precedenti conservano le tariffe originali.",
              );
            }}
          />
        </Dialogo>
      )}
      {revisioni && (
        <Dialogo
          title="Revisioni del profilo"
          onClose={() => setRevisioni(null)}
        >
          <p className="form-intro">
            Ogni salvataggio conserva una copia dei corrispettivi.
          </p>
          {revisioni.map((p) => (
            <details className="business-revision" key={p.versione}>
              <summary>
                Versione {p.versione} · {p.dati.nome} ·{" "}
                {p.dati.verificato ? "verificata" : "bozza"}
              </summary>
              <p>Fonte: {p.dati.fonte}</p>
              <p>
                Dal {p.dati.dal} al {p.dati.al ?? "termine aperto"}
              </p>
              {p.dati.voci.map((v) => (
                <p key={v.codice}>
                  {v.descrizione}: {v.corrispettivo} · {basi[v.base]} · IVA{" "}
                  {v.aliquotaIva}
                </p>
              ))}
            </details>
          ))}
        </Dialogo>
      )}
    </>
  );
}

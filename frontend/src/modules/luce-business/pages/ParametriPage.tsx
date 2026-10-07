import { useEffect, useState, type FormEvent } from "react";
import axios from "axios";
import { Info } from "lucide-react";
import { api, messaggioErrore } from "../api";
import { parametriVuoti, type Parametri } from "../domain";
import {
  Campo,
  Decimale,
  Errore,
  Caricamento,
  Salva,
  Titolo,
} from "../components/ui";

const gruppi: {
  title: string;
  items: { key: keyof Parametri; label: string; hint?: string; max?: string }[];
}[] = [
  {
    title: "Perdite e materia energia",
    items: [
      {
        key: "coefficientePerdite",
        label: "Coefficiente perdite",
        hint: "Frazione: 0,10 = 10%.",
        max: "1",
      },
      { key: "dispacciamentoKwh", label: "Dispacciamento (€/kWh con perdite)" },
    ],
  },
  {
    title: "Trasporto e gestione contatore",
    items: [
      { key: "trasportoFissoMese", label: "Quota fissa (€/mese)" },
      {
        key: "trasportoPotenzaAnno",
        label: "Quota potenza (€/kW/anno)",
        hint: "Il motore la divide per 12.",
      },
      { key: "trasportoKwh", label: "Quota energia (€/kWh netti)" },
    ],
  },
  {
    title: "Oneri e imposte",
    items: [
      { key: "oneriFissiMese", label: "Oneri fissi (€/mese)" },
      { key: "oneriPotenzaMese", label: "Oneri · quota potenza (€/kW/mese)" },
      { key: "oneriKwh", label: "Oneri variabili (€/kWh)" },
      {
        key: "accisaKwh",
        label: "Accisa (€/kWh netti)",
        hint: "Aliquota uniforme configurata; nessuna esenzione automatica.",
      },
    ],
  },
];
export default function ParametriPage() {
  const [form, setForm] = useState<Parametri>(parametriVuoti()),
    [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [retry, setRetry] = useState(0);
  useEffect(() => {
    const c = new AbortController();
    setLoading(true);
    setError("");
    api
      .get<Parametri>("/parametri", { signal: c.signal })
      .then((r) => {
        if (!c.signal.aborted) setForm(r.data);
      })
      .catch((e) => {
        if (
          !c.signal.aborted &&
          !(axios.isAxiosError(e) && e.response?.status === 404)
        )
          setError(messaggioErrore(e));
      })
      .finally(() => {
        if (!c.signal.aborted) setLoading(false);
      });
    return () => c.abort();
  }, [retry]);
  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const { data } = await api.put<Parametri>("/parametri", form);
      setForm(data);
      setNotice(
        "Parametri salvati. I confronti precedenti conservano la configurazione originale.",
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
        eyebrow="Configurazione del gestore"
        title="Parametri di calcolo"
        description="Una configurazione esplicita, con la fonte dei valori che utilizzi."
      />
      <div className="info">
        <Info size={21} />
        <p>
          Imposta i valori pertinenti alla fornitura. Questo profilo non applica
          automaticamente scaglioni, esenzioni o aliquote IVA miste. Per i test
          puoi inserire zero; i dati non vengono compilati con tariffe presunte.
        </p>
      </div>
      {loading ? (
        <Caricamento />
      ) : (
        <form className="panel settings" onSubmit={submit}>
          {error && (
            <Errore message={error} onRetry={() => setRetry((v) => v + 1)} />
          )}
          <div className="form-grid">
            <Campo
              label="Nome del profilo"
              required
              maxLength={150}
              value={form.nomeProfilo}
              onChange={(e) =>
                setForm({ ...form, nomeProfilo: e.target.value })
              }
            />
            <Campo
              label="Fonte e periodo dei valori"
              required
              maxLength={500}
              placeholder="Es. condizioni economiche · ottobre 2026"
              value={form.fonte}
              onChange={(e) => setForm({ ...form, fonte: e.target.value })}
            />
          </div>
          {gruppi.map((g) => (
            <fieldset key={g.title}>
              <legend>{g.title}</legend>
              <div className="form-grid three">
                {g.items.map((f) => (
                  <Decimale
                    key={f.key}
                    label={f.label}
                    hint={f.hint}
                    max={f.max}
                    required
                    step="0.00000001"
                    value={form[f.key] as string}
                    onChange={(e) =>
                      setForm({ ...form, [f.key]: e.target.value })
                    }
                  />
                ))}
              </div>
              {g.title === "Perdite e materia energia" && (
                <label className="check">
                  <input
                    type="checkbox"
                    checked={form.arrotondaPerdite}
                    onChange={(e) =>
                      setForm({ ...form, arrotondaPerdite: e.target.checked })
                    }
                  />
                  Arrotonda i kWh di perdita a interi, per fascia (modalità
                  Excel)
                </label>
              )}
            </fieldset>
          ))}
          {notice && (
            <p className="notice" role="status">
              {notice}
            </p>
          )}
          <label className="check">
            <input
              type="checkbox"
              checked={form.oneriSuPerdite ?? false}
              onChange={(e) =>
                setForm({ ...form, oneriSuPerdite: e.target.checked })
              }
            />
            Applica gli oneri variabili ai consumi incluse perdite
          </label>
          <Salva busy={busy} label="Salva parametri" />
        </form>
      )}
    </>
  );
}

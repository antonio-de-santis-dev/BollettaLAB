import { useState } from "react";
import Decimal from "decimal.js";
import {
  Download,
  CheckCircle2,
  ArrowDownRight,
  ArrowUpRight,
} from "lucide-react";
import { api } from "../api";
import { errorePdf, verificaPdf } from "../components/pdfPersonalizzazione";
import { Errore } from "../components/ui";
import { euro, numero } from "../domain";
import { categorie, type Simulazione } from "./model";
export default function Risultato({
  simulazione: s,
}: {
  simulazione: Simulazione;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const r = s.dati.risultato;
  const positivo = new Decimal(r.risparmioPeriodo).gte(0);
  const bozza = r.mesi.some((m) => !m.profilo.dati.verificato);
  async function download() {
    setBusy(true);
    setError("");
    try {
      const { data } = await api.get<Blob>(
        `/business/simulazioni/${s.id}/pdf`,
        { responseType: "blob" },
      );
      verificaPdf(data);
      const url = URL.createObjectURL(data);
      const a = document.createElement("a");
      a.href = url;
      a.download = `simulazione-business-${s.id}.pdf`;
      document.body.append(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) {
      setError(await errorePdf(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <section
      className="result business-result"
      id="business-result"
      aria-label="Risultato business"
    >
      <div className="result-banner">
        <div>
          <span className={`badge ${bozza ? "" : "green"}`}>
            <CheckCircle2 size={14} />
            {bozza ? "Bozza salvata" : "Simulazione salvata"} #{s.id}
          </span>
          <h2>{s.dati.input.ragioneSociale}</h2>
          <p>
            {r.mesi[0].mese} → {r.mesi.at(-1)?.mese} · {r.numeroMesi}{" "}
            {r.numeroMesi === 1 ? "mese" : "mesi"} · {s.dati.input.potenzaKw} kW
          </p>
        </div>
        <button className="button primary" disabled={busy} onClick={download}>
          <Download size={18} />
          {busy ? "Preparazione…" : "Scarica PDF"}
        </button>
      </div>
      {error && <Errore message={error} />}
      <div className="metrics">
        <div className="metric">
          <p>Fattura precedente</p>
          <strong>{euro(s.dati.input.fatturaPrecedente)}</strong>
          <small>Importo osservato del periodo</small>
        </div>
        <div className="metric">
          <p>Totale simulato</p>
          <strong>{euro(r.totale)}</strong>
          <small>IVA e partite esenti incluse</small>
        </div>
        <div className={`metric ${positivo ? "saving" : "loss"}`}>
          <p>
            {positivo ? "Risparmio nel periodo" : "Maggior costo nel periodo"}
          </p>
          <strong>
            {positivo ? (
              <ArrowDownRight size={24} />
            ) : (
              <ArrowUpRight size={24} />
            )}{" "}
            {euro(new Decimal(r.risparmioPeriodo).abs().toString())}
          </strong>
          <small>
            {r.risparmioPercentuale === null
              ? "Percentuale non disponibile"
              : `${numero(new Decimal(r.risparmioPercentuale).abs().toString())}% ${positivo ? "in meno" : "in più"}`}
          </small>
        </div>
      </div>
      <div className="business-result-columns">
        <section className="panel">
          <div className="panel-top">
            <h3>Composizione del costo</h3>
            <span className="muted">Incidenza sul totale IVA inclusa</span>
          </div>
          <div className="breakdown">
            {Object.entries(r.categorie).map(([key, value]) => (
              <div key={key}>
                <span>
                  {categorie[key as keyof typeof categorie]}
                  <small className="business-incidenza">
                    {r.incidenze[key as keyof typeof r.incidenze] === null
                      ? "n/d"
                      : `${r.incidenze[key as keyof typeof r.incidenze]}%`}
                  </small>
                </span>
                <strong>{euro(value)}</strong>
              </div>
            ))}
            <div>
              <span>IVA</span>
              <strong>{euro(r.totaleIva)}</strong>
            </div>
            <div className="breakdown-total">
              <span>Totale riconciliato</span>
              <strong>{euro(r.totale)}</strong>
            </div>
          </div>
          <p className="help business-pad">
            Categorie prima dell’IVA; altre partite include gli esenti. Le
            incidenze escludono la quota IVA.
          </p>
        </section>
        <section className="annual-card">
          <p className="eyebrow">Proiezione su 12 mesi</p>
          <h3>{euro(new Decimal(r.risparmioAnnualizzato).abs().toString())}</h3>
          <p>
            {positivo ? "Risparmio annualizzato" : "Maggior costo annualizzato"}
          </p>
          <div className="annual-note">
            <p>
              Differenza del periodo × 12 / {r.numeroMesi}. Ipotesi di
              ripetizione dello stesso periodo; non è una previsione.
            </p>
          </div>
        </section>
      </div>
      <section className="panel business-section">
        <div className="panel-top">
          <h3>Imponibili e IVA distinti</h3>
          <span className="badge">Totale = basi + IVA + esenti</span>
        </div>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Aliquota</th>
                <th>Imponibile</th>
                <th>IVA</th>
              </tr>
            </thead>
            <tbody>
              {r.iva.map((v) => (
                <tr key={v.aliquota}>
                  <td>{new Decimal(v.aliquota).times(100).toString()}%</td>
                  <td>{euro(v.imponibile)}</td>
                  <td>{euro(v.imposta)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="business-totals">
          <span>
            Imponibile <strong>{euro(r.imponibile)}</strong>
          </span>
          <span>
            IVA <strong>{euro(r.totaleIva)}</strong>
          </span>
          <span>
            Esenti <strong>{euro(r.esenti)}</strong>
          </span>
        </div>
      </section>
      <section className="panel business-section">
        <div className="panel-top">
          <h3>Dettaglio mensile</h3>
          <span className="muted">Versioni conservate</span>
        </div>
        <div className="business-month-summaries">
          {r.mesi.map((m) => (
            <article key={m.mese}>
              <h4>{m.mese}</h4>
              <p>
                {m.profilo.dati.nome} · versione {m.profilo.versione}
              </p>
              <small>{m.profilo.dati.fonte}</small>
              <strong>{euro(m.imponibile)} imponibili</strong>
            </article>
          ))}
        </div>
      </section>
      <details className="panel calculation">
        <summary>Apri il dettaglio di calcolo · {r.righe.length} righe</summary>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Mese</th>
                <th>Componente</th>
                <th>Quantità</th>
                <th>Corrispettivo</th>
                <th>Importo</th>
                <th>IVA</th>
              </tr>
            </thead>
            <tbody>
              {r.righe.map((v, index) => (
                <tr key={index}>
                  <td>{v.mese}</td>
                  <td>
                    {v.descrizione}
                    <small>{categorie[v.categoria]}</small>
                  </td>
                  <td>{numero(v.quantita, 8)}</td>
                  <td>
                    {numero(v.corrispettivo, 8)} {v.unita}
                  </td>
                  <td>{numero(v.imponibile, 8)} €</td>
                  <td>
                    {v.esente
                      ? "Esente"
                      : `${new Decimal(v.aliquotaIva).times(100).toString()}%`}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
      <div className="business-warnings">
        {r.avvisi.map((a) => (
          <p key={a}>{a}</p>
        ))}
      </div>
    </section>
  );
}

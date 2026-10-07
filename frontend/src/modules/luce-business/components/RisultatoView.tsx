import { nomi as nomiFonti } from "../fonti";
import {
  BarChart,
  Bar,
  Cell,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
import { ArrowDownRight, ArrowUpRight, CheckCircle2, Info } from "lucide-react";
import Decimal from "decimal.js";
import ScaricaPdf from "./ScaricaPdf";
import { euro, numero, type Confronto } from "../domain";

const nomi: Record<string, string> = {
  ENERGIA: "Materia energia",
  TRASPORTO: "Trasporto e contatore",
  ONERI: "Oneri di sistema",
  IMPOSTE: "Imposte",
  ALTRE_PARTITE: "Altre partite imponibili",
};
export default function RisultatoView({ confronto }: { confronto: Confronto }) {
  const { dati } = confronto,
    r = dati.risultato,
    positivo = new Decimal(r.risparmioPeriodo).gte(0);
  // Number conversion is exclusively for chart drawing; economic calculations stay on the server.
  const chart = [
    { name: "Bolletta attuale", costo: Number(dati.bolletta.totaleFatturato) },
    { name: "Offerta proposta", costo: Number(r.totale) },
  ];
  return (
    <section className="result" aria-label="Risultato del confronto">
      <div className="result-banner">
        <div>
          <span className="badge green">
            <CheckCircle2 size={14} />
            Confronto salvato #{confronto.id}
          </span>
          <h2>{dati.bolletta.cliente}</h2>
          <p>
            {dati.offerta.nomeOfferta} · {r.numeroMesi}{" "}
            {r.numeroMesi === 1 ? "mese" : "mesi"} ·{" "}
            {dati.bolletta.mesi[0].mese} → {dati.bolletta.mesi.at(-1)?.mese}
          </p>
        </div>
        <div className="result-actions">
          <span className="result-date">
            {new Date(confronto.creatoIl).toLocaleDateString("it-IT")}
          </span>
          <ScaricaPdf key={confronto.id} id={confronto.id} />
        </div>
      </div>
      <div className="metrics">
        <div className="metric">
          <p>Bolletta attuale</p>
          <strong>{euro(dati.bolletta.totaleFatturato)}</strong>
          <small>{dati.bolletta.fornitore}</small>
        </div>
        <div className="metric">
          <p>Con l’offerta proposta</p>
          <strong>{euro(r.totale)}</strong>
          <small>{dati.offerta.nomeFornitore}</small>
        </div>
        <div className={`metric ${positivo ? "saving" : "loss"}`}>
          <p>
            {positivo ? "Risparmio nel periodo" : "Maggior costo nel periodo"}
          </p>
          <strong>
            {positivo ? (
              <ArrowDownRight size={27} />
            ) : (
              <ArrowUpRight size={27} />
            )}{" "}
            {euro(new Decimal(r.risparmioPeriodo).abs().toString())}
          </strong>
          <small>
            {r.risparmioPercentuale === null
              ? "Percentuale non disponibile (totale attuale zero)"
              : `${numero(new Decimal(r.risparmioPercentuale).abs().toString())}% ${positivo ? "in meno" : "in più"}`}
          </small>
        </div>
      </div>
      <div className="result-grid">
        <section className="panel">
          <div className="panel-top">
            <h3>Due offerte, gli stessi consumi</h3>
            <span className="muted">Importi IVA inclusa</span>
          </div>
          <div
            className="chart"
            role="img"
            aria-label={`Costo attuale ${euro(dati.bolletta.totaleFatturato)}, costo proposto ${euro(r.totale)}`}
          >
            <ResponsiveContainer width="100%" height={220}>
              <BarChart
                data={chart}
                layout="vertical"
                margin={{ left: 10, right: 30, top: 25, bottom: 10 }}
              >
                <XAxis
                  type="number"
                  tickFormatter={(v) => `${v} €`}
                  axisLine={false}
                  tickLine={false}
                />
                <YAxis
                  type="category"
                  dataKey="name"
                  width={115}
                  axisLine={false}
                  tickLine={false}
                />
                <Tooltip formatter={(value) => euro(String(value ?? 0))} />
                <Bar
                  dataKey="costo"
                  radius={[0, 6, 6, 0]}
                  barSize={32}
                  isAnimationActive={false}
                >
                  {chart.map((_, i) => (
                    <Cell key={i} fill={i === 0 ? "#a8b7ae" : "#194d3d"} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>
        <section className="annual-card">
          <p className="eyebrow">Proiezione su 12 mesi</p>
          <h3>{euro(r.stimaRisparmioAnnuale)}</h3>
          <p>Risparmio annuo indicativo</p>
          <div className="annual-note">
            <Info size={18} />
            <p>{r.notaStima}</p>
          </div>
        </section>
      </div>
      <section className="panel">
        <div className="panel-top">
          <h3>Composizione del costo proposto</h3>
          <span className="muted">Profilo: {dati.parametri.nomeProfilo}</span>
        </div>
        <div className="breakdown">
          {Object.entries(r.categorie).map(([k, v]) => (
            <div key={k}>
              <span>{nomi[k] ?? k}</span>
              <strong>{euro(v)}</strong>
            </div>
          ))}
          <div>
            <span>Imponibile arrotondato</span>
            <strong>{euro(r.imponibile)}</strong>
          </div>
          <div>
            <span>
              IVA ·{" "}
              {numero(
                new Decimal(dati.bolletta.aliquotaIva).times(100).toString(),
              )}
              %
            </span>
            <strong>{euro(r.iva)}</strong>
          </div>
          <div>
            <span>Altre partite esenti IVA</span>
            <strong>{euro(r.altrePartiteEsenti)}</strong>
          </div>
          <div className="breakdown-total">
            <span>Totale offerta proposta</span>
            <strong>{euro(r.totale)}</strong>
          </div>
        </div>
        <p className="help">
          Fonte parametri: {dati.parametri.fonte}. Le categorie visualizzate
          sono arrotondate a centesimi; il motore somma le righe precise e
          arrotonda l’imponibile una sola volta.
        </p>
      </section>
      {dati.parametriMensili && (
        <details className="panel calculation">
          <summary>
            Fonti e parametri mensili salvati · {dati.parametriMensili.length}{" "}
            mesi
          </summary>
          {dati.parametriMensili.map((m) => (
            <div key={m.mese}>
              <h3>{m.mese}</h3>
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>Parametro</th>
                      <th>Valore usato</th>
                      <th>Fonte</th>
                    </tr>
                  </thead>
                  <tbody>
                    {m.fonti.map((f) => (
                      <tr key={f.id}>
                        <td>{nomiFonti[f.codice] ?? f.codice}</td>
                        <td>
                          {numero(f.valore, 8)} {f.unita}
                        </td>
                        <td>
                          {f.fonte}
                          {f.valoreManuale !== null
                            ? " · correzione manuale"
                            : ""}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ))}
        </details>
      )}
      <details className="panel calculation">
        <summary>Apri il dettaglio di calcolo · {r.righe.length} righe</summary>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Mese</th>
                <th>Voce</th>
                <th>Quantità</th>
                <th>Corrispettivo</th>
                <th>Importo</th>
              </tr>
            </thead>
            <tbody>
              {r.righe.map((line, i) => (
                <tr key={i}>
                  <td>{line.mese}</td>
                  <td>{line.descrizione}</td>
                  <td>{numero(line.quantita, 6)}</td>
                  <td>
                    {numero(line.corrispettivo, 8)} {line.unita}
                  </td>
                  <td>{numero(line.importo, 8)} €</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </section>
  );
}

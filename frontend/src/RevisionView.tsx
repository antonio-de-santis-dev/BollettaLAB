import type { ReactNode } from "react";
const labels: Record<string, string> = {
  createdAt: "Data di revisione",
  recordId: "Identificativo",
  kind: "Tipo di dato",
  previous: "Prima della modifica",
  updated: "Dopo la modifica",
  deleted: "Dato eliminato",
  name: "Nome",
  client: "Cliente",
  provider: "Fornitore",
  supplier: "Fornitore attuale",
  pdr: "PDR",
  partitaIva: "Partita IVA",
  source: "Fonte dei valori",
  status: "Stato",
  validFrom: "Valido dal",
  validTo: "Valido al",
  area: "Area / prodotto",
  mode: "Modalità di calcolo",
  vatRate: "Aliquota IVA (frazione)",
  approvedBy: "Responsabile approvazione",
  price: "Prezzo gas",
  spread: "Spread",
  type: "Tipo offerta",
  qvdFixed: "QVD fissa",
  qvdVariable: "QVD variabile",
  ccr: "CCR",
  notes: "Note",
  active: "Attiva",
  lines: "Voci tariffarie",
  code: "Codice",
  description: "Descrizione",
  category: "Categoria",
  base: "Base di calcolo",
  rate: "Tariffa",
  vatMode: "Trattamento IVA",
  vatTreatment: "Trattamento IVA",
  basis: "Base di calcolo",
  band: "Scaglione / scelta",
  bandChoice: "Scaglione scelto",
  bands: "Scaglioni disponibili",
  label: "Nome scaglione",
  min: "Minimo",
  max: "Massimo",
  periods: "Periodi",
  month: "Mese",
  consumptionSmc: "Consumo Smc",
  fixedMonths: "Quote fisse (mesi)",
  profileId: "Profilo gestore",
  psv: "PSV",
  overrides: "Modifiche manuali",
  reason: "Motivazione",
  operator: "Operatore",
  timestamp: "Data e ora",
  chosenBands: "Scaglioni selezionati",
  reference: "Riferimento fattura",
  previousTotal: "Totale fatturato",
  otherTaxable: "Altre partite imponibili",
  otherNonTaxable: "Altre partite esenti",
  original: "Valore originale",
};
const values: Record<string, string> = {
  DRAFT: "Bozza · da validare",
  PUBLISHED: "Pubblicato · approvato",
  ARCHIVED: "Archiviato",
  FIXED: "Prezzo fisso",
  PSV: "Indicizzata PSV",
  SOURCE_COMPATIBILITY: "Compatibilità Excel",
  CONFIGURED_RULES: "Regole configurate",
  FIXED_MONTH: "Quota mensile",
  AMOUNT: "Importo diretto",
  COMMODITY: "Materia gas",
  OTHER: "Altre partite",
  EXCEL_COMPAT: "Compatibilità Excel",
  CONFIGURED: "Regole configurate",
  GLOBAL: "Aliquota globale",
  CUSTOM: "Aliquota della voce",
  EXEMPT: "Non soggetta IVA",
  LINE_RATE: "Aliquota della voce",
  NON_TAXABLE: "Non soggetta IVA",
  RULES: "Regole configurate",
  EXCEL: "Compatibilità Excel",
  ENERGY: "Materia gas",
  TRANSPORT: "Trasporto e contatore",
  SYSTEM: "Oneri di sistema",
  TAX: "Accisa e addizionali",
  MONTH: "Quota mensile",
  SMC: "Consumo Smc",
};
export function DataValues({ value }: { value: unknown }): ReactNode {
  if (value === null || value === undefined || value === "")
    return <span>—</span>;
  if (typeof value === "boolean") return <span>{value ? "Sì" : "No"}</span>;
  if (Array.isArray(value))
    return (
      <div className="revision-list">
        {value.length ? (
          value.map((v, i) => (
            <details key={i}>
              <summary>
                Elemento {i + 1}
                {v?.description
                  ? ` · ${v.description}`
                  : v?.name
                    ? ` · ${v.name}`
                    : ""}
              </summary>
              <DataValues value={v} />
            </details>
          ))
        ) : (
          <p>Nessun elemento</p>
        )}
      </div>
    );
  if (typeof value === "object")
    return (
      <dl className="revision-values">
        {Object.entries(value).map(([key, v]) => (
          <div key={key}>
            <dt>{labels[key] ?? key.replace(/([a-z])([A-Z])/g, "$1 $2")}</dt>
            <dd>
              <DataValues value={v} />
            </dd>
          </div>
        ))}
      </dl>
    );
  return <span>{values[String(value)] ?? String(value)}</span>;
}
export default function RevisionView({ revisions }: { revisions: any[] }) {
  return (
    <div className="revision-list">
      {revisions.length ? (
        revisions.map((r, i) => (
          <details key={r.id ?? i}>
            <summary>
              Revisione {revisions.length - i} ·{" "}
              {r.createdAt
                ? new Date(r.createdAt).toLocaleString("it-IT")
                : "Valori salvati"}
            </summary>
            <DataValues value={r.data ?? r} />
          </details>
        ))
      ) : (
        <p>
          Nessuna modifica precedente: questo elemento è ancora nella versione
          iniziale.
        </p>
      )}
    </div>
  );
}

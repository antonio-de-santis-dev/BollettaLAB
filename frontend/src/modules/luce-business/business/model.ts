import Decimal from "decimal.js";
export type Categoria =
  | "ENERGIA"
  | "TRASPORTO"
  | "ONERI"
  | "IMPOSTE"
  | "ALTRE_PARTITE";
export type Base =
  | "KWH_FASCIA"
  | "PERDITE_FASCIA"
  | "KWH_NETTI"
  | "KWH_CON_PERDITE"
  | "KW_MESE"
  | "KW_ANNO"
  | "QUOTA_MESE"
  | "QUOTA_ANNO";
export type Fascia = "F0" | "F1" | "F2" | "F3" | "F23";
export type Voce = {
  codice: string;
  descrizione: string;
  categoria: Categoria;
  base: Base;
  fascia: Fascia | null;
  corrispettivo: string;
  indicizzata: boolean;
  aliquotaIva: string;
};
export type ProfiloInput = {
  nome: string;
  fonte: string;
  dal: string;
  al: string | null;
  verificato: boolean;
  notaVerifica: string;
  perdite: string;
  arrotondaPerdite: boolean;
  voci: Voce[];
  versione?: number | null;
};
export type Profilo = { id: number; versione: number; dati: ProfiloInput };
export type Mese = {
  mese: string;
  profiloId: number;
  f1: string;
  f2: string;
  f3: string;
  quoteFisse: number;
  pun: Partial<Record<Lowercase<Fascia>, string | null>> | null;
};
export type Partita = {
  descrizione: string;
  importo: string;
  esente: boolean;
  aliquotaIva: string;
};
export type Input = {
  ragioneSociale: string;
  pod: string;
  partitaIva: string;
  dataRiferimento: string;
  potenzaKw: string;
  fatturaPrecedente: string;
  confermaConfrontabilita: boolean;
  mesi: Mese[];
  altrePartite: Partita[];
};
export type Riga = {
  mese: string;
  codice: string;
  descrizione: string;
  categoria: Categoria;
  unita: string;
  quantita: string;
  corrispettivo: string;
  imponibile: string;
  aliquotaIva: string;
  esente: boolean;
};
export type Risultato = {
  righe: Riga[];
  mesi: {
    mese: string;
    profilo: Profilo;
    categorie: Record<Categoria, string>;
    imponibile: string;
  }[];
  categorie: Record<Categoria, string>;
  incidenze: Record<Categoria, string | null>;
  iva: { aliquota: string; imponibile: string; imposta: string }[];
  imponibile: string;
  totaleIva: string;
  esenti: string;
  totale: string;
  risparmioPeriodo: string;
  risparmioPercentuale: string | null;
  risparmioAnnualizzato: string;
  numeroMesi: number;
  avvisi: string[];
};
export type Simulazione = {
  id: number;
  creataIl: string;
  dati: { versioneMotore: string; input: Input; risultato: Risultato };
};
export type Riepilogo = {
  id: number;
  creataIl: string;
  ragioneSociale: string;
  dal: string;
  al: string;
  totale: string;
  risparmio: string;
  bozza: boolean;
};
export type Pagina<T> = {
  contenuto: T[];
  totaleElementi: number;
  pagina: number;
  dimensione: number;
};
export const categorie: Record<Categoria, string> = {
  ENERGIA: "Materia energia",
  TRASPORTO: "Trasporto e contatore",
  ONERI: "Oneri di sistema",
  IMPOSTE: "Accisa e imposte",
  ALTRE_PARTITE: "Altre partite",
};
export const basi: Record<Base, string> = {
  KWH_FASCIA: "Consumo netto per fascia · €/kWh",
  PERDITE_FASCIA: "Perdite per fascia · €/kWh",
  KWH_NETTI: "Consumi netti totali · €/kWh",
  KWH_CON_PERDITE: "Consumi incluse perdite · €/kWh",
  KW_MESE: "Potenza · €/kW/mese",
  KW_ANNO: "Potenza · €/kW/anno (÷ 12)",
  QUOTA_MESE: "Quota fissa · €/POD/mese",
  QUOTA_ANNO: "Quota fissa · €/POD/anno (÷ 12)",
};
export const meseAttuale = () =>
  new Date()
    .toLocaleDateString("sv-SE", { timeZone: "Europe/Rome" })
    .slice(0, 7);
export const nuovoMese = (profiloId = 0, mese = meseAttuale()): Mese => ({
  mese,
  profiloId,
  f1: "0",
  f2: "0",
  f3: "0",
  quoteFisse: 1,
  pun: null,
});
export const nuovoInput = (): Input => ({
  ragioneSociale: "",
  pod: "",
  partitaIva: "",
  dataRiferimento: new Date().toLocaleDateString("sv-SE", {
    timeZone: "Europe/Rome",
  }),
  potenzaKw: "",
  fatturaPrecedente: "",
  confermaConfrontabilita: false,
  mesi: [nuovoMese()],
  altrePartite: [],
});
export function nuovaVoce(index = 0): Voce {
  return {
    codice: `VOCE_${index + 1}`,
    descrizione: "Nuova componente",
    categoria: "TRASPORTO",
    base: "KWH_NETTI",
    fascia: null,
    corrispettivo: "0",
    indicizzata: false,
    aliquotaIva: "0.22",
  };
}
export function nuovoProfilo(): ProfiloInput {
  const voci: Voce[] = [];
  for (const fascia of ["F1", "F2", "F3"] as Fascia[])
    for (const perdite of [false, true])
      voci.push({
        codice: `${perdite ? "PERDITE" : "ENERGIA"}_${fascia}`,
        descrizione: `${perdite ? "Perdite di rete" : "Energia attiva"} ${fascia}`,
        categoria: "ENERGIA",
        base: perdite ? "PERDITE_FASCIA" : "KWH_FASCIA",
        fascia,
        corrispettivo: "0",
        indicizzata: false,
        aliquotaIva: "0.22",
      });
  for (const [codice, descrizione, categoria, base] of [
    ["PCV", "Commercializzazione", "ENERGIA", "QUOTA_ANNO"],
    ["TR_FISSO", "Trasporto · quota fissa", "TRASPORTO", "QUOTA_MESE"],
    ["TR_POTENZA", "Trasporto · quota potenza", "TRASPORTO", "KW_ANNO"],
    ["TR_ENERGIA", "Trasporto · quota energia", "TRASPORTO", "KWH_NETTI"],
    ["ON_FISSO", "Oneri · quota fissa", "ONERI", "QUOTA_MESE"],
    ["ON_POTENZA", "Oneri · quota potenza", "ONERI", "KW_MESE"],
    ["ON_ENERGIA", "Oneri · quota energia", "ONERI", "KWH_NETTI"],
    ["ACCISA", "Accisa configurata", "IMPOSTE", "KWH_NETTI"],
  ] as [string, string, Categoria, Base][])
    voci.push({
      codice,
      descrizione,
      categoria,
      base,
      fascia: null,
      corrispettivo: "0",
      indicizzata: false,
      aliquotaIva: "0.22",
    });
  return {
    nome: "",
    fonte: "",
    dal: meseAttuale(),
    al: null,
    verificato: false,
    notaVerifica: "",
    perdite: "0.10",
    arrotondaPerdite: true,
    voci,
  };
}
export function meseSuccessivo(m: string) {
  const [y, n] = m.split("-").map(Number);
  return `${n === 12 ? y + 1 : y}-${String(n === 12 ? 1 : n + 1).padStart(2, "0")}`;
}
export function consumo(m: Mese) {
  return new Decimal(m.f1 || 0)
    .plus(m.f2 || 0)
    .plus(m.f3 || 0)
    .toString();
}
export function payloadProfilo(
  p: ProfiloInput,
  versione?: number,
): ProfiloInput {
  return {
    ...p,
    nome: p.nome.trim(),
    fonte: p.fonte.trim(),
    al: p.al || null,
    versione: versione ?? null,
    voci: p.voci.map((v) => ({
      ...v,
      fascia: ["KWH_FASCIA", "PERDITE_FASCIA"].includes(v.base)
        ? v.fascia
        : null,
      indicizzata:
        ["KWH_FASCIA", "PERDITE_FASCIA"].includes(v.base) && v.indicizzata,
    })),
  };
}

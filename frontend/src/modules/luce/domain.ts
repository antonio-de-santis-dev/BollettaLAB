import Decimal from "decimal.js";
export type Prezzi = Partial<
  Record<"f0" | "f1" | "f23" | "f2" | "f3", string | null>
>;
export type Tariffa = "MONORARIA" | "BIORARIA" | "TRIORARIA";
export type Tipo = "PREZZO_FISSO" | "INDICIZZATA_PUN";
export interface Offerta {
  id?: number;
  versione?: number;
  nomeFornitore: string;
  nomeOfferta: string;
  tipoOfferta: Tipo;
  tipoTariffa: Tariffa;
  prezzi: Prezzi | null;
  spread: Prezzi | null;
  pcvAnnuo: string;
  attiva: boolean;
  note: string;
  voci?: {
    id: number;
    tipo: string;
    fascia: string | null;
    corrispettivo: string;
    indicizzata: boolean;
  }[];
}
export interface Mese {
  mese: string;
  f1: string;
  f2: string;
  f3: string;
  pun: Prezzi | null;
}
export interface DatiBolletta {
  cliente: string;
  pod: string;
  fornitore: string;
  potenzaKw: string;
  totaleFatturato: string;
  aliquotaIva: string;
  altrePartiteImponibili: string;
  altrePartiteEsenti: string;
  mesi: Mese[];
  versione?: number;
}
export interface Bolletta {
  id: number;
  dati: DatiBolletta;
}
export interface Parametri {
  nomeProfilo: string;
  fonte: string;
  coefficientePerdite: string;
  arrotondaPerdite: boolean;
  dispacciamentoKwh: string;
  trasportoFissoMese: string;
  trasportoPotenzaAnno: string;
  trasportoKwh: string;
  oneriFissiMese: string;
  oneriKwh: string;
  accisaKwh: string;
  versione?: number;
}
export interface Riga {
  mese: string;
  descrizione: string;
  categoria: string;
  unita: string;
  quantita: string;
  corrispettivo: string;
  importo: string;
}
export interface Risultato {
  righe: Riga[];
  categorie: Record<string, string>;
  imponibile: string;
  iva: string;
  altrePartiteEsenti: string;
  totale: string;
  risparmioPeriodo: string;
  risparmioPercentuale: string | null;
  stimaRisparmioAnnuale: string;
  numeroMesi: number;
  notaStima: string;
}
export interface Confronto {
  id: number;
  creatoIl: string;
  dati: {
    versioneMotore: string;
    bollettaId: number;
    bolletta: DatiBolletta;
    offerta: Offerta;
    parametri: Parametri;
    risultato: Risultato;
    parametriMensili?:
      | {
          mese: string;
          parametri: Parametri;
          fonti: import("./fonti").DatoFonte[];
        }[]
      | null;
  };
}
export const fasce = (tariffa: Tariffa): (keyof Prezzi)[] =>
  tariffa === "MONORARIA"
    ? ["f0"]
    : tariffa === "BIORARIA"
      ? ["f1", "f23"]
      : ["f1", "f2", "f3"];
export const normalizzaOfferta = (o: Offerta) => {
  const valori = o.tipoOfferta === "PREZZO_FISSO" ? o.prezzi : o.spread;
  const selezionati = Object.fromEntries(
    fasce(o.tipoTariffa).map((f) => [f, valori?.[f] ?? ""]),
  );
  return {
    nomeFornitore: o.nomeFornitore,
    nomeOfferta: o.nomeOfferta,
    tipoOfferta: o.tipoOfferta,
    tipoTariffa: o.tipoTariffa,
    prezzi: o.tipoOfferta === "PREZZO_FISSO" ? selezionati : null,
    spread: o.tipoOfferta === "INDICIZZATA_PUN" ? selezionati : null,
    pcvAnnuo: o.pcvAnnuo,
    attiva: o.attiva,
    note: o.note,
    versione: o.versione,
  };
};
export const euro = (v: string | number) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(
    Number(v),
  );
export const numero = (v: string | number, digits = 2) =>
  new Intl.NumberFormat("it-IT", { maximumFractionDigits: digits }).format(
    Number(v),
  );
export const consumo = (b: DatiBolletta) =>
  b.mesi
    .reduce((t, m) => t.plus(m.f1).plus(m.f2).plus(m.f3), new Decimal(0))
    .toString();
export const nuovaOfferta = (): Offerta => ({
  nomeFornitore: "",
  nomeOfferta: "",
  tipoOfferta: "PREZZO_FISSO",
  tipoTariffa: "MONORARIA",
  prezzi: { f0: "" },
  spread: {},
  pcvAnnuo: "",
  attiva: true,
  note: "",
});
export const meseVuoto = (): Mese => ({
  mese: "",
  f1: "",
  f2: "",
  f3: "",
  pun: null,
});
export const nuovaBolletta = (): DatiBolletta => ({
  cliente: "",
  pod: "",
  fornitore: "",
  potenzaKw: "3",
  totaleFatturato: "",
  aliquotaIva: "0.10",
  altrePartiteImponibili: "0",
  altrePartiteEsenti: "0",
  mesi: [meseVuoto()],
});
export const parametriVuoti = (): Parametri => ({
  nomeProfilo: "",
  fonte: "",
  coefficientePerdite: "",
  arrotondaPerdite: false,
  dispacciamentoKwh: "",
  trasportoFissoMese: "",
  trasportoPotenzaAnno: "",
  trasportoKwh: "",
  oneriFissiMese: "",
  oneriKwh: "",
  accisaKwh: "",
});

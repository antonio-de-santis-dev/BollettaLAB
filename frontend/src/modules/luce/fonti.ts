export interface DatoFonte {
  id: number;
  versione: number;
  codice: string;
  periodo: string;
  categoria: string;
  unita: string;
  valore: string;
  valoreUfficiale: string | null;
  valoreManuale: string | null;
  fonte: string;
  url: string;
  pubblicatoIl: string | null;
  acquisitoIl: string;
}
export const nomi: Record<string, string> = {
  coefficientePerdite: "Perdite di rete",
  dispacciamentoKwh: "Dispacciamento standard",
  trasportoFissoMese: "Trasporto · quota fissa",
  trasportoPotenzaAnno: "Trasporto · potenza",
  trasportoKwh: "Trasporto · consumi",
  oneriFissiMese: "Oneri · quota fissa",
  oneriKwh: "Oneri · consumi",
  accisaKwh: "Accisa · aliquota effettiva",
  aliquotaIva: "IVA",
  PUN_F0: "PUN · monorario F0",
  PUN_F1: "PUN · fascia F1",
  PUN_F2: "PUN · fascia F2",
  PUN_F3: "PUN · fascia F3",
  PUN_F23: "PUN · fasce F2+F3",
};

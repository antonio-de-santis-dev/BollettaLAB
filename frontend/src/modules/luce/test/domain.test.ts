import { describe, it, expect } from "vitest";
import {
  normalizzaOfferta,
  nuovaOfferta,
  fasce,
  consumo,
  nuovaBolletta,
} from "../domain";
describe("Tariffe e precisione degli input", () => {
  it("monoraria, bioraria e trioraria selezionano le fasce del dominio", () => {
    expect(fasce("MONORARIA")).toEqual(["f0"]);
    expect(fasce("BIORARIA")).toEqual(["f1", "f23"]);
    expect(fasce("TRIORARIA")).toEqual(["f1", "f2", "f3"]);
  });
  it("il payload elimina prezzi non applicabili e non espone le voci interne", () => {
    const o = {
      ...nuovaOfferta(),
      tipoTariffa: "BIORARIA" as const,
      prezzi: { f0: "9", f1: "0.12345678", f23: "0.2", f2: "8", f3: "7" },
      id: 1,
      versione: 2,
    };
    expect(normalizzaOfferta(o)).toMatchObject({
      prezzi: { f1: "0.12345678", f23: "0.2" },
      spread: null,
      versione: 2,
    });
    expect(normalizzaOfferta(o)).not.toHaveProperty("id");
  });
  it("un indicizzato invia solo gli spread conservando i decimali", () => {
    const o = {
      ...nuovaOfferta(),
      tipoOfferta: "INDICIZZATA_PUN" as const,
      spread: { f0: "0.01234567" },
    };
    expect(normalizzaOfferta(o)).toMatchObject({
      prezzi: null,
      spread: { f0: "0.01234567" },
    });
  });
  it("il totale dei kWh usa aritmetica decimale esatta", () => {
    expect(
      consumo({
        ...nuovaBolletta(),
        mesi: [{ mese: "2026-01", f1: "0.1", f2: "0.2", f3: "0", pun: null }],
      }),
    ).toBe("0.3");
  });
});

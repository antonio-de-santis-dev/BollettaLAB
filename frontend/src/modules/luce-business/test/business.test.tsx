import { it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import App from "../App";
import { api } from "../api";
import {
  nuovoProfilo,
  payloadProfilo,
  consumo,
  nuovoMese,
} from "../business/model";
import Tariffe from "../business/Tariffe";
it("il workspace business mantiene le sette sezioni del programma originale", async () => {
  vi.spyOn(api, "get").mockResolvedValue({ data: [] });
  render(
    <MemoryRouter>
      <App />
    </MemoryRouter>,
  );
  expect(
    await screen.findByRole("heading", {
      name: "Il risparmio della tua impresa, nero su bianco.",
    }),
  ).toBeVisible();
  const nav = screen.getByRole("navigation", {
    name: "Navigazione principale",
  });
  expect(nav.querySelectorAll("a")).toHaveLength(7);
  for (const name of [
    "Confronto",
    "Bollette clienti",
    "Offerte",
    "Parametri gestore",
    "Fonti ufficiali",
    "Impostazioni PDF",
    "Storico",
  ])
    expect(screen.getByRole("link", { name })).toBeVisible();
  expect(
    screen.getByRole("link", { name: "Progetto Luce Business" }),
  ).toBeVisible();
});
it("un profilo parte in bozza con unità di potenza e IVA modificabili", () => {
  const p = nuovoProfilo();
  expect(p.verificato).toBe(false);
  expect(
    p.voci.some((v) => v.categoria === "ONERI" && v.base === "KW_MESE"),
  ).toBe(true);
  expect(p.voci.every((v) => v.corrispettivo === "0")).toBe(true);
  expect(p.voci.some((v) => v.base === "PERDITE_FASCIA")).toBe(true);
});
it("il payload rimuove fasce e indicizzazione dalle quote annuali", () => {
  const p = nuovoProfilo();
  p.voci[0] = { ...p.voci[0], base: "QUOTA_ANNO", indicizzata: true };
  p.al = "";
  const r = payloadProfilo(p, 4);
  expect(r.voci[0].fascia).toBeNull();
  expect(r.voci[0].indicizzata).toBe(false);
  expect(r.versione).toBe(4);
  expect(r.al).toBeNull();
});
it("somma consumi senza precisione binaria", () => {
  const m = nuovoMese();
  m.f1 = "0.1";
  m.f2 = "0.2";
  expect(consumo(m)).toBe("0.3");
});
it("consente di configurare le tariffe senza chiave amministratore", async () => {
  Object.defineProperty(HTMLDialogElement.prototype, "showModal", {
    configurable: true,
    value: function () {
      this.setAttribute("open", "");
    },
  });
  Object.defineProperty(HTMLDialogElement.prototype, "close", {
    configurable: true,
    value: function () {
      this.removeAttribute("open");
    },
  });
  vi.spyOn(api, "get").mockResolvedValue({ data: [] });
  render(<Tariffe />);
  await userEvent.click(screen.getByRole("button", { name: "Nuovo profilo" }));
  expect(
    screen.queryByLabelText(/Chiave amministratore/),
  ).not.toBeInTheDocument();
  expect(screen.getByLabelText(/Nome offerta \/ profilo/)).toBeVisible();
});

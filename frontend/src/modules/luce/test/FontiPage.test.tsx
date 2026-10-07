vi.mock("../../../platform",()=>({useAuth:()=>({user:{role:"ADMIN"}})}));
import { it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import FontiPage, { type DatoFonte } from "../pages/FontiPage";
import { api } from "../api";
const stato = {
  automatico: true,
  gmeConfigurato: false,
  inCorso: false,
  periodi: ["2026-10"],
  esiti: [],
};
const dato: DatoFonte = {
  id: 7,
  versione: 3,
  codice: "accisaKwh",
  periodo: "2026-10",
  categoria: "DOMESTICO_RESIDENTE",
  unita: "€/kWh",
  valore: "0.0227",
  valoreUfficiale: "0.0227",
  valoreManuale: null,
  fonte: "PORTALE_OFFERTE",
  url: "https://www.ilportaleofferte.it/",
  pubblicatoIl: "2026-10-06",
  acquisitoIl: "2026-10-06T10:00:00Z",
};
it("modifica un dato conservando versione e motivo senza cambiare il valore ufficiale", async () => {
  const user = userEvent.setup();
  vi.spyOn(api, "get").mockImplementation(async (path) => ({
    data: path === "/fonti" ? stato : [dato],
  }));
  const put = vi.spyOn(api, "put").mockResolvedValue({ data: {} });
  render(<FontiPage />);
  await user.click(await screen.findByRole("button", { name: "Modifica" }));
  await user.clear(screen.getByLabelText("Valore (€/kWh)"));
  await user.type(screen.getByLabelText("Valore (€/kWh)"), "0,01");
  await user.type(
    screen.getByLabelText("Motivo della correzione / fonte verificata"),
    "Esenzione verificata",
  );
  await user.click(
    screen.getByRole("button", { name: "Salva valore manuale" }),
  );
  await waitFor(() =>
    expect(put).toHaveBeenCalledWith(
      "/fonti/dati",
      expect.objectContaining({
        codice: "accisaKwh",
        valore: "0.01",
        versione: 3,
        motivo: "Esenzione verificata",
      }),
    ),
  );
  expect(await screen.findByRole("status")).toHaveTextContent(
    "Correzione salvata",
  );
});
it("una sincronizzazione fallita conserva visibili i valori precedenti", async () => {
  vi.spyOn(api, "get").mockImplementation(async (path) => ({
    data: path === "/fonti" ? stato : [dato],
  }));
  vi.spyOn(api, "post").mockRejectedValue(new Error("Fonte non raggiungibile"));
  const user = userEvent.setup();
  render(<FontiPage />);
  await screen.findByRole("button", { name: "Modifica" });
  await user.click(screen.getByRole("button", { name: "Aggiorna adesso" }));
  expect(await screen.findByText("Fonte non raggiungibile")).toBeVisible();
  expect(screen.getByRole("button", { name: "Modifica" })).toBeVisible();
});
it("il ripristino richiede una motivazione e invia la versione corrente", async () => {
  vi.spyOn(api, "get").mockImplementation(async (path) => ({
    data:
      path === "/fonti"
        ? stato
        : [{ ...dato, valoreManuale: "0.01", valore: "0.01" }],
  }));
  const post = vi.spyOn(api, "post").mockResolvedValue({ data: {} });
  const user = userEvent.setup();
  render(<FontiPage />);
  await user.click(await screen.findByRole("button", { name: "Modifica" }));
  expect(
    screen.getByRole("button", { name: "Ripristina valore ufficiale" }),
  ).toBeDisabled();
  await user.type(
    screen.getByLabelText("Motivo della correzione / fonte verificata"),
    "Ripristino verificato",
  );
  await user.click(
    screen.getByRole("button", { name: "Ripristina valore ufficiale" }),
  );
  await waitFor(() =>
    expect(post).toHaveBeenCalledWith("/fonti/dati/7/ripristina", {
      versione: 3,
      motivo: "Ripristino verificato",
    }),
  );
});

it("seleziona gli indici PUN e crea un valore manuale per il mese scelto", async () => {
  const user = userEvent.setup();
  vi.spyOn(api, "get").mockImplementation(async (path) => ({
    data: path === "/fonti" ? stato : [],
  }));
  const put = vi.spyOn(api, "put").mockResolvedValue({ data: {} });
  render(<FontiPage />);
  await user.clear(screen.getByLabelText("Mese", { exact: true }));
  await user.type(screen.getByLabelText("Mese", { exact: true }), "2025-12");
  await user.selectOptions(
    screen.getByRole("combobox", { name: "Profilo" }),
    "INDICE",
  );
  await user.selectOptions(
    screen.getByRole("combobox", { name: "Parametro" }),
    "PUN_F0",
  );
  await user.type(screen.getByLabelText("Valore", { exact: true }), "0.12");
  await user.type(
    screen.getByLabelText("Motivo della correzione / fonte verificata"),
    "Dato verificato",
  );
  await user.click(
    screen.getByRole("button", { name: "Salva valore manuale" }),
  );
  await waitFor(() =>
    expect(put).toHaveBeenCalledWith(
      "/fonti/dati",
      expect.objectContaining({
        codice: "PUN_F0",
        periodo: "2025-12",
        categoria: "INDICE",
        valore: "0.12",
        versione: null,
      }),
    ),
  );
});

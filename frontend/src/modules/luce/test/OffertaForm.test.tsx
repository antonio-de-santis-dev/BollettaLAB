import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { OffertaForm } from "../pages/OffertePage";
import { nuovaOfferta } from "../domain";
import { api } from "../api";

describe("Configurazione offerte", () => {
  it("cambia campi in base alla tariffa e al tipo", async () => {
    const user = userEvent.setup();
    render(
      <OffertaForm
        initial={nuovaOfferta()}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    );
    expect(screen.getByLabelText(/Prezzo F0/)).toBeVisible();
    await user.selectOptions(screen.getByLabelText("Fasce orarie"), "BIORARIA");
    expect(screen.queryByLabelText(/Prezzo F0/)).not.toBeInTheDocument();
    expect(screen.getByLabelText(/Prezzo F23/)).toBeVisible();
    await user.selectOptions(
      screen.getByLabelText("Tipo di offerta"),
      "INDICIZZATA_PUN",
    );
    expect(screen.getByLabelText(/Spread F1/)).toBeVisible();
    expect(screen.queryByLabelText(/Prezzo F1/)).not.toBeInTheDocument();
    await user.selectOptions(
      screen.getByLabelText("Fasce orarie"),
      "TRIORARIA",
    );
    expect(screen.getByLabelText(/Spread F2/)).toBeVisible();
    expect(screen.getByLabelText(/Spread F3/)).toBeVisible();
    expect(screen.queryByLabelText(/Spread F23/)).not.toBeInTheDocument();
  });
  it("salva solo i valori pertinenti e mantiene i decimali come stringhe", async () => {
    const user = userEvent.setup(),
      onSaved = vi.fn(),
      onClose = vi.fn();
    const post = vi.spyOn(api, "post").mockResolvedValue({ data: { id: 1 } });
    render(
      <OffertaForm
        initial={{
          ...nuovaOfferta(),
          nomeFornitore: "Fornitore",
          nomeOfferta: "Offerta",
          pcvAnnuo: "120",
          prezzi: { f0: "0.12345678" },
        }}
        onClose={onClose}
        onSaved={onSaved}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Salva" }));
    await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
    expect(onClose).toHaveBeenCalledOnce();
    expect(post).toHaveBeenCalledWith(
      "/offerte",
      expect.objectContaining({
        prezzi: { f0: "0.12345678" },
        pcvAnnuo: "120",
        spread: null,
      }),
    );
  });
  it("mostra errori del backend e mantiene il form aperto", async () => {
    const user = userEvent.setup(),
      onClose = vi.fn();
    vi.spyOn(api, "post").mockRejectedValue(new Error("Prezzo F0 mancante"));
    render(
      <OffertaForm
        initial={{
          ...nuovaOfferta(),
          nomeFornitore: "F",
          nomeOfferta: "O",
          pcvAnnuo: "0",
          prezzi: { f0: "0.1" },
        }}
        onClose={onClose}
        onSaved={vi.fn()}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Salva" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Prezzo F0 mancante",
    );
    expect(onClose).not.toHaveBeenCalled();
  });
});

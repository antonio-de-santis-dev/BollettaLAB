import { it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import App from "../App";
import { api } from "../api";

it("la SPA apre direttamente la sezione offerte e mostra lo stato vuoto", async () => {
  vi.spyOn(api, "get").mockResolvedValue({ data: [] });
  render(
    <MemoryRouter initialEntries={["/offerte"]}>
      <App />
    </MemoryRouter>,
  );
  expect(screen.getByRole("heading", { name: "Le tue offerte" })).toBeVisible();
  expect(
    await screen.findByRole("heading", {
      name: "La tua prima offerta, da qui.",
    }),
  ).toBeVisible();
});
it("la SPA mostra una pagina 404 per percorsi non conosciuti", () => {
  render(
    <MemoryRouter initialEntries={["/inesistente"]}>
      <App />
    </MemoryRouter>,
  );
  expect(
    screen.getByRole("heading", { name: "Pagina non trovata" }),
  ).toBeVisible();
});

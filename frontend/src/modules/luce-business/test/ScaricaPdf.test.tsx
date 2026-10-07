import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { api } from "../api";
import ScaricaPdf from "../components/ScaricaPdf";

describe("Download PDF", () => {
  it("scarica il confronto selezionato con nome stabile e rilascia il blob", async () => {
    const blob = new Blob(["%PDF-1.7"], { type: "application/pdf" });
    vi.spyOn(api, "get").mockResolvedValue({ data: blob });
    const create = vi.fn(() => "blob:test-pdf"),
      revoke = vi.fn();
    Object.defineProperty(URL, "createObjectURL", {
      configurable: true,
      value: create,
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      configurable: true,
      value: revoke,
    });
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, "click")
      .mockImplementation(function (this: HTMLAnchorElement) {
        expect(this.download).toBe("confronto-42.pdf");
        expect(this.href).toBe("blob:test-pdf");
      });
    render(
      <MemoryRouter>
        <ScaricaPdf id={42} />
      </MemoryRouter>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Scarica PDF" }));
    expect(api.get).toHaveBeenCalledWith("/confronti/42/pdf", {
      responseType: "blob",
    });
    expect(create).toHaveBeenCalledWith(blob);
    expect(click).toHaveBeenCalledOnce();
    expect(screen.getByRole("status")).toHaveTextContent(
      "Download PDF avviato",
    );
    await waitFor(() => expect(revoke).toHaveBeenCalledWith("blob:test-pdf"), {
      timeout: 2000,
    });
  });

  it("mostra gli errori JSON ricevuti come blob e consente un nuovo tentativo", async () => {
    const blob = new Blob(["{}"], { type: "application/json" });
    Object.defineProperty(blob, "text", {
      value: async () => '{"message":"Confronto non trovato"}',
    });
    vi.spyOn(api, "get").mockRejectedValue({
      isAxiosError: true,
      response: { data: blob },
    });
    render(
      <MemoryRouter>
        <ScaricaPdf id={7} />
      </MemoryRouter>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Scarica PDF" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Confronto non trovato",
    );
    expect(screen.getByRole("button", { name: "Scarica PDF" })).toBeEnabled();
  });

  it("rifiuta una risposta non PDF invece di scaricare un file errato", async () => {
    vi.spyOn(api, "get").mockResolvedValue({
      data: new Blob(["{}"], { type: "application/json" }),
    });
    render(
      <MemoryRouter>
        <ScaricaPdf id={3} />
      </MemoryRouter>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Scarica PDF" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("PDF valido");
  });
});

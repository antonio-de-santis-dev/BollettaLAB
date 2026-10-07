import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../api";
import PdfEditor from "../components/PdfEditor";
import ImpostazioniPdfPage from "../pages/ImpostazioniPdfPage";
import { defaultPdf, leggiLogo } from "../components/pdfPersonalizzazione";
beforeEach(() => {
  localStorage.clear();
  Object.defineProperty(HTMLDialogElement.prototype, "showModal", {
    configurable: true,
    value: function (this: HTMLDialogElement) {
      this.setAttribute("open", "");
    },
  });
  Object.defineProperty(HTMLDialogElement.prototype, "close", {
    configurable: true,
    value: function (this: HTMLDialogElement) {
      this.removeAttribute("open");
    },
  });
  Object.defineProperty(URL, "createObjectURL", {
    configurable: true,
    value: vi.fn(() => "blob:preview"),
  });
  Object.defineProperty(URL, "revokeObjectURL", {
    configurable: true,
    value: vi.fn(),
  });
});
const anteprima = () => ({
  pdfBase64: "JVBERi0xLjc=",
  pagine: ["data:image/png;base64,AA=="],
});
describe("Editor PDF", () => {
  it("aggiorna le opzioni e scarica esattamente il PDF visualizzato", async () => {
    const data = anteprima();
    const post = vi.spyOn(api, "post").mockResolvedValue({ data });
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, "click")
      .mockImplementation(function (this: HTMLAnchorElement) {
        expect(this.download).toBe("confronto-0.pdf");
      });
    const view = render(
      <PdfEditor initialOptions={defaultPdf} onSave={vi.fn(async (o) => o)} />,
    );
    await screen.findByText("PDF aggiornato · formato A4");
    await userEvent.click(
      screen.getByRole("radio", { name: /Sintesi cliente/ }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Blu Istituzionale" }),
    );
    const name = screen.getByLabelText("Nome o studio");
    await userEvent.clear(name);
    await userEvent.type(name, "Studio Test");
    await waitFor(() =>
      expect(post).toHaveBeenLastCalledWith(
        "/impostazioni-pdf/anteprima",
        expect.objectContaining({
          stile: "SINTESI",
          colore: "#1E3A8A",
          consulente: expect.objectContaining({ nome: "Studio Test" }),
        }),
        expect.objectContaining({ signal: expect.any(AbortSignal) }),
      ),
    );
    await screen.findByText("PDF aggiornato · formato A4");
    const count = post.mock.calls.length;
    await userEvent.click(
      screen.getByRole("button", { name: "Scarica PDF di esempio" }),
    );
    expect(click).toHaveBeenCalledOnce();
    expect(post).toHaveBeenCalledTimes(count);
    expect(URL.createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
    expect(screen.getByAltText("Anteprima PDF · pagina 1 di 1")).toBeVisible();
    view.unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:preview");
  });
  it("ignora risposte obsolete durante un aggiornamento", async () => {
    let resolveFirst!: (data: { data: ReturnType<typeof anteprima> }) => void;
    const first = new Promise<{ data: ReturnType<typeof anteprima> }>(
      (resolve) => {
        resolveFirst = resolve;
      },
    );
    const post = vi
      .spyOn(api, "post")
      .mockReturnValueOnce(first)
      .mockResolvedValue({ data: anteprima() });
    render(
      <PdfEditor initialOptions={defaultPdf} onSave={vi.fn(async (o) => o)} />,
    );
    await waitFor(() => expect(post).toHaveBeenCalledOnce());
    await userEvent.click(screen.getByRole("radio", { name: /Essenziale/ }));
    expect(
      screen.getByRole("button", { name: "Scarica PDF di esempio" }),
    ).toHaveAttribute("aria-disabled", "true");
    await screen.findByText("PDF aggiornato · formato A4");
    const count = vi.mocked(URL.createObjectURL).mock.calls.length;
    resolveFirst({ data: anteprima() });
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(URL.createObjectURL).toHaveBeenCalledTimes(count);
    expect(post.mock.calls[0]?.[2]?.signal?.aborted).toBe(true);
  });
  it("consente un nuovo tentativo dopo un errore", async () => {
    vi.spyOn(api, "post")
      .mockRejectedValueOnce(
        new Error("Servizio temporaneamente indisponibile"),
      )
      .mockResolvedValue({ data: anteprima() });
    render(
      <PdfEditor initialOptions={defaultPdf} onSave={vi.fn(async (o) => o)} />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Servizio temporaneamente indisponibile",
    );
    await userEvent.click(screen.getByRole("button", { name: "Riprova" }));
    await screen.findByText("PDF aggiornato · formato A4");
  });
  it("salva solo su richiesta, ricarica dal server e mantiene la bozza se il salvataggio fallisce", async () => {
    vi.spyOn(api, "get").mockResolvedValue({
      data: { opzioni: defaultPdf, versione: 4 },
    });
    vi.spyOn(api, "post").mockResolvedValue({ data: anteprima() });
    const put = vi
      .spyOn(api, "put")
      .mockRejectedValueOnce(new Error("Salvataggio non riuscito"))
      .mockImplementation(async (_url, body) => ({
        data: { ...(body as object), versione: 5 },
      }));
    render(<ImpostazioniPdfPage />);
    await screen.findByText("PDF aggiornato · formato A4");
    await userEvent.click(
      screen.getByRole("button", { name: "Viola Premium" }),
    );
    expect(put).not.toHaveBeenCalled();
    expect(screen.getByText("Modifiche non salvate")).toBeVisible();
    await userEvent.click(
      screen.getByRole("button", { name: "Salva impostazioni PDF" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Salvataggio non riuscito",
    );
    expect(screen.getByLabelText(/Codice colore/)).toHaveValue("#6B21A8");
    await userEvent.click(
      screen.getByRole("button", { name: "Salva impostazioni PDF" }),
    );
    await screen.findByText(/Impostazioni PDF salvate/);
    expect(put).toHaveBeenLastCalledWith("/impostazioni-pdf", {
      opzioni: expect.objectContaining({ colore: "#6B21A8" }),
      versione: 4,
    });
  });
  it("mostra un solo foglio alla volta e cambia pagina con le frecce", async () => {
    vi.spyOn(api, "post").mockResolvedValue({
      data: {
        ...anteprima(),
        pagine: ["data:image/png;base64,AA==", "data:image/png;base64,BB=="],
      },
    });
    render(
      <PdfEditor initialOptions={defaultPdf} onSave={vi.fn(async (o) => o)} />,
    );
    await screen.findByText("PDF aggiornato · formato A4");
    expect(screen.getAllByAltText(/Anteprima PDF/)).toHaveLength(1);
    expect(
      screen.getByRole("button", { name: "Pagina precedente" }),
    ).toBeDisabled();
    await userEvent.click(
      screen.getByRole("button", { name: "Pagina successiva" }),
    );
    expect(screen.getByAltText("Anteprima PDF · pagina 2 di 2")).toBeVisible();
    expect(
      screen.getByRole("button", { name: "Pagina successiva" }),
    ).toBeDisabled();
  });
  it("rifiuta loghi non supportati o troppo grandi", async () => {
    await expect(
      leggiLogo(new File(["<svg/>"], "logo.svg", { type: "image/svg+xml" })),
    ).rejects.toThrow("PNG o JPEG");
    await expect(
      leggiLogo(
        new File([new Uint8Array(1048577)], "logo.png", { type: "image/png" }),
      ),
    ).rejects.toThrow("1 MB");
  });
});

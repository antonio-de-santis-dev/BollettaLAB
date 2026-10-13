import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi } from "vitest";
import { users, payments } from "../platform";
import { AdminLogs } from "../AdminPages";
import CompanyPage from "../CompanyPage";
import ProfilePage from "../ProfilePage";
vi.mock("../platform", async (original) => ({
  ...(await original<typeof import("../platform")>()),
  useAuth: () => ({ wallet: { agentSeats: 2 }, refresh: vi.fn() }),
}));
describe("Gestione account e attività", () => {
  it("filtra i log dal server e mostra account, stato, azione e descrizione", async () => {
    vi.spyOn(users, "get").mockResolvedValue({
      data: {
        items: [
          {
            id: "log",
            account_name: "Mario Rossi",
            account_email: "m@test.it",
            status: "ERROR",
            action: "Calcolo confronto",
            description: "Operazione gas · HTTP 500",
            service: "gas",
            created_at: "2026-10-13T10:00:00Z",
          },
        ],
        total: 1,
      },
    });
    render(
      <MemoryRouter>
        <AdminLogs />
      </MemoryRouter>,
    );
    expect(await screen.findByText("Mario Rossi")).toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "Errore" })).toBeInTheDocument();
    await userEvent.type(
      screen.getByLabelText("Cerca account, azione o descrizione"),
      "Mario",
    );
    await userEvent.selectOptions(
      screen.getByLabelText("Stato dell’operazione"),
      "ERROR",
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Applica filtri" }),
    );
    await waitFor(() =>
      expect(users.get).toHaveBeenLastCalledWith(
        "/admin/logs",
        expect.objectContaining({
          params: expect.objectContaining({
            search: "Mario",
            status: "ERROR",
            page: 0,
            size: 30,
          }),
        }),
      ),
    );
  });
  it("mostra subito il nuovo agente e la password iniziale una sola volta", async () => {
    let created = false;
    vi.spyOn(users, "get").mockImplementation(async () => ({
      data: {
        agents: created
          ? [
              {
                id: "agent",
                name: "Agente Test",
                email: "agent@test.it",
                status: "ACTIVE",
              },
            ]
          : [],
        invites: [],
      },
    }));
    vi.spyOn(payments, "get").mockResolvedValue({ data: [] });
    vi.spyOn(users, "post").mockImplementation(async () => {
      created = true;
      return {
        data: {
          email: "agent@test.it",
          temporaryPassword: "SecretAgent123!",
          message: "Agente creato",
        },
      };
    });
    HTMLDialogElement.prototype.showModal = vi.fn(function (
      this: HTMLDialogElement,
    ) {
      this.setAttribute("open", "");
    });
    HTMLDialogElement.prototype.close = vi.fn(function (
      this: HTMLDialogElement,
    ) {
      this.removeAttribute("open");
    });
    render(
      <MemoryRouter>
        <CompanyPage />
      </MemoryRouter>,
    );
    await userEvent.type(
      screen.getByLabelText("Nome e cognome"),
      "Agente Test",
    );
    await userEvent.type(
      screen.getByLabelText("Email dell’agente"),
      "agent@test.it",
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Crea agente e genera password" }),
    );
    expect(await screen.findByText("SecretAgent123!")).toBeInTheDocument();
    expect(screen.getByText("Agente Test")).toBeInTheDocument();
    expect(users.post).toHaveBeenCalledWith("/company/agents/create", {
      name: "Agente Test",
      email: "agent@test.it",
    });
    await userEvent.click(screen.getByRole("button", { name: "Chiudi" }));
    expect(screen.queryByText("SecretAgent123!")).not.toBeInTheDocument();
  });
  it("consente di cambiare i dati dal profilo e gestisce un errore senza perdere il form", async () => {
    vi.spyOn(users, "get").mockResolvedValue({
      data: {
        name: "Mario",
        email: "m@test.it",
        role: "PRIVATE",
        companyName: "",
        vatNumber: "",
        address: "",
      },
    });
    vi.spyOn(users, "put").mockRejectedValue(new Error("Email già utilizzata"));
    render(
      <MemoryRouter>
        <ProfilePage />
      </MemoryRouter>,
    );
    await screen.findByDisplayValue("Mario");
    await userEvent.clear(screen.getByLabelText("Nome e cognome"));
    await userEvent.type(screen.getByLabelText("Nome e cognome"), "Marco");
    await userEvent.click(
      screen.getByRole("button", { name: "Salva profilo" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Email già utilizzata",
    );
    expect(screen.getByDisplayValue("Marco")).toBeInTheDocument();
  });
});

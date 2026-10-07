import { useEffect, useState } from "react";
import { ArrowUpRight, ChevronLeft, ChevronRight } from "lucide-react";
import { api, messaggioErrore } from "../api";
import { Titolo, Caricamento, Errore } from "../components/ui";
import { euro } from "../domain";
import type { Pagina, Riepilogo, Simulazione } from "./model";
import Risultato from "./Risultato";
export default function Storico() {
  const [pagina, setPagina] = useState(0);
  const [data, setData] = useState<Pagina<Riepilogo> | null>(null);
  const [selected, setSelected] = useState<Simulazione | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  const [opening, setOpening] = useState<number | null>(null);
  useEffect(() => {
    const c = new AbortController();
    setLoading(true);
    setError("");
    api
      .get<Pagina<Riepilogo>>("/business/simulazioni", {
        params: { pagina, dimensione: 20 },
        signal: c.signal,
      })
      .then((r) => {
        if (!c.signal.aborted) setData(r.data);
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(messaggioErrore(e));
      })
      .finally(() => {
        if (!c.signal.aborted) setLoading(false);
      });
    return () => c.abort();
  }, [pagina, retry]);
  async function apri(id: number) {
    setOpening(id);
    setError("");
    try {
      setSelected(
        (await api.get<Simulazione>(`/business/simulazioni/${id}`)).data,
      );
    } catch (e) {
      setError(messaggioErrore(e));
    } finally {
      setOpening(null);
    }
  }
  return (
    <>
      <Titolo
        eyebrow="Le tue consulenze"
        title="Storico simulazioni"
        description="Input, corrispettivi e versioni conservati. Le nuove tariffe non modificano i risultati precedenti."
      />
      {error && (
        <Errore message={error} onRetry={() => setRetry((n) => n + 1)} />
      )}
      {loading ? (
        <Caricamento />
      ) : !data?.contenuto.length ? (
        <div className="empty">
          <h2>Il primo scenario comincia dal simulatore.</h2>
          <p>Ogni calcolo salvato comparirà qui.</p>
        </div>
      ) : (
        <section className="panel">
          <div className="panel-top">
            <h2>Scenari salvati</h2>
            <span className="count">{data.totaleElementi}</span>
          </div>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Impresa / data</th>
                  <th>Periodo</th>
                  <th>Totale</th>
                  <th>Differenza</th>
                  <th>Stato</th>
                  <th>Dettaglio</th>
                </tr>
              </thead>
              <tbody>
                {data.contenuto.map((s) => (
                  <tr key={s.id}>
                    <td>
                      <strong>{s.ragioneSociale}</strong>
                      <small>
                        {new Date(s.creataIl).toLocaleDateString("it-IT")}
                      </small>
                    </td>
                    <td>
                      {s.dal} → {s.al}
                    </td>
                    <td>{euro(s.totale)}</td>
                    <td>{euro(s.risparmio)}</td>
                    <td>
                      <span className={`badge ${s.bozza ? "" : "green"}`}>
                        {s.bozza ? "Bozza" : "Verificato internamente"}
                      </span>
                    </td>
                    <td>
                      <button
                        className="button secondary compact"
                        disabled={opening !== null}
                        onClick={() => apri(s.id)}
                      >
                        {opening === s.id ? "Caricamento…" : "Apri"}
                        <ArrowUpRight size={16} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="business-pagination">
            <button
              className="button secondary compact"
              disabled={pagina === 0}
              onClick={() => setPagina((p) => p - 1)}
            >
              <ChevronLeft size={16} />
              Precedente
            </button>
            <span>
              Pagina {pagina + 1} di {Math.ceil(data.totaleElementi / 20)}
            </span>
            <button
              className="button secondary compact"
              disabled={(pagina + 1) * 20 >= data.totaleElementi}
              onClick={() => setPagina((p) => p + 1)}
            >
              Successiva
              <ChevronRight size={16} />
            </button>
          </div>
        </section>
      )}
      {selected && <Risultato simulazione={selected} />}
    </>
  );
}

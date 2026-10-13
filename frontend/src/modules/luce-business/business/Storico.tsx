import { Link } from "react-router-dom";
import { useEffect, useState } from "react";
import { ArrowUpRight, ChevronLeft, ChevronRight } from "lucide-react";
import { api, messaggioErrore } from "../api";
import { Titolo, Caricamento, Errore } from "../components/ui";
import { euro } from "../domain";
import type { Pagina, Riepilogo } from "./model";

export default function Storico() {
  const [pagina, setPagina] = useState(0);
  const [data, setData] = useState<Pagina<Riepilogo> | null>(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);

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
                      <Link
                        className="button secondary compact"
                        to={`/app/storico/luce-business/${s.id}?kind=business&from=advanced`}
                      >
                        Apri <ArrowUpRight size={16} />
                      </Link>
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
    </>
  );
}

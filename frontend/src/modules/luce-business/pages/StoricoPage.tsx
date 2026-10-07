import { useState } from "react";
import { ArrowUpRight, History } from "lucide-react";
import { Link } from "react-router-dom";
import { useLista } from "../api";
import { euro, type Confronto } from "../domain";
import { Titolo, Errore, Caricamento, Vuoto } from "../components/ui";
import RisultatoView from "../components/RisultatoView";
export default function StoricoPage() {
  const { data, loading, error, reload } = useLista<Confronto>("/confronti");
  const [selected, setSelected] = useState<Confronto | null>(null);
  return (
    <>
      <p className="help">
        <Link to="/simulatori/luce-business/storico-simulazioni">
          Consulta lo storico delle simulazioni dettagliate
        </Link>
      </p>
      <Titolo
        eyebrow="Le tue consulenze"
        title="Storico confronti"
        description="Ogni risultato conserva prezzi, consumi e parametri utilizzati al momento del calcolo."
      />
      {loading ? (
        <Caricamento />
      ) : error ? (
        <Errore message={error} onRetry={reload} />
      ) : data.length === 0 ? (
        <Vuoto
          title="La storia comincia dal primo confronto."
          text="I risultati saranno salvati qui e resteranno consultabili anche dopo una modifica alle offerte."
          action={
            <Link className="button primary" to="/simulatori/luce-business">
              <History size={18} />
              Crea un confronto
            </Link>
          }
        />
      ) : (
        <section className="panel">
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Data</th>
                  <th>Cliente</th>
                  <th>Offerta</th>
                  <th>Totale proposto</th>
                  <th>Risparmio periodo</th>
                  <th>
                    <span className="sr-only">Dettaglio</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.map((c) => (
                  <tr key={c.id}>
                    <td>{new Date(c.creatoIl).toLocaleDateString("it-IT")}</td>
                    <td>
                      <strong>{c.dati.bolletta.cliente}</strong>
                      <small>{c.dati.bolletta.pod}</small>
                    </td>
                    <td>{c.dati.offerta.nomeOfferta}</td>
                    <td>{euro(c.dati.risultato.totale)}</td>
                    <td>{euro(c.dati.risultato.risparmioPeriodo)}</td>
                    <td>
                      <button
                        className="button secondary compact"
                        onClick={() => setSelected(c)}
                      >
                        Apri
                        <ArrowUpRight size={16} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
      {selected && <RisultatoView confronto={selected} />}
    </>
  );
}

import { useState } from "react";
import { Download, LoaderCircle, SlidersHorizontal } from "lucide-react";
import { api } from "../api";
import { Errore } from "./ui";
import { Link } from "react-router-dom";
import { downloadPdf, errorePdf, verificaPdf } from "./pdfPersonalizzazione";

export default function ScaricaPdf({ id }: { id: number }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  async function scarica() {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const { data } = await api.get<Blob>(`/confronti/${id}/pdf`, {
        responseType: "blob",
      });
      verificaPdf(data);
      downloadPdf(data, id);
      setNotice("Download PDF avviato.");
    } catch (e) {
      setError(await errorePdf(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="pdf-download">
      <Link className="button secondary" to="/simulatori/luce/impostazioni-pdf">
        <SlidersHorizontal size={18} />
        Impostazioni PDF
      </Link>
      <button
        type="button"
        className="button secondary"
        disabled={busy}
        onClick={scarica}
      >
        {busy ? (
          <LoaderCircle size={18} className="spin" />
        ) : (
          <Download size={18} />
        )}
        {busy ? "Preparazione PDF…" : "Scarica PDF"}
      </button>
      {notice && <small role="status">{notice}</small>}
      {error && <Errore message={error} />}
    </div>
  );
}

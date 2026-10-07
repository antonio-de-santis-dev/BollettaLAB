import axios from "axios";
import { useCallback, useEffect, useState } from "react";
export const api = axios.create({ baseURL: "/api/gas", timeout: 45000, headers: { "X-Requested-With": "BollettaLAB" } });
const pendingOperations=new Map<string,string>();
const operation=(c:{url?:string;data?:unknown})=>(c.url??"")+"\n"+(typeof c.data==="string"?c.data:JSON.stringify(c.data));
api.interceptors.request.use(config => { if(config.method?.toLowerCase()==="post" && ["/confronti","/business/simulazioni"].includes(config.url ?? "")){const key=operation(config);let id=pendingOperations.get(key);if(!id){id=crypto.randomUUID();if(pendingOperations.size>=50)pendingOperations.delete(pendingOperations.keys().next().value!);pendingOperations.set(key,id);}config.headers.set("X-Idempotency-Key",config.headers.get("X-Idempotency-Key")??id);}return config; });
api.interceptors.response.use(r => { if(r.config.method?.toLowerCase()==="post" && ["/confronti","/business/simulazioni"].includes(r.config.url ?? "")){pendingOperations.delete(operation(r.config));window.dispatchEvent(new Event("lab:wallet-changed"));}return r; }, e => {if(e.response?.status===402)window.dispatchEvent(new Event("lab:credits-empty"));return Promise.reject(e);});
export function messaggioErrore(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data;
    if (data?.fields && Object.keys(data.fields).length)
      return `${data.message}: ${Object.entries(data.fields)
        .map(([k, v]) => `${k}: ${v}`)
        .join("; ")}`;
    return (
      data?.message ??
      (error.response
        ? "Operazione non riuscita. Riprova."
        : "Backend non raggiungibile. Controlla che sia avviato e riprova.")
    );
  }
  return error instanceof Error ? error.message : "Operazione non riuscita";
}
export function useLista<T>(path: string) {
  const [data, setData] = useState<T[]>([]),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(""),
    [version, setVersion] = useState(0);
  const reload = useCallback(() => setVersion((v) => v + 1), []);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    api
      .get<T[]>(path, { signal: controller.signal })
      .then((r) => {
        if (!controller.signal.aborted) setData(r.data);
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(messaggioErrore(e));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [path, version]);
  return { data, loading, error, reload };
}

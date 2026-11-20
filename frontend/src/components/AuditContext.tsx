"use client";
import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import type { AuditView } from "@/lib/types";

interface Ctx { data: AuditView | null; error: string | null; refresh: () => Promise<void> }
const AuditCtx = createContext<Ctx>({ data: null, error: null, refresh: async () => {} });

const TRANSIENT = new Set(["GENERATING_PERSONAS", "TESTING", "ANALYZING"]);

export function AuditProvider({ id, children }: { id: string; children: React.ReactNode }) {
  const [data, setData] = useState<AuditView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const refresh = useCallback(async () => {
    try {
      const d = await api<AuditView>(`/audits/${id}`);
      setData(d);
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    }
  }, [id]);

  useEffect(() => {
    let alive = true;
    const loop = async () => {
      await refresh();
      if (!alive) return;
      timer.current = setTimeout(loop, 2500);
    };
    loop();
    return () => { alive = false; if (timer.current) clearTimeout(timer.current); };
  }, [refresh]);

  return <AuditCtx.Provider value={{ data, error, refresh }}>{children}</AuditCtx.Provider>;
}

export const useAudit = () => useContext(AuditCtx);
export const isTransient = (s?: string) => !!s && TRANSIENT.has(s);

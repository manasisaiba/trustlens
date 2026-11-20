"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { Preview } from "@/lib/types";

export function usePreview(auditId?: string) {
  const [preview, setPreview] = useState<Preview | null>(null);
  const [missing, setMissing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const load = useCallback(async () => {
    if (!auditId) return null;
    try {
      const p = await api<Preview>(`/audits/${auditId}/preview`);
      setPreview(p); setMissing(false); setError(null);
      return p;
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) { setMissing(true); setPreview(null); }
      else setError((e as Error).message);
      return null;
    }
  }, [auditId]);

  useEffect(() => {
    let alive = true;
    const loop = async () => {
      const p = await load();
      if (!alive) return;
      if (p?.status === "GENERATING") timer.current = setTimeout(loop, 2000);
    };
    loop();
    return () => { alive = false; if (timer.current) clearTimeout(timer.current); };
  }, [load]);

  const generate = useCallback(async () => {
    if (!auditId) return;
    setError(null);
    try {
      const p = await api<Preview>(`/audits/${auditId}/generate-preview`, { method: "POST" });
      setPreview(p); setMissing(false);
      const poll = async () => { const x = await load(); if (x?.status === "GENERATING") timer.current = setTimeout(poll, 2000); };
      timer.current = setTimeout(poll, 1500);
    } catch (e) { setError((e as Error).message); }
  }, [auditId, load]);

  return { preview, setPreview, missing, error, generate, reload: load };
}

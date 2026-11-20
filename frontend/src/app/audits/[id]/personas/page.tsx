"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { Bot, CheckCircle2, Play, RefreshCw, Search, Target } from "lucide-react";
import { useAudit } from "@/components/AuditContext";
import { Badge, Button, Card, EmptyState, ErrorBox, JourneyBadge, Notice, Spinner } from "@/components/ui";
import { api } from "@/lib/api";

export default function Personas() {
  const { data, refresh } = useAudit();
  const router = useRouter();
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  if (!data) return null;
  const { audit, personas } = data;

  async function post(path: string, key: string, next?: string) {
    setBusy(key); setErr(null);
    try { await api(`/audits/${audit.id}/${path}`, { method: "POST" }); await refresh(); if (next) router.push(next); }
    catch (e) { setErr((e as Error).message); } finally { setBusy(null); }
  }

  if (audit.status === "GENERATING_PERSONAS") {
    return (
      <div className="space-y-4">
        <Spinner label={audit.mode === "LIVE" ? `Claude is designing customer personas for a ${data.business.category.toLowerCase()}…` : "Building demo personas…"} />
        <div className="grid gap-4 md:grid-cols-2">
          {[0, 1, 2, 3].map((i) => <div key={i} className="h-52 animate-pulse rounded-xl border border-line bg-white" />)}
        </div>
      </div>
    );
  }

  if (personas.length === 0) {
    return (
      <div className="space-y-4">
        {audit.status === "FAILED" && <ErrorBox title="Persona generation failed" message={audit.errorMessage || ""} />}
        {err && <ErrorBox message={err} />}
        <EmptyState icon={<Bot className="h-6 w-6" />} title="No personas yet" description="Generate AI customer personas tailored to this business category."
          action={<Button loading={busy === "gen"} onClick={() => post("generate-personas", "gen")}><Bot className="h-4 w-4" />Generate personas</Button>} />
      </div>
    );
  }

  const canTest = ["PERSONAS_READY", "REPORT_READY", "FAILED"].includes(audit.status);
  const canRegen = ["PERSONAS_READY", "FAILED", "CREATED"].includes(audit.status);
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold">AI customer personas</h2>
          <p className="text-sm text-muted">Each persona will attempt its task on the live website in a real Chromium browser.</p>
        </div>
        <div className="flex gap-2">
          {canRegen && <Button variant="secondary" loading={busy === "gen"} onClick={() => post("generate-personas", "gen")}><RefreshCw className="h-4 w-4" />Regenerate</Button>}
          {canTest && <Button loading={busy === "test"} onClick={() => post("start-testing", "test", `/audits/${audit.id}/testing`)}><Play className="h-4 w-4" />{audit.status === "REPORT_READY" ? "Re-run browser tests" : "Start browser testing"}</Button>}
        </div>
      </div>
      {audit.mode === "DEMO" && <Notice>Demo mode: these personas come from fixed templates adapted to the category, not from Claude.</Notice>}
      {err && <ErrorBox message={err} />}
      <div className="grid gap-4 md:grid-cols-2">
        {personas.map((p) => (
          <Card key={p.id} className="flex flex-col p-5">
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-brand-50 text-sm font-semibold text-brand">{p.orderIndex}</div>
                <div>
                  <h3 className="font-semibold">{p.name}</h3>
                  <p className="text-xs text-muted">{p.objective}</p>
                </div>
              </div>
              <JourneyBadge status={p.task?.status || "PENDING"} />
            </div>
            <p className="mt-4 text-sm text-muted">{p.background}</p>
            <div className="mt-4 space-y-3 rounded-lg bg-canvas p-3.5 text-sm">
              <p className="flex gap-2"><Target className="mt-0.5 h-4 w-4 shrink-0 text-brand" /><span><span className="font-medium">Task:</span> {p.task?.description}</span></p>
              <p className="flex gap-2"><CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" /><span><span className="font-medium">Success when:</span> {p.task?.successCriteria}</span></p>
            </div>
            <div className="mt-4 flex flex-wrap items-center gap-1.5">
              <Search className="h-3.5 w-3.5 text-muted" />
              {p.lookFor.map((k) => <Badge key={k}>{k}</Badge>)}
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}

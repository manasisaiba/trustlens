"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowRight, Bot, CheckCircle2, Circle, FileText, Loader2, MousePointerClick, Play, RefreshCw, Wand2 } from "lucide-react";
import { useAudit } from "@/components/AuditContext";
import { Button, Card, CardHeader, ErrorBox, JourneyBadge, LinkButton, ScoreRing } from "@/components/ui";
import { api } from "@/lib/api";
import type { AuditStatus } from "@/lib/types";

const PIPE: { key: AuditStatus[]; label: string; icon: typeof Bot }[] = [
  { key: ["GENERATING_PERSONAS"], label: "Generate personas", icon: Bot },
  { key: ["TESTING"], label: "Browser testing", icon: MousePointerClick },
  { key: ["ANALYZING"], label: "Evidence analysis", icon: FileText },
  { key: ["REPORT_READY"], label: "Report ready", icon: CheckCircle2 },
];
const ORDER = ["CREATED", "GENERATING_PERSONAS", "PERSONAS_READY", "TESTING", "ANALYZING", "REPORT_READY"];

export default function Overview() {
  const { data, refresh } = useAudit();
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  if (!data) return null;
  const { audit, personas } = data;
  const cur = ORDER.indexOf(audit.status);

  async function run(path: string, next: string) {
    setBusy(true); setErr(null);
    try { await api(path, { method: "POST" }); await refresh(); router.push(next); }
    catch (e) { setErr((e as Error).message); } finally { setBusy(false); }
  }

  const base = `/audits/${audit.id}`;
  let primary: React.ReactNode = null;
  switch (audit.status) {
    case "CREATED": primary = <Button loading={busy} onClick={() => run(`${base}/generate-personas`, `${base}/personas`)}><Bot className="h-4 w-4" />Generate personas</Button>; break;
    case "GENERATING_PERSONAS": primary = <LinkButton href={`${base}/personas`}><Loader2 className="h-4 w-4 animate-spin" />Generating personas…</LinkButton>; break;
    case "PERSONAS_READY": primary = <Button loading={busy} onClick={() => run(`${base}/start-testing`, `${base}/testing`)}><Play className="h-4 w-4" />Start browser testing</Button>; break;
    case "TESTING": case "ANALYZING": primary = <LinkButton href={`${base}/testing`}><Loader2 className="h-4 w-4 animate-spin" />Watch live testing</LinkButton>; break;
    case "REPORT_READY": primary = <div className="flex gap-2"><LinkButton href={`${base}/report`}>View report <ArrowRight className="h-4 w-4" /></LinkButton><LinkButton variant="secondary" href={`${base}/preview`}><Wand2 className="h-4 w-4" />Improved preview</LinkButton></div>; break;
    case "FAILED": primary = <Button variant="secondary" loading={busy} onClick={() => run(`${base}/${personas.length ? "start-testing" : "generate-personas"}`, personas.length ? `${base}/testing` : `${base}/personas`)}><RefreshCw className="h-4 w-4" />Retry</Button>; break;
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
      <div className="space-y-6">
        {audit.status === "FAILED" && <ErrorBox title="This audit stopped" message={audit.errorMessage || "Unknown error"} />}
        {err && <ErrorBox message={err} />}
        <Card>
          <CardHeader title="Audit progress" subtitle={audit.progressMessage} action={primary} />
          <div className="grid gap-px overflow-hidden rounded-b-xl bg-line sm:grid-cols-4">
            {PIPE.map((p) => {
              const i = ORDER.indexOf(p.key[0]);
              const done = cur > i || audit.status === "REPORT_READY";
              const active = audit.status === p.key[0] && audit.status !== "REPORT_READY";
              return (
                <div key={p.label} className="flex items-center gap-3 bg-white p-4">
                  {done ? <CheckCircle2 className="h-5 w-5 text-emerald-600" /> : active ? <Loader2 className="h-5 w-5 animate-spin text-brand" /> : <Circle className="h-5 w-5 text-slate-300" />}
                  <div>
                    <p className="text-sm font-medium">{p.label}</p>
                    <p className="text-xs text-muted">{done ? "Done" : active ? "In progress" : "Pending"}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </Card>
        <Card>
          <CardHeader title="Customer journeys" subtitle={personas.length ? `${personas.length} AI personas` : "Personas will appear here once generated."} />
          <div className="divide-y divide-line">
            {personas.length === 0 && <p className="px-5 py-8 text-center text-sm text-muted">No personas yet.</p>}
            {personas.map((p) => (
              <div key={p.id} className="flex items-center justify-between gap-4 px-5 py-3.5">
                <div className="min-w-0">
                  <p className="text-sm font-medium">{p.name}</p>
                  <p className="truncate text-sm text-muted">{p.task?.description}</p>
                </div>
                <div className="flex shrink-0 items-center gap-3">
                  {p.task && p.task.actionCount > 0 && <span className="text-xs tabular-nums text-muted">{p.task.actionCount} actions</span>}
                  <JourneyBadge status={p.task?.status || "PENDING"} />
                </div>
              </div>
            ))}
          </div>
        </Card>
      </div>
      <div className="space-y-6">
        <Card className="p-5">
          <p className="text-sm font-semibold">Experience score</p>
          {audit.overallScore != null ? (
            <div className="mt-4 flex flex-col items-center gap-3 text-center">
              <ScoreRing score={audit.overallScore} />
              <p className="text-xs text-muted">Heuristic score from simulated journeys, not a customer satisfaction metric.</p>
            </div>
          ) : <p className="mt-2 text-sm text-muted">Available once browser testing finishes.</p>}
        </Card>
        <Card className="p-5 text-sm">
          <p className="font-semibold">Details</p>
          <dl className="mt-3 space-y-2 text-muted">
            <div className="flex justify-between"><dt>Created</dt><dd className="text-ink">{new Date(audit.createdAt).toLocaleString()}</dd></div>
            {audit.completedAt && <div className="flex justify-between"><dt>Completed</dt><dd className="text-ink">{new Date(audit.completedAt).toLocaleString()}</dd></div>}
            <div className="flex justify-between"><dt>Max actions / journey</dt><dd className="text-ink">{data.maxActions}</dd></div>
            <div className="flex justify-between"><dt>Status</dt><dd className="font-mono text-xs text-ink">{audit.status}</dd></div>
          </dl>
        </Card>
      </div>
    </div>
  );
}

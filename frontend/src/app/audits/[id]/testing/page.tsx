"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowRight, Lock, MousePointerClick, Play } from "lucide-react";
import { StepItem } from "@/components/StepItem";
import { useAudit } from "@/components/AuditContext";
import { Badge, Button, Card, EmptyState, ErrorBox, JourneyBadge, LinkButton, Notice, Spinner, cn } from "@/components/ui";
import { api, screenshotUrl } from "@/lib/api";
import type { Progress, Step } from "@/lib/types";

export default function Testing() {
  const { data, refresh } = useAudit();
  const router = useRouter();
  const [prog, setProg] = useState<Progress | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [sel, setSel] = useState(0);
  const [pinned, setPinned] = useState<Step | null>(null);
  const [busy, setBusy] = useState(false);
  const id = data?.audit.id;
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const load = useCallback(async () => {
    if (!id) return null;
    try { const p = await api<Progress>(`/audits/${id}/progress`); setProg(p); setErr(null); return p; }
    catch (e) { setErr((e as Error).message); return null; }
  }, [id]);

  useEffect(() => {
    let alive = true;
    const loop = async () => {
      const p = await load();
      if (!alive) return;
      const active = p && ["TESTING", "ANALYZING"].includes(p.status);
      timer.current = setTimeout(loop, active ? 1500 : 6000);
    };
    loop();
    return () => { alive = false; if (timer.current) clearTimeout(timer.current); };
  }, [load]);

  if (!data) return null;
  if (!prog) return err ? <ErrorBox message={err} /> : <Spinner label="Loading test session…" />;

  const start = async () => {
    setBusy(true);
    try { await api(`/audits/${data.audit.id}/start-testing`, { method: "POST" }); await refresh(); await load(); }
    catch (e) { setErr((e as Error).message); } finally { setBusy(false); }
  };

  const notStarted = prog.journeys.every((j) => j.status === "PENDING") && !["TESTING", "ANALYZING"].includes(prog.status);
  if (prog.journeys.length === 0 || (notStarted && prog.status !== "FAILED")) {
    return (
      <EmptyState icon={<MousePointerClick className="h-6 w-6" />} title="Browser testing has not started"
        description={prog.journeys.length === 0 ? "Generate personas first, then start the browser tests." : "When you start, each persona opens the website in a real Chromium browser and tries its task."}
        action={prog.journeys.length === 0
          ? <LinkButton href={`/audits/${data.audit.id}/personas`}>Go to personas</LinkButton>
          : <Button loading={busy} onClick={start}><Play className="h-4 w-4" />Start browser testing</Button>} />
    );
  }

  const j = prog.journeys[Math.min(sel, prog.journeys.length - 1)];
  const latest = j.steps[j.steps.length - 1];
  const shown = pinned && j.steps.some((s) => s.id === pinned.id) ? pinned : latest;
  const running = ["TESTING", "ANALYZING"].includes(prog.status);
  const pct = Math.round((prog.completedJourneys / Math.max(1, prog.totalJourneys)) * 100);

  return (
    <div className="space-y-5">
      {prog.status === "FAILED" && <ErrorBox title="Testing stopped" message={prog.errorMessage || "Unknown error"}
        action={<Button variant="secondary" loading={busy} onClick={start}>Retry browser tests</Button>} />}
      {err && <ErrorBox message={err} />}
      <Card className="p-5">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            {running ? <span className="relative flex h-2.5 w-2.5"><span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-brand opacity-60" /><span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-brand" /></span>
              : <span className="h-2.5 w-2.5 rounded-full bg-emerald-500" />}
            <div>
              <p className="text-sm font-semibold">{running ? (prog.status === "ANALYZING" ? "Analysing evidence" : "Browser tests running") : prog.status === "REPORT_READY" ? "Testing complete" : prog.status}</p>
              <p className="text-xs text-muted">{prog.progressMessage}</p>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <div className="w-48">
              <div className="flex justify-between text-xs text-muted"><span>Journeys</span><span className="tabular-nums">{prog.completedJourneys}/{prog.totalJourneys}</span></div>
              <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-slate-100"><div className="h-full rounded-full bg-brand transition-all" style={{ width: `${pct}%` }} /></div>
            </div>
            {prog.status === "REPORT_READY" && <Button onClick={() => router.push(`/audits/${data.audit.id}/report`)}>View report <ArrowRight className="h-4 w-4" /></Button>}
          </div>
        </div>
      </Card>
      {prog.mode === "DEMO" && <Notice>Demo mode: a keyword heuristic chooses each action instead of Claude. Every navigation, click and screenshot below is still from a real Chromium session.</Notice>}

      <div className="grid gap-5 lg:grid-cols-[300px_1fr]">
        <div className="space-y-2">
          {prog.journeys.map((x, i) => (
            <button key={x.taskId} onClick={() => { setSel(i); setPinned(null); }}
              className={cn("w-full rounded-xl border bg-white p-4 text-left transition", i === sel ? "border-brand ring-2 ring-brand/15" : "border-line hover:border-slate-300")}>
              <div className="flex items-start justify-between gap-2">
                <p className="text-sm font-medium">{x.personaName}</p>
                <JourneyBadge status={x.status} />
              </div>
              <p className="mt-1 line-clamp-2 text-xs text-muted">{x.task}</p>
              <div className="mt-3 flex items-center gap-2">
                <div className="h-1 flex-1 overflow-hidden rounded-full bg-slate-100">
                  <div className={cn("h-full rounded-full", x.status === "SUCCESS" ? "bg-emerald-500" : ["FAILED", "ERROR"].includes(x.status) ? "bg-red-400" : x.status === "PARTIAL" ? "bg-amber-400" : "bg-brand")}
                    style={{ width: `${Math.min(100, (x.actionCount / prog.maxActions) * 100)}%` }} />
                </div>
                <span className="text-[11px] tabular-nums text-muted">{x.actionCount}/{prog.maxActions}</span>
              </div>
            </button>
          ))}
        </div>

        <div className="grid gap-5 xl:grid-cols-[1fr_360px]">
          <Card className="overflow-hidden">
            <div className="flex items-center gap-2 border-b border-line bg-slate-50 px-3 py-2">
              <div className="flex gap-1.5"><span className="h-2.5 w-2.5 rounded-full bg-red-300" /><span className="h-2.5 w-2.5 rounded-full bg-amber-300" /><span className="h-2.5 w-2.5 rounded-full bg-emerald-300" /></div>
              <div className="flex min-w-0 flex-1 items-center gap-1.5 rounded-md border border-line bg-white px-2.5 py-1 text-xs text-muted">
                <Lock className="h-3 w-3 shrink-0" /><span className="truncate">{shown?.url || "about:blank"}</span>
              </div>
              {j.status === "RUNNING" && <Badge tone="blue"><span className="tl-pulse">●</span> Live</Badge>}
            </div>
            <div className="relative aspect-[16/10] bg-slate-100">
              {shown?.screenshot ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img key={shown.screenshot} src={screenshotUrl(shown.screenshot)} alt={`Screenshot at step ${shown.step}`} className="h-full w-full object-cover object-top" />
              ) : (
                <div className="flex h-full items-center justify-center">{j.status === "RUNNING" || j.status === "PENDING" ? <Spinner label="Opening browser…" /> : <p className="text-sm text-muted">No screenshot captured</p>}</div>
              )}
            </div>
            <div className="flex items-center justify-between border-t border-line px-4 py-2.5 text-xs text-muted">
              <span>{shown ? `Real browser screenshot · step ${shown.step} · ${new Date(shown.createdAt).toLocaleTimeString()}` : "Waiting for first step"}</span>
              {pinned && <button className="font-medium text-brand" onClick={() => setPinned(null)}>Back to latest</button>}
            </div>
            {j.outcomeSummary && (
              <div className="border-t border-line bg-canvas px-4 py-3 text-sm"><span className="font-medium">Outcome: </span>{j.outcomeSummary}</div>
            )}
          </Card>

          <Card className="flex max-h-[640px] flex-col">
            <div className="border-b border-line px-4 py-3">
              <p className="text-sm font-semibold">Action log</p>
              <p className="text-xs text-muted">{j.personaName}</p>
            </div>
            <ol className="flex-1 space-y-1 overflow-y-auto p-2">
              {j.steps.length === 0 && <li className="p-4 text-center text-sm text-muted">{j.status === "PENDING" ? "Queued…" : "Starting…"}</li>}
              {[...j.steps].reverse().map((s) => <StepItem key={s.id} s={s} onShot={setPinned} active={shown?.id === s.id} />)}
            </ol>
          </Card>
        </div>
      </div>
    </div>
  );
}

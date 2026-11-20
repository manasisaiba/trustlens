"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AlertTriangle, CheckCircle2, ChevronDown, FileText, Info, Lightbulb, MousePointerClick, Wand2, X } from "lucide-react";
import { useAudit } from "@/components/AuditContext";
import { StepItem } from "@/components/StepItem";
import { Badge, Button, Card, CardHeader, EmptyState, ErrorBox, JourneyBadge, LinkButton, Notice, ScoreRing, SeverityBadge, Spinner, cn } from "@/components/ui";
import { api, screenshotUrl } from "@/lib/api";
import type { Report, Step } from "@/lib/types";

function Lightbox({ step, onClose }: { step: Step; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/70 p-6" onClick={onClose}>
      <div className="max-h-full max-w-5xl overflow-auto rounded-xl bg-white" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between border-b border-line px-4 py-2.5 text-sm">
          <span className="truncate text-muted">Step {step.step} · {step.url}</span>
          <button onClick={onClose} className="rounded p-1 hover:bg-slate-100"><X className="h-4 w-4" /></button>
        </div>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={screenshotUrl(step.screenshot)} alt="Evidence screenshot" className="w-full" />
        {step.observation && <p className="border-t border-line p-4 text-sm">{step.observation}</p>}
      </div>
    </div>
  );
}

export default function ReportPage() {
  const { data } = useAudit();
  const router = useRouter();
  const [report, setReport] = useState<Report | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [open, setOpen] = useState<Record<string, boolean>>({});
  const [shot, setShot] = useState<Step | null>(null);
  const [busy, setBusy] = useState(false);
  const status = data?.audit.status;
  const id = data?.audit.id;

  useEffect(() => {
    if (!id || status !== "REPORT_READY") return;
    api<Report>(`/audits/${id}/report`).then(setReport).catch((e) => setErr(e.message));
  }, [id, status, data?.audit.completedAt]);

  if (!data) return null;
  if (status !== "REPORT_READY") {
    const running = ["TESTING", "ANALYZING"].includes(status || "");
    return (
      <EmptyState icon={<FileText className="h-6 w-6" />} title={running ? "Report is being prepared" : "No report yet"}
        description={running ? "Browser tests are still running. The report appears once every journey has finished." : "Run the browser tests to generate an evidence-backed report."}
        action={<LinkButton href={`/audits/${data.audit.id}/${running ? "testing" : "personas"}`}>{running ? "Watch live testing" : "Go to personas"}</LinkButton>} />
    );
  }
  if (err) return <ErrorBox message={err} />;
  if (!report) return <Spinner label="Loading report…" />;

  const a = report.audit;
  async function genPreview() {
    setBusy(true);
    try {
      if (!data?.preview || data.preview.status === "FAILED") await api(`/audits/${a.id}/generate-preview`, { method: "POST" });
      router.push(`/audits/${a.id}/preview`);
    } catch (e) { setErr((e as Error).message); setBusy(false); }
  }

  return (
    <div className="space-y-6">
      {shot && <Lightbox step={shot} onClose={() => setShot(null)} />}
      <div className="grid gap-6 lg:grid-cols-[300px_1fr]">
        <Card className="flex flex-col items-center p-6 text-center">
          <p className="text-sm font-semibold">Experience score</p>
          <div className="my-5"><ScoreRing score={a.overallScore ?? 0} size={140} /></div>
          <p className="text-xs leading-relaxed text-muted">{a.scoreExplanation}</p>
        </Card>
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-4">
            {[
              ["Journeys tested", report.stats.journeysTested, MousePointerClick, "text-brand"],
              ["Successful", report.stats.successful, CheckCircle2, "text-emerald-600"],
              ["Partial / failed", report.stats.partialOrFailed, AlertTriangle, "text-amber-600"],
              ["High priority", report.highPriority, Info, "text-red-600"],
            ].map(([l, v, I, c]) => {
              const Icon = I as typeof Info;
              return (
                <Card key={l as string} className="p-4">
                  <Icon className={cn("h-4 w-4", c as string)} />
                  <p className="mt-3 text-2xl font-semibold tabular-nums">{v as number}</p>
                  <p className="text-xs text-muted">{l as string}</p>
                </Card>
              );
            })}
          </div>
          <Card>
            <CardHeader title="Summary" action={<Button loading={busy} onClick={genPreview}><Wand2 className="h-4 w-4" />{data.preview ? "Open improved preview" : "Generate improved preview"}</Button>} />
            <div className="space-y-3 p-5 text-sm">
              <p>{a.reportSummary}</p>
              <Notice tone="blue" icon={<Info className="h-4 w-4" />}>
                These are <strong>simulated customer journey findings</strong>, based on AI personas browsing the site. They show where a visitor
                with these goals could struggle; they do not prove real customers left the site. No real customer review data was used in this audit.
              </Notice>
            </div>
          </Card>
        </div>
      </div>

      <div>
        <h2 className="mb-3 text-lg font-semibold">Findings <span className="text-sm font-normal text-muted">({report.findings.length})</span></h2>
        {report.findings.length === 0 && (
          <EmptyState icon={<CheckCircle2 className="h-6 w-6" />} title="No friction found" description="Every persona completed its task efficiently in the tested journeys." />
        )}
        <div className="space-y-3">
          {report.findings.map(({ finding: f, evidence }, idx) => {
            const isOpen = open[f.id] ?? (f.severity === "high" || idx === 0);
            const shotStep = evidence.find((e) => e.screenshot) || null;
            return (
              <Card key={f.id} className={cn(f.severity === "high" && "border-l-4 border-l-red-400", f.severity === "medium" && "border-l-4 border-l-amber-400")}>
                <button onClick={() => setOpen({ ...open, [f.id]: !isOpen })} className="flex w-full items-start justify-between gap-4 px-5 py-4 text-left">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <SeverityBadge severity={f.severity} />
                      <Badge tone="violet">Simulated journey</Badge>
                      {f.personaName && <span className="text-xs text-muted">found by {f.personaName}</span>}
                    </div>
                    <h3 className="mt-2 font-semibold">{f.title}</h3>
                    {f.affectedUrl && <p className="mt-0.5 truncate text-xs text-muted">{f.affectedUrl}</p>}
                  </div>
                  <ChevronDown className={cn("mt-1 h-4 w-4 shrink-0 text-muted transition", isOpen && "rotate-180")} />
                </button>
                {isOpen && (
                  <div className="grid gap-5 border-t border-line p-5 lg:grid-cols-[1fr_320px]">
                    <div className="space-y-4 text-sm">
                      <p>{f.description}</p>
                      {f.frictionReason && <div><p className="text-xs font-semibold uppercase tracking-wide text-muted">Why this creates friction</p><p className="mt-1">{f.frictionReason}</p></div>}
                      <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3.5">
                        <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-emerald-800"><Lightbulb className="h-3.5 w-3.5" />Recommendation</p>
                        <p className="mt-1 text-emerald-900">{f.recommendation}</p>
                      </div>
                      <div>
                        <p className="text-xs font-semibold uppercase tracking-wide text-muted">Browser evidence</p>
                        {f.evidenceSummary && <p className="mt-1 text-muted">{f.evidenceSummary}</p>}
                        <ol className="mt-2 space-y-1 rounded-lg border border-line p-1">{evidence.map((s) => <StepItem key={s.id} s={s} onShot={setShot} />)}</ol>
                      </div>
                    </div>
                    <div>
                      {shotStep ? (
                        <button onClick={() => setShot(shotStep)} className="block w-full overflow-hidden rounded-lg border border-line text-left">
                          {/* eslint-disable-next-line @next/next/no-img-element */}
                          <img src={screenshotUrl(f.screenshotPath || shotStep.screenshot)} alt="Evidence" className="aspect-[16/10] w-full object-cover object-top" />
                          <p className="border-t border-line px-3 py-2 text-xs text-muted">Screenshot evidence · click to enlarge</p>
                        </button>
                      ) : <p className="text-xs text-muted">No screenshot for this finding.</p>}
                    </div>
                  </div>
                )}
              </Card>
            );
          })}
        </div>
      </div>

      <Card>
        <CardHeader title="Journeys tested" subtitle="Outcome of each simulated customer journey" />
        <div className="divide-y divide-line">
          {report.journeys.map((j) => (
            <div key={j.taskId} className="flex items-start justify-between gap-4 px-5 py-3.5 text-sm">
              <div className="min-w-0">
                <p className="font-medium">{j.personaName}</p>
                <p className="text-muted">{j.task}</p>
                {j.outcomeSummary && <p className="mt-1 text-xs text-muted">{j.outcomeSummary}</p>}
              </div>
              <div className="flex shrink-0 items-center gap-3"><span className="text-xs tabular-nums text-muted">{j.actionCount} actions</span><JourneyBadge status={j.status} /></div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}

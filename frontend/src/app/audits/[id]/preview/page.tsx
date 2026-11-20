"use client";
import { useState } from "react";
import { ArrowRight, Download, ExternalLink, Monitor, RefreshCw, Smartphone, Wand2 } from "lucide-react";
import { useAudit } from "@/components/AuditContext";
import { usePreview } from "@/components/usePreview";
import { Badge, Button, Card, EmptyState, ErrorBox, LinkButton, Notice, Spinner, cn } from "@/components/ui";
import { screenshotUrl } from "@/lib/api";

export default function PreviewPage() {
  const { data } = useAudit();
  const { preview, missing, error, generate } = usePreview(data?.audit.id);
  const [busy, setBusy] = useState(false);
  const [device, setDevice] = useState<"desktop" | "mobile">("desktop");
  if (!data) return null;
  const ready = data.audit.status === "REPORT_READY";
  const run = async () => { setBusy(true); await generate(); setBusy(false); };

  if (!ready) {
    return <EmptyState icon={<Wand2 className="h-6 w-6" />} title="Finish the audit first" description="The improved preview is based on the findings in the audit report."
      action={<LinkButton href={`/audits/${data.audit.id}`}>Back to overview</LinkButton>} />;
  }
  if (missing && !preview) {
    return (
      <div className="space-y-4">
        {error && <ErrorBox message={error} />}
        <EmptyState icon={<Wand2 className="h-6 w-6" />} title="Generate an improved website preview"
          description={data.audit.mode === "LIVE" ? "Claude redesigns the page to fix the findings, using only facts found on your website." : "Demo mode builds a template page from facts found on your website."}
          action={<Button loading={busy} onClick={run}><Wand2 className="h-4 w-4" />Generate preview</Button>} />
      </div>
    );
  }
  if (!preview) return error ? <ErrorBox message={error} /> : <Spinner label="Loading preview…" />;
  if (preview.status === "GENERATING") {
    return (
      <Card className="flex flex-col items-center justify-center gap-3 py-20 text-center">
        <Spinner label={preview.mode === "LIVE" ? "Claude is designing the improved page… (this can take up to a minute)" : "Building the demo preview…"} />
        <p className="max-w-md text-xs text-muted">It uses only the audit findings and the text the browser actually saw on your site. Missing facts are marked for you to verify.</p>
      </Card>
    );
  }
  if (preview.status === "FAILED") {
    return <ErrorBox title="Preview generation failed" message={preview.errorMessage || ""} action={<Button variant="secondary" loading={busy} onClick={run}><RefreshCw className="h-4 w-4" />Try again</Button>} />;
  }

  const renderUrl = `/api/previews/${preview.id}/render`;
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold">Original vs. TrustLens improved preview</h2>
          <p className="text-sm text-muted">The preview runs inside TrustLens in a sandboxed frame with scripts disabled. Nothing is published to your website.</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex rounded-lg border border-line bg-white p-0.5">
            {(["desktop", "mobile"] as const).map((d) => (
              <button key={d} onClick={() => setDevice(d)} className={cn("flex items-center gap-1 rounded-md px-2.5 py-1 text-xs", device === d ? "bg-slate-100 text-ink" : "text-muted")}>
                {d === "desktop" ? <Monitor className="h-3.5 w-3.5" /> : <Smartphone className="h-3.5 w-3.5" />}{d}
              </button>
            ))}
          </div>
          <a href={`/api/previews/${preview.id}/export`} className="inline-flex items-center gap-1.5 rounded-lg border border-line bg-white px-3 py-2 text-sm hover:bg-slate-50"><Download className="h-4 w-4" />Export HTML</a>
          <Button variant="secondary" loading={busy} onClick={run}><RefreshCw className="h-4 w-4" />Regenerate</Button>
          <LinkButton href={`/audits/${data.audit.id}/approval`}>Review &amp; approve <ArrowRight className="h-4 w-4" /></LinkButton>
        </div>
      </div>
      {preview.verificationNotes && (
        <Notice><strong>Owner verification needed:</strong> {preview.verificationNotes} Items marked <span className="rounded border border-dashed border-amber-500 bg-amber-100 px-1">[Owner to verify]</span> in the preview were not found on your current site and were not invented.</Notice>
      )}
      <div className={cn("grid gap-5", device === "desktop" ? "xl:grid-cols-2" : "md:grid-cols-2")}>
        <Card className="overflow-hidden">
          <div className="flex items-center justify-between border-b border-line px-4 py-2.5">
            <div className="flex items-center gap-2 text-sm font-medium">Original website <Badge>screenshot</Badge></div>
            <a href={data.business.websiteUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-xs text-muted hover:text-brand">Open live <ExternalLink className="h-3 w-3" /></a>
          </div>
          <div className={cn("h-[720px] overflow-y-auto bg-slate-100", device === "mobile" && "mx-auto max-w-[390px]")}>
            {preview.baselineScreenshot
              // eslint-disable-next-line @next/next/no-img-element
              ? <img src={screenshotUrl(preview.baselineScreenshot)} alt="Original homepage (full page)" className="w-full" />
              : <p className="p-6 text-sm text-muted">No baseline screenshot.</p>}
          </div>
          <p className="border-t border-line px-4 py-2 text-xs text-muted">Full-page screenshot of the homepage captured by the browser during the audit.</p>
        </Card>
        <Card className="overflow-hidden border-brand/40 ring-2 ring-brand/10">
          <div className="flex items-center justify-between border-b border-line px-4 py-2.5">
            <div className="flex items-center gap-2 text-sm font-medium">TrustLens improved preview <Badge tone={preview.mode === "LIVE" ? "violet" : "amber"}>{preview.mode === "LIVE" ? "Claude generated" : "Demo template"}</Badge></div>
            <Badge tone={preview.status === "APPROVED" ? "green" : preview.status === "REJECTED" ? "red" : "slate"}>{preview.status}</Badge>
          </div>
          <div className="flex h-[720px] justify-center bg-slate-100">
            <iframe title="Improved preview" src={renderUrl} sandbox="" referrerPolicy="no-referrer"
              className={cn("h-full bg-white", device === "mobile" ? "w-[390px] border-x border-line" : "w-full")} />
          </div>
          <p className="border-t border-line px-4 py-2 text-xs text-muted">Interactive, sandboxed preview. Links and the enquiry form are for preview only.</p>
        </Card>
      </div>
      <Card className="p-5">
        <p className="text-sm font-semibold">Proposed changes ({preview.changes.length})</p>
        <ul className="mt-3 grid gap-3 md:grid-cols-2">
          {preview.changes.map((c) => (
            <li key={c.id} className="rounded-lg border border-line p-3.5 text-sm">
              <div className="flex items-start justify-between gap-2"><p className="font-medium">{c.title}</p>{c.needsVerification && <Badge tone="amber">verify</Badge>}</div>
              <p className="mt-1 text-muted">{c.description}</p>
              {c.relatedFinding && <p className="mt-2 text-xs text-muted">Addresses: {c.relatedFinding}</p>}
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}

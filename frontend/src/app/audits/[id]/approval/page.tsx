"use client";
import { useState } from "react";
import { Check, CheckCircle2, Download, Eye, ShieldAlert, X, XCircle } from "lucide-react";
import { useAudit } from "@/components/AuditContext";
import { usePreview } from "@/components/usePreview";
import { Badge, Button, Card, CardHeader, EmptyState, ErrorBox, LinkButton, Notice, Spinner, cn } from "@/components/ui";
import { api } from "@/lib/api";
import type { Preview } from "@/lib/types";

export default function Approval() {
  const { data, refresh } = useAudit();
  const { preview, setPreview, missing, error } = usePreview(data?.audit.id);
  const [notes, setNotes] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  if (!data) return null;

  if (missing || (preview && ["GENERATING", "FAILED"].includes(preview.status))) {
    return <EmptyState icon={<Eye className="h-6 w-6" />} title="No preview to review yet" description="Generate the improved website preview first, then review the proposed changes here."
      action={<LinkButton href={`/audits/${data.audit.id}/preview`}>Go to preview</LinkButton>} />;
  }
  if (!preview) return error ? <ErrorBox message={error} /> : <Spinner label="Loading…" />;

  async function change(id: string, status: string) {
    setBusy(id + status); setErr(null);
    try { setPreview(await api<Preview>(`/previews/${preview!.id}/changes/${id}`, { method: "PATCH", json: { status } })); }
    catch (e) { setErr((e as Error).message); } finally { setBusy(null); }
  }
  async function decide(kind: "approve" | "reject") {
    setBusy(kind); setErr(null);
    try { setPreview(await api<Preview>(`/previews/${preview!.id}/${kind}`, { method: "POST", json: { notes } })); setNotes(""); await refresh(); }
    catch (e) { setErr((e as Error).message); } finally { setBusy(null); }
  }

  const approved = preview.changes.filter((c) => c.status === "APPROVED").length;
  const rejected = preview.changes.filter((c) => c.status === "REJECTED").length;
  const verify = preview.changes.filter((c) => c.needsVerification).length;

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
      <div className="space-y-4">
        {err && <ErrorBox message={err} />}
        {preview.status === "APPROVED" && <Notice tone="blue" icon={<CheckCircle2 className="h-4 w-4" />}>This preview is approved. Export the HTML and hand it to whoever manages your website. TrustLens never publishes to your site automatically.</Notice>}
        {preview.status === "REJECTED" && <Notice icon={<XCircle className="h-4 w-4" />}>This preview was rejected. You can regenerate it from the preview page.</Notice>}
        <Card>
          <CardHeader title="Proposed changes" subtitle={`${approved} approved · ${rejected} rejected · ${preview.changes.length - approved - rejected} pending`} />
          <ul className="divide-y divide-line">
            {preview.changes.map((c) => (
              <li key={c.id} className="flex flex-wrap items-start justify-between gap-4 px-5 py-4">
                <div className="min-w-0 flex-1 text-sm">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-medium">{c.title}</p>
                    {c.needsVerification && <Badge tone="amber"><ShieldAlert className="h-3 w-3" />Needs your verification</Badge>}
                  </div>
                  <p className="mt-1 text-muted">{c.description}</p>
                  {c.relatedFinding && <p className="mt-1.5 text-xs text-muted">Addresses finding: {c.relatedFinding}</p>}
                </div>
                <div className="flex shrink-0 gap-1.5">
                  <button onClick={() => change(c.id, c.status === "APPROVED" ? "PENDING" : "APPROVED")} disabled={!!busy}
                    className={cn("inline-flex items-center gap-1 rounded-lg border px-2.5 py-1.5 text-xs font-medium", c.status === "APPROVED" ? "border-emerald-600 bg-emerald-600 text-white" : "border-line hover:bg-emerald-50")}>
                    <Check className="h-3.5 w-3.5" />{c.status === "APPROVED" ? "Approved" : "Approve"}
                  </button>
                  <button onClick={() => change(c.id, c.status === "REJECTED" ? "PENDING" : "REJECTED")} disabled={!!busy}
                    className={cn("inline-flex items-center gap-1 rounded-lg border px-2.5 py-1.5 text-xs font-medium", c.status === "REJECTED" ? "border-red-600 bg-red-600 text-white" : "border-line hover:bg-red-50")}>
                    <X className="h-3.5 w-3.5" />{c.status === "REJECTED" ? "Rejected" : "Reject"}
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </Card>
        {preview.approvals.length > 0 && (
          <Card>
            <CardHeader title="Decision history" />
            <ul className="divide-y divide-line text-sm">
              {preview.approvals.map((a) => (
                <li key={a.id} className="flex items-center justify-between px-5 py-3">
                  <span className="flex items-center gap-2"><Badge tone={a.status === "APPROVED" ? "green" : "red"}>{a.status}</Badge>{a.notes && <span className="text-muted">{a.notes}</span>}</span>
                  <span className="text-xs text-muted">{new Date(a.reviewedAt).toLocaleString()}</span>
                </li>
              ))}
            </ul>
          </Card>
        )}
      </div>
      <div className="space-y-4">
        <Card className="space-y-4 p-5">
          <div className="flex items-center justify-between"><p className="text-sm font-semibold">Owner decision</p>
            <Badge tone={preview.status === "APPROVED" ? "green" : preview.status === "REJECTED" ? "red" : "slate"}>{preview.status}</Badge></div>
          {verify > 0 && <p className="text-xs text-amber-800">{verify} change(s) depend on information you need to confirm before publishing.</p>}
          <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={3} maxLength={2000} placeholder="Notes for your web team (optional)"
            className="w-full rounded-lg border border-line px-3 py-2 text-sm outline-none focus:border-brand" />
          <div className="grid grid-cols-2 gap-2">
            <Button variant="success" loading={busy === "approve"} onClick={() => decide("approve")}><Check className="h-4 w-4" />Approve</Button>
            <Button variant="danger" loading={busy === "reject"} onClick={() => decide("reject")}><X className="h-4 w-4" />Reject</Button>
          </div>
          <div className="grid gap-2 border-t border-line pt-4">
            <LinkButton variant="secondary" href={`/audits/${data.audit.id}/preview`}><Eye className="h-4 w-4" />View preview</LinkButton>
            <a href={`/api/previews/${preview.id}/export`} className="inline-flex items-center justify-center gap-2 rounded-lg border border-line bg-white px-4 py-2 text-sm font-medium hover:bg-slate-50"><Download className="h-4 w-4" />Export HTML/CSS</a>
          </div>
        </Card>
        <p className="px-1 text-xs text-muted">Publishing to your live website is out of scope for this MVP. Approving records your decision; it does not change your site.</p>
      </div>
    </div>
  );
}

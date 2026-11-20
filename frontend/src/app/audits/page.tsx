"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowRight, ClipboardList, Plus } from "lucide-react";
import AppShell from "@/components/AppShell";
import { Badge, Card, EmptyState, ErrorBox, LinkButton, ModeBadge, Spinner } from "@/components/ui";
import { api } from "@/lib/api";
import type { Business, Mode } from "@/lib/types";

interface Row { id: string; status: string; mode: Mode; overallScore?: number; createdAt: string; business?: Business }

export default function Audits() {
  const [rows, setRows] = useState<Row[] | null>(null);
  const [err, setErr] = useState<string | null>(null);
  useEffect(() => { api<Row[]>("/audits").then(setRows).catch((e) => setErr(e.message)); }, []);
  return (
    <AppShell>
      <div className="mx-auto max-w-7xl px-6 py-8">
        <div className="mb-6 flex items-end justify-between">
          <div><h1 className="text-xl font-semibold tracking-tight">Audits</h1><p className="text-sm text-muted">Recent customer-experience audits</p></div>
          <LinkButton href="/onboard"><Plus className="h-4 w-4" />New audit</LinkButton>
        </div>
        {err && <ErrorBox message={err} />}
        {!rows && !err && <Spinner label="Loading audits…" />}
        {rows && rows.length === 0 && <EmptyState icon={<ClipboardList className="h-6 w-6" />} title="No audits yet" description="Enter a website URL to run your first AI customer audit." action={<LinkButton href="/onboard">Start an audit</LinkButton>} />}
        {rows && rows.length > 0 && (
          <Card className="divide-y divide-line">
            {rows.map((r) => (
              <Link key={r.id} href={`/audits/${r.id}`} className="flex items-center justify-between gap-4 px-5 py-4 hover:bg-slate-50">
                <div className="min-w-0">
                  <p className="font-medium">{r.business?.name}</p>
                  <p className="truncate text-sm text-muted">{r.business?.websiteUrl} · {r.business?.category}</p>
                </div>
                <div className="flex shrink-0 items-center gap-3">
                  <ModeBadge mode={r.mode} />
                  <Badge tone={r.status === "REPORT_READY" ? "green" : r.status === "FAILED" ? "red" : "blue"}>{r.status.replace("_", " ").toLowerCase()}</Badge>
                  <span className="w-12 text-right text-sm font-semibold tabular-nums">{r.overallScore ?? "—"}</span>
                  <span className="hidden w-36 text-right text-xs text-muted md:block">{new Date(r.createdAt).toLocaleString()}</span>
                  <ArrowRight className="h-4 w-4 text-muted" />
                </div>
              </Link>
            ))}
          </Card>
        )}
      </div>
    </AppShell>
  );
}

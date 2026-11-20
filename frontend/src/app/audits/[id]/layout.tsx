"use client";
import Link from "next/link";
import { use } from "react";
import { usePathname } from "next/navigation";
import { CheckCircle2, ExternalLink } from "lucide-react";
import AppShell from "@/components/AppShell";
import { AuditProvider, useAudit } from "@/components/AuditContext";
import { ErrorBox, ModeBadge, Spinner, cn } from "@/components/ui";
import type { AuditStatus } from "@/lib/types";

const ORDER: AuditStatus[] = ["CREATED", "GENERATING_PERSONAS", "PERSONAS_READY", "TESTING", "ANALYZING", "REPORT_READY"];

function Header({ id }: { id: string }) {
  const { data, error } = useAudit();
  const path = usePathname();
  if (error && !data) return <div className="mx-auto max-w-7xl px-6 py-10"><ErrorBox message={error} /></div>;
  if (!data) return <div className="mx-auto max-w-7xl px-6 py-10"><Spinner label="Loading audit…" /></div>;
  const s = data.audit.status;
  const idx = ORDER.indexOf(s);
  const tabs = [
    { href: "", label: "Overview", done: true },
    { href: "/personas", label: "Personas", done: idx >= 2 },
    { href: "/testing", label: "Live testing", done: idx >= 5 },
    { href: "/report", label: "Report", done: s === "REPORT_READY" },
    { href: "/preview", label: "Improved preview", done: !!data.preview && data.preview.status !== "GENERATING" && data.preview.status !== "FAILED" },
    { href: "/approval", label: "Approval", done: data.preview?.status === "APPROVED" },
  ];
  const base = `/audits/${id}`;
  return (
    <div className="border-b border-line bg-white">
      <div className="mx-auto max-w-7xl px-6 pt-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 text-xs text-muted">
              <Link href="/audits" className="hover:text-ink">Audits</Link><span>/</span><span>{data.business.category}</span>
            </div>
            <h1 className="mt-1 text-xl font-semibold tracking-tight">{data.business.name}</h1>
            <a href={data.business.websiteUrl} target="_blank" rel="noreferrer" className="mt-0.5 inline-flex items-center gap-1 text-sm text-muted hover:text-brand">
              {data.business.websiteUrl} <ExternalLink className="h-3 w-3" />
            </a>
          </div>
          <ModeBadge mode={data.audit.mode} />
        </div>
        <nav className="-mb-px mt-5 flex gap-1 overflow-x-auto">
          {tabs.map((t) => {
            const href = base + t.href;
            const active = t.href === "" ? path === base : path.startsWith(href);
            return (
              <Link key={t.label} href={href}
                className={cn("flex items-center gap-1.5 whitespace-nowrap border-b-2 px-3 pb-3 text-sm",
                  active ? "border-brand font-medium text-ink" : "border-transparent text-muted hover:text-ink")}>
                {t.done && t.href !== "" && <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />}
                {t.label}
              </Link>
            );
          })}
        </nav>
      </div>
    </div>
  );
}

export default function AuditLayout({ children, params }: { children: React.ReactNode; params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <AppShell>
      <AuditProvider id={id}>
        <Header id={id} />
        <div className="mx-auto max-w-7xl px-6 py-8">{children}</div>
      </AuditProvider>
    </AppShell>
  );
}

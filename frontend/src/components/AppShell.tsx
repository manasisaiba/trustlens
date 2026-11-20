"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { Plus } from "lucide-react";
import { api } from "@/lib/api";
import type { SystemStatus } from "@/lib/types";
import { cn, Logo } from "./ui";

export function useSystemStatus() {
  const [status, setStatus] = useState<SystemStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    api<SystemStatus>("/system/status").then(setStatus).catch((e) => setError(e.message));
  }, []);
  return { status, error };
}

export default function AppShell({ children }: { children: React.ReactNode }) {
  const path = usePathname();
  const { status, error } = useSystemStatus();
  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-30 border-b border-line bg-white/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-7xl items-center justify-between px-6">
          <div className="flex items-center gap-6">
            <Logo />
            <nav className="hidden items-center gap-1 sm:flex">
              <Link href="/audits" className={cn("rounded-md px-3 py-1.5 text-sm", path.startsWith("/audits") ? "bg-slate-100 text-ink" : "text-muted hover:text-ink")}>Audits</Link>
            </nav>
          </div>
          <div className="flex items-center gap-3">
            <div className="hidden items-center gap-3 text-xs text-muted md:flex">
              {error ? (
                <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-red-500" /> API offline</span>
              ) : status ? (
                <>
                  <span className="flex items-center gap-1.5" title={status.model || ""}>
                    <span className={cn("h-2 w-2 rounded-full", status.claudeConfigured ? "bg-emerald-500" : "bg-amber-500")} />
                    {status.claudeConfigured ? "Claude connected" : "Claude not configured"}
                  </span>
                  <span className="flex items-center gap-1.5">
                    <span className={cn("h-2 w-2 rounded-full", status.workerHealthy ? "bg-emerald-500" : "bg-red-500")} />
                    Browser worker {status.workerHealthy ? "ready" : "offline"}
                  </span>
                </>
              ) : null}
            </div>
            <Link href="/onboard" className="inline-flex items-center gap-1.5 rounded-lg bg-brand px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-600">
              <Plus className="h-4 w-4" /> New audit
            </Link>
          </div>
        </div>
      </header>
      <main>{children}</main>
    </div>
  );
}

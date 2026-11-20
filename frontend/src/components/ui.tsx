"use client";
import clsx from "clsx";
import Link from "next/link";
import { AlertTriangle, CheckCircle2, CircleDashed, FlaskConical, Loader2, Sparkles, XCircle } from "lucide-react";
import type { ButtonHTMLAttributes, ReactNode } from "react";

export const cn = clsx;

export function Logo({ className }: { className?: string }) {
  return (
    <Link href="/" className={cn("flex items-center gap-2 font-semibold tracking-tight text-ink", className)}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/icon.svg" alt="" className="h-7 w-7" />
      <span>TrustLens</span>
    </Link>
  );
}

type Variant = "primary" | "secondary" | "ghost" | "danger" | "success";
export function Button({ variant = "primary", loading, className, children, ...rest }:
  ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; loading?: boolean }) {
  return (
    <button
      {...rest}
      disabled={rest.disabled || loading}
      className={cn(
        "inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-brand/40 disabled:cursor-not-allowed disabled:opacity-60",
        variant === "primary" && "bg-brand text-white shadow-sm hover:bg-brand-600",
        variant === "secondary" && "border border-line bg-white text-ink shadow-sm hover:bg-slate-50",
        variant === "ghost" && "text-muted hover:bg-slate-100 hover:text-ink",
        variant === "danger" && "border border-red-200 bg-white text-red-700 hover:bg-red-50",
        variant === "success" && "bg-emerald-600 text-white hover:bg-emerald-700",
        className,
      )}
    >
      {loading && <Loader2 className="h-4 w-4 animate-spin" />}
      {children}
    </button>
  );
}

export function LinkButton({ href, children, variant = "primary", className }: { href: string; children: ReactNode; variant?: Variant; className?: string }) {
  return (
    <Link
      href={href}
      className={cn(
        "inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition-colors",
        variant === "primary" && "bg-brand text-white shadow-sm hover:bg-brand-600",
        variant === "secondary" && "border border-line bg-white text-ink shadow-sm hover:bg-slate-50",
        variant === "ghost" && "text-muted hover:bg-slate-100 hover:text-ink",
        className,
      )}
    >
      {children}
    </Link>
  );
}

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cn("rounded-xl border border-line bg-white shadow-[0_1px_2px_rgba(16,24,40,0.04)]", className)}>{children}</div>;
}

export function CardHeader({ title, subtitle, action, icon }: { title: ReactNode; subtitle?: ReactNode; action?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-line px-5 py-4">
      <div className="flex items-start gap-3">
        {icon && <div className="mt-0.5 text-brand">{icon}</div>}
        <div>
          <h3 className="text-sm font-semibold text-ink">{title}</h3>
          {subtitle && <p className="mt-0.5 text-sm text-muted">{subtitle}</p>}
        </div>
      </div>
      {action}
    </div>
  );
}

export function Badge({ children, tone = "slate", className }: { children: ReactNode; tone?: "slate" | "blue" | "green" | "amber" | "red" | "violet"; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium",
        tone === "slate" && "border-slate-200 bg-slate-50 text-slate-700",
        tone === "blue" && "border-indigo-200 bg-indigo-50 text-indigo-700",
        tone === "green" && "border-emerald-200 bg-emerald-50 text-emerald-700",
        tone === "amber" && "border-amber-200 bg-amber-50 text-amber-800",
        tone === "red" && "border-red-200 bg-red-50 text-red-700",
        tone === "violet" && "border-violet-200 bg-violet-50 text-violet-700",
        className,
      )}
    >
      {children}
    </span>
  );
}

export function ModeBadge({ mode }: { mode: "LIVE" | "DEMO" }) {
  return mode === "LIVE" ? (
    <Badge tone="violet"><Sparkles className="h-3 w-3" /> Live · Claude</Badge>
  ) : (
    <Badge tone="amber"><FlaskConical className="h-3 w-3" /> Demo mode · no Claude</Badge>
  );
}

export function JourneyBadge({ status }: { status: string }) {
  switch (status) {
    case "SUCCESS": return <Badge tone="green"><CheckCircle2 className="h-3 w-3" /> Completed</Badge>;
    case "PARTIAL": return <Badge tone="amber"><AlertTriangle className="h-3 w-3" /> Partial</Badge>;
    case "FAILED": return <Badge tone="red"><XCircle className="h-3 w-3" /> Failed</Badge>;
    case "ERROR": return <Badge tone="red"><XCircle className="h-3 w-3" /> Error</Badge>;
    case "RUNNING": return <Badge tone="blue"><Loader2 className="h-3 w-3 animate-spin" /> Testing</Badge>;
    default: return <Badge><CircleDashed className="h-3 w-3" /> Waiting</Badge>;
  }
}

export function SeverityBadge({ severity }: { severity: string }) {
  const tone = severity === "high" ? "red" : severity === "medium" ? "amber" : "slate";
  return <Badge tone={tone} className="uppercase tracking-wide">{severity}</Badge>;
}

export function Spinner({ label }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 text-sm text-muted">
      <Loader2 className="h-4 w-4 animate-spin text-brand" /> {label}
    </div>
  );
}

export function ErrorBox({ title = "Something went wrong", message, action }: { title?: string; message: string; action?: ReactNode }) {
  return (
    <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
      <XCircle className="mt-0.5 h-4 w-4 shrink-0" />
      <div className="flex-1">
        <p className="font-medium">{title}</p>
        <p className="mt-1 text-red-700">{message}</p>
        {action && <div className="mt-3">{action}</div>}
      </div>
    </div>
  );
}

export function Notice({ children, tone = "amber", icon }: { children: ReactNode; tone?: "amber" | "blue"; icon?: ReactNode }) {
  return (
    <div className={cn("flex items-start gap-3 rounded-xl border p-3.5 text-sm",
      tone === "amber" ? "border-amber-200 bg-amber-50 text-amber-900" : "border-indigo-200 bg-indigo-50 text-indigo-900")}>
      <div className="mt-0.5 shrink-0">{icon ?? <AlertTriangle className="h-4 w-4" />}</div>
      <div>{children}</div>
    </div>
  );
}

export function EmptyState({ icon, title, description, action }: { icon: ReactNode; title: string; description: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-line bg-white px-6 py-14 text-center">
      <div className="mb-3 rounded-full bg-brand-50 p-3 text-brand">{icon}</div>
      <h3 className="text-base font-semibold">{title}</h3>
      <p className="mt-1 max-w-md text-sm text-muted">{description}</p>
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function ScoreRing({ score, size = 120 }: { score: number; size?: number }) {
  const r = (size - 14) / 2;
  const c = 2 * Math.PI * r;
  const color = score >= 75 ? "#059669" : score >= 50 ? "#d97706" : "#dc2626";
  return (
    <div className="relative" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={r} stroke="#eef0f5" strokeWidth={10} fill="none" />
        <circle cx={size / 2} cy={size / 2} r={r} stroke={color} strokeWidth={10} fill="none" strokeLinecap="round"
          strokeDasharray={c} strokeDashoffset={c * (1 - score / 100)} />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-3xl font-semibold tabular-nums">{score}</span>
        <span className="text-[11px] text-muted">/ 100</span>
      </div>
    </div>
  );
}

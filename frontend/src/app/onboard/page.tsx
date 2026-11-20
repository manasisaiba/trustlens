"use client";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Building2, FlaskConical, Globe, KeyRound, ShieldCheck, Sparkles } from "lucide-react";
import AppShell, { useSystemStatus } from "@/components/AppShell";
import { Button, Card, ErrorBox, Notice, cn } from "@/components/ui";
import { api } from "@/lib/api";
import type { AuditSession, Business } from "@/lib/types";

const CATEGORIES = ["Dental clinic", "Restaurant", "Law firm", "Salon & spa", "Gym & fitness", "Real estate agency", "Veterinary clinic", "Hotel", "Online store", "Accounting firm", "Plumber / home services", "School / coaching"];

export default function Onboard() {
  const router = useRouter();
  const { status } = useSystemStatus();
  const [name, setName] = useState("");
  const [url, setUrl] = useState("");
  const [category, setCategory] = useState("");
  const [description, setDescription] = useState("");
  const [authorized, setAuthorized] = useState(false);
  const [demoChosen, setDemoChosen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErr, setFieldErr] = useState<Record<string, string>>({});

  const claudeOk = status?.claudeConfigured ?? false;
  const mode = claudeOk && !demoChosen ? "LIVE" : "DEMO";

  const clearErr = (k: string) => setFieldErr((m) => { const n = { ...m }; delete n[k]; return n; });

  function useSample() {
    setName("Bright Dental Clinic");
    setUrl(`${window.location.origin}/sample-site/index.html`);
    setCategory("Dental clinic");
    setDescription("Family dental clinic (fictional sample website bundled with TrustLens for demos).");
    setAuthorized(true);
    setFieldErr({});
  }

  function validate() {
    const e: Record<string, string> = {};
    if (!name.trim()) e.name = "Business name is required.";
    if (!category.trim()) e.category = "Choose or type a category.";
    try {
      const u = new URL(/^https?:\/\//i.test(url.trim()) ? url.trim() : "https://" + url.trim());
      if (!u.hostname.includes(".") && u.hostname !== "localhost") e.url = "Enter a full website address, e.g. https://example.com";
    } catch {
      e.url = "Enter a valid website URL.";
    }
    if (!authorized) e.authorized = "Please confirm you are allowed to test this website.";
    setFieldErr(e);
    return Object.keys(e).length === 0;
  }

  async function submit(ev: React.FormEvent) {
    ev.preventDefault();
    setError(null);
    if (!validate()) return;
    if (!claudeOk && !demoChosen) {
      setError("Claude is not configured on the server. Turn on Demo mode below to continue without it.");
      return;
    }
    setBusy(true);
    try {
      const b = await api<Business>("/businesses", { method: "POST", json: { name, websiteUrl: url, category, description } });
      const a = await api<AuditSession>("/audits", { method: "POST", json: { businessId: b.id, mode } });
      await api(`/audits/${a.id}/generate-personas`, { method: "POST" });
      router.push(`/audits/${a.id}/personas`);
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  }

  const input = "w-full rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none transition focus:border-brand focus:ring-2 focus:ring-brand/15";

  return (
    <AppShell>
      <div className="mx-auto grid max-w-6xl gap-8 px-6 py-10 lg:grid-cols-[1fr_340px]">
        <div>
          <p className="text-sm font-medium text-brand">New audit</p>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight">Tell us about the business</h1>
          <p className="mt-1 text-sm text-muted">We&apos;ll generate customer personas for this kind of business and test the live website in a real browser.</p>

          <Card className="mt-6">
            <form onSubmit={submit} className="space-y-5 p-6" noValidate>
              <div className="flex items-center justify-between rounded-lg bg-canvas px-4 py-3 text-sm">
                <span className="text-muted">No website handy? Use our fictional sample clinic site, which has some built-in UX problems.</span>
                <button type="button" onClick={useSample} className="shrink-0 font-medium text-brand hover:underline">Use sample site</button>
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium">Business name</label>
                <div className="relative">
                  <Building2 className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-muted" />
                  <input className={cn(input, "pl-9")} value={name} onChange={(e) => { setName(e.target.value); clearErr("name"); }} placeholder="Bright Dental Clinic" maxLength={200} />
                </div>
                {fieldErr.name && <p className="mt-1 text-xs text-red-600">{fieldErr.name}</p>}
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium">Existing website URL</label>
                <div className="relative">
                  <Globe className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-muted" />
                  <input className={cn(input, "pl-9")} value={url} onChange={(e) => { setUrl(e.target.value); clearErr("url"); }} placeholder="https://example.com" inputMode="url" />
                </div>
                {fieldErr.url && <p className="mt-1 text-xs text-red-600">{fieldErr.url}</p>}
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium">Business category</label>
                <input className={input} list="categories" value={category} onChange={(e) => { setCategory(e.target.value); clearErr("category"); }} placeholder="e.g. Dental clinic" maxLength={200} />
                <datalist id="categories">{CATEGORIES.map((c) => <option key={c} value={c} />)}</datalist>
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {CATEGORIES.slice(0, 6).map((c) => (
                    <button type="button" key={c} onClick={() => { setCategory(c); clearErr("category"); }}
                      className={cn("rounded-full border px-2.5 py-0.5 text-xs", category === c ? "border-brand bg-brand-50 text-brand" : "border-line text-muted hover:text-ink")}>
                      {c}
                    </button>
                  ))}
                </div>
                {fieldErr.category && <p className="mt-1 text-xs text-red-600">{fieldErr.category}</p>}
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium">Description <span className="font-normal text-muted">(optional)</span></label>
                <textarea className={input} rows={3} value={description} onChange={(e) => setDescription(e.target.value)} maxLength={2000}
                  placeholder="What do you offer, who are your customers, what makes you different?" />
              </div>

              <label className="flex items-start gap-2.5 text-sm">
                <input type="checkbox" checked={authorized} onChange={(e) => { setAuthorized(e.target.checked); clearErr("authorized"); }} className="mt-0.5 h-4 w-4 accent-[#4f46e5]" />
                <span>I own this website or am authorised to test it. I understand TrustLens will browse it automatically using test data only.</span>
              </label>
              {fieldErr.authorized && <p className="-mt-3 text-xs text-red-600">{fieldErr.authorized}</p>}

              {status && !claudeOk && (
                <div className="space-y-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
                  <div className="flex items-start gap-2.5">
                    <KeyRound className="mt-0.5 h-4 w-4 shrink-0" />
                    <div>
                      <p className="font-medium">Claude API is not configured</p>
                      <p className="mt-0.5 text-amber-800">Set <code className="rounded bg-amber-100 px-1">ANTHROPIC_API_KEY</code> on the server to run live AI personas and analysis.</p>
                    </div>
                  </div>
                  <label className="flex items-start gap-2.5">
                    <input type="checkbox" checked={demoChosen} onChange={(e) => setDemoChosen(e.target.checked)} className="mt-0.5 h-4 w-4 accent-[#d97706]" />
                    <span><strong>Continue in Demo mode:</strong> personas come from fixed templates and a keyword heuristic picks the clicks instead of Claude. The browser test is still real.</span>
                  </label>
                </div>
              )}
              {status && claudeOk && (
                <label className="flex items-start gap-2.5 text-sm text-muted">
                  <input type="checkbox" checked={demoChosen} onChange={(e) => setDemoChosen(e.target.checked)} className="mt-0.5 h-4 w-4" />
                  <span>Use Demo mode instead of Claude (deterministic, no API usage)</span>
                </label>
              )}

              {error && <ErrorBox title="Could not start the audit" message={error} />}

              <div className="flex items-center justify-between border-t border-line pt-5">
                <span className="text-xs text-muted">Mode: {mode === "LIVE" ? <span className="font-medium text-violet-700">Live (Claude)</span> : <span className="font-medium text-amber-700">Demo</span>}</span>
                <Button type="submit" loading={busy} disabled={!status}>{mode === "LIVE" ? <Sparkles className="h-4 w-4" /> : <FlaskConical className="h-4 w-4" />} Create audit &amp; generate personas</Button>
              </div>
            </form>
          </Card>
        </div>

        <aside className="space-y-4 lg:pt-16">
          <Card className="p-5">
            <h3 className="text-sm font-semibold">What happens next</h3>
            <ol className="mt-3 space-y-3 text-sm text-muted">
              {["4 customer personas are generated for your category", "Each persona tries a real task on your site in Chromium", "Findings are built only from recorded browser evidence", "An improved page preview is drafted for your approval"].map((t, i) => (
                <li key={t} className="flex gap-3"><span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-brand-50 text-xs font-medium text-brand">{i + 1}</span>{t}</li>
              ))}
            </ol>
          </Card>
          <Notice tone="blue" icon={<ShieldCheck className="h-4 w-4" />}>
            Browsing stays on your domain. Private networks are blocked, form submissions and POST requests are blocked, and only synthetic test data is ever typed.
          </Notice>
        </aside>
      </div>
    </AppShell>
  );
}

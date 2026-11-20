import Link from "next/link";
import { ArrowRight, Bot, Camera, FileSearch, MousePointerClick, ShieldCheck, Wand2 } from "lucide-react";
import { LinkButton, Logo } from "@/components/ui";

const steps = [
  { icon: Bot, title: "AI customers", text: "Claude creates realistic personas for your industry, each with a real task to complete." },
  { icon: MousePointerClick, title: "Real browser testing", text: "Each persona drives a real Chromium browser: it reads, clicks and navigates your live site." },
  { icon: Camera, title: "Evidence, not opinions", text: "Every finding links to the URLs visited, the actions taken and screenshots of what was seen." },
  { icon: Wand2, title: "Improved preview", text: "Get a redesigned page that fixes the friction, plus a list of changes you approve before anything ships." },
];

export default function Landing() {
  return (
    <div className="min-h-screen bg-white">
      <header className="mx-auto flex max-w-6xl items-center justify-between px-6 py-5">
        <Logo />
        <nav className="flex items-center gap-2">
          <Link href="/audits" className="rounded-lg px-3 py-2 text-sm text-muted hover:text-ink">Audits</Link>
          <LinkButton href="/onboard">Start an audit</LinkButton>
        </nav>
      </header>

      <section className="mx-auto max-w-6xl px-6 pb-20 pt-16 text-center">
        <span className="inline-flex items-center gap-2 rounded-full border border-line bg-canvas px-3 py-1 text-xs font-medium text-muted">
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" /> AI Customer Experience Doctor
        </span>
        <h1 className="mx-auto mt-6 max-w-3xl text-4xl font-semibold leading-tight tracking-tight text-ink sm:text-6xl">
          Your AI customers.<br />Your toughest critics.
        </h1>
        <p className="mx-auto mt-5 max-w-2xl text-lg text-muted">
          TrustLens sends AI customer personas through your real website in a real browser. It shows you where they get stuck,
          with screenshots as proof, and drafts an improved page for you to review.
        </p>
        <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
          <LinkButton href="/onboard" className="px-5 py-2.5 text-base">Test my website <ArrowRight className="h-4 w-4" /></LinkButton>
          <LinkButton href="/audits" variant="secondary" className="px-5 py-2.5 text-base">View past audits</LinkButton>
        </div>
        <p className="mt-4 text-xs text-muted">Nothing to install. Nothing is published to your site. Forms are never submitted.</p>
      </section>

      <section className="border-t border-line bg-canvas py-20">
        <div className="mx-auto grid max-w-6xl gap-5 px-6 sm:grid-cols-2 lg:grid-cols-4">
          {steps.map((s, i) => (
            <div key={s.title} className="rounded-xl border border-line bg-white p-5">
              <div className="flex items-center justify-between">
                <s.icon className="h-5 w-5 text-brand" />
                <span className="text-xs font-medium text-muted">0{i + 1}</span>
              </div>
              <h3 className="mt-4 font-semibold">{s.title}</h3>
              <p className="mt-1.5 text-sm text-muted">{s.text}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="mx-auto grid max-w-6xl gap-10 px-6 py-20 md:grid-cols-2">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight">Not another review dashboard</h2>
          <p className="mt-3 text-muted">
            Most tools read your text and guess. TrustLens personas actually <em>use</em> your site: they open menus, follow links and
            fill forms with test data, step by step, the way a real visitor would. Findings come only from what the browser recorded.
          </p>
        </div>
        <div className="grid gap-3 text-sm">
          {[
            [ShieldCheck, "Safe by design", "Private and internal addresses are blocked, browsing stays on your domain, and every form submission or POST request is blocked."],
            [FileSearch, "Honest scoring", "The experience score is a heuristic based on the journeys tested. It is not a customer satisfaction metric."],
          ].map(([Icon, t, d]) => {
            const I = Icon as typeof ShieldCheck;
            return (
              <div key={t as string} className="flex gap-3 rounded-xl border border-line p-4">
                <I className="mt-0.5 h-5 w-5 shrink-0 text-brand" />
                <div><p className="font-medium">{t as string}</p><p className="text-muted">{d as string}</p></div>
              </div>
            );
          })}
        </div>
      </section>
      <footer className="border-t border-line py-8 text-center text-xs text-muted">TrustLens · Hackathon MVP</footer>
    </div>
  );
}

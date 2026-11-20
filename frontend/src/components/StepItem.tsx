"use client";
import { Eye, Globe, MousePointerClick, ScrollText, Square, Type, XCircle } from "lucide-react";
import { cn } from "./ui";
import type { Step } from "@/lib/types";

const ACTION_ICON: Record<string, typeof Eye> = {
  click_element: MousePointerClick, navigate_to_page: Globe, fill_form: Type, scroll: ScrollText, extract_visible_text: Eye, finish: Square,
};
const ACTION_LABEL: Record<string, string> = {
  click_element: "Click", navigate_to_page: "Navigate", fill_form: "Fill form (test data)", scroll: "Scroll", extract_visible_text: "Read page", finish: "Finish",
};

export function StepItem({ s, onShot, active }: { s: Step; onShot?: (s: Step) => void; active?: boolean }) {
  const Icon = ACTION_ICON[s.action] || Eye;
  return (
    <li className={cn("relative flex gap-3 rounded-lg p-2.5", active && "bg-brand-50")}>
      <div className={cn("mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full border",
        s.success ? "border-line bg-white text-brand" : "border-red-200 bg-red-50 text-red-600")}>
        {s.success ? <Icon className="h-3.5 w-3.5" /> : <XCircle className="h-3.5 w-3.5" />}
      </div>
      <div className="min-w-0 flex-1 text-sm">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs font-medium text-muted">Step {s.step}</span>
          <span className="font-medium">{ACTION_LABEL[s.action] || s.action}</span>
          {s.actionInput && <code className="truncate rounded bg-slate-100 px-1.5 py-0.5 text-xs">{s.actionInput}</code>}
        </div>
        <p className="mt-0.5 truncate text-xs text-muted">{s.url}</p>
        {s.observation && <p className="mt-1.5 text-[13px] leading-relaxed">{s.observation}</p>}
        {s.reasoning && <p className="mt-1 text-xs italic text-muted">Why: {s.reasoning}</p>}
        {s.screenshot && onShot && (
          <button onClick={() => onShot(s)} className="mt-1.5 text-xs font-medium text-brand hover:underline">View screenshot</button>
        )}
      </div>
    </li>
  );
}


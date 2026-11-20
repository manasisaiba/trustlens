package com.trustlens.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.trustlens.ai.ClaudeClient;
import com.trustlens.ai.PromptLibrary;
import com.trustlens.domain.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Generates the improved website preview (HTML + CSS) and the list of proposed changes. */
@Service
public class PreviewGenerator {
    private static final Pattern PHONE = Pattern.compile("(\\+?\\(?\\d[\\d\\s().-]{5,}\\d)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private final ClaudeClient claude;
    private final PromptLibrary prompts;
    private final PersonaRepository personas;
    private final JourneyTaskRepository tasks;
    private final BrowserEvidenceRepository evidence;
    private final AuditFindingRepository findings;
    private final WebsitePreviewRepository previews;
    private final ProposedChangeRepository changes;

    public PreviewGenerator(ClaudeClient claude, PromptLibrary prompts, PersonaRepository personas, JourneyTaskRepository tasks,
                            BrowserEvidenceRepository evidence, AuditFindingRepository findings, WebsitePreviewRepository previews,
                            ProposedChangeRepository changes) {
        this.claude = claude;
        this.prompts = prompts;
        this.personas = personas;
        this.tasks = tasks;
        this.evidence = evidence;
        this.findings = findings;
        this.previews = previews;
        this.changes = changes;
    }

    public void generate(AuditSession audit, Business business, WebsitePreview preview) {
        Map<String, String> pages = extractedPages(audit);
        List<AuditFinding> fs = findings.findByAuditSessionId(audit.id);
        fs.sort(Comparator.comparingInt(f -> switch (f.severity) { case "high" -> 0; case "medium" -> 1; default -> 2; }));
        if (Status.LIVE.equals(preview.mode)) generateLive(business, pages, fs, preview);
        else generateDemo(business, pages, fs, preview);
        preview.status = Status.DRAFT;
        previews.save(preview);
    }

    /** Unique page URL -> visible text excerpt, as actually observed by the browser. */
    private Map<String, String> extractedPages(AuditSession audit) {
        Map<String, String> pages = new LinkedHashMap<>();
        for (Persona p : personas.findByAuditSessionIdOrderByOrderIndex(audit.id)) {
            tasks.findByPersonaId(p.id).ifPresent(t -> {
                for (BrowserEvidence e : evidence.findByJourneyTaskIdOrderByStepIndex(t.id)) {
                    if (e.pageUrl != null && e.pageTextExcerpt != null && !e.pageTextExcerpt.isBlank()) pages.putIfAbsent(e.pageUrl, e.pageTextExcerpt);
                }
            });
        }
        return pages;
    }

    private void generateLive(Business b, Map<String, String> pages, List<AuditFinding> fs, WebsitePreview preview) {
        StringBuilder sb = new StringBuilder();
        sb.append("BUSINESS: ").append(b.name).append("\nCATEGORY: ").append(b.category).append("\nWEBSITE: ").append(b.websiteUrl)
                .append("\nOWNER DESCRIPTION: ").append(b.description == null ? "(none)" : b.description).append("\n\nAUDIT FINDINGS:\n");
        for (AuditFinding f : fs) {
            sb.append("- [").append(f.severity).append("] ").append(f.title).append(": ").append(f.description)
                    .append(" Recommendation: ").append(f.recommendation).append("\n");
        }
        sb.append("\nEXTRACTED WEBSITE CONTENT (the only facts you may use):\n");
        int budget = 14000;
        for (var e : pages.entrySet()) {
            if (budget <= 0) break;
            String t = e.getValue().length() > budget ? e.getValue().substring(0, budget) : e.getValue();
            budget -= t.length();
            sb.append("--- ").append(e.getKey()).append("\n").append(t).append("\n");
        }
        ArrayNode content = claude.content();
        claude.addText(content, sb.toString());
        JsonNode out = claude.callTool(prompts.system("website_improvement"), content, "save_website_preview",
                "Save the improved website preview.", prompts.schema("website_improvement"), 12000);
        String html = out.path("html").asText("");
        if (html.isBlank()) throw new ClaudeClient.ClaudeException("Claude returned an empty page");
        preview.generatedHtml = sanitizeHtml(html);
        preview.generatedCss = sanitizeCss(out.path("css").asText(""));
        preview.verificationNotes = out.path("verificationNotes").asText("");
        int i = 0;
        for (JsonNode c : out.path("changes")) {
            saveChange(preview, i++, c.path("title").asText(), c.path("description").asText(),
                    c.path("relatedFinding").asText(null), c.path("needsVerification").asBoolean(false));
        }
    }

    /** DEMO MODE: template-based page that only uses facts found on the real site; unknowns are marked for verification. */
    private void generateDemo(Business b, Map<String, String> pages, List<AuditFinding> fs, WebsitePreview preview) {
        String all = String.join("\n", pages.values());
        String phone = firstMatch(PHONE, all);
        String email = firstMatch(EMAIL, all);
        String name = esc(b.name);
        String verify = "<span class=\"tl-verify\">[Owner to verify: %s]</span>";
        String phoneHtml = phone != null ? "<a class=\"btn btn-primary\" href=\"tel:" + esc(phone.replaceAll("[^+\\d]", "")) + "\">Call " + esc(phone) + "</a>"
                : "<a class=\"btn btn-primary\" href=\"#contact\">Call us</a> " + verify.formatted("phone number");
        List<String> serviceLines = shortListItems(pages);
        StringBuilder services = new StringBuilder();
        if (serviceLines.isEmpty()) {
            services.append("<div class=\"card\"><h3>").append(verify.formatted("your top 3 services")).append("</h3><p>Short, plain-language description of each service.</p></div>");
        } else {
            for (String s : serviceLines) services.append("<div class=\"card\"><p>").append(esc(s)).append("</p></div>");
        }
        List<String> priceLines = candidateLines(all, List.of("$", "₹", "€", "£", "price", "fee", "cost"));
        StringBuilder pricing = new StringBuilder();
        if (priceLines.isEmpty()) pricing.append("<p>").append(verify.formatted("starting prices or price ranges for key services")).append("</p>");
        else for (String s : priceLines) pricing.append("<li>").append(esc(s)).append("</li>");
        List<String> hourLines = candidateLines(all, List.of("hours", "mon", "saturday", "sunday", "open", "am", "pm"));
        String hours = hourLines.isEmpty() ? verify.formatted("opening hours incl. evenings/weekends") : esc(String.join(" · ", hourLines));

        String html = """
                <header class="topbar"><div class="wrap row"><strong class="brand">%s</strong>
                <nav><a href="#services">Services</a><a href="#pricing">Pricing</a><a href="#hours">Hours</a><a href="#contact">Contact</a></nav>
                %s</div></header>
                <main>
                <section class="hero"><div class="wrap">
                <p class="eyebrow">%s</p><h1>%s</h1>
                <p class="lead">%s</p>
                <div class="actions">%s <a class="btn" href="#contact">Book or enquire</a></div>
                </div></section>
                <section id="services" class="wrap"><h2>Services</h2><div class="grid">%s</div></section>
                <section id="pricing" class="wrap"><h2>Pricing</h2><ul class="list">%s</ul></section>
                <section id="hours" class="wrap"><h2>Opening hours &amp; location</h2><p>%s</p></section>
                <section id="contact" class="wrap contact"><h2>Contact us</h2>
                <p>%s%s</p>
                <form action="#" class="form"><label>Name<input placeholder="Your name"></label><label>Phone or email<input placeholder="How should we reach you?"></label>
                <label>How can we help?<textarea rows="3"></textarea></label><button type="button" class="btn btn-primary">Send enquiry (preview only)</button></form>
                </section>
                </main>
                <footer class="wrap foot">Preview generated by TrustLens (demo mode). Not published.</footer>
                """.formatted(name, phone != null ? "<a class=\"btn btn-primary small\" href=\"tel:" + esc(phone.replaceAll("[^+\\d]", "")) + "\">" + esc(phone) + "</a>" : "",
                esc(b.category), name,
                b.description != null && !b.description.isBlank() ? esc(b.description) : verify.formatted("one-sentence summary of what makes you different"),
                phoneHtml, services, pricing, hours,
                phone != null ? "Phone: " + esc(phone) + "<br>" : verify.formatted("phone number") + "<br>",
                email != null ? "Email: " + esc(email) : verify.formatted("email address"));
        String css = """
                *{box-sizing:border-box}body{margin:0;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;color:#0f172a;background:#f8fafc;line-height:1.55}
                .wrap{max-width:1040px;margin:0 auto;padding:0 20px}.row{display:flex;align-items:center;gap:20px;justify-content:space-between;flex-wrap:wrap}
                .topbar{background:#fff;border-bottom:1px solid #e2e8f0;padding:14px 0;position:sticky;top:0}.brand{font-size:18px}
                nav a{margin-right:16px;color:#334155;text-decoration:none;font-size:14px}nav a:hover{color:#4f46e5}
                .hero{background:linear-gradient(180deg,#eef2ff,#f8fafc);padding:64px 0}.eyebrow{text-transform:uppercase;letter-spacing:.08em;color:#4f46e5;font-size:12px;font-weight:600}
                h1{font-size:40px;margin:6px 0 10px}.lead{font-size:18px;color:#475569;max-width:640px}.actions{display:flex;gap:12px;flex-wrap:wrap;margin-top:20px;align-items:center}
                .btn{display:inline-block;padding:10px 18px;border-radius:10px;border:1px solid #c7d2fe;color:#3730a3;text-decoration:none;font-weight:600;background:#fff;cursor:pointer;font-size:15px}
                .btn-primary{background:#4f46e5;color:#fff;border-color:#4f46e5}.small{padding:6px 12px;font-size:13px}
                section{padding:36px 20px}h2{font-size:24px;margin:0 0 16px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:14px}
                .card{background:#fff;border:1px solid #e2e8f0;border-radius:12px;padding:16px}.list{background:#fff;border:1px solid #e2e8f0;border-radius:12px;padding:16px 32px}
                .form{display:grid;gap:12px;max-width:520px;background:#fff;border:1px solid #e2e8f0;border-radius:12px;padding:20px}
                .form label{display:grid;gap:4px;font-size:14px;color:#334155}.form input,.form textarea{padding:10px;border:1px solid #cbd5e1;border-radius:8px;font:inherit}
                .tl-verify{background:#fef3c7;color:#92400e;border:1px dashed #f59e0b;border-radius:6px;padding:1px 6px;font-size:.9em}
                .foot{color:#64748b;font-size:13px;padding:30px 20px}
                """;
        preview.generatedHtml = sanitizeHtml(html);
        preview.generatedCss = sanitizeCss(css);
        List<String> notes = new ArrayList<>();
        if (phone == null) notes.add("phone number");
        if (email == null) notes.add("email address");
        if (priceLines.isEmpty()) notes.add("pricing");
        if (hourLines.isEmpty()) notes.add("opening hours");
        if (serviceLines.isEmpty()) notes.add("service list");
        preview.verificationNotes = notes.isEmpty() ? "Review all extracted text for accuracy before publishing."
                : "Not found on the current website and must be supplied/verified by the owner: " + String.join(", ", notes) + ".";
        int i = 0;
        saveChange(preview, i++, "Sticky header with click-to-call and section navigation",
                "Contact options and the four key topics (services, pricing, hours, contact) are reachable from every scroll position.", firstTitle(fs, "high"), phone == null);
        saveChange(preview, i++, "Primary calls-to-action above the fold", "'Call' and 'Book or enquire' buttons appear in the hero section.", firstTitle(fs, "high"), false);
        saveChange(preview, i++, "Dedicated pricing section", "Gives price-conscious visitors a single place to find costs.", findTitle(fs, "pric"), priceLines.isEmpty());
        saveChange(preview, i++, "Opening hours & location section", "Answers 'when can I come?' without leaving the page.", findTitle(fs, "hour"), hourLines.isEmpty());
        saveChange(preview, i++, "Simplified 3-field enquiry form", "Short form (name, contact, message). Preview only: it does not submit.", null, false);
    }

    private static String firstTitle(List<AuditFinding> fs, String sev) {
        return fs.stream().filter(f -> sev.equals(f.severity)).map(f -> f.title).findFirst().orElse(null);
    }

    private static String findTitle(List<AuditFinding> fs, String needle) {
        return fs.stream().filter(f -> (f.title + f.description).toLowerCase(Locale.ROOT).contains(needle)).map(f -> f.title).findFirst().orElse(null);
    }

    private void saveChange(WebsitePreview p, int idx, String title, String desc, String finding, boolean verify) {
        ProposedChange c = new ProposedChange();
        c.websitePreviewId = p.id;
        c.orderIndex = idx;
        c.title = TestingOrchestrator.trunc(title, 290);
        c.description = desc;
        c.relatedFinding = TestingOrchestrator.trunc(finding, 290);
        c.needsVerification = verify;
        c.status = "PENDING";
        changes.save(c);
    }

    /** Short list-like lines (e.g. "Preventive care") from pages whose title/URL suggests services/products. */
    private static List<String> shortListItems(Map<String, String> pages) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (var e : pages.entrySet()) {
            String lines[] = e.getValue().split("\n");
            String head = (e.getKey() + " " + (lines.length > 0 ? String.join(" ", Arrays.copyOfRange(lines, 0, Math.min(6, lines.length))) : "")).toLowerCase(Locale.ROOT);
            if (!head.matches("(?s).*(service|treatment|solution|product|menu|offer|what we do|practice area|class).*")) continue;
            for (String line : lines) {
                String l = line.trim();
                if (l.length() >= 4 && l.length() <= 40 && !l.endsWith("?") && !l.contains("|") && !l.contains("©") && Character.isUpperCase(l.charAt(0))
                        && l.split("\\s+").length <= 5) out.add(l);
                if (out.size() >= 8) break;
            }
        }
        // drop navigation/heading noise
        out.removeIf(x -> x.matches("(?i)(home|about|faq|contact|reach us|our story|menu|search|log ?in|sign ?up|smile solutions|.*clinic)"));
        return new ArrayList<>(out).subList(0, Math.min(6, out.size()));
    }

    private static List<String> candidateLines(String text, List<String> needles) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String line : text.split("\n")) {
            String l = line.trim();
            if (l.length() < 4 || l.length() > 140 || l.endsWith("?")) continue;
            String low = l.toLowerCase(Locale.ROOT);
            for (String n : needles) {
                if (low.contains(n)) {
                    out.add(l);
                    break;
                }
            }
            if (out.size() >= 6) break;
        }
        return new ArrayList<>(out);
    }

    private static String firstMatch(Pattern p, String text) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            String v = m.group().trim();
            if (p == PHONE && v.replaceAll("\\D", "").length() < 7) continue;
            if (p == PHONE && v.matches("\\d{4}\\s*[-–]\\s*\\d{4}")) continue; // year ranges
            return v;
        }
        return null;
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s);
    }

    // ------------------------------------------------------------------ sanitisation

    public static String sanitizeHtml(String html) {
        Safelist safe = Safelist.relaxed()
                .addTags("header", "nav", "main", "section", "footer", "article", "aside", "figure", "figcaption", "form", "input",
                        "label", "select", "option", "textarea", "button", "span", "div", "small", "strong", "em", "hr", "address", "details", "summary")
                .addAttributes(":all", "class", "id", "role", "aria-label", "aria-hidden", "title")
                .addAttributes("input", "type", "placeholder", "name", "value")
                .addAttributes("textarea", "placeholder", "rows", "name")
                .addAttributes("button", "type")
                .addAttributes("form", "action")
                .addAttributes("option", "value")
                .addAttributes("a", "href")
                .addProtocols("a", "href", "http", "https", "mailto", "tel", "#")
                .preserveRelativeLinks(true);
        String clean = Jsoup.clean(html, "https://preview.trustlens.invalid/", safe);
        Document doc = Jsoup.parseBodyFragment(clean);
        doc.select("form").attr("action", "#");
        doc.select("a[href]").forEach(a -> {
            String h = a.attr("href");
            if (h.startsWith("https://preview.trustlens.invalid/")) a.attr("href", h.substring("https://preview.trustlens.invalid/".length() - 1));
        });
        return doc.body().html();
    }

    public static String sanitizeCss(String css) {
        if (css == null) return "";
        return css.replaceAll("(?i)</style", "")
                .replaceAll("(?i)@import[^;]*;?", "")
                .replaceAll("(?i)expression\\s*\\(", "(")
                .replaceAll("(?i)javascript:", "")
                .replaceAll("(?i)behavior\\s*:", "x-behavior:");
    }
}

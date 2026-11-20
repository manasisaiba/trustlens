package com.trustlens.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DEMO MODE planner (no Claude key). Deterministic keyword-based navigation. The browser actions it chooses are
 * still executed for real by Playwright; only the decision-making is a simple heuristic instead of Claude.
 */
@Component
public class HeuristicActionPlanner {
    private static final Set<String> STOP = Set.of("about", "their", "there", "where", "which", "would", "should", "could",
            "website", "find", "information", "whether", "understand", "available", "customer", "page", "with", "from",
            "that", "this", "what", "when", "they", "them", "have", "into", "your", "the", "and", "for", "how", "out");
    private static final List<String> GENERIC = List.of("contact", "service", "pricing", "price", "fees", "book", "appointment",
            "hours", "about", "location", "menu", "faq", "treatment", "product", "shop", "visit");
    private static final Pattern PHONE = Pattern.compile("(\\+?\\d[\\d\\s().-]{7,}\\d)");

    public PlannedAction next(JourneyContext ctx, JsonNode state) {
        String url = state.path("url").asText();
        String text = state.path("text").asText("");
        String lower = text.toLowerCase(Locale.ROOT);
        ctx.visitedUrls.add(stripFragment(url));

        List<String> found = new ArrayList<>();
        for (String k : ctx.lookFor) if (!k.isBlank() && lower.contains(k.toLowerCase(Locale.ROOT))) found.add(k);
        ctx.matchedKeywords.addAll(found);
        boolean hasPhone = PHONE.matcher(text).find() || anyHref(state, "tel:");
        String obs = found.isEmpty()
                ? "Page \"" + state.path("title").asText() + "\" does not mention any of: " + String.join(", ", ctx.lookFor) + "."
                : "Page mentions: " + String.join(", ", found) + ". Excerpt: \"" + snippet(text, found.get(0)) + "\"";
        if (hasPhone) obs += " A phone number or tel: link is visible.";

        int needed = Math.max(2, (int) Math.ceil(ctx.lookFor.size() / 2.0));
        if (found.size() >= needed) {
            return finish(obs, "Enough of the information this persona looks for is visible on this page.", "success",
                    "Found " + String.join(", ", found) + " on " + url);
        }
        if (ctx.step >= ctx.maxActions) {
            String outcome = ctx.matchedKeywords.isEmpty() ? "failed" : "partial";
            return finish(obs, "Action limit reached.", outcome, ctx.matchedKeywords.isEmpty()
                    ? "Could not find information about " + String.join(", ", ctx.lookFor) + " within " + ctx.maxActions + " actions."
                    : "Only partially found the needed information (" + String.join(", ", ctx.matchedKeywords) + ") within the action limit.");
        }

        List<String> keywords = new ArrayList<>(ctx.lookFor);
        for (String w : (ctx.task.description + " " + ctx.persona.objective).toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (w.length() > 4 && !STOP.contains(w)) keywords.add(w);
        }
        JsonNode best = null;
        int bestScore = 0;
        JsonNode explore = null;
        for (JsonNode e : state.path("elements")) {
            String href = e.path("href").asText("");
            String tag = e.path("tag").asText("");
            boolean expandable = href.isEmpty() && (tag.equals("summary") || tag.equals("button") || e.has("role"));
            if (expandable) {
                // in-page controls (accordions, tabs): only if their label matches the task and they were not clicked yet
                String key = url + "|" + e.path("text").asText();
                if (ctx.clicked.contains(key) || e.path("text").asText().isBlank()) continue;
            } else {
                if (href.isEmpty() || href.startsWith("tel:") || href.startsWith("mailto:") || href.startsWith("javascript")) continue;
                if (ctx.visitedUrls.contains(stripFragment(href)) || href.contains("#") && stripFragment(href).equals(stripFragment(url))) continue;
                if (explore == null && href.startsWith(origin(url))) explore = e;
            }
            String label = (e.path("text").asText("") + " " + href).toLowerCase(Locale.ROOT);
            int score = 0;
            for (String k : keywords) if (label.contains(k.toLowerCase(Locale.ROOT))) score += 3;
            for (String g : GENERIC) if (label.contains(g)) score += 1;
            if (expandable) score = score >= 3 ? score + 1 : 0;
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        if (best != null) {
            ctx.clicked.add(url + "|" + best.path("text").asText());
            return new PlannedAction(obs, "Demo heuristic: \"" + best.path("text").asText() + "\" best matches the task keywords.",
                    "click_element", best.path("id").asText(), null, List.of(), null, null);
        }
        if (explore != null) {
            return new PlannedAction(obs, "Demo heuristic: no link mentions the task keywords; exploring unvisited page \""
                    + explore.path("text").asText() + "\".", "click_element", explore.path("id").asText(), null, List.of(), null, null);
        }
        if (ctx.history.stream().noneMatch(h -> h.startsWith("scroll") && h.contains(url))) {
            return new PlannedAction(obs, "Demo heuristic: no relevant links visible; scrolling for more content.", "scroll", null, null, List.of(), null, null);
        }
        String outcome = ctx.matchedKeywords.isEmpty() ? "failed" : "partial";
        return finish(obs, "No further relevant navigation options found.", outcome,
                "Navigation ran out of relevant links. Looked for: " + String.join(", ", ctx.lookFor) + ".");
    }

    private PlannedAction finish(String obs, String reasoning, String outcome, String summary) {
        return new PlannedAction(obs, reasoning, "finish", null, null, List.of(), outcome, summary);
    }

    private static boolean anyHref(JsonNode state, String prefix) {
        for (JsonNode e : state.path("elements")) if (e.path("href").asText("").startsWith(prefix)) return true;
        return false;
    }

    static String origin(String u) {
        try {
            java.net.URI x = java.net.URI.create(u);
            return x.getScheme() + "://" + x.getRawAuthority();
        } catch (Exception e) {
            return u;
        }
    }

    static String stripFragment(String u) {
        int i = u.indexOf('#');
        String s = i >= 0 ? u.substring(0, i) : u;
        s = s.replaceAll("/index\\.html?$", "/");
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    static String snippet(String text, String keyword) {
        Matcher m = Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE).matcher(text);
        if (!m.find()) return "";
        int s = Math.max(0, m.start() - 60), e = Math.min(text.length(), m.end() + 80);
        return text.substring(s, e).replaceAll("\\s+", " ").trim();
    }
}

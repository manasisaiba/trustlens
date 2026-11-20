package com.trustlens.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.trustlens.domain.Business;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class PersonaGenerator {
    public record GeneratedPersona(String name, String background, String objective, String taskDescription,
                                   String successCriteria, List<String> lookFor) {}

    private final ClaudeClient claude;
    private final PromptLibrary prompts;

    public PersonaGenerator(ClaudeClient claude, PromptLibrary prompts) {
        this.claude = claude;
        this.prompts = prompts;
    }

    public List<GeneratedPersona> generateLive(Business b, String homepageExcerpt) {
        ArrayNode content = claude.content();
        claude.addText(content, "Business name: " + b.name + "\nCategory: " + b.category + "\nWebsite: " + b.websiteUrl
                + "\nDescription: " + (b.description == null || b.description.isBlank() ? "(not provided)" : b.description)
                + (homepageExcerpt == null ? "" : "\n\nHomepage text excerpt (for context only):\n" + homepageExcerpt)
                + "\n\nGenerate the 4 customer personas now.");
        JsonNode out = claude.callTool(prompts.system("persona_generation"), content, "save_personas",
                "Save the generated customer personas.", prompts.schema("persona_generation"), 2500);
        List<GeneratedPersona> list = new ArrayList<>();
        for (JsonNode p : out.path("personas")) {
            List<String> look = new ArrayList<>();
            p.path("lookFor").forEach(k -> { if (!k.asText().isBlank()) look.add(k.asText().trim()); });
            GeneratedPersona gp = new GeneratedPersona(req(p, "name"), req(p, "background"), req(p, "objective"),
                    req(p, "taskDescription"), req(p, "successCriteria"), look);
            if (look.isEmpty()) throw new ClaudeClient.ClaudeException("Persona '" + gp.name() + "' has no lookFor keywords");
            list.add(gp);
        }
        if (list.size() < 3) throw new ClaudeClient.ClaudeException("Claude returned too few personas (" + list.size() + ")");
        return list.subList(0, Math.min(4, list.size()));
    }

    private static String req(JsonNode n, String f) {
        String v = n.path(f).asText("").trim();
        if (v.isEmpty()) throw new ClaudeClient.ClaudeException("Persona field '" + f + "' missing");
        return v.length() > 1000 ? v.substring(0, 1000) : v;
    }

    /** DEMO MODE: deterministic, category-parameterised personas (clearly labelled as demo in the UI). */
    public List<GeneratedPersona> generateDemo(Business b) {
        String cat = b.category.toLowerCase(Locale.ROOT);
        return List.of(
                new GeneratedPersona("Urgent customer (demo)",
                        "Needs help from a " + cat + " today and is browsing on a phone between other commitments.",
                        "Find out quickly whether " + b.name + " can help urgently and how to reach them.",
                        "Find urgent or same-day availability and a phone number or contact method.",
                        "A phone number, contact link or urgent/same-day information is visible.",
                        List.of("contact", "call", "phone", "urgent", "same day", "emergency")),
                new GeneratedPersona("Price-conscious customer (demo)",
                        "Comparing several " + cat + " options and wants to understand costs before committing.",
                        "Understand typical prices and what is included.",
                        "Find pricing, fees or a price range for the main services offered.",
                        "Prices, fees or a pricing page are visible.",
                        List.of("price", "pricing", "fees", "cost", "$", "packages")),
                new GeneratedPersona("First-time visitor (demo)",
                        "Has never used " + b.name + " before and wants to know what they offer.",
                        "Understand the main services and how to get started.",
                        "Understand the services offered and find how to book or enquire.",
                        "A list of services and a booking or enquiry option are visible.",
                        List.of("services", "book", "appointment", "enquire", "get started")),
                new GeneratedPersona("Busy professional (demo)",
                        "Works long hours and can only visit outside office hours.",
                        "Check opening hours, location and evening or weekend availability.",
                        "Find opening hours including evenings or weekends, and the location.",
                        "Opening hours (including evening/weekend info) and an address are visible.",
                        List.of("hours", "opening", "saturday", "weekend", "evening", "address"))
        );
    }
}

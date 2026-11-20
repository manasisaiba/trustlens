package com.trustlens;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustlens.ai.*;
import com.trustlens.config.AppProperties;
import com.trustlens.domain.Business;
import com.trustlens.domain.JourneyTask;
import com.trustlens.domain.Persona;
import com.trustlens.security.UrlGuard;
import com.trustlens.service.PreviewGenerator;
import com.trustlens.web.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UnitTests {
    final ObjectMapper mapper = new ObjectMapper();
    final AppProperties props = new AppProperties("", "m", "u", "w", "s", 8, 60, 2, "x", false);

    @Test
    void urlGuardBlocksInternalTargets() {
        UrlGuard g = new UrlGuard(props);
        for (String u : List.of("http://localhost:8080", "http://127.0.0.1", "http://10.0.0.5/admin", "http://169.254.169.254/latest",
                "ftp://example.com", "http://user:pw@example.com", "http://intranet", "http://[::1]/")) {
            assertThrows(ApiException.class, () -> g.normalizeAndValidate(u), u);
        }
    }

    @Test
    void sanitizerStripsScriptsAndHandlers() {
        String out = PreviewGenerator.sanitizeHtml("<section id=\"a\" onclick=\"x()\"><script>alert(1)</script><a href=\"javascript:alert(1)\">x</a>"
                + "<a href=\"#contact\">c</a><form action=\"https://evil.test\"><input type=\"text\"></form><span class=\"tl-verify\">v</span></section>");
        assertFalse(out.contains("script"));
        assertFalse(out.contains("onclick"));
        assertFalse(out.contains("javascript:"));
        assertFalse(out.contains("evil.test"));
        assertTrue(out.contains("href=\"#contact\""));
        assertTrue(out.contains("tl-verify"));
        assertFalse(PreviewGenerator.sanitizeCss("a{}</style><script>@import url(x);").contains("</style"));
    }

    private JourneyContext ctx(List<String> look) {
        Business b = new Business(); b.name = "B"; b.category = "Dental clinic"; b.websiteUrl = "https://x.test/";
        Persona p = new Persona(); p.name = "P"; p.objective = "find prices"; p.background = "b";
        JourneyTask t = new JourneyTask(); t.description = "Find pricing for cleaning"; t.successCriteria = "price visible";
        return new JourneyContext(b, p, t, look, 6);
    }

    @Test
    void heuristicPlannerFinishesWhenInfoVisibleAndOtherwiseClicksRelevantLink() throws Exception {
        HeuristicActionPlanner h = new HeuristicActionPlanner();
        JsonNode found = mapper.readTree("{\"url\":\"https://x.test/p\",\"title\":\"Fees\",\"text\":\"Our pricing: cleaning fees from $80\",\"elements\":[]}");
        assertEquals("success", h.next(ctx(List.of("pricing", "fees", "$")), found).outcome());
        JsonNode home = mapper.readTree("{\"url\":\"https://x.test/\",\"title\":\"Home\",\"text\":\"Welcome\",\"elements\":["
                + "{\"id\":\"e1\",\"tag\":\"a\",\"text\":\"About\",\"href\":\"https://x.test/about\"},"
                + "{\"id\":\"e2\",\"tag\":\"a\",\"text\":\"Fees & pricing\",\"href\":\"https://x.test/fees\"}]}");
        PlannedAction a = h.next(ctx(List.of("pricing", "fees")), home);
        assertEquals("click_element", a.action());
        assertEquals("e2", a.elementId());
    }

    @Test
    void claudeOutputIsValidatedBeforeExecution() throws Exception {
        JsonNode state = mapper.readTree("{\"elements\":[{\"id\":\"e1\"}]}");
        JourneyContext c = ctx(List.of("x"));
        c.step = 1;
        // unknown action type -> safe fallback
        assertEquals("scroll", ClaudeActionPlannerAccess.validate(mapper.readTree("{\"action\":\"run_shell\"}"), state, c).action());
        // hallucinated element -> re-read page instead of clicking
        assertEquals("extract_visible_text", ClaudeActionPlannerAccess.validate(mapper.readTree("{\"action\":\"click_element\",\"elementId\":\"e99\"}"), state, c).action());
        // action limit -> forced finish
        c.step = 6;
        assertEquals("finish", ClaudeActionPlannerAccess.validate(mapper.readTree("{\"action\":\"scroll\"}"), state, c).action());
    }
}

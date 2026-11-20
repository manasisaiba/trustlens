package com.trustlens.security;

import com.trustlens.config.AppProperties;
import com.trustlens.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Validates that a target website is a public http(s) URL.
 * Blocks localhost, private, link-local, CGNAT, multicast and other internal ranges (SSRF protection).
 * The browser worker enforces the same rules again for every request it makes.
 */
@Component
public class UrlGuard {
    private final AppProperties props;

    public UrlGuard(AppProperties props) {
        this.props = props;
    }

    public String normalizeAndValidate(String raw) {
        if (raw == null || raw.isBlank()) throw bad("Website URL is required.");
        String s = raw.trim();
        if (!s.matches("(?i)^https?://.*")) s = "https://" + s;
        URI uri;
        try {
            uri = URI.create(s);
        } catch (IllegalArgumentException e) {
            throw bad("That doesn't look like a valid URL.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) throw bad("Only http and https URLs are supported.");
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw bad("The URL must include a hostname.");
        if (uri.getUserInfo() != null) throw bad("URLs with embedded credentials are not allowed.");
        String h = host.toLowerCase(Locale.ROOT);
        if (!props.allowPrivateTargets()) {
            if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")
                    || !h.contains(".")) {
                throw bad("Internal or local hostnames cannot be tested. Use a public website.");
            }
            try {
                for (InetAddress a : InetAddress.getAllByName(host)) {
                    if (isPrivate(a)) throw bad("This hostname resolves to a private or internal address and cannot be tested.");
                }
            } catch (UnknownHostException e) {
                throw bad("Could not resolve hostname '" + host + "'. Check the URL is correct and publicly reachable.");
            }
        }
        return uri.toString();
    }

    public static boolean isPrivate(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) return true;
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int o0 = b[0] & 0xff, o1 = b[1] & 0xff;
            if (o0 == 0) return true;                               // 0.0.0.0/8
            if (o0 == 100 && o1 >= 64 && o1 <= 127) return true;    // CGNAT 100.64/10
            if (o0 == 192 && o1 == 0 && (b[2] & 0xff) == 0) return true; // 192.0.0.0/24
            if (o0 == 198 && (o1 == 18 || o1 == 19)) return true;   // benchmarking
            if (o0 >= 224) return true;                             // multicast/reserved
        } else if (b.length == 16) {
            int first = b[0] & 0xff;
            if ((first & 0xfe) == 0xfc) return true;                // fc00::/7 unique local
        }
        return false;
    }

    private ApiException bad(String msg) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_URL", msg);
    }
}

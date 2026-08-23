package com.funtime.jira;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thin Jira Cloud REST v3 client used by {@code JiraBugListener} to file Bug tickets.
 *
 * <p>Deliberate design constraints:
 * <ul>
 *   <li><b>Never throws into the test flow</b> — {@link #reportFailure(JiraTicket)} catches
 *       everything and returns a ticket URL or {@code null}; a Jira outage must not change the
 *       suite outcome.</li>
 *   <li><b>Never logs the API token</b> — only status codes and short sanitized messages.</li>
 *   <li><b>Deduplicates</b> — an open issue with the same summary is reused, not re-created.</li>
 *   <li>Requests are serialized by an internal lock (parallel test threads may fail at once).</li>
 *   <li>Bounded timeouts (connect 5s, request 10s) so a slow Jira never hangs the suite.</li>
 * </ul>
 */
public final class JiraClient {

    private static final Logger LOG = LoggerFactory.getLogger(JiraClient.class);

    private static final String REST_API = "/rest/api/3";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    /** Jira Cloud file-attachment cap is ~2MB per file; stay clear of it and skip oversized shots. */
    private static final long MAX_ATTACHMENT_BYTES = 1_500_000L;
    private static final String RESOLVED_STATUSES = "Done, Closed, Resolved, Canceled";
    private static final String SCREENSHOT_FILENAME = "failure-screenshot.png";

    private final JiraConfig config;
    private final HttpClient http;
    private final Gson gson = new Gson();
    private final Object lock = new Object();

    public JiraClient(JiraConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Orchestrates filing one ticket: dedupe → create-or-reuse → attach screenshot (and comment
     * on reuse). Returns the ticket URL, or {@code null} when Jira rejected the call.
     */
    public String reportFailure(JiraTicket ticket) {
        synchronized (lock) {
            try {
                String key = findOpenIssueBySummary(ticket.summary());
                if (key == null || key.isBlank()) {
                    key = createIssue(ticket.summary(), ticket.description(), ticket.labels());
                    LOG.info("Jira: created {} for {}", key, ticket.testKey());
                } else {
                    addComment(key, "Failed again — " + firstLine(ticket.description()));
                    LOG.info("Jira: reused open {} for {}", key, ticket.testKey());
                }
                if (ticket.screenshotBytes() != null && ticket.screenshotBytes().length > 0) {
                    attachScreenshot(key, ticket.screenshotBytes());
                }
                return config.baseUrl() + "/browse/" + key;
            } catch (Exception e) {
                LOG.warn("Jira: could not file ticket for {} (test outcome unchanged): {}",
                        ticket.testKey(), sanitize(e));
                return null;
            }
        }
    }

    /** Key of the first <i>open</i> issue with the exact summary, or {@code null}. */
    public String findOpenIssueBySummary(String summary) {
        String jql = "project=" + config.projectKey()
                + " AND summary~\"" + summarySearchTerm(summary) + "\""
                + " AND status NOT IN (" + RESOLVED_STATUSES + ")";
        // Jira Cloud removed the classic GET /rest/api/3/search (HTTP 410 Gone, CHANGE-2046);
        // the replacement is GET /rest/api/3/search/jql with the same query parameters.
        String url = config.baseUrl() + REST_API + "/search/jql?maxResults=10&fields=key&jql="
                + URLEncoder.encode(jql, StandardCharsets.UTF_8);
        HttpResponse<String> resp = send(request(URI.create(url)).GET().build());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("search returned HTTP " + resp.statusCode() + " — " + truncate(resp.body()));
        }
        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonArray issues = root.getAsJsonArray("issues");
        if (issues != null && issues.size() > 0) {
            return issues.get(0).getAsJsonObject().get("key").getAsString();
        }
        return null;
    }

    /** Creates a Bug issue in the configured project and returns its key. */
    public String createIssue(String summary, String description, List<String> labels) {
        String body = gson.toJson(Map.of(
                "fields", Map.of(
                        "project", Map.of("key", config.projectKey()),
                        "issuetype", Map.of("name", config.issueType()),
                        "summary", summary,
                        "description", adfDescription(description),
                        "labels", labels == null ? List.of() : labels)));
        HttpRequest req = request(URI.create(config.baseUrl() + REST_API + "/issue"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = send(req);
        if (resp.statusCode() != 201) {
            throw new IllegalStateException("create returned HTTP " + resp.statusCode() + " — " + truncate(resp.body()));
        }
        return JsonParser.parseString(resp.body()).getAsJsonObject().get("key").getAsString();
    }

    /**
     * Derives a JQL-safe search term from the issue summary.
     *
     * <p>The summary shape is {@code [AUTO-TEST] <fqcn>#<method>}, but JQL's {@code ~} operator
     * does not match values containing {@code [ ] . #} (verified against Jira Cloud 2026-08-23).
     * The two word tokens <i>simple class name</i> + <i>method name</i> match reliably, so we
     * search on those instead of the raw summary.
     */
    static String summarySearchTerm(String summary) {
        int hash = summary.lastIndexOf('#');
        String method = hash >= 0 ? summary.substring(hash + 1) : "";
        String cls = hash >= 0 ? summary.substring(0, hash) : summary;
        int dot = cls.lastIndexOf('.');
        String simple = dot >= 0 ? cls.substring(dot + 1) : cls;
        String token = (simple + " " + method).trim();
        return token.isEmpty()
                ? summary.replaceAll("[^A-Za-z0-9_]", " ")
                : token;
    }

    /**
     * Converts a plain-text (newline-separated) description into Atlassian Document Format.
     * Jira Cloud now rejects plain-text {@code description} values (HTTP 400, ADF content required);
     * each non-empty line becomes a text paragraph, blank lines stay empty paragraphs.
     */
    static Map<String, Object> adfDescription(String text) {
        List<Map<String, Object>> content = new ArrayList<>();
        for (String line : text.split("\r?\n", -1)) {
            content.add(Map.of(
                    "type", "paragraph",
                    "content", line.isEmpty()
                            ? List.of()
                            : List.of(Map.of("type", "text", "text", line))));
        }
        return Map.of("type", "doc", "version", 1, "content", content);
    }

    /** Attaches the failure screenshot. Skips (with a warning) when the file exceeds the cap. */
    public void attachScreenshot(String issueKey, byte[] bytes) {
        if (bytes.length > MAX_ATTACHMENT_BYTES) {
            LOG.warn("Jira: skipping screenshot attachment for {} ({} bytes > {} cap)",
                    issueKey, bytes.length, MAX_ATTACHMENT_BYTES);
            return;
        }
        String boundary = "----JavaPlaywrightBoundary" + UUID.randomUUID();
        HttpRequest req = request(URI.create(config.baseUrl() + REST_API + "/issue/" + issueKey + "/attachments"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Atlassian-Token", "no-check")
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(boundary, bytes)))
                .build();
        HttpResponse<String> resp = send(req);
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("attach returned HTTP " + resp.statusCode() + " — " + truncate(resp.body()));
        }
    }

    /** Posts a comment to an issue. Jira Cloud rejects plain-text bodies (ADF required, like description). */
    public void addComment(String issueKey, String comment) {
        HttpRequest req = request(URI.create(config.baseUrl() + REST_API + "/issue/" + issueKey + "/comment"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(Map.of("body", adfDescription(comment)))))
                .build();
        HttpResponse<String> resp = send(req);
        if (resp.statusCode() != 201) {
            throw new IllegalStateException("comment returned HTTP " + resp.statusCode() + " — " + truncate(resp.body()));
        }
    }

    private HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", config.basicAuthHeader());
    }

    /** Sends and wraps checked IO/Interrupted exceptions so callers only deal with runtime errors. */
    private HttpResponse<String> send(HttpRequest req) {
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("HTTP " + req.method() + " " + req.uri() + " failed: " + sanitize(e));
        }
    }

    /** Builds a multipart/form-data body carrying one PNG file part. */
    private byte[] multipartBody(String boundary, byte[] png) {
        byte[] header = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + SCREENSHOT_FILENAME + "\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[header.length + png.length + footer.length];
        System.arraycopy(header, 0, body, 0, header.length);
        System.arraycopy(png, 0, body, header.length, png.length);
        System.arraycopy(footer, 0, body, header.length + png.length, footer.length);
        return body;
    }

    private static String sanitize(Exception e) {
        String msg = e.getMessage();
        return truncate(msg == null ? e.toString() : msg);
    }

    private static String truncate(String s) {
        return s == null ? "null" : (s.length() > 400 ? s.substring(0, 400) + "…" : s);
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "";
        }
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }
}
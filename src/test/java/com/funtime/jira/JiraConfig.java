package com.funtime.jira;

import com.funtime.config.ConfigReader;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Immutable Jira Cloud connection settings, resolved once from {@link ConfigReader}.
 *
 * <p>The API token is carried here but must <b>never</b> be logged or rendered anywhere —
 * logs, ticket descriptions, Allure report. Only {@link #basicAuthHeader()} consumes it.
 */
public record JiraConfig(
        String baseUrl,
        String email,
        String apiToken,
        String projectKey,
        String issueType,
        List<String> labels) {

    /** Builds from the resolved framework config. Call only when {@code ConfigReader.isJiraConfigured()}. */
    public static JiraConfig fromConfig() {
        return new JiraConfig(
                ConfigReader.getJiraBaseUrl(),
                ConfigReader.getJiraEmail(),
                ConfigReader.getJiraApiToken(),
                ConfigReader.getJiraProjectKey(),
                ConfigReader.getJiraIssueType(),
                ConfigReader.getJiraLabels());
    }

    /** Basic-auth header value for the Jira REST API ({@code email:apiToken}, base64). */
    public String basicAuthHeader() {
        String credentials = email + ":" + apiToken;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
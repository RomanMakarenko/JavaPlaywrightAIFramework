package com.funtime.jira;

import java.util.List;

/**
 * Everything {@link JiraClient} needs to file one Bug ticket for a failed test.
 *
 * @param testKey          stable identifier, e.g. {@code com.funtime.tests.LoginTest#login_...} — drives deduplication
 * @param summary          ticket title (must be identical across reruns for the dedupe search)
 * @param description      markdown-ish plain text: context + stack trace, never credentials
 * @param labels           Jira labels applied on create
 * @param screenshotBytes  PNG bytes of the failure screenshot; may be {@code null}
 */
public record JiraTicket(
        String testKey,
        String summary,
        String description,
        List<String> labels,
        byte[] screenshotBytes) {
}
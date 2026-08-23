package com.funtime.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Loads the framework configuration from {@code config.properties} (test classpath), merges an
 * optional per-machine {@code config.local.properties} (gitignored) on top, and applies
 * environment-variable overrides last.
 *
 * <p>Env var → property key mapping (kept in sync with CLAUDE.md):
 * <pre>
 *   APP_ENV            → app.env
 *   BROWSER            → browser
 *   HEADLESS           → headless
 *   BASE_URL           → base.url (explicit override — wins over env.&lt;app.env&gt;.url)
 *   TEST_TIMEOUT       → test.timeout
 *   RETRIES            → retries
 *   TEST_USER_EMAIL    → test.user.email
 *   TEST_USER_PASSWORD → test.user.password
 *   JIRA_BASE_URL      → jira.base.url
 *   JIRA_EMAIL         → jira.email
 *   JIRA_API_TOKEN     → jira.api.token  (secret — never logged, never in the Allure report)
 *   JIRA_TICKETS_ENABLED → jira.tickets.enabled
 *   JIRA_PROJECT_KEY   → jira.project.key
 *   JIRA_ISSUE_TYPE    → jira.issue.type
 *   JIRA_LABELS        → jira.labels
 * </pre>
 *
 * <p>{@link #getBaseUrl()} resolves in this order: {@code BASE_URL} env var, then the
 * per-environment URL {@code env.&lt;app.env&gt;.url}, then the legacy {@code base.url} property.
 *
 * <p>Values are read once at class-load time, so env vars must be set before the
 * JVM starts. Use the typed getters from production code; {@link #get(String)} is
 * the raw fallback. Diagnostic: run {@code main} to print the resolved values, e.g.
 * {@code mvn exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.funtime.config.ConfigReader}.
 */
public final class ConfigReader {

    private static final String PROPERTIES_FILE = "config.properties";

    /** Optional per-machine override file (gitignored); merged over the base config. */
    private static final String LOCAL_PROPERTIES_FILE = "config.local.properties";

    /**
     * Env var for each known property key; only these keys can be overridden from the environment.
     * {@code Map.ofEntries} — more than 10 pairs (Phase 15 added the Jira keys).
     */
    private static final Map<String, String> ENV_VAR_BY_KEY = Map.ofEntries(
            Map.entry("app.env", "APP_ENV"),
            Map.entry("browser", "BROWSER"),
            Map.entry("headless", "HEADLESS"),
            Map.entry("base.url", "BASE_URL"),
            Map.entry("test.timeout", "TEST_TIMEOUT"),
            Map.entry("retries", "RETRIES"),
            Map.entry("test.user.email", "TEST_USER_EMAIL"),
            Map.entry("test.user.password", "TEST_USER_PASSWORD"),
            Map.entry("jira.base.url", "JIRA_BASE_URL"),
            Map.entry("jira.email", "JIRA_EMAIL"),
            Map.entry("jira.api.token", "JIRA_API_TOKEN"),
            Map.entry("jira.tickets.enabled", "JIRA_TICKETS_ENABLED"),
            Map.entry("jira.project.key", "JIRA_PROJECT_KEY"),
            Map.entry("jira.issue.type", "JIRA_ISSUE_TYPE"),
            Map.entry("jira.labels", "JIRA_LABELS")
    );

    private static final Properties properties = load();

    private ConfigReader() {
    }

    private static Properties load() {
        Properties props = new Properties();
        try (InputStream in = ConfigReader.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + PROPERTIES_FILE + " on the classpath");
            }
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + PROPERTIES_FILE, e);
        }
        // Per-machine override file (src/test/resources/config.local.properties, gitignored) — e.g.
        // real test credentials on a dev machine. Absent on CI and for other devs, so the base
        // config is kept as is. Env vars still win over both files (see get()).
        try (InputStream in = ConfigReader.class.getClassLoader().getResourceAsStream(LOCAL_PROPERTIES_FILE)) {
            if (in != null) {
                Properties local = new Properties();
                local.load(in);
                for (String key : local.stringPropertyNames()) {
                    props.setProperty(key, local.getProperty(key));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + LOCAL_PROPERTIES_FILE, e);
        }
        return props;
    }

    /** Raw value: a non-blank env var wins, otherwise the property value is returned. */
    public static String get(String key) {
        String envVar = ENV_VAR_BY_KEY.get(key);
        if (envVar != null) {
            String envValue = System.getenv(envVar);
            if (envValue != null && !envValue.isBlank()) {
                return envValue;
            }
        }
        return properties.getProperty(key);
    }

    public static String getBrowser() {
        return get("browser");
    }

    public static boolean getHeadless() {
        return Boolean.parseBoolean(get("headless"));
    }

    public static String getEnvironment() {
        String env = get("app.env");
        return (env == null || env.isBlank()) ? "prod" : env.toLowerCase(Locale.ROOT);
    }

    public static String getBaseUrl() {
        // 1. Explicit BASE_URL env var wins (CI/local override, backward-compatible).
        String envBase = System.getenv("BASE_URL");
        if (envBase != null && !envBase.isBlank()) {
            return envBase;
        }
        // 2. Per-environment URL for the active app.env (env.<app.env>.url).
        String envUrl = get("env." + getEnvironment() + ".url");
        if (envUrl != null && !envUrl.isBlank()) {
            return envUrl;
        }
        // 3. Legacy base.url property (config.local.properties overrides honored via get()).
        String legacy = get("base.url");
        if (legacy != null && !legacy.isBlank()) {
            return legacy;
        }
        throw new IllegalStateException("No base URL for environment='" + getEnvironment()
                + "' — set BASE_URL, APP_ENV=dev|stage|prod, or a base.url override");
    }

    public static int getTimeout() {
        return Integer.parseInt(get("test.timeout"));
    }

    public static int getRetries() {
        return Integer.parseInt(get("retries"));
    }

    /** Configured real test-account email (may be blank — see {@code test.user.email}). */
    public static String getTestUserEmail() {
        return get("test.user.email");
    }

    /** Configured real test-account password (may be blank — see {@code test.user.password}). */
    public static String getTestUserPassword() {
        return get("test.user.password");
    }

    // ---- Jira bug pipeline (Phase 15) -----------------------------------------------------
    // All values are optional; the pipeline stays off until isJiraConfigured() is true.
    // The API token is a secret: it must never be logged or written to the Allure report.

    /** Jira Cloud base URL, e.g. {@code https://<your-site>.atlassian.net}. */
    public static String getJiraBaseUrl() {
        return get("jira.base.url");
    }

    /** Atlassian account email used for Basic auth with {@code getJiraApiToken()}. */
    public static String getJiraEmail() {
        return get("jira.email");
    }

    /** Jira API token (secret — never log). Paired with {@link #getJiraEmail()}. */
    public static String getJiraApiToken() {
        return get("jira.api.token");
    }

    /** Master switch for the Jira bug pipeline (env {@code JIRA_TICKETS_ENABLED}). */
    public static boolean isJiraTicketsEnabled() {
        return Boolean.parseBoolean(get("jira.tickets.enabled"));
    }

    /** Jira project key the Bug tickets are filed against (e.g. {@code QA}). */
    public static String getJiraProjectKey() {
        return get("jira.project.key");
    }

    /** Issue type for auto-filed tickets (default {@code Bug}). */
    public static String getJiraIssueType() {
        return get("jira.issue.type");
    }

    /** Comma-separated labels from config (default {@code auto-test}). */
    public static List<String> getJiraLabels() {
        return Arrays.stream(get("jira.labels").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** True when the switch is on AND every required credential is present. */
    public static boolean isJiraConfigured() {
        return isJiraTicketsEnabled()
                && !isBlank(getJiraBaseUrl())
                && !isBlank(getJiraEmail())
                && !isBlank(getJiraApiToken())
                && !isBlank(getJiraProjectKey());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Prints the resolved configuration — handy for verifying env overrides. */
    public static void main(String[] args) {
        System.out.printf("environment        = %s%n", getEnvironment());
        System.out.printf("browser            = %s%n", getBrowser());
        System.out.printf("headless           = %s%n", getHeadless());
        System.out.printf("base.url           = %s%n", getBaseUrl());
        System.out.printf("test.timeout       = %d%n", getTimeout());
        System.out.printf("retries            = %d%n", getRetries());
        System.out.printf("test.user.email    = %s%n", getTestUserEmail());
        System.out.printf("test.user.password = %s%n", getTestUserPassword());
        System.out.printf("jira.enabled       = %b%n", isJiraTicketsEnabled());
        System.out.printf("jira.configured    = %b%n", isJiraConfigured());
        System.out.printf("jira.base.url      = %s%n", isBlank(getJiraBaseUrl()) ? "<unset>" : getJiraBaseUrl());
        System.out.printf("jira.email         = %s%n", isBlank(getJiraEmail()) ? "<unset>" : getJiraEmail());
        System.out.printf("jira.api.token     = %s%n", isBlank(getJiraApiToken()) ? "<unset>" : "<set>"); // never print the secret
        System.out.printf("jira.project.key   = %s%n", isBlank(getJiraProjectKey()) ? "<unset>" : getJiraProjectKey());
        System.out.printf("jira.issue.type    = %s%n", getJiraIssueType());
        System.out.printf("jira.labels        = %s%n", getJiraLabels());
    }
}
package com.funtime.config;

import java.io.IOException;
import java.io.InputStream;
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

    /** Env var for each known property key; only these keys can be overridden from the environment. */
    private static final Map<String, String> ENV_VAR_BY_KEY = Map.of(
            "app.env", "APP_ENV",
            "browser", "BROWSER",
            "headless", "HEADLESS",
            "base.url", "BASE_URL",
            "test.timeout", "TEST_TIMEOUT",
            "retries", "RETRIES",
            "test.user.email", "TEST_USER_EMAIL",
            "test.user.password", "TEST_USER_PASSWORD"
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
    }
}
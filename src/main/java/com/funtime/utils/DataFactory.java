package com.funtime.utils;

import com.funtime.config.ConfigReader;

import java.util.UUID;

/**
 * Generates test data so tests never hardcode literals.
 *
 * <p>Two kinds of data live here:
 * <ul>
 *   <li><b>Generated</b> — unique emails/strings for anonymous or negative paths
 *       ({@link #randomEmail()}, {@link #invalidUsers()}).</li>
 *   <li><b>Configured</b> — a real test account for the login happy path, supplied via
 *       {@code TEST_USER_EMAIL} / {@code TEST_USER_PASSWORD} (or {@code test.user.*}
 *       in {@code config.properties}). Blank unless a dedicated account exists.</li>
 * </ul>
 *
 * <p>Secrets rule: passwords returned by this factory are for a <b>dedicated test account</b>.
 * Tests may pass them to the page object but must <b>never log them</b> (see CLAUDE.md).
 */
public final class DataFactory {

    private DataFactory() {
    }

    /** A unique, valid-format email that is safe to use on public forms (example.com domain). */
    public static String randomEmail() {
        return "qa." + randomString("") + "@example.com";
    }

    /** {@code prefix} + a random suffix — unique per call, no spaces. */
    public static String randomString(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Invalid credentials for the login negative path: a well-formed email for an account
     * that does not exist, paired with a plausible but wrong password. Every row must be
     * rejected by the auth gate (the user must not be signed in afterwards).
     *
     * <p>Rows: {@code {email, password}} — consumed by a TestNG {@code @DataProvider};
     * the password is for the form only and is never logged.
     */
    public static Object[][] invalidUsers() {
        return new Object[][]{
                {randomEmail(), "WrongPassword1!"},
                {randomEmail(), "WrongPassword2!"},
        };
    }

    /** The configured real test-account email, or blank when none is set up. */
    public static String getConfiguredEmail() {
        return ConfigReader.getTestUserEmail();
    }

    /** The configured real test-account password, or blank when none is set up. */
    public static String getConfiguredPassword() {
        return ConfigReader.getTestUserPassword();
    }

    /** Whether a real test account is configured (both email and password present). */
    public static boolean isValidUserConfigured() {
        return !getConfiguredEmail().isBlank() && !getConfiguredPassword().isBlank();
    }
}
package com.funtime.listeners;

import com.funtime.base.BaseTest;
import com.funtime.config.ConfigReader;
import com.funtime.jira.JiraClient;
import com.funtime.jira.JiraConfig;
import com.funtime.jira.JiraTicket;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Files a Jira Bug ticket (with a failure screenshot) when a test fails.
 *
 * <p><b>Lifecycle:</b>
 * <ul>
 *   <li>{@link #onTestFailure} — runs on the failing test's thread while its {@link Page} is
 *       still alive (before {@code @AfterMethod} closes the context): captures a screenshot and
 *       records the failure. Nothing touches the network here.</li>
 *   <li>{@link #onFinish} — after the whole suite, files one ticket per recorded failure,
 *       deduplicated against open Jira issues by a stable summary. With retries enabled, a retry
 *       that <i>passes</i> must not leave a ticket: {@link RetryAnalyzer} calls
 *       {@link #onRetryGranted} to drop the pending record when it grants another attempt.</li>
 * </ul>
 *
 * <p>The pipeline is a no-op unless {@link ConfigReader#isJiraConfigured()}. Jira failures are
 * logged and swallowed — a Jira outage must never change the suite outcome, and the API token is
 * never logged.
 */
public class JiraBugListener implements ITestListener, ISuiteListener {

    private static final Logger LOG = LoggerFactory.getLogger(JiraBugListener.class);

    private static final long MAX_ATTACHMENT_BYTES = 1_500_000L;
    private static final String SUMMARY_PREFIX = "[AUTO-TEST] ";

    /** Pending failures keyed by {@code className#methodName}; thread-safe for parallel runs. */
    private static final Map<String, FailureRecord> pending = new ConcurrentHashMap<>();

    @Override
    public void onTestFailure(ITestResult result) {
        if (!ConfigReader.isJiraConfigured()) {
            return;
        }
        String key = testKey(result);
        byte[] screenshot = captureScreenshot(key);
        pending.put(key, new FailureRecord(key, result, screenshot)); // replaces on a later retry attempt
    }

    /** Called by {@link RetryAnalyzer} when it grants another attempt — a passed retry leaves no ticket. */
    public static void onRetryGranted(ITestResult result) {
        pending.remove(testKey(result));
    }

    @Override
    public void onFinish(ISuite suite) {
        if (pending.isEmpty()) {
            return;
        }
        if (!ConfigReader.isJiraConfigured()) {
            LOG.warn("Jira: {} failure(s) not filed — Jira not configured (see config.properties)", pending.size());
            return;
        }
        JiraClient client = new JiraClient(JiraConfig.fromConfig());
        for (FailureRecord record : pending.values()) {
            String url = client.reportFailure(toTicket(record));
            if (url != null) {
                LOG.info("Jira: filed {} — {}", record.key(), url);
            }
        }
        pending.clear();
    }

    /** Full-page screenshot, falling back to viewport when the page is very tall. Never throws. */
    private byte[] captureScreenshot(String testKey) {
        Page page = BaseTest.currentPage();
        if (page == null) {
            LOG.warn("Jira: no live page for {} — ticket without screenshot", testKey);
            return null;
        }
        try {
            byte[] shot = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
            if (shot.length > MAX_ATTACHMENT_BYTES) {
                byte[] viewport = page.screenshot(new Page.ScreenshotOptions());
                LOG.info("Jira: {} full-page screenshot {} bytes > cap — using viewport {} bytes",
                        testKey, shot.length, viewport.length);
                return viewport;
            }
            return shot;
        } catch (Exception e) {
            LOG.warn("Jira: could not capture failure screenshot for {}", testKey, e);
            return null;
        }
    }

    private JiraTicket toTicket(FailureRecord r) {
        String summary = SUMMARY_PREFIX + r.key();
        String description = String.join("\n",
                "Automated failure report from the E2E suite.",
                "",
                "Test:        " + r.key(),
                "Environment: " + ConfigReader.getEnvironment() + " (" + ConfigReader.getBaseUrl() + ")",
                "Browser:     " + ConfigReader.getBrowser() + " (headless=" + ConfigReader.getHeadless() + ")",
                "Group:       " + r.group(),
                "Time (UTC):  " + OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "",
                "Stack trace:",
                r.stackTrace());
        return new JiraTicket(r.key(), summary, description, ConfigReader.getJiraLabels(), r.screenshotBytes());
    }

    private static String testKey(ITestResult result) {
        return result.getTestClass().getName() + "#" + result.getMethod().getMethodName();
    }

    private record FailureRecord(String key, String group, String stackTrace, byte[] screenshotBytes) {
        FailureRecord(String key, ITestResult result, byte[] screenshotBytes) {
            this(key,
                    result.getMethod().getGroups().length > 0
                            ? String.join(",", result.getMethod().getGroups())
                            : "-",
                    throwableToString(result),
                    screenshotBytes);
        }
    }

    private static String throwableToString(ITestResult result) {
        Throwable t = result.getThrowable();
        if (t == null) {
            return "(no throwable recorded)";
        }
        StringBuilder sb = new StringBuilder(t + "\n");
        for (StackTraceElement e : t.getStackTrace()) {
            sb.append("    at ").append(e).append('\n');
            if (sb.length() > 3000) {
                sb.append("    …truncated");
                break;
            }
        }
        return sb.toString();
    }
}
package com.funtime.base;

import com.funtime.config.ConfigReader;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the Playwright lifecycle. Tests extend this class and only use the injected
 * {@code page} (via {@link #page()}); they never touch {@code Playwright.create()}.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>{@code @BeforeSuite} — log the configured browser once per suite.</li>
 *   <li>{@code @BeforeMethod} — lazily create the test-thread {@link Playwright} + {@link Browser}
 *       (first test on that thread), then a fresh, isolated {@link BrowserContext} + {@link Page}
 *       per test.</li>
 *   <li>{@code @AfterMethod} — close the context, isolating every test.</li>
 *   <li>{@code @AfterSuite} — close every {@link Playwright} created by this suite.</li>
 * </ul>
 *
 * <p><b>Why per-thread Playwright/Browser?</b> Playwright objects are <b>not thread-safe</b>;
 * the official guidance is one {@code Playwright} instance per test thread
 * (microsoft/playwright-java#212). Sharing a single instance across {@code parallel="methods"}
 * threads fails randomly with "Cannot find object to call …" exceptions, so each worker thread
 * owns its own browser chain (created once per thread, reused across that thread's tests).
 *
 * <p>{@link BrowserContext}/{@link Page} are per-test and {@link ThreadLocal}-backed so tests can
 * run in parallel ({@code parallel="methods"} in testng.xml, Phase 6).
 */
public abstract class BaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(BaseTest.class);

    /** Every Playwright created by this suite; closed once in {@code @AfterSuite}. */
    private static final Set<Playwright> createdPlaywrights = ConcurrentHashMap.newKeySet();

    private static final ThreadLocal<Playwright> playwright = new ThreadLocal<>();
    private static final ThreadLocal<Browser> browser = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> context = new ThreadLocal<>();
    private static final ThreadLocal<Page> page = new ThreadLocal<>();

    @BeforeSuite
    public static void beforeSuite() {
        LOG.info("Suite config: environment={}, browser={}, headless={}, base.url={}",
                ConfigReader.getEnvironment(), ConfigReader.getBrowser(), ConfigReader.getHeadless(), ConfigReader.getBaseUrl());
    }

    /**
     * {@code alwaysRun = true} is required for group-filtered runs: TestNG does not execute a
     * {@code @BeforeMethod} without a matching {@code groups} attribute when the suite is run
     * with {@code -Dgroups=smoke} (verified 2026-08-22), so the per-test page would never open.
     */
    @BeforeMethod(alwaysRun = true)
    public void beforeMethod() {
        ensureBrowserForThisThread();
        BrowserContext ctx = browser.get().newContext();
        context.set(ctx);
        Page p = ctx.newPage();
        page.set(p);
        p.setDefaultTimeout(ConfigReader.getTimeout());
        LOG.debug("Opened context + page on thread {}", Thread.currentThread().getName());
    }

    private static void ensureBrowserForThisThread() {
        if (playwright.get() != null) {
            return;
        }
        Playwright pw = Playwright.create();
        createdPlaywrights.add(pw);
        playwright.set(pw);
        browser.set(PlaywrightFactory.launch(pw));
        LOG.debug("Launched {} (headless={}) on thread {}",
                ConfigReader.getBrowser(), ConfigReader.getHeadless(), Thread.currentThread().getName());
    }

    /** See {@link #beforeMethod()} — {@code alwaysRun} keeps cleanup paired with the test under group filtering. */
    @AfterMethod(alwaysRun = true)
    public void afterMethod() {
        BrowserContext ctx = context.get();
        if (ctx != null) {
            ctx.close();
        }
        context.remove();
        page.remove();
    }

    @AfterSuite
    public static void afterSuite() {
        createdPlaywrights.forEach(Playwright::close);
        createdPlaywrights.clear();
    }

    /** The current test's page. */
    protected Page page() {
        return page.get();
    }

    /**
     * The current test's page — static accessor for TestNG listeners, which run on the
     * test thread and therefore see this thread's {@link ThreadLocal} page. Used by
     * {@link com.funtime.listeners.ScreenshotOnFailureListener} to capture a failure shot.
     */
    public static Page currentPage() {
        return page.get();
    }

    /** The current test's browser context. */
    protected BrowserContext context() {
        return context.get();
    }
}
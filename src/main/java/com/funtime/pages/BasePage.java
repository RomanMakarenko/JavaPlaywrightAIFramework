package com.funtime.pages;

import com.funtime.config.ConfigReader;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;

/**
 * Common page behaviour. Page Objects hold a {@link Page} and expose user-intent actions
 * that return the successor page for fluent chaining.
 *
 * <p>Conventions (CLAUDE.md): Page Objects never contain assertions; {@link #waitForVisible}
 * is Playwright's auto-wait, not a test assertion. Locators are private and declared once.
 */
public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }

    /** Navigates to a site path, e.g. {@code "/nature"} or {@code ""} for the home page. */
    protected void navigate(String path) {
        String base = ConfigReader.getBaseUrl();
        String p = path == null ? "" : path.trim();
        page.navigate(base + (p.isEmpty() || p.startsWith("/") ? p : "/" + p));
    }

    /** Browser tab title — a page-state accessor available to tests. */
    public String getTitle() {
        return page.title();
    }

    /** Current URL of the page — lets tests assert where a flow left the user. */
    public String getUrl() {
        return page.url();
    }

    /** Auto-waits until the locator is visible (retries until the action timeout). */
    protected void waitForVisible(Locator locator) {
        PlaywrightAssertions.assertThat(locator).isVisible();
    }
}
package com.funtime.base;

import com.funtime.config.ConfigReader;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

/**
 * Builds the configured {@link Browser}. All knobs come from {@link ConfigReader}
 * ({@code browser}, {@code headless}), so the same factory serves local runs and CI.
 *
 * <p>Kept small on purpose: a CI-specific launch variant (e.g. a browser channel)
 * is added here without touching {@link BaseTest}.
 */
public final class PlaywrightFactory {

    private PlaywrightFactory() {
    }

    public static Browser launch(Playwright playwright) {
        String browserName = ConfigReader.getBrowser();
        boolean headless = ConfigReader.getHeadless();
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless);
        return switch (browserName) {
            case "firefox" -> playwright.firefox().launch(options);
            case "webkit" -> playwright.webkit().launch(options);
            case "msedge" -> playwright.chromium().launch(options.setChannel("msedge"));
            default -> playwright.chromium().launch(options);
        };
    }
}
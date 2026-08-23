package com.funtime.listeners;

import com.funtime.base.BaseTest;
import com.microsoft.playwright.Page;
import io.qameta.allure.Attachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * Captures a full-page screenshot whenever a test fails and attaches it to the Allure report.
 *
 * <p>The listener runs on the same thread as the failed test, so it reads the live page via
 * {@link BaseTest#currentPage()} (a {@link ThreadLocal}); by the time {@code onTestFailure}
 * fires, {@code @AfterMethod} has not yet closed the context. All capture is guarded — a page
 * torn down early or a mid-navigation failure must never mask the original error.
 */
public class ScreenshotOnFailureListener implements ITestListener {

    private static final Logger LOG = LoggerFactory.getLogger(ScreenshotOnFailureListener.class);

    @Override
    public void onTestFailure(ITestResult result) {
        String testName = result.getTestClass().getName() + "." + result.getMethod().getMethodName();
        Page page = BaseTest.currentPage();
        if (page == null) {
            LOG.warn("No live page for {} — skipping failure screenshot", testName);
            return;
        }
        try {
            byte[] screenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
            attachScreenshot(testName, screenshot);
            LOG.info("Attached failure screenshot for {}", testName);
        } catch (Exception e) {
            LOG.warn("Could not capture failure screenshot for {}", testName, e);
        }
    }

    /** Allure attachment: {@code {0}} is replaced with the test name by the framework. */
    @Attachment(value = "Page screenshot on failure: {0}", type = "image/png")
    public static byte[] attachScreenshot(String testName, byte[] screenshot) {
        return screenshot;
    }
}
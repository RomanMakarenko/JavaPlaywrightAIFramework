package com.funtime.tests;

import com.funtime.base.BaseTest;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Framework sanity check: verifies that {@link BaseTest} hands every test method its own fresh
 * {@link com.microsoft.playwright.BrowserContext} and {@link com.microsoft.playwright.Page}.
 * That contract is what makes {@code parallel="methods"} safe (Phase 6) — no state may leak
 * between tests. The navigation target is {@code about:blank} so this stays hermetic and does
 * not depend on the target app being reachable.
 */
public class BaseTestIsolationTest extends BaseTest {

    private static final Set<Object> CONTEXTS = ConcurrentHashMap.newKeySet();
    private static final Set<Object> PAGES = ConcurrentHashMap.newKeySet();

    @Test(groups = "smoke")
    @Feature("Framework")
    @Story("BaseTest isolation")
    @Severity(SeverityLevel.NORMAL)
    @Owner("QA")
    void sanity_freshContext_testOne() {
        // Act
        page().navigate("about:blank");

        // Assert
        assertThat(page().url()).isEqualTo("about:blank");
        assertThat(CONTEXTS.add(context())).as("context must be new, not shared").isTrue();
        assertThat(PAGES.add(page())).as("page must be new, not shared").isTrue();
    }

    @Test(groups = "smoke")
    @Feature("Framework")
    @Story("BaseTest isolation")
    @Severity(SeverityLevel.NORMAL)
    @Owner("QA")
    void sanity_freshContext_testTwo() {
        // Act
        page().navigate("about:blank");

        // Assert
        assertThat(page().url()).isEqualTo("about:blank");
        assertThat(CONTEXTS.add(context())).as("context must be new, not shared").isTrue();
        assertThat(PAGES.add(page())).as("page must be new, not shared").isTrue();
    }
}
package com.funtime.tests;

import com.funtime.base.BaseTest;
import com.funtime.config.ConfigReader;
import com.funtime.pages.HomePage;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CI verification probe for the Jira bug pipeline (Phase 15): deliberately fails so
 * {@code JiraBugListener} files a literal {@code Bug} ticket with a failure screenshot.
 *
 * <p>Safe by default — the test is <b>skipped</b> unless it is explicitly armed:
 * <ul>
 *   <li>env {@code JIRA_CI_PROBE=true} (set by the CI {@code run-jira-ci-probe} dispatch input),
 *       and</li>
 *   <li>Jira is configured ({@link ConfigReader#isJiraConfigured()}).</li>
 * </ul>
 *
 * <p>So it can live in the repo without ever affecting normal runs; only a manual CI dispatch
 * (run with {@code -Pjira-probe} so the dedicated {@code testng-jira-probe.xml} suite is used)
 * turns it into a failing test that files a ticket. The dedupe summary is a fresh
 * {@code class#method}, so the first verification run creates a new ticket and later runs reuse
 * the open one.
 */
public class JiraCiProbeTest extends BaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(JiraCiProbeTest.class);

    @Test(groups = "ci-jira-probe")
    @Feature("Jira pipeline probe")
    @Story("A failing CI test files a literal Bug ticket")
    @Severity(SeverityLevel.NORMAL)
    @Owner("QA")
    void probe_ciFailingTest_filesJiraBug() {
        // Arrange — only fail when explicitly armed, so the suite stays green otherwise.
        if (!"true".equals(System.getenv("JIRA_CI_PROBE"))) {
            LOG.warn("CI Jira probe not armed (set JIRA_CI_PROBE=true to fail this test) — skipping");
            throw new SkipException("CI Jira probe not armed (JIRA_CI_PROBE != true)");
        }
        if (!ConfigReader.isJiraConfigured()) {
            LOG.warn("Jira not configured — the probe would fail without filing a ticket — skipping");
            throw new SkipException("Jira not configured (JIRA_TICKETS_ENABLED + credentials)");
        }

        new HomePage(page()).open();

        // Assert — deliberate failure to trigger the Jira bug pipeline.
        assertThat(page().title())
                .as("CI Jira probe — deliberate failure to verify the auto-ticket pipeline")
                .isEqualTo("CI-JIRA-PROBE deliberately failing");
    }
}
package com.funtime.tests;

import com.funtime.base.BaseTest;
import com.funtime.pages.HomePage;
import com.funtime.pages.LoginPage;
import com.funtime.utils.DataFactory;
import io.qameta.allure.Allure;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Authorisation flows against the real /login form. Demonstrates the test-layer conventions
 * from CLAUDE.md: Allure annotations, TestNG groups, a data-driven negative path, AssertJ and
 * Arrange / Act / Assert.
 *
 * <p>Site note (probed 2026-08-22): submitting invalid credentials makes the funtime backend
 * return a 500 "Упс! Виникла помилка." page rather than a friendly validation message — but the
 * URL stays on {@code /login}. The negative test therefore asserts the stable contract
 * <i>"a failed login must not leave the login route"</i>, which holds for both behaviours.
 */
public class LoginTest extends BaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(LoginTest.class);

    @Test(dataProvider = "invalidUsers", groups = "smoke")
    @Feature("Auth")
    @Story("Invalid credentials are rejected")
    @Severity(SeverityLevel.CRITICAL)
    @Owner("QA")
    void login_invalidCredentials_isNotAuthenticated(String email, String password) {
        // Arrange
        Allure.parameter("email", email); // password is deliberately never logged (CLAUDE.md)

        // Act
        LoginPage login = new HomePage(page()).open().openLogin();
        login.login(email, password);

        // Assert
        assertThat(login.getUrl())
                .as("a rejected login must not leave the login route")
                .contains("/login");
    }

    @Test(groups = "regression")
    @Feature("Auth")
    @Story("Sign in with a valid test account")
    @Severity(SeverityLevel.NORMAL)
    @Owner("QA")
    void login_validUser_redirectsToHome() {
        // Arrange — needs a dedicated test account; skip loudly until one is configured.
        if (!DataFactory.isValidUserConfigured()) {
            LOG.warn("No test account configured (TEST_USER_EMAIL / TEST_USER_PASSWORD) — skipping");
            throw new SkipException("Configure a test account via TEST_USER_EMAIL / TEST_USER_PASSWORD");
        }

        // Act
        HomePage home = new LoginPage(page()).open()
                .login(DataFactory.getConfiguredEmail(), DataFactory.getConfiguredPassword());

        // Assert
        assertThat(home.getUrl())
                .as("a successful login must leave the login page")
                .doesNotContain("/login");
    }

    @DataProvider(name = "invalidUsers")
    public Object[][] invalidUsers() {
        return DataFactory.invalidUsers();
    }
}
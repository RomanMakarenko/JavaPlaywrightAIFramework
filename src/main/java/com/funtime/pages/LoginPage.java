package com.funtime.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Step;

/**
 * Authorisation form ({@code /login}) — email + password + optional "remember me".
 * There is also a Google social login, not modelled yet.
 */
public class LoginPage extends BasePage {

    private static final String EMAIL_PLACEHOLDER = "Введіть email";
    private static final String PASSWORD_PLACEHOLDER = "Введіть пароль";

    public LoginPage(Page page) {
        super(page);
    }

    @Step("Open the login page")
    public LoginPage open() {
        navigate("/login");
        return this;
    }

    public LoginPage fillEmail(String email) {
        page.getByPlaceholder(EMAIL_PLACEHOLDER).fill(email);
        return this;
    }

    public LoginPage fillPassword(String password) {
        page.getByPlaceholder(PASSWORD_PLACEHOLDER).fill(password);
        return this;
    }

    public LoginPage rememberMe() {
        page.locator("input[name='remember']").check();
        return this;
    }

    public boolean isLoginButtonVisible() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Увійти").setExact(true))
                .isVisible();
    }

    /**
     * Submits the form. A successful login is assumed to land on the home page —
     * to be confirmed against a real account once test credentials exist.
     */
    @Step("Submit the login form")
    public HomePage submit() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Увійти").setExact(true)).click();
        return new HomePage(page);
    }

    /**
     * Convenience: fill credentials and submit in one intent.
     * The step message deliberately omits the password (CLAUDE.md: never log secrets).
     */
    @Step("Log in as {email}")
    public HomePage login(String email, String password) {
        return fillEmail(email).fillPassword(password).submit();
    }
}
package com.funtime.pages;

import com.microsoft.playwright.Page;
import io.qameta.allure.Step;

/** Home page of funtime.com.ua — the entry point to the site's categories. */
public class HomePage extends BasePage {

    public HomePage(Page page) {
        super(page);
    }

    @Step("Open the home page")
    public HomePage open() {
        navigate("");
        return this;
    }

    /**
     * Opens a top-level category from the desktop menu (e.g. {@code "Природа"}).
     * Menu items are icon-only links whose accessible name is their {@code title}
     * attribute, so they are matched by {@code getByTitle} (stable, unlike role-name).
     */
    @Step("Open category {categoryName}")
    public CategoryPage openCategory(String categoryName) {
        page.getByTitle(categoryName).first().click();
        return new CategoryPage(page);
    }

    /**
     * Opens the authorisation page. The desktop header has no visible "Увійти" link —
     * the login entry lives only in the hidden mobile/sidebar menu, so we navigate to
     * {@code /login} directly (same target, stable locator).
     */
    @Step("Open the login page")
    public LoginPage openLogin() {
        navigate("/login");
        return new LoginPage(page);
    }
}
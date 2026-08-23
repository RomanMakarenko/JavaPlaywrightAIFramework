package com.funtime.pages;

import com.microsoft.playwright.Page;
import io.qameta.allure.Step;

/** A single article page (e.g. {@code /articles/<slug>}). */
public class ArticlePage extends BasePage {

    public ArticlePage(Page page) {
        super(page);
    }

    @Step("Open article {path}")
    public ArticlePage open(String path) {
        navigate(path);
        return this;
    }

    public String getArticleTitle() {
        return page.locator("h1").first().innerText();
    }

    /** Body text of the article. The site's own class name is {@code acticle-content} (sic). */
    public String getArticleText() {
        return page.locator(".acticle-content").first().innerText();
    }
}
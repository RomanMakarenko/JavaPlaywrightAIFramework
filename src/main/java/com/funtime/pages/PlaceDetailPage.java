package com.funtime.pages;

import com.microsoft.playwright.Page;
import io.qameta.allure.Step;

/** A single place detail page (e.g. {@code /nature/bile}). */
public class PlaceDetailPage extends BasePage {

    public PlaceDetailPage(Page page) {
        super(page);
    }

    @Step("Open place detail {path}")
    public PlaceDetailPage open(String path) {
        navigate(path);
        return this;
    }

    public String getPlaceTitle() {
        return page.locator("h1").first().innerText();
    }

    /** Raw text of the location content block (breadcrumb + description body). */
    public String getContentText() {
        return page.locator(".location.content-block").first().innerText();
    }
}
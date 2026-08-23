package com.funtime.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;

import java.util.List;

/** A category listing (e.g. {@code /nature}) showing the category's place cards. */
public class CategoryPage extends BasePage {

    /**
     * A place card on a category listing. On funtime.com.ua the category's places are
     * {@code article.img-list-grid} cards; the unrelated {@code article.card.collection-card}
     * block at the page bottom is the "Актуальні добірки" (collections) section.
     */
    private static final String PLACE_CARD = "article.img-list-grid";

    public CategoryPage(Page page) {
        super(page);
    }

    @Step("Open category listing {path}")
    public CategoryPage open(String path) {
        navigate(path);
        return this;
    }

    public String getHeading() {
        return page.locator("h1.category-h1").innerText();
    }

    public int getPlaceCardCount() {
        // count() is a synchronous DOM query — wait for at least one card first (auto-wait).
        page.locator(PLACE_CARD).first().waitFor();
        return page.locator(PLACE_CARD).count();
    }

    /**
     * Names of all place cards on the page, in display order. Like {@link #getPlaceCardCount()},
     * this waits for the first title before reading — {@code allInnerTexts()} does not auto-wait
     * (the category grid is rendered asynchronously after navigation, so without the wait it can
     * return an empty list and the caller gets a confusing {@code IndexOutOfBoundsException}).
     */
    public List<String> getPlaceNames() {
        page.locator(PLACE_CARD + " .grid-title").first().waitFor();
        return page.locator(PLACE_CARD + " .grid-title").allInnerTexts();
    }

    /** Opens a place card by its title and returns the place detail page. */
    @Step("Open place \"{placeName}\"")
    public PlaceDetailPage openPlace(String placeName) {
        page.locator(PLACE_CARD)
                .filter(new Locator.FilterOptions().setHasText(placeName))
                .first()
                .locator("a.grid-inner-wrapper")
                .click();
        return new PlaceDetailPage(page);
    }
}
package com.funtime.tests;

import com.funtime.base.BaseTest;
import com.funtime.pages.CategoryPage;
import com.funtime.pages.HomePage;
import com.funtime.pages.PlaceDetailPage;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sample "full journey" test — the real-site replacement for the saucedemo-era
 * {@code CheckoutFlowTest} (funtime.com.ua has no cart/checkout). Walks Home → Category →
 * Place Detail through Page Objects only, exercising fluent chaining from one page to the next.
 */
public class SiteJourneyTest extends BaseTest {

    @Test(groups = "smoke")
    @Feature("Places")
    @Story("Browse a place from the home page through its category")
    @Severity(SeverityLevel.NORMAL)
    @Owner("QA")
    void browsePlace_fullJourney_showsContent() {
        // Act — anonymous browsing has no preconditions, so there is nothing to arrange
        CategoryPage nature = new HomePage(page()).open().openCategory("Природа");
        String firstPlace = nature.getPlaceNames().get(0);
        PlaceDetailPage place = nature.openPlace(firstPlace);

        // Assert
        assertThat(place.getPlaceTitle()).isNotBlank();
        assertThat(place.getContentText()).isNotBlank();
    }
}
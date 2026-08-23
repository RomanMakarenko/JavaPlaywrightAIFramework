package com.funtime.tests;

import com.funtime.base.BaseTest;
import com.funtime.pages.ArticlePage;
import com.funtime.pages.CategoryPage;
import com.funtime.pages.HomePage;
import com.funtime.pages.LoginPage;
import com.funtime.pages.PlaceDetailPage;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-app smoke checks against funtime.com.ua. Every flow goes through Page Objects
 * only and asserts on page state. Independent, fast, {@code smoke}-grouped (runs on
 * every PR in CI).
 */
public class FuntimeSiteSmokeTest extends BaseTest {

    @Test(groups = "smoke")
    @Feature("Home")
    @Story("Open the home page")
    @Severity(SeverityLevel.CRITICAL)
    @Owner("QA")
    void homePage_opens_showsFuntimeTitle() {
        // Act
        HomePage home = new HomePage(page()).open();

        // Assert
        assertThat(home.getTitle()).contains("Funtime");
    }

    @Test(groups = "smoke")
    @Feature("Categories")
    @Story("Browse a category")
    @Severity(SeverityLevel.NORMAL)
    @Owner("QA")
    void categoryPage_opens_showsPlaceCards() {
        // Act
        CategoryPage nature = new HomePage(page()).open().openCategory("Природа");

        // Assert
        assertThat(nature.getHeading()).isEqualTo("Природа");
        assertThat(nature.getPlaceCardCount()).isGreaterThan(0);
    }

    @Test(groups = "smoke")
    @Feature("Places")
    @Story("Open a place detail from a category")
    @Severity(SeverityLevel.NORMAL)
    @Owner("QA")
    void placeDetail_viaCategory_showsTitle() {
        // Act
        CategoryPage nature = new HomePage(page()).open().openCategory("Природа");
        String firstPlace = nature.getPlaceNames().get(0);
        PlaceDetailPage place = nature.openPlace(firstPlace);

        // Assert
        assertThat(place.getPlaceTitle()).isNotBlank();
    }

    @Test(groups = "smoke")
    @Feature("Articles")
    @Story("Open an article")
    @Severity(SeverityLevel.NORMAL)
    @Owner("QA")
    void articlePage_opens_showsTitle() {
        // Act
        ArticlePage article = new ArticlePage(page())
                .open("/articles/kudy-poyikhaty-na-vykhidni-z-kyyeva-na-mashyni-5-idei-dlya-podorozhi-u-2025-rotsi");

        // Assert
        assertThat(article.getArticleTitle()).isNotBlank();
        assertThat(article.getArticleText()).isNotBlank();
    }

    @Test(groups = "smoke")
    @Feature("Auth")
    @Story("Login form is reachable")
    @Severity(SeverityLevel.NORMAL)
    @Owner("QA")
    void loginPage_opens_showsForm() {
        // Act
        LoginPage login = new HomePage(page()).open().openLogin();

        // Assert
        assertThat(login.isLoginButtonVisible()).isTrue();
    }
}
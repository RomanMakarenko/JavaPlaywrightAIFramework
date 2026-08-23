---
name: page-object-generator
description: Create a Page Object class for this framework from a URL and captured locators/DOM, following the conventions in CLAUDE.md. Use when adding a new page or reusable fragment to the pages/ layer.
---

# Page Object Generator

Produce a Page Object (PO) class for a page (or reusable fragment) of the target app, following CLAUDE.md conventions. The output must be independently verifiable by `style-review`.

## When to use
- A new page/section of the app needs test coverage and has no Page Object yet.
- Do **not** use this to write tests — that is `test-generator`.

## Before writing anything
1. Read `CLAUDE.md` → `Page Objects` + `Selector strategy` sections.
2. Study the canonical templates in `src/main/java/com/funtime/pages/`:
   - `BasePage.java` — helpers: `navigate(path)`, `getTitle()`, `getUrl()`, `waitForVisible(locator)`.
   - `HomePage.java`, `CategoryPage.java`, `LoginPage.java` — locator + intent-method patterns and `@Step` annotations.
3. Capture the real page:
   - Use the Playwright MCP (`playwright_*` tools) to open the URL and inspect the live DOM / accessibility tree, **or** fetch the page with `curl` and inspect the HTML.
   - Look for stable hooks: `data-testid`/`data-test`, roles, labels, `title` attributes, semantic CSS classes. Prefer what survives markup churn.

## Selector strategy (priority order)
1. `data-testid` / `data-test` attributes (add them to the app when missing).
2. Role-based: `page.getByRole(AriaRole.button, new Page.GetByRoleOptions().setName("Login"))` — Java uses the `AriaRole` enum, not strings.
3. `getByLabel` / `getByPlaceholder` / `getByText`.
4. CSS/XPath — **last resort** (brittle to UI changes).

## Producing the PO class
- Package `com.funtime.pages`; `public class XPage extends BasePage`; constructor `XPage(Page page) { super(page); }` (a reusable fragment takes the parent `Locator` instead — see CLAUDE.md).
- **Locators are `private`, declared once at the top** as fields/constants; reuse them, never re-query inline.
- Methods describe **user intent**, not mechanics: `login(email, password)` over `fillEmail().clickSubmit()`.
- Return the **successor page** for fluent chaining: `openCategory(name)` returns `CategoryPage`, `openLogin()` returns `LoginPage`. State accessors (`getTitle()`, `getHeading()`, `getPlaceNames()`) return values.
- Annotate intent methods with `@Step("Human-readable {param}")` — e.g. `@Step("Open category {categoryName}")` — so Allure records the fluent chain. Never interpolate secrets into step names.
- **No assertions, no `Assert` imports, no `Thread.sleep`** — wait via Playwright auto-wait (`locator.first().waitFor()`, or `BasePage.waitForVisible`).

## Real-site quirks to remember (funtime.com.ua)
- Icon-only nav links are matched by `getByTitle("Природа")` — their accessible name is the `title` attribute, not a role-name.
- Elements hidden on the current breakpoint (e.g. the mobile-only login menu) are excluded by `getByRole` — navigate directly (`/login`) when the desktop entry doesn't exist.
- `Locator.count()` and `allInnerTexts()` do **not** auto-wait — wait for the first element before reading them, or they can return empty and the caller gets a confusing `IndexOutOfBoundsException`.

## After writing
1. Compile: `mvn -q clean compile` (main sources must stay green).
2. Exercise it through an existing test that reaches the page, or run `mvn test -Dtest=FuntimeSiteSmokeTest`.
3. Run `style-review` on the file and fix every violation before calling the work done.
---
name: test-generator
description: Write a TestNG test for this framework from a natural-language scenario, following the conventions in CLAUDE.md. Use whenever asked to add a new test or cover a new flow.
---

# Test Generator

Turn a natural-language scenario into a TestNG test class that complies with this framework's conventions (CLAUDE.md → `Conventions`). The output must be independently verifiable by `style-review`.

## When to use
- "Add a test that …", "Write a test for …", a new scenario or flow to cover.
- Do **not** use this to create Page Objects — that is `page-object-generator`.

## Before writing anything
1. Read `CLAUDE.md` → `Conventions` (test structure & naming, Allure annotations, groups, data-driven, timing).
2. Study the canonical templates in `src/test/java/com/funtime/tests/`:
   - `LoginTest.java` — data-driven negative path, `@DataProvider`, `Allure.parameter`, skip-with-reason for unconfigured accounts.
   - `SiteJourneyTest.java` — fluent Page-Object chaining across an end-to-end journey.
   - `FuntimeSiteSmokeTest.java` — small independent smoke checks, one `@Test` per behaviour.
3. Check the existing Page Objects in `src/main/java/com/funtime/pages/` for the actions the scenario needs. **Reuse them** — a test never contains raw locators, `page.locator(...)`, or `Playwright.create()`.

## Producing the test

### Class layout
- One class per feature/flow, named `<Feature>Test`, in `src/test/java/com/funtime/tests/`, package `com.funtime.tests`.
- `extends BaseTest`; use `page()` for the current page. Never touch `context()` unless truly necessary.
- Javadoc: 1–2 lines on the flow covered and any site quirks worth knowing.

### Method naming
`{action}_{expectedResult}_{condition}` — e.g. `login_invalidCredentials_isNotAuthenticated`, `browsePlace_fullJourney_showsContent`.

### Body
- **Arrange / Act / Assert** — three phases separated by blank lines; assert only at the end.
- Act through Page Objects only: `new HomePage(page()).open().openLogin().login(...)`.
- Assert with **AssertJ** (`assertThat(...)`), prefer a `.as("…")` description. Playwright auto-waiting handles timing — **never `Thread.sleep`**.

### Annotations (on every test method)
```java
@Test(groups = "smoke")              // smoke | regression — smoke must stay fast (~<1 min) & stable
@Feature("…")                        // e.g. "Auth", "Places", "Categories", "Home"
@Story("…")                          // short user-facing story
@Severity(SeverityLevel.CRITICAL)    // BLOCKER | CRITICAL | NORMAL | MINOR | TRIVIAL
@Owner("QA")
```
Add `@TmsLink`/`@AllureId` when a test-management ID is available.

### Data-driven cases
- `@Test(dataProvider = "…", groups = "…")` plus a `@DataProvider` named after the flow.
- Put reusable data in `utils/DataFactory`; one-off rows can live in a private provider.
- Log row identifiers with `Allure.parameter("email", email)` so failures are identifiable.
- **Never log passwords/secrets** — add a comment noting that.

### Unconfigured preconditions
If a scenario needs a real account/endpoint that isn't configured, follow the `LoginTest` skip pattern: log a warning and `throw new SkipException("…")` with a clear reason — never silently pass.

## After writing
1. Compile: `mvn -q clean compile`.
2. Run just the new test for fast feedback: `mvn test -Dtest=<ClassName>#<methodName>`. (Note: `-Dtest` bypasses `testng.xml`, so retries + failure screenshots only apply on `mvn test` / `-Dgroups` — see README.)
3. Run `style-review` on the file and fix every violation it reports before calling the work done.
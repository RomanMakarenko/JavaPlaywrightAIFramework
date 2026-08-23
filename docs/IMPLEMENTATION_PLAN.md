# Implementation Plan — JavaPlaywrightAIFramework

Detailed step-by-step plan to scaffold the E2E test framework defined in [CLAUDE.md](../CLAUDE.md): **Java 21 + TestNG + Playwright + Maven**, Allure reporting, GitHub Actions CI, Playwright MCP, Claude skills, and style-enforcement hooks.

---

## Progress

> **Resume mechanism:** this checklist is the single source of truth for "where are we".
> A new session reads `CLAUDE.md` + this file and starts at the **first unchecked phase**.
> Update this list after each phase completes.

Current position: **Phase 12 implemented** — Definition of Done verified (see the done-note below). The repo was pushed to GitHub 2026-08-23 (commit `73aa656`) — one acceptance item (CI green + Pages) awaits the first real GitHub run. Next: Phase 13 (future funtime.com.ua work, outside skeleton scope).

- [x] Phase 0 — Prerequisites verified (JDK 21, Maven, Node, Allure CLI, Playwright browsers)
- [x] Phase 1 — `pom.xml`: dependencies + surefire/AspectJ `argLine` + allure-maven plugin
- [x] Phase 2 — directory structure & config layer (`ConfigReader`, `config.properties`, `allure.properties`)
- [x] Phase 3 — `BaseTest` + Playwright lifecycle (`ThreadLocal`, `PlaywrightFactory`)
- [x] Phase 4 — Page Object layer + target app (BasePage + funtime.com.ua POs)
- [x] Phase 5 — test layer: sample tests, Allure annotations, DataProvider, listeners, RetryAnalyzer
- [x] Phase 6 — `testng.xml` (`parallel="methods"`, groups, listeners) & running model
- [x] Phase 7 — Allure report verified (`allure serve` / `mvn allure:report`)
- [x] Phase 8 — Playwright MCP registered (`.mcp.json`) — **pulled forward** during Phase 4
- [x] Phase 9 — Claude skills (`.claude/skills/`: test-generator, page-object-generator, style-review, test-runner)
- [x] Phase 10 — style hooks (`.claude/settings.json` + `scripts/style-check.sh`)
- [x] Phase 11 — GitHub Actions CI (`.github/workflows/ci.yml`) — *acceptance awaits the first real GitHub run*
- [x] Phase 12 — Definition of Done checklist — *one item deferred: CI on GitHub (repo not pushed yet), see note*
- [ ] Phase 13 — funtime.com.ua (future, outside skeleton scope)

---

## Status & Context

- CLAUDE.md is the single source of truth (spec + conventions) — **do not drift from it**.
- The framework is a **generic skeleton validated against the real target** `https://funtime.com.ua/` (user override 2026-08-22 — no demo app). The skeleton stays app-agnostic; only the `pages/` layer is site-specific.

## Key decisions (already agreed)

| Decision | Choice |
|---|---|
| Docs language | English (CLAUDE.md, plan, code comments) |
| Base package name | `com.funtime` (decided 2026-08-22; replaces `org.example`) |
| Target app | **`https://funtime.com.ua/` wired in now** — user override (2026-08-22): skip generic demo app, go straight to the real site. Affects Phase 2 `base.url`, Phase 4 POs (explore real site via MCP), Phase 13 |
| Assertion library | **AssertJ** (`assertThat(...)`) |
| Skills | `test-generator`, `page-object-generator`, `style-review`, `test-runner` |
| Hooks | PostToolUse style checks on test/page files |
| CI | GitHub Actions, smoke-fast-on-PR / full-on-main |

## Open decision points (resolve before Phase 1) — **all resolved 2026-08-22**

1. **Base package name** → `com.funtime`. ✅
2. **Demo/validation app** → **no demo app**: user chose to target `https://funtime.com.ua/` directly. (saucedemo / demo.playwright.dev discarded.) ✅
3. **Assertion library** → **AssertJ**. ✅

---

## Phase 0 — Prerequisites (dev machine)

Verify once before starting:

```bash
java -version            # expect 21+
mvn -version             # expect 3.8+
node -v                  # for Playwright MCP (expect 18+)
allure --version         # optional but recommended; else use mvn allure:report
```

Missing items: install JDK 21 / Maven / Allure CLI / Node before Phase 1. Install Playwright browsers after Phase 1:

```bash
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

---

## Phase 1 — Maven project setup (`pom.xml`)

**Goal:** reproducible build with all dependencies and tooling wired up.

**1.1 Versions as properties** (`<properties>`): Java 21 source/target, `project.build.sourceEncoding=UTF-8`, and:

| Dependency | Proposed version | Purpose |
|---|---|---|
| `com.microsoft.playwright:playwright` | 1.57.0 | Browser automation |
| `org.testng:testng` | 7.11.0 | Test runner |
| `io.qameta.allure:allure-bom` | 2.32.0 | Allure version alignment |
| `io.qameta.allure:allure-testng` | (via BOM) | Allure ↔ TestNG integration |
| `org.aspectj:aspectjweaver` | 1.9.25 | Auto-record Playwright steps (runtime agent) |
| `org.slf4j:slf4j-api` | 2.0.x | Logging facade |
| `ch.qos.logback:logback-classic` | 1.5.x | Logging impl |

> Verify latest versions at build time; versions above are known-good reference points from framework research (2026).

**1.2 Dependencies:** `playwright`, `testng` (`scope=test`), `allure-testng` (`scope=test`), `slf4j-api` + `logback-classic` (`scope=test`), `aspectjweaver` (`scope=test`, `runtime`-usable agent).

**1.3 Plugins:**
- `maven-compiler-plugin` (release 21)
- `maven-surefire-plugin` 3.5.4 configured with:
  - `<suiteXmlFiles><suiteXmlFile>src/test/resources/testng.xml</suiteXmlFile></suiteXmlFiles>`
  - `<argLine>-javaagent:${settings.localRepository}/org/aspectj/aspectjweaver/${aspectj.version}/aspectjweaver-${aspectj.version}.jar</argLine>` (AspectJ agent → automatic Allure steps)
- `io.qameta.allure:allure-maven` plugin (for `mvn allure:report`)
- `exec-maven-plugin` (already used for browser install command above)

**Acceptance criteria:**
- [ ] `mvn -q clean compile` succeeds with no dependency errors
- [ ] `mvn exec:java ... install chromium` downloads the Chromium binary
- [ ] Playwright browsers land in `~/.cache/ms-playwright/`

**✅ Done 2026-08-22** — versions resolved against Maven Central (stable, no pre-releases):
playwright **1.62.0** · testng **7.12.0** · allure-bom/allure-testng **2.35.4** · aspectjweaver **1.9.25** · slf4j-api **2.0.18** (2.1.0-alpha skipped) · logback-classic **1.6.3** · assertj-core **3.27.7** (4.0.0-M1 skipped) · surefire **3.5.6** (3.6.0-M1 skipped) · compiler **3.15.0** · allure-maven **3.0.3** · exec **3.6.3**.
- `mvn -q clean compile` → exit 0. `install chromium` → no-op (expected rev. **chromium v1234** already in cache).
- **macOS note:** browsers live in `~/Library/Caches/ms-playwright/`, not `~/.cache/ms-playwright/` (Linux path). `PLAYWRIGHT_BROWSERS_PATH` overrides.
- **Early file:** `src/test/resources/testng.xml` created as an empty placeholder so surefire `suiteXmlFiles` resolves; real suite config lands in Phase 6. `org/example/Main.java` removed; `groupId=com.funtime`.

---

## Phase 2 — Project structure & configuration layer

**Goal:** predictable layout + config that switches browser/headless/URL/timeout without code changes.

**2.1 Create directories:**

```
src/main/java/com/funtime/
  config/          # ConfigReader
  pages/           # Page Objects (app layer)
  utils/           # helpers
src/test/java/com/funtime/
  base/BaseTest.java
  listeners/       # AllureListener, ScreenshotOnFailure, RetryListener
  tests/           # sample tests
src/test/resources/
  testng.xml
  config.properties
  allure.properties
  logback-test.xml
```

**2.2 `config.properties`** (defaults — env vars override):

```properties
browser=chromium
headless=true
base.url=https://funtime.com.ua   # user decision 2026-08-22: real target, no demo app
test.timeout=30000
retries=0
```

**2.3 `allure.properties`:**

```properties
allure.results.directory=target/allure-results
```

**2.4 `ConfigReader`** (`src/main/java/com/funtime/config/ConfigReader.java`):
- Loads `config.properties` from classpath into a `Properties` object (once, static).
- `get(key)`: checks env var first (names upper-snake: `BROWSER`, `HEADLESS`, `BASE_URL`, `TEST_TIMEOUT`, `RETRIES`), falls back to property.
- Typed getters: `getBrowser()`, `getHeadless()` (parse bool), `getBaseUrl()`, `getTimeout()` (int), `getRetries()` (int).
- Keeps mapping env-var → property key in one place (documented table).

**2.5 Logging:** `logback-test.xml` with a readable pattern; log level `INFO`, Playwright debug off by default.

**Acceptance criteria:**
- [ ] `ConfigReader` returns values from both env and properties; unit-verify with a quick main/test
- [ ] Env override works: `HEADLESS=false mvn test` runs headed

**✅ Done 2026-08-22** — created `com/funtime/config/ConfigReader.java` (env→property map in one place, typed getters `getBrowser()/getHeadless()/getBaseUrl()/getTimeout()/getRetries()`, diagnostic `main`), `config.properties` (`base.url=https://funtime.com.ua`), `allure.properties`, `logback-test.xml` (INFO, Playwright quiet), and the empty package dirs from 2.1.
- Verified: `mvn exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.funtime.config.ConfigReader` → defaults (chromium/true/URL/30000/0); with `HEADLESS=false BROWSER=firefox BASE_URL=… TEST_TIMEOUT=60000 RETRIES=2` → all overridden. Env resolution confirmed.
- The "runs headed" half of criterion 2 needs a real browser test → lands in Phase 3.

---

## Phase 3 — BaseTest + Playwright lifecycle

**Goal:** single place that owns browser/context/page lifecycle; isolated, parallel-safe tests.

**3.1 `BaseTest`** (`src/test/java/com/funtime/base/BaseTest.java`):

```java
public abstract class BaseTest {
    static Playwright playwright;
    static Browser browser;
    static final ThreadLocal<BrowserContext> context = new ThreadLocal<>();
    static final ThreadLocal<Page> page = new ThreadLocal<>();

    @BeforeSuite   // once: playwright = Playwright.create(); browser = newBrowser(playwright, ConfigReader.getBrowser())
    @BeforeMethod  // per test: context.set(browser.newContext()); page.set(context.get().newPage()); page.get().setDefaultTimeout(...)
    @AfterMethod   // per test: context.get().close(); context.remove(); page.remove();
    @AfterSuite    // once: browser.close(); playwright.close();

    protected Page page() { return page.get(); }          // test code uses this
    protected BrowserContext context() { return context.get(); }
}
```

**3.2 `PlaywrightFactory`** (or static method in BaseTest) — builds `BrowserType.LaunchOptions` from `ConfigReader` (`setHeadless`), returns launched browser. Kept small so a third factory (e.g. CI-specific channel) can be added later.

**3.3 Parallel safety:** every test gets a fresh `BrowserContext` + `Page` in `@BeforeMethod` — no state leaks. `ThreadLocal` guarantees isolation when `parallel="methods"` (Phase 6).

**Acceptance criteria:**
- [ ] `mvn test` launches the browser, opens a page, closes cleanly (log lines visible)
- [ ] Two `@Test` methods run with distinct contexts (no `Page` sharing)

**✅ Done 2026-08-22** — `PlaywrightFactory` (config-driven browser launch) + `BaseTest` + `BaseTestIsolationTest` (sanity, registered in testng.xml).
- Verified: `mvn clean test` green under `parallel="methods"` (2/2), log lines visible; `HEADLESS=false mvn test` → headed (closes the Phase 2 env-override criterion end-to-end).
- **⚠️ Spec correction (CLAUDE.md + this phase):** the original "one `Playwright`/`Browser` per suite + parallel methods" design is **broken** — Playwright objects are not thread-safe (microsoft/playwright-java#212), and the first parallel run failed with `Cannot find object to call pausedStateChanged`. `BaseTest` now creates a **per-thread** Playwright+Browser (lazy, `ThreadLocal`), closed in `@AfterSuite`. CLAUDE.md's lifecycle section was updated to match.

---

## Phase 4 — Page Object layer + target app

**Goal:** demonstrate the full PO convention from CLAUDE.md against the **real target** funtime.com.ua (user override 2026-08-22 — no demo app; saucedemo POs below were discarded).

**4.1 `BasePage`** (`src/main/java/com/funtime/pages/BasePage.java`):
- Holds `protected final Page page;`
- Constructor `BasePage(Page page)`; common helpers: `navigate(path)`, `getTitle()`, explicit-wait wrappers (`waitForVisible(locator)` using `assertThat`/`expect`).
- **No assertions, no `Thread.sleep`** — enforced by hooks (Phase 10).

**4.2 Page Objects built for funtime.com.ua** (real-site structure mapped via curl + Playwright probe):
- `HomePage` — `open()`, `openCategory(name)` → `CategoryPage`, `openLogin()` → `LoginPage`
- `CategoryPage` — `open(path)`, `getHeading()`, `getPlaceCardCount()`, `getPlaceNames()`, `openPlace(name)` → `PlaceDetailPage`
- `PlaceDetailPage` — `open(path)`, `getPlaceTitle()`, `getContentText()`
- `ArticlePage` — `open(path)`, `getArticleTitle()`, `getArticleText()`
- `LoginPage` — `open()`, `fillEmail()/fillPassword()/rememberMe()`, `isLoginButtonVisible()`, `submit()`/`login()` → `HomePage`

**4.3 Real-site PO learnings** (documented in code comments):
- Desktop nav category links are **icon-only** with a `title` attribute → match with `getByTitle("Природа")`, not role-name.
- A category's **place cards are `article.img-list-grid`** (name in `.grid-title`, link `.grid-inner-wrapper`). The `article.card.collection-card` block at the page bottom is the unrelated "Актуальні добірки" (collections) section.
- **The login entry exists only in the hidden mobile/sidebar menu** (`a[href='/login']` are `display:none` on desktop; `getByRole` excludes hidden elements → matches nothing). `HomePage.openLogin()` navigates to `/login` directly.
- `Locator.count()` is a **synchronous DOM query that does not auto-wait** — `getPlaceCardCount()` waits for the first card (`waitFor()`) before counting; `allInnerTexts()` auto-waits natively.

**4.4 PO rules applied (from CLAUDE.md):**
- Locators `private`, declared once, `getByRole`/`getByTitle` preferred; CSS last resort.
- Methods describe user intent and return the successor page (`openCategory(...)` returns `CategoryPage`).
- Data lives in tests or `utils/DataFactory`, not in PO classes.

**✅ Done 2026-08-22** — `BasePage` + 5 POs + `FuntimeSiteSmokeTest` (5 smoke tests: home title, category cards, place detail, article, login form reachable). `mvn clean test` **7/7 green** against the live site under `parallel="methods"`.
- Debugged two failures via a throwaway Playwright probe (compiled to `target/classes`, run with `exec:java`, then deleted):
  1. login test: hidden mobile-only "Увійти" link → direct `/login` navigation;
  2. category count: wrong selector (collections, not places) + no-wait `count()` → fixed selector + auto-wait.
- Playwright Java API notes: `AriaRole` enum (not strings), `LocatorAssertions.isVisible()` (renamed from `toBeVisible()`), `BrowserType.LaunchOptions` (not `Browser.LaunchOptions`), `LoadState` is a top-level options enum.

---

## Phase 5 — Test layer (sample tests, reporting, retries)

**Goal:** prove the whole loop — tests, Allure metadata, data-driven cases, failure artifacts.

**5.1 Sample tests** in `src/test/java/com/funtime/tests/`:
- `LoginTest`:
  - `login_validUser_redirectsToInventory` — smoke, `@Severity(CRITICAL)`, `@Feature("Login")`
  - `login_lockedOutUser_showsError` — regression, data-driven
- `CheckoutFlowTest`:
  - `buyProduct_happyPath_showsConfirmation` — full journey (login → add → checkout → confirm), smoke
- Use **Arrange / Act / Assert** (blank-line-separated), AssertJ assertions.
- Allure annotations on every test: `@Feature`, `@Story`, `@Severity`, `@Owner`; `@TmsLink` when available.
- TestNG groups: `smoke` / `regression` on each test.

**5.2 Data-driven example** (CLAUDE.md convention): `@DataProvider(name = "users")` returning `Object[][]`; each row logged via `Allure.parameter(...)`; never log passwords.

**5.3 Test data factory** `utils/DataFactory.java`: generates/returns test users, strings (e.g. `randomEmail()`), so tests don't hardcode literals.

**5.4 Listeners** (`src/test/java/com/funtime/listeners/`):
- `ScreenshotOnFailureListener` (implements `ITestListener`): on `onTestFailure`, capture `page.screenshot()` → attach to Allure via `@Attachment`.
- `AllureTestListener` (if needed beyond `allure-testng` defaults): attach browser/OS/env info.
- `RetryAnalyzer` (implements `IRetryAnalyzer`): retries `ConfigReader.getRetries()` times; respects a `@Retry` marker for explicit opt-in.

**Acceptance criteria:**
- [ ] `mvn test -Dgroups=smoke` passes for all sample tests
- [ ] A forced failure produces a screenshot attachment in the Allure report
- [ ] Allure report shows `@Feature/@Story/@Severity` grouping (check in Phase 7)

---

**✅ Done 2026-08-22** — full test layer against the real site. `mvn clean test` → **11/11** (1 skipped: valid-user login — no test account configured, skipped with a clear log reason), `mvn test -Dgroups=smoke` → **10/10 green**, forced-failure screenshot attachment verified in Allure results.
- **Sample tests** (adapted from the saucedemo drafts in this section — that app was discarded; the real site has no cart/checkout):
  - `LoginTest` — `login_invalidCredentials_isNotAuthenticated` (smoke, CRITICAL, data-driven via `DataFactory.invalidUsers()`; email logged with `Allure.parameter`, password never) + `login_validUser_redirectsToHome` (regression; reads `TEST_USER_EMAIL`/`TEST_USER_PASSWORD` → `@SkipException` with a clear reason when unconfigured).
  - `SiteJourneyTest` (replaces `CheckoutFlowTest`) — `browsePlace_fullJourney_showsContent` (smoke): Home → Category → Place Detail through fluent POs.
- **Site finding (probed via Playwright MCP):** invalid login on funtime returns a 500 "Упс! Виникла помилка." page, not a graceful validation message. The negative test asserts the resilient contract *"a rejected login stays on `/login`"* (holds for the 500 today and a proper error message later). Added `BasePage.getUrl()` for it.
- **TestNG group-filter gotcha found & fixed:** with `-Dgroups=smoke`, TestNG does **not** run `@BeforeMethod`/`@AfterMethod` lacking a `groups`/`alwaysRun` attribute → every test NPE'd on a null page. `BaseTest` config methods now use `alwaysRun = true` (root cause + fix documented in `BaseTest`).
- **DataFactory** (`src/main/java/com/funtime/utils/`) — `randomEmail()`, `randomString()`, `invalidUsers()`, configured real-account accessors. `ConfigReader` gained `test.user.email` / `test.user.password` keys (env `TEST_USER_EMAIL` / `TEST_USER_PASSWORD`).
- **Listeners** (`src/test/java/com/funtime/listeners/`): `ScreenshotOnFailureListener` (ITestListener → `@Attachment` full-page PNG on failure via new `BaseTest.currentPage()`), `AllureTestListener` (writes `environment.properties` at suite start — `Allure.addAttachment` fails with "no test is running" there, so env goes to the properties file instead), `@Retry(retries=N)` + `RetryAnalyzer` (IRetryAnalyzer, config-driven budget) + `RetryListener` (IAnnotationTransformer wiring all tests). All registered in `testng.xml`; `allure-testng` auto-registers its own listener via ServiceLoader (verified).
- `@Owner` added to the pre-existing tests for the annotation-completeness convention.
- **Deferred by plan:** Allure report rendering check (@Feature/@Story/@Severity grouping) is Phase 7.

---

## Phase 6 — `testng.xml` & running model

**Goal:** deterministic suites, parallelism, listeners registered.

```xml
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<suite name="JavaPlaywrightAIFramework" parallel="methods" thread-count="4">
  <listeners>
    <listener class-name="com.funtime.listeners.ScreenshotOnFailureListener"/>
  </listeners>
  <groups>
    <run><include name="smoke"/></run>   <!-- or regression -->
  </groups>
  <test name="Smoke">
    <classes>
      <class name="com.funtime.tests.LoginTest"/>
      <class name="com.funtime.tests.CheckoutFlowTest"/>
    </classes>
  </test>
</suite>
```

**Commands (document in README):**

```bash
mvn clean test                     # default suite (smoke + regression via profile switch)
mvn test -Dgroups=smoke            # group-level filter
mvn test -Dtest=LoginTest          # single class
BROWSER=firefox HEADLESS=false mvn test   # env-driven config
```

**Acceptance criteria:**
- [ ] `mvn clean test` runs the suite with `parallel="methods"` and all tests pass
- [ ] Listeners fire (screenshot artifact present on forced failure)
- [ ] Group filter works

**✅ Done 2026-08-23** — running model formalised and verified end-to-end; `README.md` created with the command set.
- **`testng.xml`** restructured: suite-level `parallel="methods" thread-count="4"`; split into two `<test>` blocks — `Framework sanity (hermetic)` (BaseTestIsolationTest) and `funtime.com.ua E2E` (smoke + journey + login); header comment documents the group model. **No hard-coded `<groups>` filter** — the default run exercises all groups; filtering is the surefire `-Dgroups=smoke` / `-Dgroups=regression` property (this *is* the "profile switch", and it works with `suiteXmlFiles`, verified).
- **Verified commands:** `mvn clean test` → 11 run / 0 failures / 1 skipped (valid-user login — no test account) · `-Dgroups=smoke` → 10/10 · `-Dtest=LoginTest` → 3 run / 1 skipped · `-Dtest=LoginTest#login_invalidCredentials_isNotAuthenticated` → green · `BROWSER=firefox mvn test -Dtest=BaseTestIsolationTest` → `browser=firefox` confirmed in logs, 2/2.
- **Listener verified:** a real failure screenshot (`1280×3133` PNG, full-page) was captured when `SiteJourneyTest` failed on a flaky run — the `ScreenshotOnFailureListener` path works end-to-end.
- **⚠️ Running-model gotcha documented in README:** surefire runs `-Dtest`-filtered tests **directly through the TestNG provider, bypassing `testng.xml`** → the suite listeners (screenshot / retry / env) do **not** fire for `-Dtest` runs. Use `-Dtest` for debugging; use the suite (`mvn test` / `-Dgroups`) for retries + failure artifacts. (Kept listeners in the suite XML — single registration point.)
- **Flakiness fix (CategoryPage):** `getPlaceNames()` used `allInnerTexts()`, which does **not** auto-wait for elements; on the slow live site the category grid renders after navigation, so the call intermittently returned `[]` → `IndexOutOfBoundsException: Index 0` in `SiteJourneyTest` and one smoke run failed. Fixed by `waitFor()`-ing the first `.grid-title` before reading (same pattern as `getPlaceCardCount()`). Passed twice green after the fix.

---

## Phase 7 — Allure reporting

**Goal:** report that is useful for humans and CI.

**7.1 Verify:** `allure serve target/allure-results` (or `mvn allure:report`) → Overview, Behaviors (Epic/Feature/Story), Suites, Categories, Graphs.

**7.2 Polish:**
- `allure.properties` already points results to `target/allure-results`.
- `environment.properties` → **superseded**: `AllureTestListener` (Phase 5) writes it dynamically into the results dir at suite start (browser, headless, base.url, OS, JVM) — the report Overview shows it. Extend with app build info in CI later.
- Failure categories → **Allure 3 config**, not `categories.json` (see done-note below).

**Acceptance criteria:**
- [x] Report renders with steps auto-recorded (AspectJ working), screenshots on failure, environment info
- [x] `mvn allure:report` works without a global Allure CLI

**✅ Done 2026-08-23** — report verified end-to-end; **major finding: `allure-maven` bundles Allure 3.x, which changed two mechanics from the plan.**
- **`mvn allure:report` works without a global CLI** — the plugin downloads its own generator (`Allure 3.4.1` via Node into `.allure/`, gitignored now) and emits the report to **`target/site/allure-maven-plugin`** (not `allure-maven` as the plan assumed). README + this note corrected.
- **⚠️ Allure 3.x ignores `categories.json` in the results dir** (the Allure 2 mechanism). Custom failure categories now live in **`allurerc.json`** at the repo root (`categories.rules`), read by both `mvn allure:report` and `allure serve`/`generate` from the project root. Verified with a deliberately failing probe: classified as `Product defects`, skipped test as `Skipped`. (A first attempt — categories file in test resources copied into the results dir — was removed as dead weight.)
- **Steps auto-recorded (AspectJ) proven:** added `@Step` to the Page Object intent methods (`open`, `openCategory`, `openPlace`, `login`, `submit`, …) — requires `io.qameta.allure:allure-java-commons` as a **compile** dependency (POs live in `src/main`, outside the test classpath; version from the BOM). Report shows the fluent chain as nested steps (e.g. `Open the home page → Open category Природа → Open place "Засвинське"`). Passwords are never interpolated into step names (`Log in as {email}`).
- **Content verified in the generated report:** overview summary (11 / 10 passed / 1 skipped), `allure_environment` widget (browser=chromium, headless, base.url, Java 21, macOS/aarch64), Suites tree (`JavaPlaywrightAIFramework → Framework sanity (hermetic) | funtime.com.ua E2E`), Feature/Story/Severity/Owner labels, and a real **full-page failure screenshot attachment** (409 KB PNG, rendered by the failure probe then cleaned up).
- Report serves over HTTP (equivalent of `allure serve`) — verified via a local static server.
- **Clean final state:** `mvn clean test` → 11 run / 0 failures / 1 skipped; final report status `passed`, categories empty except `Skipped` (no failures to classify). Probe test deleted, `testng.xml` reverted.

---

## Phase 8 — Playwright MCP

**Goal:** Claude Code can explore the app and capture real locators.

**8.1 Register server** project-scoped in `.mcp.json` (checked into repo):

```json
{
  "mcpServers": {
    "playwright": {
      "command": "npx",
      "args": ["@playwright/mcp@latest", "--headless"]
    }
  }
}
```

**8.2 `--allowed-origins https://funtime.com.ua`** already added (the real site is wired in since Phase 4).

**8.3 Document usage** in README: `claude mcp list` to verify; explore → capture locators → feed into `page-object-generator` skill.

**✅ Done 2026-08-22** (pulled forward with Phase 4) — `.mcp.json` registered: `npx @playwright/mcp@latest --headless --allowed-origins https://funtime.com.ua`. Server initialize handshake verified. Remaining (needs an interactive step): connect the server to a running Claude session — run `/mcp` in the session or restart with `claude --continue` so the `playwright_*` MCP tools appear in the toolset.

---

## Phase 9 — Claude skills (`.claude/skills/`)

**Goal:** AI-assisted test authoring that follows framework conventions.

Each skill = directory with `SKILL.md` (frontmatter: name, description; body: instructions referencing CLAUDE.md conventions):

| Skill | SKILL.md must instruct |
|---|---|
| `test-generator` | Parse a scenario → choose feature/groups → write TestNG test extending `BaseTest`, Arrange/Act/Assert, Allure annotations, AssertJ, name per convention; point to `LoginTest` as template |
| `page-object-generator` | From URL + captured locators/DOM → produce PO class: private locators, selector strategy order, intent-level methods returning successor pages, no assertions |
| `style-review` | Audit test/PO files against CLAUDE.md Conventions; output violation list (file:line); exit nonzero if violations block |
| `test-runner` | Run targeted tests, read Allure results, summarize failures, serve report |

**Acceptance criteria:**
- [x] `/test-generator` (etc.) are invokable via the Skill tool
- [x] Generated output complies with CLAUDE.md conventions (verified by `style-review`)

**✅ Done 2026-08-23** — all four skills created as `.claude/skills/<name>/SKILL.md` (YAML frontmatter `name` + `description`; body instructs on CLAUDE.md conventions and points at the real templates):
- **`test-generator`** — maps a natural-language scenario → one `<Feature>Test` class: extend `BaseTest`, `{action}_{expectedResult}_{condition}` naming, Arrange/Act/Assert, AssertJ with `.as(...)`, Allure annotations + TestNG group on every method, `@DataProvider` + `Allure.parameter` for data-driven rows, never-log-secrets, `SkipException`-with-reason for unconfigured preconditions. Templates: `LoginTest`, `SiteJourneyTest`, `FuntimeSiteSmokeTest`.
- **`page-object-generator`** — from URL + captured DOM (Playwright MCP / curl) → PO class extending `BasePage`: private locators declared once, selector-strategy order, intent methods returning successor pages, `@Step` on intent methods, no assertions / `Thread.sleep`. Documents the real-site quirks (icon-only nav → `getByTitle`; hidden mobile login menu → direct `/login`; `count()`/`allInnerTexts()` don't auto-wait).
- **`style-review`** — audits test/PO files against the CLAUDE.md convention checklist and outputs `path:line — rule — message`; hard (blocking) vs soft (warn) rules; verdict PASS/BLOCKED. Forward-compatible with the Phase 10 `style-check.sh` hard-block script.
- **`test-runner`** — command table for target selection (with the `-Dtest`-bypasses-suite-listeners caveat), reads `target/allure-results/*.json` to interpret failures, classifies flaky vs regression vs site-change, serves the report (`mvn allure:report` or `allure serve`).

> **Invocation note:** the Skill tool indexes `.claude/skills/` at session start — the four skills are available to **new/restarted** Claude Code sessions (`/test-generator` etc.). Compliance with conventions is enforced end-to-end: `test-generator`/`page-object-generator` output is cross-checked by `style-review` before the work is reported done.

---

## Phase 10 — Style-enforcement hooks (`.claude/settings.json`)

**Goal:** automatic, non-blocking-by-default checks on test/page files; hard blocks only for real violations.

**10.1 Hook config** (PostToolUse on Write/Edit):

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Write|Edit",
        "hooks": [
          {
            "type": "command",
            "command": "bash .claude/scripts/style-check.sh \"$CLAUDE_FILE_PATH\"",
            "timeout": 15
          }
        ]
      }
    ]
  }
}
```

**10.2 `scripts/style-check.sh`** — greps the touched file (only when path matches `src/test/**/*.java` or `src/main/**/pages/**/*.java`):
- [ ] reject `Thread.sleep(` in test files (exit 1, hard block)
- [ ] reject `Assert`/`assert` imports in `pages/` (exit 1, hard block)
- [ ] reject test classes not extending `BaseTest` (exit 1, hard block)
- [ ] warn (exit 0, log to stderr) on: missing group annotation, missing Allure annotations, naming-convention mismatch

**10.3 Escalation path:** script logs violations to stderr; CI (Phase 11) enforces the same rules as hard checks so the hook is a fast local feedback loop, not the only gate.

**Acceptance criteria:**
- [x] Editing a file with `Thread.sleep(` is blocked with a clear message
- [x] Editing a well-formed test file passes silently (exit 0)
- [x] Same rules runnable standalone: `bash .claude/scripts/style-check.sh <file>`

**✅ Done 2026-08-23** — hook + script created and verified standalone:
- **`.claude/settings.json`** — PostToolUse hook on `Write|Edit`, command `bash .claude/scripts/style-check.sh "$CLAUDE_FILE_PATH"`, timeout 15s (matches 10.1; `.claude/settings.local.json` MCP config untouched).
- **`.claude/scripts/style-check.sh`** — audits only `src/test/**/*.java` (test) and `src/main/**/pages/**/*.java` (page); everything else passes silently. **Hard blocks (exit 1):** `Thread.sleep(` anywhere; a class declaring `@Test` without `extends BaseTest`; in Page Objects, test assertion imports / inline references (`org.testng.Assert`, `org.assertj.core`) and bare `assert` statements. **Warnings (exit 0):** method naming `{action}_{expectedResult}_{condition}`, `@Test` without a TestNG `groups`, missing `@Feature/@Story/@Severity/@Owner`. `PlaywrightAssertions.assertThat(...)` (the sanctioned auto-wait in `BasePage`) is explicitly allowed.
- **Comment-awareness (fixed during verification):** every check runs on comment-stripped text so docs/javadoc never false-block — a real find: `RetryListener.java`'s javadoc mentions `@Test`, which a naive grep would block. `strip_comments` preserves line numbers.
- **Verified:** all **19** existing Java files pass (exit 0) with no output noise on out-of-scope files; planted violations (Thread.sleep in test, `org.testng.Assert` import in PO, `@Test` without `BaseTest`) each **BLOCK** with a clear message and exit 1; a javadoc mentioning `Thread.sleep` passes (docs allowed); warnings-only file passes with exit 0.
- **⚠️ Live-hook caveat:** `.claude/settings.json` hooks load at **session start** — a probe Write mid-session was not blocked. The standalone script is fully verified now; the blocking hook takes effect in a new/restarted Claude Code session (same as the Phase 8 MCP-server note). CI (Phase 11) will enforce the same rules independently of the hook.

---

## Phase 11 — GitHub Actions CI

**Goal:** tests + report on every push/PR.

**11.1 `.github/workflows/ci.yml`** — the working file (the sketch below was replaced by the real implementation). Triggers: `push` to `main` → full suite + Pages deploy · `pull_request` → `smoke` group only · `workflow_dispatch` → full suite. One run per branch at a time (`concurrency`, stale runs cancelled).

**`test` job (ubuntu-latest):**
1. checkout · 2. JDK 21 (Temurin) + Maven cache · 3. Playwright chromium cached in `~/.cache/ms-playwright` (keyed on `pom.xml`)
4. `mvn exec:java ... install --with-deps chromium`
5. **style-check.sh conventions gate** (Phase 10 escalation — hard rules fail the build in CI, not just the local hook)
6. `mvn clean test ${{ github.event_name == 'pull_request' && '-Dgroups=smoke' || '' }}`
7. `mvn allure:report` (always) · 8–9. upload `allure-results` (failure screenshots) + `allure-report` artifacts (`if: always()`)

**`pages` job** — `push` to `main` only, after `test` succeeds: downloads the report artifact, deploys to `gh-pages` via `peaceiris/actions-gh-pages@v4` (`permissions: contents: write`).

**11.2 Report publishing: decided 2026-08-23** — generate with `mvn allure:report` (bundles its own Allure generator, no global CLI, reads `allurerc.json` for categories — all verified in Phase 7) and deploy via `peaceiris/actions-gh-pages@v4`. The plan's `allure-actions/allure-report@v2` sketch was dropped (it expects an existing `gh-pages` history; our path is self-contained). Artifact-only remains a one-line fallback: delete the `pages` job and keep the artifacts.

**Acceptance criteria:**
- [ ] PR run: `smoke` group green, report artifact uploaded — **locally simulated**: `mvn clean test -Dgroups=smoke` → **10/10 in 15 s**; real GitHub run pending (see prerequisite below)
- [ ] `main` run: full suite, report published to Pages — configured; first real run pending

**✅ Done 2026-08-23** — `.github/workflows/ci.yml` created and validated locally (Ruby/YAML parse + `actionlint` exit 0). CI command set simulated on the dev machine: `mvn clean test -Dgroups=smoke` → 10/10 green; style-check passes all 17 Java files; `mvn allure:report` already proven in Phase 7.
- **✅ Prerequisite resolved 2026-08-23** — repo initialized, committed (`73aa656`), pushed to `git@github.com:RomanMakarenko/JavaPlaywrightAIFramework.git` (`main` tracks `origin/main`). The first real GitHub run is triggered by this push. Remaining to close acceptance: enable Pages in repo settings (Settings → Pages → `Deploy from a branch` → `gh-pages`) so the report is served; optionally set `TEST_USER_EMAIL`/`TEST_USER_PASSWORD` as secrets. Also tracked in the Phase 12 DoD note.

---

## Phase 12 — Final verification (Definition of Done)

Checklist before declaring the skeleton done:

- [x] `mvn clean test` green from a clean checkout (no local state)
- [x] `mvn allure:report` / `allure serve` renders a complete report
- [x] `HEADLESS=false BROWSER=firefox mvn test -Dgroups=smoke` runs headed in Firefox
- [x] `/test-generator` + `/page-object-generator` produce convention-compliant code
- [x] `style-check.sh` blocks a planted violation
- [ ] CI green on a test PR; Allure report accessible — **deferred: not a git repo yet** (see note)
- [x] README written: prerequisites, commands, config table, MCP usage, how to add a Page Object + test, CI notes
- [x] CLAUDE.md verified still accurate after build (update Status section)

**✅ Done 2026-08-23** — all locally verifiable items checked; the CI-on-GitHub item is blocked on the folder not being on GitHub (unchanged prerequisite from Phase 11).
- `mvn clean test` from a clean state → 11 run / 0 failures / 1 skipped (valid-user login — no test account), BUILD SUCCESS.
- `mvn allure:report` → complete report in `target/site/allure-maven-plugin`: summary 11/10/1, all widgets (environment, categories, statistic, timeline, tree), Feature/Story/Severity/Owner labels on every result, `allurerc.json` categories applied.
- `HEADLESS=false BROWSER=firefox mvn test -Dgroups=smoke` → 10/10 green headed in Firefox.
- Both skills exercised against the live site: `page-object-generator` produced `AboutPage`, `test-generator` produced `AboutPageTest` — both passed `style-check.sh` (exit 0), compiled, and the test ran green (1/1). The generated files were **removed afterwards** to keep the skeleton in its documented 11-test state (the verification stands; a real page PO + test can be added via the same flow).
- `style-check.sh` blocks a planted violation: a probe test with `Thread.sleep` → exit 1 + clear BLOCK message; a compliant file → exit 0. Probe deleted.
- README gained an "Adding a Page Object + test" walkthrough (the last missing DoD section); CLAUDE.md Status/config-table/CI sections updated to match the built framework.
- **Pushed 2026-08-23** (commit `73aa656` → `main` on `git@github.com:RomanMakarenko/JavaPlaywrightAIFramework.git`); the first CI run is triggered by the push. Remaining before "CI green": enable Pages (Settings → Pages → `Deploy from a branch` → `gh-pages`), optionally set `TEST_USER_EMAIL`/`TEST_USER_PASSWORD` secrets, then confirm the run (smoke or full suite) is green and the report URL serves. Re-check this DoD item afterwards.

---

## Phase 13 — ~~Future: wire in `https://funtime.com.ua/`~~ folded into Phase 4

**Superseded 2026-08-22** — the real site is the skeleton's target (user override). All 5 sub-steps below were completed during Phase 4:
1. `--allowed-origins https://funtime.com.ua` added to `.mcp.json` ✅
2. Real app explored (curl + Playwright probe) → real Page Objects created ✅
3. Skeleton `BaseTest`, config (`base.url`) reused as-is ✅
4. `@Feature/@Story` on smoke tests map the first journeys (Home, Categories, Places, Articles, Auth) ✅
5. App-specific data factory — still open: needs real test credentials (Phase 5/5.3 will provide `utils/DataFactory`).

---

## Risks & notes

- **Versions:** dependency versions are reference points; bump to latest stable at build time and lock them in `pom.xml`.
- **Target app changes:** funtime.com.ua is a live third-party site; if its markup changes, the `pages/` layer locators must be re-mapped (via curl / Playwright probe / MCP). Conventions remain identical.
- **AspectJ:** the weaver `argLine` must match the resolved `aspectjweaver` version; misconfiguration silently produces a report without step recording.
- **Parallelism flakiness:** keep `smoke` independent; CI screenshot artifacts are the debugging path.
- **Hook scope creep:** hooks must stay fast (<15s) and predictable; put complex rules in `style-review` skill, keep hard blocks minimal.
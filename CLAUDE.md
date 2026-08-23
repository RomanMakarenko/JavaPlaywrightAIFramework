# JavaPlaywrightAIFramework

E2E UI test automation framework built on **Java + TestNG + Playwright + Maven**, with **Allure** reporting and an **AI-assisted authoring workflow** (Playwright MCP + custom Claude skills + style-enforcement hooks).

The framework is a **generic skeleton**: structure and conventions are app-agnostic. The intended target application (`https://funtime.com.ua/`) will be wired in as a separate later task.

## Status

Spec-driven build **complete — Phases 0–12, Definition of Done closed** against the real target `https://funtime.com.ua/` (scaffold, config layer, `BaseTest`, Page Objects, tests, `testng.xml`, Allure, Playwright MCP, `.claude/skills/`, style hooks, GitHub Actions CI). Phase 12 verified: clean test green, Allure report renders, headed Firefox smoke green, skills produce convention-compliant code, style-check blocks violations, README complete. **CI green on GitHub and the Allure report is live** at `https://romanmakarenko.github.io/JavaPlaywrightAIFramework/` (runs #1–#5 pass: full suite, style gate, report + `gh-pages` deploy; Pages enabled via API, source `gh-pages`; full suite **11/11 green** — the valid-user login test runs via `TEST_USER_EMAIL`/`TEST_USER_PASSWORD` repo secrets, which ci.yml maps into the test-step `env:`). Tracked in [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) — that checklist is the source of truth for "where are we". Sections below describe the **target** architecture and conventions.

**Phase 15 (Jira bug pipeline) implemented and verified end-to-end** — on test failure the suite files a Jira ticket with the failure screenshot (off by default; see [TASK_SPECK.md](TASK_SPECK.md) and the Jira section below). Verified 2026-08-23 on the real site: failing probe → `SCRUM-10` filed with screenshot; a second run **reused** the open ticket instead of duplicating; full suite 11/11 green; Jira outages never change the suite outcome.

## Tech Stack

| Component | Choice |
|---|---|
| Language | Java 21 |
| Build | Maven |
| Test runner | TestNG 7.x |
| Browser automation | Playwright for Java |
| Reporting | Allure (`allure-testng` + AspectJ agent) |
| AI assistance | Playwright MCP, custom Claude skills + hooks |

## Commands

Run from the repo root.

```bash
mvn clean test                          # run all tests (default suite)
mvn test -Dtest=LoginTest               # single test class
mvn test -Dtest=LoginTest#methodName    # single test method
mvn test -Dgroups=smoke                 # TestNG group
mvn clean test -DsuitePath=testng.xml   # specific suite file
# install a browser binary (run once per machine / CI cache):
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

Allure report:

```bash
allure serve target/allure-results      # generate + open report (requires local allure CLI)
mvn allure:report                       # generate via Maven plugin (target/site/allure-maven)
```

Configuration is read from env vars, falling back to `src/test/resources/config.properties`:

| Env var | Property | Default | Purpose |
|---|---|---|---|
| `APP_ENV` | `app.env` | `prod` | target environment — `dev` / `stage` / `prod` (all resolve to the shared URL for now) |
| `BROWSER` | `browser` | `chromium` | `chromium` / `firefox` / `webkit` / `msedge` |
| `HEADLESS` | `headless` | `true` | headed vs headless mode |
| `BASE_URL` | `base.url` | — | explicit URL override — always wins over the per-environment URL |
| `TEST_TIMEOUT` | `test.timeout` | `30000` | default action timeout (ms) |
| `RETRIES` | `retries` | `0` | TestNG retry count for flaky tests |
| `TEST_USER_EMAIL` | `test.user.email` | — | valid login test account (test skips until set) |
| `TEST_USER_PASSWORD` | `test.user.password` | — | password for the account above (never logged) |
| `JIRA_BASE_URL` | `jira.base.url` | — | Jira Cloud site, e.g. `https://<site>.atlassian.net` (Jira pipeline) |
| `JIRA_EMAIL` | `jira.email` | — | Atlassian account email for Basic auth (Jira pipeline) |
| `JIRA_API_TOKEN` | `jira.api.token` | — | Jira API token — **never commit or log** (Jira pipeline) |
| `JIRA_PROJECT_KEY` | `jira.project.key` | — | Jira project to file Bug tickets against (Jira pipeline) |
| `JIRA_TICKETS_ENABLED` | `jira.tickets.enabled` | `false` | master switch for the Jira bug pipeline |
| `JIRA_ISSUE_TYPE` | `jira.issue.type` | `Bug` | issue type for auto-filed tickets |
| `JIRA_LABELS` | `jira.labels` | `auto-test` | comma-separated labels on auto-filed tickets |

The base URL for the active environment is read from `env.<app.env>.url` in `config.properties`
(`env.prod.url`, `env.stage.url`, `env.dev.url` — all point at the one known site today). An
explicit `BASE_URL` env var always overrides it.

> **Local overrides (never committed):** copy keys you want to change per-machine into
> `src/test/resources/config.local.properties` (gitignored) — e.g. real `test.user.email`/
> `test.user.password` so the login happy-path test runs locally. It is merged over
> `config.properties`; env vars still win. CI injects the same values via repo secrets
> mapped in `.github/workflows/ci.yml` (`env:`), so no secrets are ever committed.

## Architecture

```
src/
  main/java/<group>/...
    pages/           # Page Object classes — locators + user-intent actions
      <AppName>Page.java
    config/          # config loaders (env / properties)
    utils/           # helpers (data factories, string/time utils)
  test/java/<group>/...
    base/BaseTest.java       # TestNG lifecycle: playwright + browser + context + page
    tests/                   # test classes, one per feature/flow
    listeners/               # Allure/TestNG listeners (screenshot on failure, Jira bug tickets)
    jira/                    # Jira Cloud REST client (Phase 15): JiraConfig, JiraClient, JiraTicket
  test/resources/
    testng.xml               # suite config: parallelism, groups, listeners
    config.properties        # default config (env vars override)
    allure.properties        # allure.results.directory=target/allure-results
.claude/
  skills/                    # custom Claude skills for AI test authoring
  settings.json              # hooks enforcing framework conventions
.github/workflows/ci.yml     # GitHub Actions CI
```

### Layer rules

- **Page Objects** (`pages/`) — the only layer allowed to touch Playwright locators/actions. **Never** contain assertions; methods return the successor `Page` object (fluent chaining) or page state.
- **Tests** (`tests/`) — orchestrate at business-flow level: build page, act, assert. No raw locators, no `Playwright.create()`.
- **BaseTest** — owns the Playwright lifecycle; tests inherit it and only use the injected `page`.

### BaseTest lifecycle

```java
@BeforeSuite   → log suite config                                       (once per suite)
@BeforeMethod  → ensure per-thread Playwright + Browser (lazy), then context + page   (fresh, isolated per test)
@AfterMethod   → context.close()                                        (isolate every test)
@AfterSuite    → close every Playwright created by the suite            (once per suite)
```

`Playwright` + `Browser` are **per-thread** `ThreadLocal`-backed (created lazily on each worker thread's first test, reused across that thread's tests); `Page`/`BrowserContext` are per-test `ThreadLocal`-backed. That is what makes `parallel="methods"` safe.

> **Playwright objects are not thread-safe.** Sharing one `Playwright`/`Browser` across parallel threads fails randomly with `Cannot find object to call ...` exceptions (microsoft/playwright-java#212). Every test thread therefore owns its own browser chain.

## Conventions

Enforced by repo hooks and the `style-review` skill. Follow them when writing tests.

### Test structure
- Extend `BaseTest`; one test class per feature/flow.
- Name methods `{method}_{expectedResult}_{condition}`, e.g. `login_validUser_redirectsToAccount`.
- Use **Arrange / Act / Assert** — separate the three with blank lines; assert only at the end.
- Add Allure annotations: `@Feature`, `@Story`, `@Severity`, `@Owner`; `@TmsLink`/`@AllureId` when available.
- Assign a TestNG group (`smoke`, `regression`). `smoke` must stay fast (~<1 min total) and stable.

### Page Objects
- One class per page or reusable fragment; constructor takes `Page` (or parent `Locator` for fragments).
- Locators are `private`, declared once at the top, chosen by the selector strategy below.
- Methods describe *user intent*, not mechanics: `login(email, password)` over `fillEmail().clickSubmit()`.
- Return the successor page for chaining: `login(...)` returns `AccountPage`.

### Timing
- **Never** `Thread.sleep()`. Use Playwright auto-waiting, `expect(locator).toBeVisible()`, or `page.waitForSelector(...)`.
- Keep the default action timeout in config; bump per-locator only when a page is legitimately slow.

### Selector strategy (priority order)
1. `data-testid` / `data-test` attributes (add to the app when missing)
2. Role-based: `getByRole("button", ...).setName("Login")`
3. `getByLabel` / `getByPlaceholder` / `getByText`
4. CSS/XPath — **last resort** (brittle to UI changes)

### Data-driven tests
- Use TestNG `@DataProvider`; name providers after the flow, keep data readable.
- Log row values with `Allure.parameter(...)` so failures are identifiable; never log passwords/secrets.

### Parallelism
- Default `parallel="methods"` in `testng.xml`. Every test must be independent (isolated context per test) — no shared state between tests.

## Jira bug pipeline (Phase 15)

On a failing test the suite can automatically file a **Bug ticket in Jira Cloud with the failure
screenshot**. Off by default — enabled only when `JIRA_TICKETS_ENABLED=true` **and** all of
`JIRA_BASE_URL` / `JIRA_EMAIL` / `JIRA_API_TOKEN` / `JIRA_PROJECT_KEY` are set
(`ConfigReader.isJiraConfigured()`).

How it works:
- `JiraBugListener` (registered in `testng.xml`) captures the failure screenshot in
  `onTestFailure` (the page is still alive) and files the ticket on suite finish.
- Tickets are **deduplicated** by a stable summary `[AUTO-TEST] <Class>#<method>` — an open Jira
  issue is reused (with a "failed again" comment + fresh screenshot), never re-created.
- A test that passes on **retry** never files a ticket (`RetryAnalyzer` drops the pending record).
- Jira failures are logged and swallowed — a Jira outage never changes the suite outcome.

Credential hygiene (non-negotiable):
- **Never** commit the token, log it, or let it into the Allure report — `environment.properties`
  is published to public GitHub Pages and must not carry Jira credentials.
- Locally: real values go in `src/test/resources/config.local.properties` (gitignored).
- CI: repo secrets `JIRA_BASE_URL` / `JIRA_EMAIL` / `JIRA_API_TOKEN` / `JIRA_PROJECT_KEY`, mapped
  in the `.github/workflows/ci.yml` test-step `env:`. `JIRA_TICKETS_ENABLED` is `true` only on
  `main` pushes (or a manual `workflow_dispatch` with `create-jira-tickets`), never on PR runs.
- Rotate the token if it was ever exposed.

Verify locally: set the four values + `jira.tickets.enabled=true` in `config.local.properties`,
run a deliberately failing test, confirm a Bug ticket with a screenshot lands in Jira, then revert
the probe test. Same flow works on CI via a manual `workflow_dispatch`.

> **Gotchas (found during end-to-end verification 2026-08-23):** the configured project's issue-type
> scheme must actually contain `jira.issue.type` — SCRUM is team-managed and its default scheme
> (Epic/Subtask/Task/Story) has **no `Bug` type**. The REST API cannot add issue types to a
> team-managed scheme (JSWCLOUD-23545), so **Bug** is added via the UI: Project settings → Issue
> types → Add issue type. Both the local `jira.issue.type` and the CI `JIRA_ISSUE_TYPE` secret are
> set to `Bug`. And if your `JIRA_API_TOKEN` ends with `=` (base64), don't truncate it when
> copy-pasting — the trailing `=…` is part of the token.

## Playwright MCP

Used to explore the target app and capture real locators for AI test authoring.

```bash
npx @playwright/mcp@latest                          # start the MCP server
claude mcp add playwright -s user -- npx @playwright/mcp@latest   # register for Claude Code
# or declare it project-scoped in .mcp.json
```

Suggested flags: `--headless` for CI-like exploration; `--allowed-origins https://funtime.com.ua` once the real site is wired in.

**Authoring workflow:** explore app → capture locators → create a Page Object (`page-object-generator`) → write the test (`test-generator`) → run + verify (`test-runner`) → review style (`style-review`).

## Claude Skills & Hooks

### Skills (`.claude/skills/`)
| Skill | Purpose |
|---|---|
| `test-generator` | Writes a TestNG test from a natural-language scenario, following these conventions |
| `page-object-generator` | Creates a Page Object from a URL + captured locators/DOM |
| `style-review` | Audits test/PO code against the conventions above and reports violations |
| `test-runner` | Runs tests, serves the Allure report, interprets failures |

### Hooks (`.claude/settings.json`)
Run on `Write`/`Edit` of `src/test/**/*.java` and `src/main/**/pages/**/*.java`; non-zero exit blocks the change:
- no `Thread.sleep(` in tests
- test classes extend `BaseTest`
- Page Objects contain no assertions / `Assert` imports
- test methods follow the naming convention, carry Allure annotations, and belong to a group
- check result (pass/violations) logged to stderr

## CI (GitHub Actions)

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) — CI green on GitHub (see Actions runs):
- Triggers: `push` to `main` (full suite + Allure report → GitHub Pages), `pull_request` (fast `smoke` group only), `workflow_dispatch` (full suite, with **environment** + **browser** inputs, defaults `prod` / `chromium`). One run per branch at a time (`concurrency`, stale runs cancelled).
- JDK 21 (Temurin) + Maven cache; Playwright browser binaries cached in `~/.cache/ms-playwright` (per-browser cache key), installed for the selected browser with `mvn exec:java ... install --with-deps <browser>`. `msedge` installs system-wide via apt and is not cached.
- **Style gate:** `style-check.sh` hard rules run over every `src/**/*.java` — a convention violation fails the build (the Phase 10 hook's CI escalation).
- `mvn clean test` headless; smoke group on PRs, full suite on `main`. The selected browser + environment are injected as `BROWSER` / `APP_ENV` env vars (defaults `chromium` / `prod`).
- Always (`if: always()`): `mvn allure:report` (bundles its own Allure generator; `allurerc.json` drives failure categories), then upload `allure-results` (failure screenshots) + the generated report as artifacts. On `main`, the `pages` job deploys the report to the `gh-pages` branch via `peaceiris/actions-gh-pages`.

## References

- Playwright Java — Page Object Model: https://playwright.dev/java/docs/pom
- Allure + Playwright Java: https://allurereport.org/docs/playwright-java/
- Playwright MCP: https://github.com/microsoft/playwright-mcp
- Reference frameworks: [MCP---Playright-JAVA](https://github.com/itsawaz/MCP---Playright-JAVA) · [javamavenplaywright](https://github.com/ahmadazerichandrabhuana/javamavenplaywright) · [Playwrightautomation](https://github.com/rakesh-attri/Playwrightautomation) · [Playwright-Java-PageObjectModel](https://explore.market.dev/ecosystems/maven/projects/playwright-java-pageobjectmodel)
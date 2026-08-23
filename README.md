# JavaPlaywrightAIFramework

E2E UI test automation framework built on **Java 21 + TestNG + Playwright + Maven**, with **Allure** reporting and an AI-assisted authoring workflow (Playwright MCP + Claude skills + style-enforcement hooks). The skeleton is generic; it is validated against the real target app **https://funtime.com.ua/** (only the `pages/` layer is site-specific).

> Spec & conventions: [`CLAUDE.md`](CLAUDE.md) — build progress: [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md)

## Prerequisites

| Tool | Version |
|---|---|
| JDK | 21 |
| Maven | 3.8+ |
| Node.js | 18+ (Playwright MCP only) |
| Allure CLI | optional — `mvn allure:report` needs no global CLI |

Install the browser binaries once per machine (or warm the CI cache):

```bash
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

## Commands

Run from the repo root. `mvn test` (without `-Dtest`) runs the suite defined in `src/test/resources/testng.xml` — `parallel="methods"`, all groups (`smoke` + `regression`), all listeners.

| Command | What it runs |
|---|---|
| `mvn clean test` | Default suite — all groups, parallel methods, full listener stack |
| `mvn test -Dgroups=smoke` | Fast PR feedback — only the `smoke` group |
| `mvn test -Dgroups=regression` | Only the `regression` group |
| `mvn test -Dtest=LoginTest` | Single test class |
| `mvn test -Dtest=LoginTest#methodName` | Single test method |
| `BROWSER=firefox HEADLESS=false mvn test` | Environment-driven config override (see table below) |

Allure report:

```bash
allure serve target/allure-results      # generate + open report (requires global Allure CLI)
mvn allure:report                       # no global CLI needed; report in target/site/allure-maven-plugin
```

> **Allure 3 config:** failure categories live in [`allurerc.json`](allurerc.json) at the repo root
> (Allure 3.x reads this central config, not a `categories.json` in the results dir). The Maven
> plugin bundles its own Allure generator (downloaded to `.allure/` on first run).

> **`-Dtest` caveat:** surefire runs `-Dtest`-filtered tests directly through the TestNG provider, **bypassing `testng.xml`**. The suite listeners registered there (`ScreenshotOnFailureListener`, `RetryListener`, `AllureTestListener`) therefore do **not** fire for `-Dtest` runs. Use `-Dtest` for quick debugging; use the suite (`mvn test` / `-Dgroups`) whenever retries and failure screenshots matter (CI, sign-off runs).

## Configuration

Configuration is read from env vars, falling back to `src/test/resources/config.properties` (resolved in `com.funtime.config.ConfigReader`):

| Env var | Property | Default | Purpose |
|---|---|---|---|
| `BROWSER` | `browser` | `chromium` | `chromium` / `firefox` / `webkit` / `msedge` |
| `HEADLESS` | `headless` | `true` | headed vs headless mode |
| `BASE_URL` | `base.url` | `https://funtime.com.ua` | target app URL |
| `TEST_TIMEOUT` | `test.timeout` | `30000` | default action timeout (ms) |
| `RETRIES` | `retries` | `0` | TestNG retry count for flaky tests |
| `TEST_USER_EMAIL` | `test.user.email` | — | valid login test account (skipped until set) |
| `TEST_USER_PASSWORD` | `test.user.password` | — | password for the account above (never logged) |

> **Local overrides (never committed):** copy the keys you want to change per-machine into
> `src/test/resources/config.local.properties` (gitignored) — e.g. real `test.user.email` /
> `test.user.password` so the login happy-path test runs on your machine. It is merged over
> `config.properties`; env vars still take precedence. CI injects the same values via repo
> secrets mapped in `.github/workflows/ci.yml` (`env:`), so no secrets are ever committed.

## Project layout

```
src/main/java/com/funtime/
  pages/           # Page Objects — locators + user-intent actions, no assertions
  config/          # ConfigReader (env → properties)
  utils/           # DataFactory (test data, random generators)
src/test/java/com/funtime/
  base/            # BaseTest (Playwright lifecycle, per-thread browser) + PlaywrightFactory
  tests/           # test classes, one per feature/flow
  listeners/       # Allure/TestNG listeners (screenshot on failure, env info, retry)
src/test/resources/
  testng.xml       # suite: parallelism, groups model, listeners
  config.properties · allure.properties · logback-test.xml
```

## AI-assisted authoring

- **Playwright MCP** is registered project-scoped in [`.mcp.json`](.mcp.json) (`npx @playwright/mcp@latest --headless --allowed-origins https://funtime.com.ua`) for exploring the app and capturing real locators.
- **Claude skills** live in [`.claude/skills/`](.claude/skills/) — `test-generator` (write a TestNG test from a scenario), `page-object-generator` (build a Page Object from a URL + captured DOM), `style-review` (audit test/PO files against CLAUDE.md conventions), `test-runner` (run tests and interpret Allure results). Invoke with `/skill-name` or ask for one in a prompt.
- **Style-enforcement hook** — [`.claude/settings.json`](.claude/settings.json) runs [`style-check.sh`](.claude/scripts/style-check.sh) on every Write/Edit of test & Page Object files: hard-blocks `Thread.sleep`, missing `extends BaseTest`, and assertion imports in POs; warns on naming / groups / Allure annotations. Runnable standalone: `bash .claude/scripts/style-check.sh <file>`. (Hooks load at session start — restart Claude Code to activate.)

## Adding a Page Object + test

The AI-assisted path for a new page — the same conventions apply if you write by hand:

1. **Explore the page** — open it in the Playwright MCP browser and capture stable locators (roles, `title` attributes, `data-testid`, semantic classes). See [`.mcp.json`](.mcp.json).
2. **Generate the Page Object** — ask for `page-object-generator` with the URL + captured DOM. It produces a `pages/XPage.java` extending `BasePage`: private locators declared once, intent methods returning the successor page, `@Step` annotations, no assertions.
3. **Generate the test** — ask for `test-generator` with a natural-language scenario. It produces a `tests/XTest.java` extending `BaseTest`: `{action}_{expectedResult}_{condition}` naming, Arrange / Act / Assert, AssertJ assertions, Allure annotations, a `smoke`/`regression` group.
4. **Verify** — run `bash .claude/scripts/style-check.sh <file>` on both (the PostToolUse hook also runs it automatically once active), then `mvn test -Dtest=XTest` for fast feedback.
5. **Register** — add the new test class to `src/test/resources/testng.xml` so it runs with the default suite (`mvn clean test`).

## CI (GitHub Actions)

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs the framework on every push/PR:

| Trigger | What runs |
|---|---|
| `pull_request` | `mvn clean test -Dgroups=smoke` — fast (~15 s) PR feedback |
| `push` to `main` | full suite (all groups) + Allure report published to **GitHub Pages** |
| `workflow_dispatch` | full suite, manual trigger |

The `test` job installs chromium (+ OS deps), caches Maven deps and Playwright browsers, then runs the **`style-check.sh` conventions gate** (hard rules fail the build), tests, and always uploads `allure-results` (failure screenshots) + the generated Allure report as artifacts. On `main`, the `pages` job deploys the report to the `gh-pages` branch (`mvn allure:report` — no global Allure CLI; `allurerc.json` drives the failure categories).

> **Getting it running:** the folder is not a git repository yet. `git init && git add -A && git commit -m "…"`, create a GitHub repo, then `git remote add origin <url> && git push -u origin main`. Enable **Pages** in repo settings (`Deploy from a branch` → `gh-pages`). Set `TEST_USER_EMAIL` / `TEST_USER_PASSWORD` as repo secrets to activate the valid-user login regression test.

## Conventions

Full conventions live in [`CLAUDE.md`](CLAUDE.md) (test structure & naming, Page Object rules, timing, selector strategy, data-driven tests, parallelism). Highlights:

- Tests extend `BaseTest`, follow Arrange / Act / Assert, carry Allure annotations + a TestNG group.
- Page Objects never assert and never use `Thread.sleep`; methods describe user intent and return the successor page.
- Selectors: `data-testid` → role → label/placeholder/text → CSS/XPath (last resort).
---
name: test-runner
description: Run tests in this framework, read the Allure results, summarize failures, and serve the report. Use when asked to run tests, check whether the suite is green, investigate a failure, or show the report.
---

# Test Runner

Run the right tests, interpret the results, and surface failures — with the Allure report as evidence.

## When to use
- "Run the tests", "is it green?", "why did this test fail?", "show me the report".

## Run the target
Choose the command for what you need (from README, run from the repo root):

| Target | Command |
|---|---|
| All groups, parallel, full listener stack | `mvn clean test` |
| Fast PR feedback (smoke only) | `mvn test -Dgroups=smoke` |
| Deeper coverage (regression only) | `mvn test -Dgroups=regression` |
| Single test class | `mvn test -Dtest=ClassName` |
| Single test method | `mvn test -Dtest=ClassName#methodName` |
| Env-driven config | `BROWSER=firefox HEADLESS=false mvn test` |

> **`-Dtest` caveat:** `-Dtest` bypasses `testng.xml`, so the suite listeners (screenshot-on-failure, retry, env info) do **not** fire. Use `-Dtest` for quick debugging; use `mvn test` / `-Dgroups` whenever retries and failure screenshots matter.

## Interpret results
1. Read the Maven/TestNG output: totals (`Tests run: X, Failures: Y, Errors: Z, Skipped: W`), then each failure's class/method and stack trace.
2. Read the Allure results in `target/allure-results/` (`result-*.json` — per-test status, `statusMessage`, `statusTrace`, attached screenshots) to get the failure detail and artifacts.
3. Classify each failure:
   - **Flaky** — passed on retry, or timing/network (retry budget is `RETRIES`). Suggest re-running before treating as a defect.
   - **Regression** — a real assertion failure; give the failing assertion and expected vs actual.
   - **Site change** — locator/timeout failures on the live app; the `pages/` layer likely needs a re-map. Re-capture the DOM (Playwright MCP / curl) and run `page-object-generator`.

## Report & serve
- Summarize in a table: test → status → cause → evidence (e.g. screenshot path).
- Serve the Allure report:
  - No global CLI: `mvn allure:report` → open `target/site/allure-maven-plugin/index.html`.
  - Global CLI present: `allure serve target/allure-results`.
- Point at the relevant report sections: Behaviors (Feature/Story grouping) and Categories (failure classification from `allurerc.json`).
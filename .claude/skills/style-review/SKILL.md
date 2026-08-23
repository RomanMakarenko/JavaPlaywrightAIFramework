---
name: style-review
description: Audit test and Page Object files against the framework conventions in CLAUDE.md and report violations as file:line. Use after generating or editing test/PO files, and before handing a change to CI.
---

# Style Review

Audit one or more test / Page Object files against CLAUDE.md conventions and report every violation as `path:line — rule — message`. Empty list = compliant.

## When to use
- After `test-generator` / `page-object-generator` have written files.
- After editing existing tests or Page Objects.
- Before handing a change to CI.

## Procedure
1. Read `CLAUDE.md` → `Conventions`.
2. For each target file:
   a. Run the hard-check script first — `bash .claude/scripts/style-check.sh <file>` — it blocks the hard rules below (comment-aware: javadoc/docs do not trigger false violations).
   b. Review the file against the full checklist below, including the soft rules the script does not cover.

## Checklist

### Test layer (`src/test/**/*.java`)
- [ ] Extends `BaseTest` (imports `com.funtime.base.BaseTest`).
- [ ] No raw Playwright: no `Playwright.create()`, no `page.locator(...)` in tests — Page Objects only.
- [ ] No `Thread.sleep(`.
- [ ] Method name matches `{action}_{expectedResult}_{condition}`.
- [ ] Arrange / Act / Assert phases, separated by blank lines; assertions only at the end.
- [ ] AssertJ (`assertThat(...)`), preferably with `.as("…")` descriptions.
- [ ] Allure annotations present: `@Feature`, `@Story`, `@Severity`, `@Owner` (`@TmsLink`/`@AllureId` when available).
- [ ] TestNG `groups` attribute (`smoke` | `regression`) on every `@Test`.
- [ ] Data-driven: `@DataProvider` named after the flow; row identifiers logged via `Allure.parameter`; passwords never logged.
- [ ] Unconfigured preconditions → `SkipException` with a clear reason (never a silent pass).

### Page Object layer (`src/main/**/pages/**/*.java`)
- [ ] Extends `BasePage`; constructor takes `Page` (or parent `Locator` for fragments).
- [ ] No assertions / no `Assert` imports (no testng/AssertJ assertion API).
- [ ] No `Thread.sleep(`.
- [ ] Locators `private`, declared once at the top; selector strategy order respected (`data-testid` → role → label/placeholder/text → CSS last resort).
- [ ] Methods describe user intent and return the successor page for fluent chaining.
- [ ] Intent methods carry `@Step("…")` for Allure step recording.

## Report
- Output a **violation list** as `path:line — <rule> — <message>` (empty list = compliant).
- **Hard violations (blocking — the change must be fixed before proceeding):**
  - `Thread.sleep` in tests or POs.
  - Assertions / `Assert` imports in Page Objects.
  - Test classes not extending `BaseTest`.
  - Raw locators / `Playwright.create()` in tests.
- **Soft violations (warn — fix where reasonable):**
  - Missing/incorrect group, missing Allure annotations, naming mismatch, missing `.as()` description, data rows not logged, `SkipException` misuse.
- End with a clear verdict: **PASS** (no hard violations) or **BLOCKED** (list the blockers). Do not report work as done while any file is BLOCKED.
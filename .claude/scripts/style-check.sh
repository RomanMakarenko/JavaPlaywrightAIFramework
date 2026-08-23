#!/usr/bin/env bash
#
# style-check.sh — style-enforcement gate for this framework (docs/IMPLEMENTATION_PLAN.md, Phase 10).
#
# Wired into the .claude/settings.json PostToolUse hook (runs on every Write/Edit) and also
# usable standalone:  bash .claude/scripts/style-check.sh <file>
#
# Scope: only files under src/test/**/*.java and src/main/**/pages/**/*.java are audited.
# Everything else is out of scope and passes through untouched (no output, exit 0).
#
# Exit codes:  0 = pass / warning-only / out of scope
#              1 = hard violation — blocks the change (see BLOCK lines)
#
# Hard rules (CLAUDE.md conventions — these BLOCK the change):
#   * Thread.sleep() is banned everywhere (use Playwright auto-waiting).
#   * A class declaring @Test must extend BaseTest.
#   * Page Objects must not use test assertion libraries (org.testng.Assert, AssertJ).
#     PlaywrightAssertions.assertThat(...) is the sanctioned auto-wait and is allowed.
# Soft rules (warnings, exit 0): method naming, TestNG group, Allure annotations.
#
# Every check runs on comment-stripped text, so words like "@Test" or "assert" in javadoc/docs
# never trigger a false violation (e.g. RetryListener's javadoc mentions "@Test").

set -u

FILE="${1:-}"
if [ -z "$FILE" ]; then
  echo "style-check: no file given — skipping" >&2
  exit 0
fi

# ---- Scope ------------------------------------------------------------------
KIND=""
case "$FILE" in
  *src/test/*.java)         KIND="test" ;;
  *src/main/*/pages/*.java) KIND="page" ;;
esac
[ -n "$KIND" ] || exit 0          # out of scope — stay silent

if [ ! -f "$FILE" ]; then
  echo "style-check: file not found — skipping: $FILE" >&2
  exit 0
fi

warn()  { echo "style-check (warn)  $FILE: $*" >&2; }
block() { echo "style-check (BLOCK) $FILE: $*" >&2; }

BLOCKED=0

# strip_comments: removes // and /* */ Java comments while preserving line numbers
# (one output line per input line), so grep -n line numbers still match the file.
strip_comments() {
  awk '
    BEGIN { in_block = 0 }
    {
      line = $0; out = ""; i = 1; n = length(line)
      while (i <= n) {
        c = substr(line, i, 1)
        if (in_block) {
          if (c == "*" && substr(line, i + 1, 1) == "/") { in_block = 0; i += 2 }
          else i++
        } else if (c == "/" && substr(line, i + 1, 1) == "*") { in_block = 1; i += 2 }
        else if (c == "/" && substr(line, i + 1, 1) == "/") { i = n + 1 }
        else { out = out c; i++ }
      }
      print out
    }
  '
}

STRIPPED=$(strip_comments < "$FILE")

# ---- Hard: Thread.sleep is banned (global CLAUDE.md timing rule) -------------
sleep_lines=$(printf '%s\n' "$STRIPPED" | grep -n 'Thread\.sleep' || true)
if [ -n "$sleep_lines" ]; then
  while IFS= read -r l; do
    block "$l — Thread.sleep() is banned; use Playwright auto-waiting"
  done <<< "$sleep_lines"
  BLOCKED=1
fi

if [ "$KIND" = "page" ]; then
  # ---- Hard: no test assertion libraries in Page Objects --------------------
  # org.testng.Assert / AssertJ block; PlaywrightAssertions (auto-wait) is allowed.
  bad_imports=$(printf '%s\n' "$STRIPPED" | grep -nE 'import[[:space:]]+(static[[:space:]]+)?(org\.testng\.Assert|org\.assertj\.core)' || true)
  if [ -n "$bad_imports" ]; then
    while IFS= read -r l; do
      block "$l — test assertion import is not allowed in Page Objects (use Playwright auto-wait)"
    done <<< "$bad_imports"
    BLOCKED=1
  fi

  bad_inline=$(printf '%s\n' "$STRIPPED" | grep -nE 'org\.testng\.Assert\.|org\.assertj\.core' || true)
  if [ -n "$bad_inline" ]; then
    while IFS= read -r l; do
      block "$l — inline test assertion reference is not allowed in Page Objects"
    done <<< "$bad_inline"
    BLOCKED=1
  fi

  bad_assert=$(printf '%s\n' "$STRIPPED" | grep -nE '(^|[^[:alnum:]_])assert[[:space:]]' || true)
  if [ -n "$bad_assert" ]; then
    while IFS= read -r l; do
      block "$l — bare assert statement is not allowed in Page Objects"
    done <<< "$bad_assert"
    BLOCKED=1
  fi
fi

if [ "$KIND" = "test" ]; then
  # ---- Hard: a class declaring @Test must extend BaseTest -------------------
  if printf '%s\n' "$STRIPPED" | grep -q '@Test' \
      && ! printf '%s\n' "$STRIPPED" | grep -q 'extends BaseTest'; then
    block "test class declares @Test but does not extend BaseTest"
    BLOCKED=1
  fi

  # ---- Soft: method naming {action}_{expectedResult}_{condition} -------------
  printf '%s\n' "$STRIPPED" | awk -v f="$FILE" '
    /@Test/ { prev_test = 1; next }
    prev_test && /void[[:space:]]+[A-Za-z_][A-Za-z0-9_]*[[:space:]]*\(/ {
      m = $0
      sub(/^.*void[[:space:]]+/, "", m)
      sub(/[[:space:]]*\(.*$/, "", m)
      if (m !~ /_/) {
        print f ":" NR ": style-check (warn) — test method \"" m "\" should be named {action}_{expectedResult}_{condition}"
      }
      prev_test = 0
    }
  ' >&2

  # ---- Soft: every @Test must declare a TestNG group ------------------------
  while IFS=: read -r n line; do
    if ! printf '%s' "$line" | grep -q 'groups'; then
      warn "$n — @Test without a TestNG group (add groups = \"smoke\" | \"regression\")"
    fi
  done < <(printf '%s\n' "$STRIPPED" | grep -n '@Test')

  # ---- Soft: Allure annotations present (file-level heuristic) --------------
  if printf '%s\n' "$STRIPPED" | grep -q '@Test'; then
    for ann in Feature Story Severity Owner; do
      if ! grep -q "@${ann}" "$FILE"; then
        warn "no @${ann} annotation on test methods (CLAUDE.md: @Feature/@Story/@Severity/@Owner on every test)"
      fi
    done
  fi
fi

# ---- Verdict ----------------------------------------------------------------
if [ "$BLOCKED" -eq 1 ]; then
  echo "style-check: BLOCKED — $FILE" >&2
  exit 1
fi
echo "style-check: ok — $FILE" >&2
exit 0
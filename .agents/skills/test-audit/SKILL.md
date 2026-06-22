---
name: test-audit
description: Audit tests you just wrote (or were asked to review) to prove they give real signal — that test data is representative, assertions pin down expected values rather than just "not null", and the suite would actually fail if the code broke. Use immediately after writing or modifying tests, or when asked to audit/review test quality. Focuses on quality, security, and logical correctness over test count.
---

# Test Audit

A passing test proves nothing if it would still pass when the code is broken. The job of this audit is to **prove the tests provide real signal** — that they would catch a regression, exercise realistic data, and assert the behaviour we actually expect.

This is about quality, not quantity. A handful of sharp tests beats a wall of weak ones. The output of an audit is often "these 3 assertions are worthless", not "add 20 tests".

## The bias problem — the audit MUST be run by a fresh subagent

The agent that wrote the tests is the worst-placed agent to audit them. It knows what it *intended*, so it reads its own assertions charitably and skips the cases it already decided were fine. An honest audit needs a reviewer with **no memory of why the tests were written the way they were**.

**Therefore: you (the orchestrating agent) MUST NOT perform the audit yourself.** Your only job is to:

1. Determine the **scope** — which test files and which source files under test are in play (from the recent diff, the user's request, or `git status`/`git diff --name-only`).
2. **Spawn a fresh, context-free subagent** to conduct the entire audit, using the Agent tool with `subagent_type: "general-purpose"`.
3. **Relay** the subagent's report back to the user, then act on its findings (strengthen/fix/delete) if asked.

Do **not** summarise your intentions to the subagent, do **not** tell it which tests you think are good, and do **not** pass it any reasoning about the tests. Give it only the file paths and the methodology below. Its independence is the whole point.

### Spawning the auditor

Call the Agent tool once, with a self-contained prompt. Fill in the scope; paste the methodology and report format verbatim so the subagent has everything it needs without any of your context:

```
subagent_type: general-purpose
description: "Audit test signal"
prompt: |
  You are an independent test auditor. You have NO prior context and must form
  your own judgement only from reading the files. Do not assume any test is
  correct because it exists.

  Scope — audit these test files against the source they cover:
    Tests:  <paths to test files>
    Source: <paths to the code under test>

  Read the tests and the code under test TOGETHER — you cannot judge an assertion
  without knowing what the code is supposed to return. Verify any fixture/seed
  data the assertions rely on actually exists with the asserted values; do not
  assume it. For the most important behaviours, mentally mutate the source (flip a
  boolean, change a returned field, off-by-one a boundary, swap < for <=) and
  confirm a test would die — if nothing catches the mutation, that is a finding.

  <PASTE the "What to check" and "Severity classification" and "Report format"
   sections from the test-audit SKILL here verbatim.>

  Report ONLY in the severity-classified format. Do not edit any files — you are a
  read-only auditor. Be honest: if a test has a blind spot you cannot close, say so.
```

The subagent is **read-only** — it reports, it does not fix. After it reports, you decide what to change.

## The core question the auditor applies

For every test: **"If I introduced a realistic bug in the code under test, would this test fail?"** If the answer is no, or "only if the bug made it crash", the test is weak.

## What to check

Go through the tests against these five lenses.

### 1. Assertions pin down expected values

The most common failure. The test runs the code but doesn't check the result is *correct* — only that it's *present*.

- ❌ `assertNotNull(user)` — passes for any object, even a wrong one.
- ❌ `assertTrue(result.size() > 0)` — passes for 1 item or 10,000 wrong items.
- ❌ `.body("id", notNullValue())` — passes for any id.
- ✅ `assertEquals("Jane", user.getFName())` — pins the actual expected value.
- ✅ `.body("email", equalTo("jane.smith@example.com")).body("active", equalTo(true))` — checks the real data.

Rule: every assertion should encode *what we expect*, not merely *that something happened*. If the test asserts a status code, also assert the meaningful parts of the body. If it asserts a collection is non-empty, assert its size and at least one element's contents.

### 2. Test data is representative

- Reject placeholder data (`"foo"`, `"test"`, `123`, `"a@b.c"`) when it sidesteps real validation or formatting logic.
- Dates, IDs, emails, money, and enums should be realistic and varied — not all the same value.
- Seed/fixture data the assertions depend on (e.g. the user at `/id/1` returning `John Doe`) must actually exist and be checked against its real seeded values. Verify the fixture, don't assume it.
- If the code branches on input (active/inactive, role, boundary), the data must hit those branches.

### 3. Edge cases and boundaries that matter

- Not-found / empty / null returns (e.g. the `204 No Content` cases).
- Boundary values (0, 1, max length, empty string, first/last page).
- Invalid input → correct error response and status code (not a 500).
- State that should change: assert the *after* state, not just the call's return.

Skip combinatorial explosions. One good boundary test beats ten near-duplicates.

### 4. Security and authorization

This codebase uses `@TestSecurity` with roles and `@SecurityAttribute`. Auth is where weak tests are dangerous, and the heart of the product is **aggregate, anonymised sentiment without leaking PII** — guard that:

- Endpoints that require a role must have a test proving an **unauthorized/wrong-role** caller is rejected (401/403), not only the happy path.
- A user must not be able to read or mutate **another user's** data — assert this where ownership applies.
- No endpoint or aggregation may expose PII (name, email, exact DOB) alongside a vote or characteristic. A test must prove the boundary holds.
- Don't let a test's security context paper over a real authorization gap (e.g. acting as one user but reading another's record and calling it correct).
- Never hard-code real secrets/tokens; check none leaked into fixtures.

### 5. The test would actually fail (mutation thinking)

- Flip a boolean, change a returned field, off-by-one a boundary, swap a `<` for `<=`.
- If no assertion would catch that mutation, the test has a blind spot.
- Watch for tautological tests that assert on the *input* they just sent rather than the *output* the code produced.
- Watch for over-mocking: if the mock returns the exact value being asserted and the real logic is stubbed away, the test only verifies the mock.

## Severity classification

The auditor classifies every finding into exactly one of three severities:

- **HIGH** — a security flaw or business logic that is **not followed**. The test passes while the code violates a security/authorization requirement (e.g. wrong-role caller accepted, one user reads another's data, PII leaked alongside a vote) **or** the code under test does not implement the specified business logic and the test fails to catch it. These are the dangerous ones: a green suite hiding a real defect.
- **MEDIUM** — business logic that is **not properly checked**. The behaviour may well be correct, but the test does not actually verify it: weak/absent assertions on important behaviour, missing edge/boundary cases, a fixture assumed rather than verified, over-mocking that stubs away the logic. A realistic bug could slip through.
- **LOW** — a **pointless test**: it doesn't test anything. Tautological (asserts the input it sent), `assertNotNull`-only, asserts a constant, or redundant duplicates that add no signal. Noise that inflates the count without protecting anything.

## Report format

The auditor (subagent) ends with exactly this, and nothing it edited:

```
Test Audit — <scope>
Verdict: <Strong / Needs work / Weak>

HIGH (security flaw / business logic not followed):
- <file:line> — <what's wrong, what bug it lets through, how to fix>
  (or "none")

MEDIUM (business logic not properly checked):
- <file:line> — <which behaviour isn't actually verified, what to assert instead>
  (or "none")

LOW (pointless test — doesn't test anything):
- <file:line> — <why it has no signal; strengthen or delete>
  (or "none")

Proven by reasoning (mutation thinking):
- <e.g. flipping `active` default would NOT be caught by testGetInactiveUser — blind spot>

Remaining gaps (honest list, or "none"):
- <...>
```

## After the subagent reports

1. Relay the severity-classified report to the user.
2. If asked to act on it, fix in place: close HIGH findings first (they hide real defects), then strengthen MEDIUM, then delete/strengthen LOW.
3. **Lint at the end** once you've changed anything:
   - Frontend (Expo/React Native): `cd frontend/mobile/your-say-news && npm run lint`.
   - Backend (Quarkus): no dedicated Java linter; `./gradlew :<service>:test` compiles and surfaces compile-level problems. Treat compiler warnings on touched files as lint findings.
4. Re-run the suite to confirm it is green after your changes. The audit is not done while the linter or the suite reports errors on the files you changed.

Be honest. If a HIGH finding can't be closed without a code change the user hasn't approved, surface it rather than declaring victory.

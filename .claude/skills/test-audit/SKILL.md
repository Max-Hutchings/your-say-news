---
name: test-audit
description: Audit tests you just wrote (or were asked to review) to prove they give real signal — that test data is representative, assertions pin down expected values rather than just "not null", and the suite would actually fail if the code broke. Use immediately after writing or modifying tests, or when asked to audit/review test quality. Focuses on quality, security, and logical correctness over test count.
---

# Test Audit

A passing test proves nothing if it would still pass when the code is broken. The job of this audit is to **prove the tests provide real signal** — that they would catch a regression, exercise realistic data, and assert the behaviour we actually expect.

This is about quality, not quantity. Do **not** add tests just to raise a number. A handful of sharp tests beats a wall of weak ones. The output of an audit is often "delete/strengthen these 3 assertions", not "add 20 tests".

## When to run

Run this audit:
- Immediately after writing or editing any test.
- When the user asks to audit, review, or "make sure the tests actually work".
- Before declaring a feature "done and tested".

## The core question

For every test, ask: **"If I introduced a realistic bug in the code under test, would this test fail?"**

If the answer is no, or "only if the bug made it crash", the test is weak. Fix it or remove it.

## What to check

Go through the tests against these five lenses. For each finding, decide: strengthen, fix, or delete.

### 1. Assertions pin down expected values

The most common failure. The test runs the code but doesn't actually check the result is *correct* — only that it's *present*.

- ❌ `assertNotNull(user)` — passes for any object, even a wrong one.
- ❌ `assertTrue(result.size() > 0)` — passes for 1 item or 10,000 wrong items.
- ❌ `.body("id", notNullValue())` — passes for any id.
- ✅ `assertEquals("Jane", user.getFName())` — pins the actual expected value.
- ✅ `.body("email", equalTo("jane.smith@example.com")).body("active", equalTo(true))` — checks the real data.

Rule: every assertion should encode *what we expect*, not merely *that something happened*. If the test asserts a status code, also assert the meaningful parts of the body. If it asserts a collection is non-empty, assert its size and at least one element's contents.

### 2. Test data is representative

Inputs should look like real production data and exercise the logic that matters.

- Reject placeholder data (`"foo"`, `"test"`, `123`, `"a@b.c"`) when it sidesteps real validation or formatting logic.
- Dates, IDs, emails, money, and enums should be realistic and varied — not all the same value.
- Seed/fixture data the assertions depend on (e.g. the user at `/id/1` returning `John Doe`) must actually exist and be checked against its real seeded values. Verify the fixture, don't assume it.
- If the code branches on input (active/inactive, role, boundary), the data must hit those branches.

### 3. Edge cases and boundaries that matter

Cover the cases where bugs actually live — not every permutation, just the meaningful ones:
- Not-found / empty / null returns (e.g. the `204 No Content` cases here).
- Boundary values (0, 1, max length, empty string, first/last page).
- Invalid input → correct error response and status code (not a 500).
- State that should change: assert the *after* state, not just the call's return.

Skip combinatorial explosions. One good boundary test beats ten near-duplicates.

### 4. Security and authorization

This codebase uses `@TestSecurity` with roles and `@SecurityAttribute`. Auth is a place where weak tests are dangerous:
- Endpoints that require a role should have a test proving an **unauthorized/wrong-role** caller is rejected (401/403), not only the happy path.
- A user must not be able to read or mutate **another user's** data — assert this where ownership applies.
- Don't let a test's security context paper over a real authorization gap (e.g. acting as one user but reading another's record and calling it correct).
- Never hard-code real secrets/tokens; check none leaked into fixtures.

### 5. The test would actually fail (mutation thinking)

For the highest-value tests, mentally mutate the code under test and confirm the test dies:
- Flip a boolean, change a returned field, off-by-one a boundary, swap a `<` for `<=`.
- If no assertion would catch that mutation, the test has a blind spot — tighten it.
- Watch for tests that assert on the *input* they just sent rather than the *output* the code produced (tautological tests).
- Watch for over-mocking: if the mock returns the exact value being asserted and the real logic is stubbed away, the test only verifies the mock.

## How to perform the audit

1. **Read the tests and the code under test together.** You cannot judge an assertion without knowing what the code is supposed to return.
2. **Run the suite** and confirm it passes before auditing (a red suite is a different problem):
   - Backend (Quarkus, per service): `./gradlew :user-service:test` (or `:post-service:test`).
   - Run a single test: `./gradlew :user-service:test --tests YourSayUserControllerTest`.
3. **Verify fixture/seed data** the assertions rely on actually exists with the values asserted.
4. **Apply the five lenses** above to each test; note specific findings with file:line.
5. **Prove signal where it counts:** for the most important behaviours, make a temporary local mutation to the source, re-run, and confirm a test fails. Revert the mutation. (Do this for the critical paths, not every test.)
6. **Fix in place:** strengthen weak assertions, replace placeholder data, add the missing edge/security case, delete tautological or redundant tests.
7. **Lint at the end.** Once the tests are fixed, run the linter over the touched code and resolve any new issues before reporting:
   - Frontend (Expo/React Native): `cd frontend/mobile/your-say-news && npm run lint`.
   - Backend (Quarkus): no dedicated Java linter is configured; `./gradlew :<service>:test` compiles the test/source and surfaces compile-level problems. Treat compiler warnings on the touched files as lint findings.
   - The audit is not done while the linter reports errors on the files you changed.

## Report format

End with a concise summary:

```
Test Audit — <scope>
Verdict: <Strong / Needs work / Weak>

Strengthened:
- <file:line> — was `assertNotNull`, now asserts expected email/name/active

Added (only where signal was missing):
- <file:line> — wrong-role caller now asserted 403

Removed:
- <file:line> — tautological, asserted the input it sent

Proven by mutation:
- Flipped `active` default in UserService → testGetInactiveUser failed as expected ✅

Lint: <pass / fixed N issues> (`npm run lint` clean on touched files)

Remaining gaps (if any):
- <honest list, or "none">
```

Be honest. If a test still has a blind spot you couldn't close, say so rather than declaring victory.

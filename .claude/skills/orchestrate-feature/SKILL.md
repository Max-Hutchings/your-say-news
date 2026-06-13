---
name: orchestrate-feature
description: Orchestrate a feature end-to-end with the main agent owning planning and integration while two independent subagents build it in parallel — one writing tests (then self-auditing), one writing production code. Use when the user asks to "orchestrate", build a feature with separate test/code agents, or run the plan → parallel-build → wire-up → verify flow. The main agent decides and integrates; it never just bends tests to make code pass.
---

# Feature Orchestration

Build a feature in four phases with a strict division of labour. The **main agent** (you) owns
thinking, deciding and integrating. Two **independent subagents** do the parallel grunt work of
writing tests and writing production code against a contract you defined. You then wire them
together honouring the business logic — never weakening tests just to get green.

```
Phase 1  PLAN      you  ── design, present options w/ pros & cons, get the user's decisions
Phase 2  CONTRACT  you  ── freeze the shared contract both agents build against
Phase 3  BUILD     ⇉    ── two independent subagents in parallel:
                          A) test agent  → writes tests, then runs the test-audit skill
                          B) code agent  → writes production code
Phase 4  WIRE+VERIFY you ── integrate to honour the business logic, run tests, confirm vs the plan
```

The whole point: tests and production code are written **independently and blind to each other**,
both targeting the same frozen contract. That independence is what gives the tests real signal —
the code agent can't overfit to the tests it never saw, and the test agent can't soften tests to
match an implementation it never saw. **You** are the only one who sees both, and you reconcile
them by reasoning about correctness, not by chasing green.

---

## Phase 1 — Plan (you, with the user)

You produce the plan. You ask the user for input. **You** make the decisions — but every decision
is offered to the user first with its trade-offs.

1. Read the relevant code and `CLAUDE.md` so the plan fits the existing architecture (DDD package
   layout, feature-folder layout, Liquibase migrations vs seeding, auth via Keycloak, no PII
   alongside votes).
2. Draft the approach. For **every** meaningful choice, **outline the thing and give pros and
   cons** — don't silently pick. Examples of choices that need this treatment:
   - data model / DTO shape, new tables or migrations,
   - endpoint shape and verbs, error/status semantics,
   - where the logic lives (which domain, which service),
   - frontend feature boundaries and component split,
   - what's in scope for this pass vs deferred.
3. Surface decisions to the user with `AskUserQuestion` (or plain prose for open-ended ones).
   Lead with your **recommended** option and say why. Keep each option's pros/cons concrete.
4. Fold the user's answers back in. If you're in plan mode, present the final plan with
   `ExitPlanMode` for approval before any building starts.

Do not move to Phase 3 until the user has signed off on the plan. The plan is the source of truth
you check the finished work against in Phase 4.

## Phase 2 — Freeze the contract (you)

Before spawning anything, write down the **contract** both agents will build against. This is the
single most important artifact — if it's vague, the two independently-built halves won't meet.

The contract must pin, concretely:
- **Signatures / interfaces:** exact class + method signatures, the public service interface,
  controller routes and verbs.
- **Data shapes:** DTO/entity fields and types, request/response JSON, validation rules.
- **Behaviour:** what each operation does, including edge cases — not-found, empty, invalid input,
  auth/role requirements, boundary values. State the **expected** outputs, not just "returns a
  user".
- **Fixtures:** which seed/migration data the behaviour relies on, with the real values
  (e.g. user at `/id/1` is `John Doe`).
- **Architecture placement:** which domain package / feature folder each piece lives in, per
  `CLAUDE.md`.

Write the contract to `docs/plans/<feature>-contract.md` (alongside the plan) so both subagents
read the same fixed reference and you can diff the finished work against it.

## Phase 3 — Build (two independent subagents, in parallel)

Spawn **both** subagents with the `Agent` tool in the **same response** so they run concurrently
and genuinely independently. Give each the contract path and the architecture rules, but **do not
give the code agent the tests, and do not give the test agent the implementation.**

### Subagent A — Test agent

Prompt it to:
- Write tests **only against the contract** in `docs/plans/<feature>-contract.md` — backend with
  Quarkus + Testcontainers (real Postgres/Keycloak/S3, `@TestSecurity` for role-gated endpoints);
  frontend with React Testing Library (test what the user sees and does).
- Make assertions **pin expected values** (the contract's stated outputs), cover the edge/
  auth/boundary cases the contract lists, and use representative data — not `assertNotNull`,
  not placeholder `"foo"`.
- It must **not** read or write production source; if the implementation doesn't exist yet, that's
  fine — tests are written to the contract and are expected to fail until wired up.
- **When finished, run the `test-audit` skill** over the tests it just wrote and act on the
  findings (strengthen weak assertions, add missing edge/security cases, delete tautological
  tests) before reporting back. Its final message must summarise the audit verdict.

### Subagent B — Code agent

Prompt it to:
- Implement **only against the contract** — the signatures, data shapes and behaviour exactly as
  written, placed in the correct domain package / feature folder per `CLAUDE.md` (public face at
  the top level, internals in `model/` / `service/`; thin route files on the frontend).
- Write the migrations/seeding in the right split (schema in `migrations/`, seed data in
  `seeding/`, never mixed) if the contract needs new tables.
- It must **not** read the tests and must **not** write tests. It builds to the contract, full stop.
- Report what it implemented and any place it had to deviate from the contract (and why).

Run them in the background if they're long; you'll be notified on completion. Wait for **both**
before Phase 4.

## Phase 4 — Wire up & verify (you)

Now you're the only one holding both halves. Integrate them — but **integration means honouring
the business logic, not making tests pass by any means.**

1. **Read both outputs and the contract together.** Where tests and code disagree, decide which is
   *correct against the contract and the business rules* — then fix that side:
   - Code wrong (doesn't meet the contract's behaviour) → fix the **code**.
   - Test wrong (asserts something the contract never promised) → fix the **test**, and note it.
   - **Never** weaken a correct test or delete a real assertion just to get green. If you find
     yourself loosening an assertion, stop and confirm it contradicts the contract first.
   - If the contract itself was wrong or incomplete, surface it to the user — don't quietly paper
     over it in code.
2. **Run the tests** and iterate until green *for the right reasons*:
   - Backend: `./gradlew :<service>:test` (e.g. `:user-service:test`), single test via
     `--tests <ClassName>`.
   - Frontend: `cd frontend/mobile/your-say-news && bun run test` (and `bun run lint`).
3. **Prove signal didn't evaporate during wiring.** For the critical paths, mutate the source
   locally (flip a boolean, change a returned field, off-by-one a boundary), re-run, confirm a
   test dies, then revert. If nothing fails, the wiring quietly defanged the tests — fix it.
4. **Confirm against the plan.** Walk the Phase 1 plan and Phase 2 contract point by point and
   verify the built feature actually does each thing — scope, behaviour, edge cases, architecture
   placement, no PII leak. Report any deviation honestly rather than declaring victory.

## Report format

End with:

```
Orchestration — <feature>
Plan: approved <date> · Contract: docs/plans/<feature>-contract.md

Test agent:    <n tests, areas covered> · audit verdict: <Strong/Needs work/Weak>
Code agent:    <what was implemented, any deviations>

Wire-up decisions:
- <file:line> — code fixed to meet contract behaviour X (not test weakened)
- <file:line> — test corrected: asserted Y the contract never promised

Tests: <pass> (`./gradlew :user-service:test`, `bun run test`) · Lint: <pass>
Signal proven: mutated <X> → <test> failed ✅ (reverted)

Plan conformance:
- <each plan/contract point> — ✅ / ⚠️ <deviation + why>

Remaining gaps: <honest list, or "none">
```

## Guardrails

- The two build agents are **independent and blind to each other** — that's the source of the
  tests' value. Don't hand either one the other's work.
- **You decide and integrate.** Subagents write; they don't make product or architecture calls and
  they don't reconcile conflicts — that's your job in Phase 4.
- **Green is not the goal; correct is.** A passing suite that you reached by softening tests is a
  failure of this skill.
- Keep `app/` thin and the backend domain boundaries intact (`CLAUDE.md`) — review the subagents'
  placement, don't assume it.
- Only spawn these subagents under this orchestration flow; this skill *is* the user asking for it.

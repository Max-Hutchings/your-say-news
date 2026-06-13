---
name: commit-message
description: Write a git commit message in this project's house format — a "Feature Name: what it does" header followed by Add/Update/Remove bullets, where each bullet says what changed in one sentence and what was used to achieve it in a second. Use whenever creating a commit, drafting a commit message, or when the user asks to commit changes.
---

# Commit Message Format

Every commit message in this project follows one fixed shape:

```
<Feature Name: what it does>

* Add: <what was added>. <What was used to achieve it.>

* Update: <what changed>. <What was used to achieve it.>

* Remove: <what was removed>. <Why / what replaced it.>
```

## Rules

- **Header:** `Feature Name: what it does`. The feature name is a short title; after the colon, one short phrase describing what the feature does. Title-case the feature name. No trailing period.
- **Bullets** use `*` and are separated by a blank line (as shown above).
- Use only the categories that apply — `Add`, `Update`, `Remove`. Omit any with no changes. Don't invent other categories.
- **Each bullet is exactly two sentences:**
  1. What it did (the change).
  2. What was used to achieve it (the library, pattern, file, framework, or approach).
- Keep each sentence tight and concrete. Name real things — `RestAssured`, `@TestSecurity`, `expo-auth-session`, the actual class/component/file — not vague phrases like "various changes".
- Group multiple related changes of the same kind under repeated bullets of that category rather than cramming them into one.


## How to write it

1. Run `git diff --staged` (or `git diff` / `git status` if nothing is staged yet) to see the actual changes — never guess.
2. Identify the single feature or theme the commit represents; that becomes the header.
3. Sort each change into Add / Update / Remove.
4. For every bullet, write sentence one (what) and sentence two (how/what-with), grounding the second sentence in the real tools and files from the diff.
5. Drop any category with no changes.

## Example

```
Authentication: lets users sign in via Keycloak

* Add: User login and token-refresh flow on the mobile app. Built with expo-auth-session against the Keycloak OIDC endpoints.

* Add: Server-side role checks on the user endpoints. Enforced with Quarkus @RolesAllowed and validated by @TestSecurity in YourSayUserControllerTest.

* Update: User service to read identity from the JWT instead of the request body. Used Quarkus SecurityIdentity and SecurityAttribute claims.

* Remove: The temporary hard-coded dev user. Replaced by the real Keycloak-issued identity.

```

## Committing

When asked to commit, write the message to a temp file or use a multi-line `-m`/`-F` so the blank-line formatting survives, e.g.:

```sh
git commit -F - <<'EOF'
Feature Name: what it does

* Add: ...

* Update: ...


EOF
```

Only commit when the user has asked you to. If on the default branch, branch first per project conventions.

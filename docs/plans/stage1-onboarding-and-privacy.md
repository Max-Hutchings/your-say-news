# Stage 1 — Sign-up, onboarding & the privacy promise

Implementation plan for MVP1 Stage 1 (see `docs/plans/mvp1-roadmap.md`).

## Decisions locked

- **Characteristic coverage:** add **all 7** roadmap axes (political persuasion expanded to a
  7-point band, religion + religiosity, urban/rural, marital status, sexual orientation,
  citizenship, employment sector). Each lands in backend enum + `options.ts` +
  `CharacteristicAnswers` + onboarding UI + seed.
- **Consent storage:** flag on the user record — `consentedAt` + `privacyPolicyVersion` on
  `YourSayUser` (user-service), set via a dedicated consent endpoint. Consent lives with the
  identity/PII it governs, never with the characteristic data.

## Why a backend redesign

`usercharacteristic` was a scaffold: the entity had **no Liquibase migration** (table never
existed) and its DTO didn't match the frontend `CharacteristicAnswers` payload. Stage 1 is the
stage that builds the domain out, so we design the table, DTO and contract fresh — aligned across
backend and frontend — rather than bolting onto the divergent scaffold.

The PII boundary is preserved exactly: the request body carries **only** characteristic answers;
`userId` is derived server-side from the authenticated subject (token email → `YourSayUser`),
never trusted from the body.

## Workstreams

### Backend (`user-service`)

1. **Enums** — new: `AgeRange`, `Gender`, `EducationLevel`, `OccupationStatus`, `UrbanRural`,
   `MaritalStatus`, `SexualOrientation`, `Religion`, `Religiosity`, `EmploymentSector`. Updated:
   `PoliticalPersuasion` (7-point band + PNTS), `IncomeRange` (align values with `options.ts`).
   Citizenship reuses the `CountryOfBirth` country list.
2. **Entity / DTO / service** — redesign `UserCharacteristic` to the captured set + new axes;
   multi-select `race` via an element-collection table; `userId` set from the token.
3. **Migration** — `0002-create-user-characteristic.yaml` (+ race collection table).
4. **Controller** — `POST /user-characteristics` (save current user), `GET /user-characteristics/me`;
   `userId` resolved from the authenticated email via the `user` domain's public interface.
5. **Consent** — `consentedAt` + `privacyPolicyVersion` columns on `your_say_user`
   (`0002-add-consent-to-user.yaml`); `POST /your-say-user/consent`.
6. **Seed** — characteristic rows for the seeded users (self-describing names land in Workstream T;
   here we just give the existing seed users full profiles so onboarding/aggregation has data).
7. **Tests** — unit (enum validation / mapping) + `@QuarkusTest` integration (save + read-back,
   PII boundary: body `userId` ignored, consent recorded). Run `test-audit` after.

### Frontend (`features/user-characteristics`, `features/auth`)

1. **`options.ts`** — convert age/gender/education/occupation to enum-backed `Option[]`; add the
   7 new axis option sets; fix income values.
2. **Contract** — flatten `CharacteristicAnswers` (country/city/region) and add the new axes;
   update `answers.ts` (`OnboardingForm`, `isRequiredComplete`, `buildCharacteristicAnswers`) and
   its tests.
3. **Onboarding** — extend `OnboardingScreen` with the new fields/steps; political persuasion gets
   a prominent step; all sensitive axes are optional + "Prefer not to say".
4. **Privacy & consent** — a consent screen shown after first sign-in and before onboarding,
   explaining what we collect and that only aggregated/anonymised characteristics are ever shown;
   records consent via the backend.
5. **Sign-up** — a "Create account" affordance on the sign-in screen (Keycloak self-registration
   is already enabled in the realm); first-time users are routed consent → onboarding.

**Demoable:** a new account signs up, reads the privacy promise, consents, and completes a full
characteristic profile.

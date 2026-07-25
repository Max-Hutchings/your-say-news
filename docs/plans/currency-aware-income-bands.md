# Currency-aware income bands

## Goal

Make annual personal and household income choices reasonable for the user's economic market and
selected currency. Selecting INR must produce materially different thresholds from selecting GBP;
changing the selection must change the offered bands, not only their prefix.

Keep collecting ranges rather than exact income. Preserve the separation between identity and
characteristics, and keep public reporting aggregate-only.

## Current state and defect

- `GET /user-characteristics/options` exposes one ordered `incomeRange` catalogue backed by the
  `IncomeRange` Java enum.
- The mobile app owns 20 currency choices. Currency is held only in the local onboarding draft and
  is omitted by `buildCharacteristicAnswers`.
- `OnboardingScreen` applies the selected three-letter currency code to every number in the shared
  labels. `INR 20k–30k` and `GBP 20k–30k` therefore submit the same enum value.
- `user_characteristic` stores only `personal_income_range` and `household_income_range`; it cannot
  recover the currency or the economic meaning of an existing answer.
- Vote snapshots copy those raw enum names. Treating them as comparable across countries would
  produce misleading sentiment breakdowns.
- The existing columns are `VARCHAR(32)` and the enum retains deprecated values for historical
  readability. Seed data and some older vote fixtures also contain historical names.

## Product and modelling decision

Use immutable, backend-owned **income profiles**, identified by economic market and currency rather
than currency alone. A profile contains separate personal-income and household-income band sets.

For example, `IN-INR-...` and `GB-GBP-...` are distinct profiles. A EUR profile should normally be
country-specific; a reviewed regional fallback may be used only where suitable country data is not
available. Currency is the amount unit, while the market identifies the income distribution and
price context used to choose sensible cut points.

Each offered band has:

- a profile-specific stable band ID;
- lower-inclusive and upper-exclusive annual amounts in the profile currency (open at either end
  where appropriate);
- a localized display label;
- an ordinal, currency-neutral reporting tier such as `TIER_1` through `TIER_7`;
- a `PERSONAL` or `HOUSEHOLD` measure type.

Use seven target tiers based approximately on distribution cut points P10, P25, P50, P75, P90 and
P95. The profile research step may merge adjacent tiers when repeated cut points or sparse source
data make a separate band misleading, but must not invent false precision. Round thresholds to
locally understandable units while retaining monotonic ordering. Use local formatting conventions,
including lakh/crore-friendly INR labels where appropriate.

The tier is an ordinal position within the relevant local distribution, not a claim that two users
have the same nominal income or living standard. Personal and household tiers remain separate axes.

## How profiles are sourced and versioned

### Source hierarchy

Build each profile from a documented evidence pack in this order:

1. Recent national-statistics or tax-administration distributions that match the question: annual,
   gross/before-tax personal income or total gross household income.
2. Harmonized primary datasets from the ILO or OECD when the national source is unavailable or
   unsuitable. Record construct differences explicitly; OECD equivalised disposable household
   income, for example, must not silently be represented as gross household income.
3. A purchasing-power fallback derived from the closest reviewed profile using World Bank
   International Comparison Program household-consumption PPPs, brought to the target period with
   an official CPI series and checked against local wages, tax thresholds and minimum wages.

Do not generate profiles from a currency symbol, live spot FX, a single average wage, or tax
brackets alone. Market exchange rates do not adjust for local price levels, and a mean does not
describe a distribution. Use FX only for documented display conversion when a reviewed product
requirement calls for it, never to choose the underlying market tier.

For the first INR profile, research the latest suitable India distribution (including MoSPI/PLFS
earnings evidence for personal income). If no defensible gross household-income distribution exists,
publish an explicitly lower-confidence PPP-calibrated household profile or defer that measure for
INR; do not copy or live-convert GBP thresholds.

Useful methodology anchors:

- World Bank International Comparison Program methodology:
  https://www.worldbank.org/en/programs/icp/methodology
- World Bank ICP data and reference-year notes:
  https://www.worldbank.org/en/programs/icp/data
- ILOSTAT earnings concepts and gross-remuneration definition:
  https://ilostat.ilo.org/methods/concepts-and-definitions/description-wages-and-working-time-statistics/
- OECD Income Distribution Database and metadata:
  https://www.oecd.org/en/data/datasets/income-and-wealth-distribution-database.html

### Versioning rules

Store reviewed profiles as version-controlled backend resources, not values fetched from a live
third party during onboarding. Each immutable profile records:

- `profileId`, semantic `profileVersion` and catalogue version;
- market code, ISO 4217 currency code and measure definitions;
- source publisher, dataset/table, source URL, income/reference year and retrieval date;
- derivation method, original cut points, rounding decisions and confidence level;
- effective date, superseding profile (when present), and reviewer.

Never edit thresholds in place. Publish a new profile version, retain the old definition for reading
historical answers, and decide explicitly whether users should refresh their answer. Review profiles
at least annually and when source methodology changes materially. Validate the resource at service
startup: unique IDs, supported currency, ordered non-overlapping amounts, complete tier mappings and
valid source metadata.

## API and catalogue changes

1. Keep `GET /user-characteristics/options` as the questionnaire catalogue. Add an
   `incomeCatalog` section with its own version and supported market/currency/profile summaries.
   Currency is no longer presentation-only, superseding that narrow part of ADR-018.
2. Add
   `GET /user-characteristics/income-options?marketCode={market}&currencyCode={currency}` to return
   one current profile with ordered personal and household bands. Use immutable cache keys/ETags
   based on profile version.
3. Introduce a versioned nested answer in save/read DTOs:

   ```json
   {
     "income": {
       "answerVersion": 2,
       "catalogVersion": "2026.1",
       "profileId": "IN-INR-GROSS-2024-v1",
       "currencyCode": "INR",
       "personalBandId": "PERSONAL_P25_P50",
       "householdBandId": "HOUSEHOLD_P50_P75"
     }
   }
   ```

4. Resolve the market from a stable residence-country/market code, not the current display label.
   Add that stable code to the location answer if the existing country selection cannot provide it.
   Return an explicit unsupported-pair response rather than silently falling back.
5. On save, validate that the profile exists, is accepted for new answers, matches the submitted
   market/currency, and owns both band IDs. Derive reporting tiers server-side; never trust tier,
   amount boundaries or labels submitted by the client.
6. Keep the old flat range fields temporarily for old clients and legacy reads. New responses should
   identify legacy answers explicitly instead of presenting them as version-2 profiles.

## Persistence and domain changes

Add nullable columns first:

- `income_answer_version`;
- `income_profile_id` and `income_profile_version`;
- `income_currency_code` and `income_market_code`;
- `personal_income_band_id` and `household_income_band_id`;
- `personal_income_tier` and `household_income_tier`.

Use lengths based on the published ID contract rather than the current `VARCHAR(32)`. Add database
checks for the ISO currency shape and answer-version consistency where practical. Profile/band
membership remains domain validation because definitions are versioned resources.

Keep `personal_income_range` and `household_income_range` readable during the compatibility window.
Move new domain code away from a single numeric `IncomeRange` enum toward an income-answer value
object and a profile catalogue service. New writes persist profile/band provenance and the
server-derived tier; they do not write a nominal legacy enum as if it were globally meaningful.

Vote snapshots should contain both:

- canonical personal/household tiers plus an answer-version marker for reviewed cross-market
  analysis; and
- immutable profile/band IDs plus ISO currency for currency-qualified direct-result labels.

Do not snapshot exact income. Direct post results resolve the retained band through the immutable
profile catalogue and display its local range and currency. Canonical tiers remain an internal
analysis representation and are not shown as salary-range labels. Historical vote snapshots remain
point-in-time records.

## Frontend behaviour

- Make the backend catalogue the source of supported currencies and profiles.
- Default the market from the stable residence selection, then let the user choose among supported
  currency/profile combinations. Explain when a currency has more than one market profile.
- Fetch and render personal and household bands from the selected profile; do not transform labels
  with a regular expression.
- Use accessible localized number formatting and wording such as “Under”, “to”, and “or more”.
- When market, currency or profile changes, clear both selected band IDs before showing the new
  bands. Never retain a band ID from the previous profile.
- Persist the complete profile identity and band IDs in the local onboarding draft. Invalidate only
  its finance selections if the stored profile is no longer accepted.
- Keep the finance reassurance: ranges are used only in aggregate and never shown on a profile.

## Migration and backward compatibility

Existing rows cannot be accurately converted: the application never stored their currency, and
country is not proof of the unit used. Do not infer or backfill a currency from residence and do not
map old nominal bands into local tiers.

1. Mark existing answers as `answerVersion=1` / `LEGACY_NOMINAL_V1`, preserve both old range strings
   and leave version-2 profile/tier fields null.
2. Treat old vote-snapshot income values as legacy buckets. Do not combine them with version-2 tiers
   in one result. Prefer excluding legacy income from cross-market income breakdowns with an
   internal reason metric; if displayed for a single legacy cohort, label it explicitly.
3. Ask existing users to refresh the finance step before their next answer is used in new
   cross-market income analysis. Future votes take the refreshed tier; previous vote snapshots stay
   unchanged.
4. During a bounded transition, accept the old flat fields only when the nested version-2 answer is
   absent and record the result as legacy. Instrument usage by app version.
5. After supported mobile versions submit version 2, reject legacy-shaped answers from current
   clients while retaining legacy read support. Removing old columns/enums is a separate later
   migration after retention and reporting dependencies are audited.
6. Update seed profiles and vote snapshots with explicit, realistic profile versions. Keep dedicated
   legacy fixtures to prove compatibility.

## Privacy and reporting

- Continue asking only for ranges; never collect exact income.
- Treat market/profile and income tier as sensitive quasi-identifiers. They stay in the private
  characteristic record and must not be joined to identity in published output.
- Use canonical tiers for internal cross-market aggregation. Use immutable currency-qualified band
  IDs for direct post results so users see the original local range.
- Apply the existing minimum-bucket suppression to every income result. Consider coarsening adjacent
  upper tiers before release where a profile has too few users; never reveal a rare top band merely
  because the global tier is common.
- Do not expose source-year/profile metadata as a user-level breakdown axis.
- Log catalogue/profile IDs and validation outcomes, not user answers.

## Test plan

### Backend unit tests

- Profile resource validation rejects duplicate IDs, gaps/overlaps, reversed boundaries, invalid
  currencies, missing sources and invalid tier mappings.
- Resolver chooses the correct market/currency version, including country-specific EUR and explicit
  unsupported pairs.
- Submission validation rejects a band from another profile, retired versions for new answers,
  client-supplied tiers and mixed personal/household IDs.
- Tier derivation pins representative GBP and INR examples to exact expected IDs/tiers.
- Legacy adapter never invents currency or a normalized tier.

### Backend integration tests

- Options and income-options contracts include exact versioned profile data and cache metadata.
- Saving and reading version-2 answers persists profile provenance and derived tiers.
- Old request shapes remain legacy during the compatibility phase; malformed mixed v1/v2 requests
  fail with `400`.
- Vote creation snapshots canonical tiers plus immutable currency-qualified band identities. Direct
  results resolve exact GBP/INR range labels, cross-market analysis uses tiers, legacy nominal
  values remain excluded, and both paths enforce suppression.
- Liquibase upgrade from representative legacy rows preserves original strings and leaves unknown
  semantics explicit.

### Frontend unit and integration tests

- Selecting GBP and INR renders exact, materially different expected labels from fixtures.
- Changing currency/profile clears both prior income answers and refetches the correct profile.
- Personal and household controls use their respective band sets.
- Draft restoration retains a valid profile version and invalidates a retired one safely.
- Submission includes the nested version-2 answer and still contains no identity.
- Unsupported/offline profile loading has an actionable retry state and never falls back to shared
  nominal bands.

After changing tests, run the repository's `test-audit` skill, then run targeted frontend Jest and
backend unit/integration suites with Java from
`/Users/maxpersonal/.sdkman/candidates/java/current`.

## Rollout

1. Research and review pilot profiles for GB/GBP and IN/INR, including separate evidence for
   personal and household measures. Product/data review signs off the evidence packs and labels.
2. Add the immutable profile loader, additive APIs, nullable schema and legacy read/write adapter.
   Keep existing clients working and add metrics for profile resolution and legacy saves.
3. Release the mobile client using version-2 profiles. Initially keep cross-market income reporting
   behind a feature flag while validating band selection distribution and suppression rates.
4. Expand to the remaining supported market/currency pairs only after each profile passes the same
   evidence review. Do not claim support merely because an ISO currency code exists.
5. Prompt legacy users to refresh finance answers, monitor adoption/error rates, then require
   version 2 for current clients.
6. Enable currency-qualified direct results and tier-based internal analysis, with dashboards
   separating answer version, profile version and suppression. Roll back by disabling new profile
   selection/reporting; immutable version-2 answers remain readable.

## Acceptance criteria

- GBP and INR return different reviewed thresholds and locally appropriate labels for both personal
  and household income.
- Changing profile invalidates previous selections and the server rejects cross-profile band IDs.
- Every new answer is reproducible from immutable profile/version metadata.
- Existing unknown-currency answers are preserved but never misrepresented as comparable tiers.
- Vote snapshots retain canonical tiers and immutable currency-qualified band identities, never
  exact income. Public direct results expose only suppressed aggregate range labels per currency;
  internal cross-market analysis exposes canonical tiers rather than nominal ranges.
- No runtime dependency on live FX, PPP or third-party income APIs exists.

## Implementation status — 2026-07-25

Implemented catalogue version `2026.1` as immutable backend code. GB/GBP and IN/INR use locally
reviewed cut points and distinct personal/household bands; INR labels use lakh units. Eighteen
additional launch profiles are explicitly marked medium-confidence World Bank 2024 PPP-calibrated
fallbacks and retain their derivation/source metadata for later replacement by stronger national
distributions.

The API now exposes the profile directory and market/currency profile endpoint. New saves use the
version-2 nested answer, validate country/profile/band provenance, reject mixed legacy/versioned
answers, and persist server-derived tiers. Legacy rows remain version 1 without an inferred
currency. The mobile flow derives supported profiles from residence, refetches bands when the
profile changes, clears both prior selections, and submits only band IDs and profile provenance.
Vote snapshots separate `LEGACY_*` buckets from `V2_TIER_*` buckets.

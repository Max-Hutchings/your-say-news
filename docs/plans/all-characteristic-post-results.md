# All-characteristic post results

Implementation plan for making every retained, reportable non-news characteristic available in the
mobile post-results experience without turning the existing selector into an unusable strip of 35
chips.

This plan implements the aggregation scope and semantics recorded in
[ADR-028](../../wiki/ADR-028-2026-07-25-safe-demographic-insight-selection.md). It does not expose
the internal `PostAnalysisAggregateV1` contract to the mobile app.

## Goal

After voting on a post, a user can inspect the vote split for any of the 35 retained non-news
characteristics. The experience must:

- remain quick for the most commonly used breakdowns;
- make every other characteristic discoverable without horizontal-chip overload;
- describe overlapping multi-select cohorts honestly;
- preserve the existing vote-first and small-bucket privacy gates;
- fetch one selected axis at a time rather than downloading the complete analysis dataset; and
- keep all four news-habit answers out of this surface.

## Product scope

### Included axes

| Section | Characteristics |
| --- | --- |
| Identity | Age, gender, sex at birth, sexual orientation, marital status, ethnic background |
| Place and background | Country, region, urban/rural, UK county, country of birth, citizenship |
| Beliefs | Political leaning, religion, religiosity |
| Education and work | Education, occupation, employment sector, university subject |
| Income | Personal income tier, household income tier |
| Body and family | Height, weight range, eye colour, parent/caregiver status |
| Lifestyle | Has pets, pet type, chronotype, outlook |
| Neurodiversity and disability | Neurodivergent, neurodivergence type, has a disability, disability type |
| Housing | Housing status, property type |

The backend field names remain:

```text
ageRange, gender, sexAtBirth, sexualOrientation, maritalStatus, race,
country, region, urbanRural, ukCounty, countryOfBirth, citizenship,
politicalPersuasion, religion, religiosity,
education, occupation, employmentSector, universitySubject,
personalIncomeRange, householdIncomeRange,
height, weightRange, eyeColor, parent,
hasPet, petType, chronotype, outlook,
neurodivergent, neurodivergenceType, hasDisability, disabilityType,
housingStatus, propertyType
```

### Explicitly excluded

Do not expose `newsFrequency`, `balancedNewsViewpoint`, `mainstreamNewsPercent`, or
`betterWorldWithData`. Do not add characteristic intersections to the direct mobile results in this
work. Intersections remain governed inputs for Post Unwrapped only.

Exact age, city and free-text gender self-description are not reportable axes and remain excluded.

### Display and denominator rules

- Personal and household income results display the local range and ISO currency, for example
  `£40,000–£59,999 · GBP` or `₹8–12 lakh · INR`. Do not display a currency-neutral `TIER_3` label
  to users and do not imply that nominal ranges in different currencies are directly comparable.
- `country`, `countryOfBirth` and `citizenship` show at most the top 10 buckets by included voter
  count. If more exist, say `Showing the 10 countries with the most voters for this post.` Do not
  combine the remainder into a synthetic `Other` bucket.
- A missing or not-applicable answer does not enter any bucket and is not included in the selected
  axis total. This applies especially to pet type, neurodivergence type, disability type,
  university subject, employment sector and property type.
- If no vote has an applicable captured value for the selected axis, return no buckets and show
  `No data is available for this characteristic yet.`
- Overall post totals remain unchanged and continue to include every canonical vote.

## UX direction

### Information architecture

Retain a short horizontal **Quick breakdowns** row for:

- Political leaning
- Age
- Gender
- Ethnic background
- Country
- Personal income

Follow it with one full-width **All characteristics** control. It opens a searchable, categorised
bottom sheet containing all 35 axes. Selecting an axis closes the sheet, makes it the active
breakdown and adds it temporarily to the quick row for the rest of that results session.

This keeps the common path to one tap while making the long tail reachable in two taps. Do not put
all 35 axes in one horizontal `ScrollView`; users cannot scan or understand that structure.

### Visual language

Continue the established editorial theme from `constants/theme/editorial.ts`:

- paper/ink surfaces;
- Newsreader for the selected breakdown title;
- Schibsted Grotesk for controls;
- Spline Sans Mono for counts, caveats and cohort semantics;
- teal/coral only for voting-option meaning, not characteristic categories.

The signature element is an editorial **characteristic index**: category headings act like the
index tabs in a reference book, with a quiet count such as `Identity · 6`. This encodes the real
structure of the data rather than decorating the sheet.

```text
┌─────────────────────────────────────┐
│ Break it down                       │
│ [Politics] [Age] [Gender] [More  ›] │
│                                     │
│ Age                                 │
│ Number of votes                     │
│ █████████  25–34                    │
│ ██████     35–44                    │
└─────────────────────────────────────┘

             tap More

┌─────────────────────────────────────┐
│ All characteristics           Close │
│ [ Search characteristics…         ] │
│                                     │
│ IDENTITY · 6                        │
│ Age                              ✓  │
│ Gender                              │
│ Sex at birth                        │
│ Sexual orientation                  │
│ …                                   │
│                                     │
│ PLACE AND BACKGROUND · 6            │
│ Country                             │
│ Region                              │
└─────────────────────────────────────┘
```

Use one opening/closing sheet animation only. Axis changes should retain the existing focused chart
loading state; do not add decorative motion to every row.

### Multi-select explanation

The following axes use `MULTI_MEMBERSHIP`:

- ethnic background (`race`);
- citizenship;
- pet type;
- neurodivergence type; and
- disability type.

When one is active, show this persistent note directly below the chart heading:

> People can appear in more than one group. Group totals should not be added together.

Do not show a population-share view for these axes unless that view explicitly supports overlapping
denominators. Existing per-group option percentages remain valid because each percentage uses the
number of voters belonging to that group as its denominator.

### Chart compatibility

- **Bars** remains the default and is available for every axis.
- **Table** remains available, but its final column is labelled `Voters in group` rather than
  `Total` for multi-membership axes.
- **Counts** remains available for every surfaced bucket.
- **Columns** is unavailable for multi-membership axes because its population-distribution shape
  implies mutually exclusive groups.
- Country-like axes render only their top 10 buckets in every view.
- Naturally ordered bands—age, local-currency income, height, weight and religiosity—use governed
  semantic ordering instead of largest-bucket-first ordering.

### Labels

Do not rely on `prettifyBucket` alone for the expanded surface. It cannot produce good copy for
booleans, income tiers, parent/caregiver values, housing values or several enum names.

Use governed display labels:

- `true`/`false` become context-specific labels such as `Has pets`/`Does not have pets`;
- income buckets receive their immutable profile label plus currency rather than `V2 TIER 3`;
- conditional type axes use plain labels matching onboarding;
- historical/deprecated values retain a stable fallback label; and
- an unknown value is never displayed as a selectable cohort.

## Backend contract

### One shared reportable-axis catalogue

Move the 35-axis definition into one votes-domain catalogue consumed by:

- `PostAnalysisAggregateBuilder`;
- direct sentiment-axis validation;
- membership-semantics selection; and
- an axis-metadata endpoint for the mobile app.

The catalogue is the single source of truth. Each entry contains:

```java
record CharacteristicAxisMetadataDto(
    String field,
    String label,
    String section,
    int sectionOrder,
    int axisOrder,
    MembershipSemantics membershipSemantics,
    BucketOrdering bucketOrdering,
    Integer displayLimit
) {}
```

Keep display labels/versioning backend-owned so mobile and analysis cannot silently drift. The
catalogue must contain exactly 35 unique fields and explicitly reject all four news-habit fields.
The three country-like axes declare a display limit of 10. Ordered bands declare their governed
bucket ordering rather than inheriting total-count ordering.

### Metadata endpoint

Add:

```text
GET /votes/sentiment/axes
```

It returns the ordered, identity-free static catalogue. This endpoint contains no vote counts and
does not need a post ID. It may be cached by the app for the session.

Do not return `PostAnalysisAggregateV1` to the frontend. That contract contains the complete cohort
comparison family for internal deterministic selection and would create unnecessary payload,
coupling and privacy surface.

### Direct result endpoint

Retain:

```text
GET /votes/{postId}/sentiment/{axis}
```

Change its validation to use the shared reportable-axis catalogue instead of the broader raw
snapshot field set. An excluded news-habit or unknown field returns `400`.

Extend `SentimentBreakdownDto` with:

```java
MembershipSemantics membershipSemantics,
long includedVoteCount
```

`OVERALL` and exclusive axes return `EXCLUSIVE`; the five multi-select axes return
`MULTI_MEMBERSHIP`. `includedVoteCount` is the number of canonical votes with a captured,
applicable value on the selected axis. It is not the sum of bucket totals for multi-membership
axes.

`SentimentTally.byCharacteristic` skips `UNKNOWN`, null, empty and not-applicable values rather than
creating a bucket for them. If every vote is skipped, return an empty `buckets` list and
`includedVoteCount=0`. Do not change the overall canonical vote count.

Keep the existing must-have-voted authorization check and `suppressBelow=k` policy. Never return a
voter ID, snapshot, exact age, city, free text or individual vote row.

### Bucket display labels

Prefer adding a `label` to each returned bucket:

```java
record BucketSentiment(
    String bucket,
    String label,
    long total,
    List<ChoiceSentiment> choices
) {}
```

Generate it from a versioned votes-domain reporting-label catalogue. This keeps historic snapshot
values readable and avoids duplicating backend enum knowledge throughout the mobile app.

If bucket labels cannot be added in the first delivery slice, add a typed frontend label catalogue
covering every current value and make missing labels fail tests. Treat that as a temporary
compatibility path, not the long-term source of truth.

### Currency-qualified income buckets

The direct mobile result needs different snapshot data from cross-market Post Unwrapped analysis:

- retain the immutable income profile/band ID and ISO currency needed to resolve the original local
  display range;
- retain the server-derived canonical tier separately for internal cross-market analysis;
- use the currency-qualified band for direct `personalIncomeRange` and `householdIncomeRange`
  breakdowns; and
- use the canonical tier only in internal analysis where a reviewed cross-market comparison is
  intended.

A direct-result bucket key must be stable and currency-qualified, for example
`GB-GBP-GROSS-2026-v1:PERSONAL_P25_P50`. The response label resolves that key through the retained
immutable profile definition. Do not copy exact user income into a vote snapshot; the stored value
remains a range selected from the governed catalogue.

Legacy income answers without a stored currency cannot be displayed as currency-specific ranges.
Exclude them from the direct income-axis denominator and record an internal exclusion metric.

## Mobile implementation

### Domain model and service

In `features/votes`:

- add `CharacteristicAxisMetadata` and `MembershipSemantics` to `types.ts`;
- add `getSentimentAxes()` to `SentimentService`;
- extend `SentimentBreakdown` and `BucketSentiment` with the new semantics/label fields;
- replace the hard-coded ten-item `SENTIMENT_AXES` export with the six quick-axis field names and
  presentation helpers only; and
- cache axis metadata for the app session.

Keep axis-result requests lazy. Do not issue 35 result requests when the sheet opens.

### Components

Add:

- `CharacteristicPickerSheet.tsx` — modal host, search, category sections and selected state;
- `CharacteristicSection.tsx` — one accessible category and its axis rows;
- `CharacteristicRow.tsx` — one axis choice with selected indication; and
- `MembershipSemanticsNote.tsx` — the multi-membership explanation.

Update:

- `SentimentResults.tsx` — quick row, picker state, selected metadata and semantics note;
- `AxisChip.tsx` — support the `All characteristics` affordance without pretending it is an axis;
- `ChartHead.tsx` — accept the governed axis label and optional semantics copy; and
- chart/table components — use backend bucket labels and never sum multi-membership totals.

Keep routes thin and preserve `features/votes/index.ts` as the feature's public face.

### Loading, empty and error states

- Axis catalogue loading: retain the six local quick-field identifiers so the common path is not
  blocked; reconcile labels when metadata arrives.
- Selected-axis loading: clear stale bars and keep the current focused spinner.
- No captured/applicable votes for an axis: `No data is available for this characteristic yet.`
- Partial data: show `Based on {includedVoteCount} voters with data for this characteristic.`
- Country-like axes with more than 10 buckets: show
  `Showing the 10 countries with the most voters for this post.`
- Suppressed buckets: retain the existing exact hidden-group count.
- Catalogue failure: quick axes continue to work and **All characteristics** offers a retry.
- Axis failure: retain the existing retry without closing the picker or changing selection.

### Accessibility

- The picker is a modal with a labelled heading and explicit close action.
- Search results announce their section and result count.
- Axis rows use `accessibilityRole="radio"` and selected state.
- Quick chips retain at least a 44-point touch target.
- Focus returns to **All characteristics** after dismissing without a selection and to the newly
  selected chip after choosing an axis.
- Category, selected and multi-membership meaning must not depend on colour.

## Delivery sequence

### Slice 1 — Govern the contract

1. Extract the shared 35-axis backend catalogue.
2. Make the analysis builder and direct endpoint use it.
3. Add axis metadata and membership semantics to public DTOs.
4. Add governed bucket labels or the temporary exhaustive mobile label catalogue.
5. Update ADR-028 with the direct-results catalogue and frontend disclosure rules.

Exit condition: one exact backend test proves all 35 fields, ordering, section, semantics and four
news exclusions from the same source of truth.

### Slice 2 — Build the characteristic index

1. Add metadata fetching/caching and typed mobile contracts.
2. Build the searchable categorised picker.
3. Replace the ten-axis rail with six quick axes plus **All characteristics**.
4. Preserve the selected axis during view changes and for the lifetime of the open results sheet.

Exit condition: every metadata axis is selectable and triggers exactly one correctly addressed
sentiment request.

### Slice 3 — Make semantics and labels honest

1. Render governed axis and bucket labels.
2. Add the multi-membership note and disable any incompatible composition treatment.
3. Verify empty, suppressed and historical-value fallbacks.
4. Complete screen-reader and keyboard behaviour.

Exit condition: no raw enum, boolean, tier key or joined multi-select label appears in the UI.

### Slice 4 — Prove the complete flow

1. Add deterministic seed votes covering all 35 axes and every multi-select field.
2. Add backend integration tests for representative exclusive and multi-membership axes.
3. Add frontend interaction tests for quick selection, category selection, search and error retry.
4. Add a smoke flow that votes, opens results, selects a non-core axis and verifies its exact
   labelled bucket/count.

Exit condition: the same seeded post proves an exclusive long-tail axis and an overlapping
multi-select axis end to end.

## Test plan

### Backend unit tests

- Catalogue contains exactly 35 unique axes in the expected category/order.
- The four news-habit fields are absent.
- The five multi-select axes declare `MULTI_MEMBERSHIP`; every other axis declares `EXCLUSIVE`.
- `PostAnalysisAggregateBuilder` produces each catalogue axis once.
- Multi-select members contribute once to every selected bucket with no joined synthetic bucket.
- Intersections remain the reviewed exclusive two-axis allowlist.
- Every bucket key has an approved display label or a tested historical fallback.
- Missing/not-applicable values create no bucket and do not increment `includedVoteCount`.
- Currency-qualified personal and household income bands resolve exact GBP and INR labels while
  retaining separate canonical tiers for internal analysis.

### Backend integration tests

- Axis metadata returns exact ordered DTOs and no identity/count data.
- A voter can request a newly exposed exclusive axis after voting.
- A voter can request a multi-membership axis and receives overlapping buckets plus its semantics.
- A caller who has not voted remains unable to retrieve a post breakdown.
- Unknown and all four news-habit axes return `400`.
- Suppression applies identically to old and newly exposed axes.
- Responses contain no user ID, name, email, exact age, city, snapshot or individual vote row.
- Completely empty axes return no buckets and `includedVoteCount=0`.
- Partial axes calculate percentages and `includedVoteCount` from applicable captured values only.

### Mobile unit/component tests

- Quick row contains the six governed quick fields and **All characteristics**.
- Picker renders all nine sections and all 35 unique axes.
- Search matches both user-facing labels and reasonable keywords.
- Selecting a long-tail axis closes the picker, updates the title and calls the exact backend field.
- Switching chart views does not refetch or change the selected axis.
- Returning to a previously selected axis uses the session cache where valid.
- Multi-membership note appears for exactly five axes.
- Backend bucket labels render instead of raw keys.
- Empty, suppression, metadata-error and axis-error copy is exact.
- Country, country-of-birth and citizenship views render no more than 10 buckets and disclose the
  limit when additional buckets exist.
- GBP and INR income buckets render their exact local ranges and currency codes.
- Modal focus, selected state and close behaviour are accessible.

## Acceptance criteria

- All 35 retained non-news axes are selectable from post results.
- The six quick axes remain one tap away; every other axis is at most two taps away.
- None of the four news-habit answers appears or can be requested through the direct results API.
- Only the selected axis is fetched.
- Five multi-select axes visibly declare overlapping membership semantics.
- No multi-select buckets are added together or described as exclusive population shares.
- Every axis and bucket uses governed human-facing labels.
- Country-like axes display at most the top 10 buckets by included voter count.
- Missing and not-applicable answers are absent from buckets and selected-axis totals.
- An axis with no applicable captured values shows the exact no-data state.
- Personal and household income display immutable local ranges per currency; incompatible currencies
  are never presented as one nominal scale.
- Existing vote-first authorization and suppression behaviour remains unchanged.
- The mobile app never receives `PostAnalysisAggregateV1`, characteristic snapshots or individual
  vote records.
- Backend, mobile and one end-to-end seeded flow pin exact results for both exclusive and
  multi-membership axes.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| A 35-item picker overwhelms users | Six quick axes plus search and nine meaningful sections |
| Backend/frontend axis lists drift | One backend catalogue and exact contract tests |
| Multi-select totals look contradictory | Explicit semantics in the API and persistent UI note |
| Raw enum keys produce poor copy | Governed backend bucket labels with historical fallbacks |
| Opening results becomes slow | Fetch metadata once and result data one selected axis at a time |
| Rare cohorts expose sensitive patterns | Preserve vote-first authorization and bucket suppression |
| New axes become agent narration by accident | Keep direct results separate from the bounded Unwrapped selector |
| News fields leak into the surface | Catalogue exclusion plus endpoint and frontend tests |

## Out of scope

- News-habit aggregation or display.
- Arbitrary characteristic intersections in direct results.
- User-created filters or combining several axes.
- Download/export of cohort data.
- Changing the release decision for `suppressBelow=k`.
- Sending the internal full post-analysis aggregate to mobile.

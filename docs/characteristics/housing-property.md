# Housing and Property Characteristics

| Area | Current state | Recommended change | Why |
| --- | --- | --- | --- |
| Housing status / tenure | `LIVE_WITH_PARENTS`, `RENT`, `OWN`. | Split into clearer tenure buckets: own outright, own with mortgage, shared ownership, private rent, social rent, live with parents/family, live rent-free, student/university accommodation, temporary/no fixed address, other. | Official housing classifications separate tenure in more detail than the current three options; the current list hides major differences between mortgage owners, outright owners, private renters, and social renters. |
| Property type | Only asked when `housingStatus === "OWN"` and only offers `HOUSE` or `FLAT`. | Ask "What type of home do you live in?" for all settled housing statuses, not just owners; offer house subtypes, flat/apartment, room in shared house, student halls, mobile/temporary structure, other. | Dwelling type matters for renters and people living with family too. Property type describes the home, not ownership. |
| User-facing wording | Page is titled "Property"; first question is "Do you..."; second is "Do you own a house or a flat?" | Rename page to "Housing"; ask "Which best describes your housing situation?" and "What type of home do you live in?" | "Property" can imply ownership. Clear housing wording works for renters, family homes, students, and people without stable housing. |
| Privacy / aggregation | Housing fields are treated as required and shown only in aggregate. | Keep buckets broad in reporting, suppress low-count combinations, and avoid crossing temporary/no-fixed-address with small location buckets. | Housing status can be sensitive and, when combined with location, age, income, and household details, can become identifying. |

## Current State

The frontend defines these enum-backed lists in `frontend/mobile/your-say-news/features/user-characteristics/data/options.ts`:

| Field | Label | Value |
| --- | --- | --- |
| `housingStatus` | `Live with parents` | `LIVE_WITH_PARENTS` |
| `housingStatus` | `Rent` | `RENT` |
| `housingStatus` | `Own a property` | `OWN` |
| `propertyType` | `House` | `HOUSE` |
| `propertyType` | `Flat` | `FLAT` |

The onboarding screen shows a final "Property" step with:

- Field label: `Do you... *`
- Conditional follow-up when the user selects `OWN`: `Do you own a house or a flat? *`
- `propertyType` is cleared whenever `housingStatus` is not `OWN`.

The backend mirrors this contract in:

| Backend enum | Current values |
| --- | --- |
| `HousingStatus` | `LIVE_WITH_PARENTS`, `RENT`, `OWN` |
| `PropertyType` | `HOUSE`, `FLAT` |

ADR-010 intentionally made `propertyType` owner-only to avoid contradictory data. That keeps today's data clean, but it also means the app cannot learn whether renters live in a flat, house share, student hall, temporary accommodation, or another common housing type.

## Recommended Option Set

### Housing Status / Tenure

Recommended label: `Which best describes your housing situation? *`

| Recommended label | Recommended enum value | Notes |
| --- | --- | --- |
| `Own outright` | `OWN_OUTRIGHT` | Common official tenure bucket. |
| `Own with a mortgage or loan` | `OWN_WITH_MORTGAGE` | Separates mortgage holders from outright owners. |
| `Shared ownership / part rent, part buy` | `SHARED_OWNERSHIP` | Important UK housing tenure; wording explains the term. |
| `Rent privately` | `PRIVATE_RENT` | More useful than broad `RENT`. |
| `Rent from council, local authority, or housing association` | `SOCIAL_RENT` | Combines social-rent landlord types into one broad, privacy-safer bucket. |
| `Live with parents or family` | `LIVE_WITH_PARENTS_OR_FAMILY` | Broader and less age-assumptive than `Live with parents`. |
| `Live rent-free` | `LIVE_RENT_FREE` | Officially recognised in UK tenure data and distinct from renting. |
| `Student or university accommodation` | `STUDENT_ACCOMMODATION` | Common enough to merit a bucket; avoid forcing students into private/social rent. |
| `Temporary accommodation / no fixed address` | `TEMPORARY_OR_NO_FIXED_ADDRESS` | Sensitive, but more accurate than "Other" for people in unstable housing. Consider optional wording. |
| `Other housing situation` | `OTHER` | Catch-all for edge cases. |

No `Prefer not to say` (see ADR-016); housing status is a required real choice.

Minimum-change version if the product wants fewer new buckets:

| Recommended label | Recommended enum value |
| --- | --- |
| `Own outright` | `OWN_OUTRIGHT` |
| `Own with a mortgage or shared ownership` | `OWN_WITH_MORTGAGE_OR_SHARED_OWNERSHIP` |
| `Rent privately` | `PRIVATE_RENT` |
| `Rent social housing` | `SOCIAL_RENT` |
| `Live with parents or family` | `LIVE_WITH_PARENTS_OR_FAMILY` |
| `Live rent-free` | `LIVE_RENT_FREE` |
| `Other housing situation` | `OTHER` |

### Property Type / Accommodation Type

Recommended label: `What type of home do you live in? *`

Ask this for users with a current home regardless of tenure: owners, renters, people living with family, rent-free occupants, and most student accommodation. For `TEMPORARY_OR_NO_FIXED_ADDRESS`, either skip the field or make it optional with a sensitive label such as `If one applies, what type of accommodation is it?`.

| Recommended label | Recommended enum value | Notes |
| --- | --- | --- |
| `Detached house` | `DETACHED_HOUSE` | Census-style house subtype. |
| `Semi-detached house` | `SEMI_DETACHED_HOUSE` | Common UK category. |
| `Terraced house` | `TERRACED_HOUSE` | Common UK category. |
| `House, not sure what type` | `HOUSE_OTHER_OR_UNKNOWN` | Keeps the UI usable for non-UK users and users who do not know the subtype. |
| `Flat / apartment in a purpose-built block` | `PURPOSE_BUILT_FLAT_APARTMENT` | Matches common ONS wording while remaining user-friendly. |
| `Flat / apartment in a converted house or building` | `CONVERTED_FLAT_APARTMENT` | Captures converted houses/buildings without over-splitting. |
| `Room in a shared house or flat` | `ROOM_IN_SHARED_HOME` | Important for house shares; not the same as whole-property rent. |
| `Student halls / dormitory` | `STUDENT_HALLS_OR_DORM` | Useful if tenure is `STUDENT_ACCOMMODATION`. |
| `Mobile home, caravan, or temporary structure` | `MOBILE_OR_TEMPORARY_STRUCTURE` | ONS accommodation type includes mobile or temporary structures. |
| `Other type of home` | `OTHER` | Catch-all. |

If the team wants a compact first pass, use:

| Recommended label | Recommended enum value |
| --- | --- |
| `House` | `HOUSE` |
| `Flat / apartment` | `FLAT_APARTMENT` |
| `Room in shared accommodation` | `ROOM_IN_SHARED_ACCOMMODATION` |
| `Student halls / dormitory` | `STUDENT_HALLS_OR_DORM` |
| `Mobile or temporary home` | `MOBILE_OR_TEMPORARY_HOME` |
| `Other` | `OTHER` |

## Wording and UX Notes

Recommended page copy:

| Element | Recommended wording |
| --- | --- |
| Page title | `Housing` |
| Page subtitle | `Broad housing categories only. Used for anonymous aggregate comparisons.` |
| Tenure field | `Which best describes your housing situation? *` |
| Property field | `What type of home do you live in? *` |
| Privacy helper | `Choose the closest option. These answers are never shown against your profile.` |

Wording details:

- Use `housing situation` or `housing tenure`, not `property`, for the first question. "Property" sounds ownership-specific.
- Avoid the prompt `Do you...`; it depends too heavily on chip labels and reads awkwardly with expanded options.
- Use "live with parents or family" rather than "live with parents" so adults living with grandparents, siblings, or other family can answer accurately.
- Use "rent privately" and "rent social housing" rather than only "rent". These are materially different groups for housing, cost-of-living, policy, and local-news topics.
- Keep "temporary accommodation / no fixed address" carefully worded. It is a legitimate housing circumstance, but sensitive enough to need privacy reassurance and low-count suppression.
- Do not ask "Do you own a house or a flat?" if the answer is needed for all housing situations. Ask what type of home they live in.

## Implementation Notes

Files likely affected when implementing this recommendation:

| File | Change |
| --- | --- |
| `frontend/mobile/your-say-news/features/user-characteristics/data/options.ts` | Replace `HOUSING_STATUS_OPTIONS` and expand `PROPERTY_TYPE_OPTIONS`. |
| `frontend/mobile/your-say-news/features/user-characteristics/components/OnboardingScreen.tsx` | Rename the step to "Housing"; update field labels; ask property type for all applicable statuses rather than only `OWN`. |
| `frontend/mobile/your-say-news/features/user-characteristics/answers.ts` | Update completion rules and the logic that currently nulls `propertyType` for non-owners. |
| `user-service/src/main/java/com/yoursay/usercharacteristic/model/Enums/HousingStatus.java` | Add new tenure enum constants. |
| `user-service/src/main/java/com/yoursay/usercharacteristic/model/Enums/PropertyType.java` | Add new accommodation-type enum constants. |
| Backend and frontend tests | Update required-field tests, mapping tests, seed data, and valid request bodies. |
| `wiki/ADR-010-2026-06-30-property-characteristics.md` or a new ADR | Record the model change because it changes the meaning of `propertyType` from owner-only property type to current-home accommodation type. |

Migration approach:

- Existing `OWN` can be kept as a legacy bucket or migrated to `OWN_WITH_MORTGAGE_OR_SHARED_OWNERSHIP` only if the product accepts loss of precision. It cannot be reliably split into outright/mortgage/shared ownership after the fact.
- Existing `RENT` cannot be reliably split into private rent and social rent. Keep it as a legacy bucket or ask users to refresh this answer.
- Existing `LIVE_WITH_PARENTS` can safely map to `LIVE_WITH_PARENTS_OR_FAMILY`.
- Existing `HOUSE` and `FLAT` can remain valid as broad legacy property-type values, or map to `HOUSE_OTHER_OR_UNKNOWN` and `FLAT_APARTMENT` in a compact model.
- Public breakdowns should use aggregation thresholds. Avoid showing small combinations such as `TEMPORARY_OR_NO_FIXED_ADDRESS` + city + age band + income.

## Sources

- Office for National Statistics, "Tenure of household variable: Census 2021": https://www.ons.gov.uk/census/census2021dictionary/variablesbytopic/housingvariablescensus2021/tenureofhousehold
- Office for National Statistics, "Accommodation type variable: Census 2021": https://www.ons.gov.uk/census/census2021dictionary/variablesbytopic/housingvariablescensus2021/accommodationtype
- Nomis, "TS054 - Tenure": https://www.nomisweb.co.uk/datasets/c2021ts054
- Nomis, "TS044 - Accommodation type": https://www.nomisweb.co.uk/datasets/c2021ts044
- Scotland's Census, "Tenure of household": https://www.scotlandscensus.gov.uk/metadata/tenure-of-household/
- US Census Bureau, "Housing Characteristics: 2020": https://www2.census.gov/library/publications/decennial/2020/census-briefs/c2020br-09.pdf

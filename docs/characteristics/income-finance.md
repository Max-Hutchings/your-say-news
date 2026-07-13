# Income and Finance Characteristics

| Area | Current state | Recommended change | Why |
| --- | --- | --- | --- |
| Step grouping | Body fields and income fields share the "Body & finances" page. | Split finance into its own page and place "Quirky questions" between body and finance. | Height/weight and income are both sensitive; separating them reduces cognitive load and avoids stacking serious questions together. |
| Personal vs household income | App already stores `personalIncomeRange` and `householdIncomeRange`, using the same option list for both. | Keep both fields, but make the wording explicit: "your annual personal income before tax" and "total annual household income before tax". | Personal and household income answer different analysis questions; clear wording prevents users from double-counting or answering inconsistently. |
| Low and middle bands | Current options jump from `<20k` to `20k-50k`. | Split the 20k-50k band into narrower steps: `20k-30k`, `30k-40k`, `40k-50k`. | This is the range the user called out, and it sits near common UK/US income thresholds where many users will cluster. |
| Higher bands | Current upper bands are broad: `200k-500k`, `500k-1M`, `1M+`. | Keep broad upper bands for privacy unless product analysis proves enough sample size for narrower groups. | Very high income groups can become identifying when crossed with location, occupation, age, and other characteristics. |
| Currency display | Labels are generated as `USD <20k`, `USD 20k-50k`, etc. | Show the selected currency in the field helper/header once, then labels as clean amounts, or use labels like `Under USD 20k` and `USD 20k to USD 30k`. | Prefix replacement currently makes compact but slightly awkward labels; clearer wording matters because income answers are sensitive. |
| Privacy and aggregation | Privacy note exists globally. | Add a finance-specific reassurance near the currency/income fields: "Used only in aggregate; never shown against your profile." | Income is sensitive enough to deserve contextual reassurance at the point of entry. |

## Current State

The frontend defines one shared `INCOME_OPTIONS` list in `frontend/mobile/your-say-news/features/user-characteristics/data/options.ts`:

| Label | Value |
| --- | --- |
| `<20k` | `BELOW_20K` |
| `20k-50k` | `BETWEEN_20K_AND_50K` |
| `50k-100k` | `BETWEEN_50K_AND_100K` |
| `100k-150k` | `BETWEEN_100K_AND_150K` |
| `151k-200k` | `BETWEEN_151K_AND_200K` |
| `200k-500k` | `BETWEEN_200K_AND_500K` |
| `500k-1M` | `BETWEEN_500K_AND_1000K` |
| `1M+` | `ABOVE_1000000` |

The backend enum at `user-service/src/main/java/com/yoursay/usercharacteristic/model/Enums/IncomeRange.java` mirrors those values exactly. The onboarding screen asks for both "Annual personal income" and "Annual household income" on the same page as height, weight, eye colour, and parent status.

ADR-006 already records the correct product direction: collect both annual personal income and annual household income because they provide different aggregate signals. The recommended changes below refine the bands and wording rather than reversing that decision.

## Recommended Option Set

Use the same band list for personal and household income unless future research shows household-specific bands are needed. Keeping one enum simplifies implementation and cross-axis analysis.

| Recommended label | Recommended enum value | Notes |
| --- | --- | --- |
| `Under {currency} 20k` | `BELOW_20K` | Keep existing value. |
| `{currency} 20k to 30k` | `BETWEEN_20K_AND_30K` | New split from current 20k-50k band. |
| `{currency} 30k to 40k` | `BETWEEN_30K_AND_40K` | New split from current 20k-50k band. |
| `{currency} 40k to 50k` | `BETWEEN_40K_AND_50K` | New split from current 20k-50k band. |
| `{currency} 50k to 75k` | `BETWEEN_50K_AND_75K` | Optional but recommended; current 50k-100k is still broad. |
| `{currency} 75k to 100k` | `BETWEEN_75K_AND_100K` | Optional but recommended; aligns better with common survey breakpoints. |
| `{currency} 100k to 150k` | `BETWEEN_100K_AND_150K` | Keep existing value. |
| `{currency} 150k to 200k` | `BETWEEN_150K_AND_200K` | Rename current `151K` label/value for consistency if migration cost is acceptable. Otherwise keep `BETWEEN_151K_AND_200K`. |
| `{currency} 200k to 500k` | `BETWEEN_200K_AND_500K` | Keep broad for privacy. |
| `{currency} 500k to 1M` | `BETWEEN_500K_AND_1000K` | Keep broad for privacy. |
| `{currency} 1M or more` | `ABOVE_1000000` | Keep existing value. |

Minimum change version:

| Recommended label | Recommended enum value |
| --- | --- |
| `Under {currency} 20k` | `BELOW_20K` |
| `{currency} 20k to 30k` | `BETWEEN_20K_AND_30K` |
| `{currency} 30k to 40k` | `BETWEEN_30K_AND_40K` |
| `{currency} 40k to 50k` | `BETWEEN_40K_AND_50K` |
| `{currency} 50k to 100k` | `BETWEEN_50K_AND_100K` |
| `{currency} 100k to 150k` | `BETWEEN_100K_AND_150K` |
| `{currency} 151k to 200k` | `BETWEEN_151K_AND_200K` |
| `{currency} 200k to 500k` | `BETWEEN_200K_AND_500K` |
| `{currency} 500k to 1M` | `BETWEEN_500K_AND_1000K` |
| `{currency} 1M or more` | `ABOVE_1000000` |

## Wording and UX Notes

Recommended page order:

| Current page | Recommended page |
| --- | --- |
| Body & finances | Body |
| Quirky questions | Quirky questions |
| News habits | Finance |
| Neurodiversity & disability | News habits |
| Property | Neurodiversity & disability, then Property |

Recommended finance page copy:

| Element | Recommended wording |
| --- | --- |
| Page title | `Finance` |
| Page subtitle | `Income bands only. Used for anonymous aggregate comparisons.` |
| Currency field | `Currency` |
| Personal income field | `Your annual personal income before tax *` |
| Household income field | `Total annual household income before tax *` |
| Helper/privacy note | `Choose the closest band. These answers are used only in aggregate and are never shown against your profile.` |

Wording details:

- Use "income" rather than "salary" because users may have income from self-employment, pensions, benefits, investments, or multiple jobs.
- Say "before tax" to make answers more comparable across countries and to match common tax/statistical framing.
- Say "household" only for the household question; personal income should mean the respondent alone.
- Keep the user-selected currency visible. For international users, avoid silently implying GBP or USD.
- Prefer "Under 20k" and "1M or more" over symbols such as `<20k` and `1M+` in the final UI; they are clearer for screen readers and localization.
- Consider allowing "Not sure" only if the broader characteristics model reintroduces non-answer states. If added, keep it out of aggregate buckets rather than treating it as a real income band.

## Research Notes

The recommendation is intentionally a hybrid of survey usability, tax/statistical thresholds, and privacy:

- The UK Office for National Statistics reported median UK household disposable income of GBP 36,700 for FYE 2024, with the poorest fifth at GBP 16,800 and richest fifth at GBP 71,100. This supports more granularity around 20k-50k and some additional separation between 50k-100k. Source: https://www.ons.gov.uk/peoplepopulationandcommunity/personalandhouseholdfinances/incomeandwealth/bulletins/householddisposableincomeandinequality/financialyearending2024
- GOV.UK's income tax bands place the UK basic-rate threshold at GBP 12,571 to GBP 50,270 and higher-rate threshold from GBP 50,271 to GBP 125,140. This makes 50k a meaningful boundary, but the current 20k-50k bucket is too wide below it. Source: https://www.gov.uk/income-tax-rates
- DWP's Households Below Average Income release reports FYE 2025 median household income before housing costs of about GBP 37,500 per year and after housing costs of about GBP 32,500 per year. This again supports narrower 30k-40k and 40k-50k ranges. Source: https://www.gov.uk/government/statistics/households-below-average-income-for-financial-years-ending-1995-to-2025/households-below-average-income-an-analysis-of-the-uk-income-distribution-fye-1995-to-fye-2025
- The US Census CPS HINC-06 table publishes household income distribution up to USD 250,000 or more, using detailed ranges and specifically collecting 2024 household income in the 2025 ASEC supplement. This supports the idea that household income is a standard standalone survey axis. Source: https://www.census.gov/data/tables/time-series/demo/income-poverty/cps-hinc/hinc-06.html
- The IRS 2025 tax brackets put single-filer thresholds around USD 11,925, USD 48,475, USD 103,350, USD 197,300, USD 250,525, and USD 626,350. This is not a survey design by itself, but it reinforces 50k-ish and 100k-ish boundaries as understandable personal-income thresholds. Source: https://www.irs.gov/filing/federal-income-tax-rates-and-brackets

## Implementation Notes

Files likely affected when implementing this recommendation:

| File | Change |
| --- | --- |
| `frontend/mobile/your-say-news/features/user-characteristics/data/options.ts` | Add new income enum-backed options and update labels. |
| `frontend/mobile/your-say-news/features/user-characteristics/components/OnboardingScreen.tsx` | Split body and finance into separate steps; move quirky questions between them; update finance labels and helper text. |
| `user-service/src/main/java/com/yoursay/usercharacteristic/model/Enums/IncomeRange.java` | Add matching enum constants. |
| `user-service/src/main/resources/db/migrations/...` | Add migration/backfill strategy for existing `BETWEEN_20K_AND_50K` values. |
| Frontend/backend tests and seed data | Update expected enum values and valid request bodies. |

Migration approach:

- Existing `BETWEEN_20K_AND_50K` answers cannot be accurately split after the fact. Keep the old enum value temporarily for historical rows, or migrate legacy rows to a compatibility bucket such as `BETWEEN_20K_AND_50K_LEGACY` if the backend must distinguish old coarse answers from new precise answers.
- New submissions should use only the new split values.
- If analytics expects ordered income bands, add an explicit ordering map rather than relying on enum declaration order.
- Treat personal and household bands as sensitive quasi-identifiers. In public or user-facing breakdowns, suppress or merge cells below the product's minimum anonymity threshold.
- If `BETWEEN_151K_AND_200K` is renamed to `BETWEEN_150K_AND_200K`, add a data migration and compatibility handling. If minimizing risk, leave the enum value as-is and only change the label to `150k to 200k`.

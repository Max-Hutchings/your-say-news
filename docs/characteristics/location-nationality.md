# Location, Country of Birth, and Nationality

| Area | Current state | Recommended change | Why |
| --- | --- | --- | --- |
| Country of residence | UI asks "Country or region you live in" using `COUNTRY_OF_BIRTH_OPTIONS`; payload key is `country` as a stored label. | Rename product language to "Country of residence" or "Where do you live now?" and store a stable country code/value rather than only a display label. | Residence, birth country, and nationality are different concepts; using one country list is fine, but the question should say what it measures. |
| Country of birth | UI asks "Country of birth" and sends `countryOfBirth`; frontend list has 195 sovereign-state style options. | Keep this as "Country of birth"; source the list from ISO 3166/UN country names and include commonly needed territories only if the product chooses to support them consistently. | This is a factual birthplace country question, not identity or citizenship. A standard source keeps labels stable and globally understandable. |
| Nationality / citizenship | UI asks "Citizenship / nationality"; state and payload are named `citizenship`; options are mostly country names plus British, English, Scottish, Welsh, Northern Irish. | Present this to users as "Nationality" with helper text "Select the nationality or national identity you use for yourself"; support multiple selections or add "Another nationality" if single-select must remain. | ONS treats national identity as self-defined and potentially multi-response; UK users may identify as British, English, Scottish, Welsh, Northern Irish, Irish, or more than one. |
| Multiple nationalities | Nationality is single-select and mostly country names. | Support more than one nationality (or add "Another nationality") so people who hold or identify with multiple nationalities are not forced to pick one. | The app runs in many countries; dual/multiple national identity is common everywhere, not a UK-only concern. |
| Privacy / aggregation | Residence country, city, region, birth country, and nationality can combine into small buckets. | Aggregate display should suppress low-count buckets and roll rare values to region-level groups where needed, especially when combined with city/region. | These are non-PII fields individually, but combinations can become identifying in small communities. |

## Current State

The onboarding currently separates three related fields:

- `country`: where the user lives now. In the UI this is labelled "Country or region you live in *" and uses `COUNTRY_OF_BIRTH_OPTIONS`. The form stores the display label, for example "United Kingdom".
- `countryOfBirth`: the country where the user was born. This is enum-backed by `CountryOfBirth`.
- `citizenship`: the nationality/citizenship field. The UI label is "Citizenship / nationality *"; frontend options are generated from country-of-birth options, with `United Kingdom` replaced by `British`, `English`, `Scottish`, `Welsh`, and `Northern Irish`.

The concepts are mostly separated in data, but the user-facing wording is slightly muddled. "Country" should describe residence or birth country. "Nationality" should not read like another country selector; it is a self-described identity that can differ from residence and birthplace.

## Recommended Option Set

### Country of Residence

Use the same country source as country of birth, but treat it as a residence field:

- Label: "Country of residence *"
- Placeholder: "Search countries"
- Stored value: prefer stable country enum/code, with display label derived from options.
- Keep city and region optional.
- Consider a future structured subnational region field (state/province/region) per country instead of free-text if regional breakdowns become core to reporting. Do not hard-code one country's administrative units.

Recommended first-pass options:

- All ISO 3166-1 countries, using common English short names.
- Add user-facing aliases in search only, not as separate stored values. Examples: "UK" -> United Kingdom, "USA" -> United States, "UAE" -> United Arab Emirates.
- Do not mix nationality labels such as British or English into residence.

### Country of Birth

Keep this as a country list:

- Label: "Country of birth *"
- Placeholder: "Search countries"
- Stored value: country enum/code.
- Option source: same canonical country table as residence.

Recommended additions/cleanup:

- Align frontend values with backend enum values. Current examples to check: frontend has `NORTH_KOREA` and `SOUTH_KOREA`; backend `CountryOfBirth` uses `KOREA_NORTH` and `KOREA_SOUTH`.
- Add missing backend-supported countries/territories to the frontend where intended: Kosovo and Palestine appear in backend `CountryOfBirth` but not the frontend country list.
- Add `COTE_DIVOIRE` to frontend or backend consistently. The frontend label exists as "Cote d'Ivoire" but the backend country-of-birth enum currently does not include that value.

### Nationality

The product requirement says this should be "nationality", not "country". I recommend choosing one of two meanings and wording accordingly:

| Meaning | UI label | Control | Example options |
| --- | --- | --- | --- |
| Self-described national identity | "Nationality" | Multi-select preferred | British, English, Scottish, Welsh, Northern Irish, Irish, French, Nigerian, Indian, Pakistani, Polish, Romanian, American, Canadian, Australian, Other |
| Legal citizenship | "Citizenship" | Multi-select preferred | British citizen, Irish citizen, French citizen, Nigerian citizen, Indian citizen, Pakistani citizen, Polish citizen, Romanian citizen, United States citizen, Other |

For this app, use national identity language unless there is a legal/compliance reason to ask citizenship. It is more natural for sentiment aggregation and less likely to feel like immigration paperwork.

Recommended global demonym list:

- Demonyms for all supported countries, searchable globally, with none privileged over others (for example British, Irish, French, Nigerian, Indian, American, and so on).
- Where a country has widely-used internal national identities (for example English, Scottish, Welsh, Northern Irish), include them alongside the country demonym.
- Add "Another nationality" if the enum cannot cover all cases immediately.
- Do not add "Prefer not to say" (see ADR-016); nationality is a required real choice.

Important: country names can be acceptable as implementation values, but the labels should be demonyms where they are familiar. For example, show "French", not "France"; "Nigerian", not "Nigeria"; "United States / American" if needed for clarity.

## Wording and UX Notes

Use one clear question per concept:

- Residence: "Where do you live now?"
- Country of birth: "Where were you born?"
- Nationality: "How would you describe your nationality?"

Suggested helper copy:

- Residence: "Used for broad geographic comparisons, never to show your exact location."
- Country of birth: "This may be different from where you live now."
- Nationality: "Choose the nationality or national identity you use for yourself."

For Northern Ireland and mixed-identity users, nationality should allow more than one answer. ONS guidance says national identity is self-defined and recommends a multi-response approach in Northern Ireland so people are not forced to choose between British, Irish, and Northern Irish identities.

Avoid using "country" as shorthand for nationality. A user can live in the UK, have been born in India, and identify as British and Indian; those should be captured as separate facts.

## Implementation Notes

- Keep editing fields distinct in code even if backend names remain temporarily: `country` should become `countryOfResidence` in a future DTO/migration, while `citizenship` should become `nationality` if the product is asking identity rather than legal citizenship.
- If renaming backend fields is too much for the next implementation pass, change the UI first: label the current `citizenship` field as "Nationality" and document that the payload key is legacy.
- Convert nationality to multi-select if possible. If not, add clear copy such as "Choose the one you most identify with for comparisons" and plan a migration later.
- Use one canonical country dataset for residence and birth country, ideally ISO 3166-1/UN short names, with search aliases for common abbreviations.
- Add a separate `NATIONALITY_OPTIONS` dataset instead of deriving labels directly from country names. Derivation is fine for values, but labels should be demonyms or identity labels.
- Fix backend enum consistency before broadening the frontend options. `CountryOfBirth.fromValue` currently appears to return `Race.valueOf(...)`, which should be checked before relying on JSON enum parsing.
- Do not surface granular residence fields in vote breakdowns without k-anonymity suppression. Country-level buckets are usually safe; city + region + country-of-birth + nationality can become too identifying.

## Sources

- Office for National Statistics, "Ethnic group, national identity and religion": https://www.ons.gov.uk/methodology/classificationsandstandards/measuringequality/ethnicgroupnationalidentityandreligion
- ISO, "ISO 3166 - Country Codes": https://www.iso.org/iso-3166-country-codes.html
- GOV.UK, "Types of British nationality": https://www.gov.uk/types-of-british-nationality

# User Characteristics Recommendations

This folder captures research-backed recommendations for improving the user-characteristics sign-up options and flow. Each characteristic area has its own file with a summary table at the top.

| File | Focus | Highest-priority recommendations |
| --- | --- | --- |
| [question-flow.md](question-flow.md) | Overall onboarding order | Split "Body & finances"; place "Quirky questions" between body and finance; rename the user-facing nationality field. |
| [location-nationality.md](location-nationality.md) | Residence, country of birth, nationality | Treat residence, birth country, and nationality as separate concepts; use "Nationality" in the UI; consider multi-select nationality. |
| [income-finance.md](income-finance.md) | Personal and household income | Split the 20k-50k band; keep income bands broad at the high end; add clearer before-tax wording. |
| [body-health.md](body-health.md) | Height, weight, eye colour, sex at birth, disability/neurodiversity wording | Separate body from finance; fix the current frontend/backend height mismatch; keep sensitive body fields broad. |
| [identity-demographics.md](identity-demographics.md) | Age, gender, ethnicity, sexual orientation, relationship status | Ask current age as a number (min 16, stores birth year so it auto-increments); use globally recognisable ethnicity groups; keep sex-at-birth Female/Male only; clean up dated enum naming. No prefer-not-to-say. |
| [beliefs-politics.md](beliefs-politics.md) | Religion, religiosity, political persuasion | Keep the actual religion names; reword religiosity by importance; add `Not political` and `Not sure` to politics. No prefer-not-to-say. |
| [education-work.md](education-work.md) | Education, work status, employment sector, university subject | Expand qualification/work-status options; align sectors to standard industry groups; gate university subject. |
| [family-lifestyle.md](family-lifestyle.md) | Parent/caregiver status, pets, chronotype, outlook | Stop deriving mum/dad from sex at birth; expand pet types; use clearer chronotype and outlook labels. |
| [news-habits.md](news-habits.md) | News frequency, balanced viewpoint, mainstream/social mix, representative-data belief | Use anchored frequency bands; keep balanced viewpoint a yes/no (not an agreement scale); define source categories; submit all collected news fields. |
| [housing-property.md](housing-property.md) | Housing tenure and accommodation/property type | Rename page to Housing; expand tenure; ask accommodation type for all relevant users, not only owners. |

The main implementation theme is to collect enough detail for useful aggregate analysis while keeping every characteristic a clear, required choice. `Prefer not to say` is never an option (see ADR-016), and options should be internationally recognisable so users in any country can identify with them.

## Field Coverage

Every field in `OnboardingForm` is covered by at least one recommendation file.

| Form field | Covered in |
| --- | --- |
| `country` | [location-nationality.md](location-nationality.md) |
| `city` | [location-nationality.md](location-nationality.md) |
| `region` | [location-nationality.md](location-nationality.md) |
| `ukCounty` | [location-nationality.md](location-nationality.md) |
| `urbanRural` | [location-nationality.md](location-nationality.md) |
| `ageRange` | [identity-demographics.md](identity-demographics.md) |
| `gender` | [identity-demographics.md](identity-demographics.md) |
| `sexAtBirth` | [identity-demographics.md](identity-demographics.md), [body-health.md](body-health.md) |
| `sexualOrientation` | [identity-demographics.md](identity-demographics.md) |
| `maritalStatus` | [identity-demographics.md](identity-demographics.md) |
| `raceSelections` | [identity-demographics.md](identity-demographics.md) |
| `countryOfBirth` | [location-nationality.md](location-nationality.md) |
| `citizenship` | [location-nationality.md](location-nationality.md) |
| `religion` | [beliefs-politics.md](beliefs-politics.md) |
| `religiosity` | [beliefs-politics.md](beliefs-politics.md) |
| `politicalPersuasion` | [beliefs-politics.md](beliefs-politics.md) |
| `education` | [education-work.md](education-work.md) |
| `occupation` | [education-work.md](education-work.md) |
| `employmentSector` | [education-work.md](education-work.md) |
| `universitySubject` | [education-work.md](education-work.md) |
| `personalIncomeRange` | [income-finance.md](income-finance.md) |
| `householdIncomeRange` | [income-finance.md](income-finance.md) |
| `height` | [body-health.md](body-health.md) |
| `weightRange` | [body-health.md](body-health.md) |
| `eyeColor` | [body-health.md](body-health.md) |
| `parent` | [family-lifestyle.md](family-lifestyle.md) |
| `newsFrequencyScore` | [news-habits.md](news-habits.md) |
| `hasPet` | [family-lifestyle.md](family-lifestyle.md) |
| `petType` | [family-lifestyle.md](family-lifestyle.md) |
| `chronotype` | [family-lifestyle.md](family-lifestyle.md) |
| `outlook` | [family-lifestyle.md](family-lifestyle.md) |
| `neurodivergent` | [body-health.md](body-health.md) |
| `neurodivergenceType` | [body-health.md](body-health.md) |
| `hasDisability` | [body-health.md](body-health.md) |
| `disabilityType` | [body-health.md](body-health.md) |
| `housingStatus` | [housing-property.md](housing-property.md) |
| `propertyType` | [housing-property.md](housing-property.md) |
| `balancedNewsViewpoint` | [news-habits.md](news-habits.md) |
| `mainstreamNewsPercent` | [news-habits.md](news-habits.md) |
| `betterWorldWithData` | [news-habits.md](news-habits.md) |

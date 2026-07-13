# User Characteristics Question Flow

| Area | Current state | Recommended change | Why it matters |
| --- | --- | --- | --- |
| Body and finance grouping | Step 7 combines height, weight, currency, income, eye colour, and parent status under "Body & finances". | Split into separate "Body" and "Finances" steps. | Body data and money data are both sensitive; separating them makes the flow clearer and less blunt. |
| Serious-question pacing | "Quirky questions" comes after the combined body/finance page. | Place "Quirky questions" between "Body" and "Finances". | Gives users a lighter step between two high-friction topics, matching the explicit product note. |
| Nationality wording | Step 4 asks "Citizenship / nationality" and state is stored as `citizenship`. | Label the user-facing field "Nationality" and use citizenship only where the data model truly means legal citizenship. | Nationality, citizenship, country of birth, and country of residence are distinct concepts; clearer wording reduces bad data. |
| News habits placement | News habits currently follows quirky questions and precedes neurodiversity/disability. | Keep news habits after finances, before neurodiversity/disability. | It reconnects the setup to the product purpose before the final sensitive health/disability questions. |
| Privacy framing | Every step shows the privacy note; subtitles repeat aggregate use. | Keep persistent privacy copy and avoid exact location/income/body values unless aggregation needs them. | Survey guidance consistently treats age, sex/gender, race/ethnicity, religion, income, disability, and similar items as sensitive. |
| Completion burden | The current flow has 11 steps. | Accept 12 steps after splitting body and finances, or combine lower-risk fields elsewhere only if completion analytics show drop-off. | A slightly longer flow is preferable to one overloaded sensitive page. |

## Current State

`frontend/mobile/your-say-news/features/user-characteristics/components/OnboardingScreen.tsx` defines `STEP_META` with 11 steps:

1. Where in the world?
2. A little about you
3. How you identify
4. Your background
5. Beliefs & politics
6. Education & work
7. Body & finances
8. Quirky questions
9. News habits
10. Neurodiversity & disability
11. Property

The rendered question placement mostly follows that order. The main issue is step 7: it combines height, weight, currency, personal income, household income, eye colour, and parent status. That creates a dense page with two different sensitive themes. It also means the lighter "Quirky questions" page cannot act as a buffer between body and finance questions.

## Proposed Step Order

1. **Where in the world?** Country or region lived in, optional city, optional region/state/county, settlement type.
2. **A little about you** Current age (number, min 16), gender, sex registered at birth.
3. **How you identify** Race/ethnicity, sexual orientation, relationship status.
4. **Your background** Country of birth, nationality.
5. **Beliefs & politics** Political leaning, religion, religiosity.
6. **Education & work** Highest education, employment status, industry/sector, optional university subject.
7. **Body** Height, weight, eye colour, parent status.
8. **Quirky questions** Pets, pet type, chronotype, outlook.
9. **Finances** Currency, annual personal income, annual household income.
10. **News habits** Balanced viewpoint, mainstream/social mix, news frequency, representative-data belief.
11. **Neurodiversity & disability** Neurodivergence and disability questions with conditional follow-ups.
12. **Property** Housing status and property type follow-up.

## Rationale

The flow should feel like a coherent profile setup rather than a long demographics form. The first six steps gather location, identity, background, beliefs, and socioeconomic context. The middle section should avoid stacking the most personal topics together: body data, income, neurodiversity, disability, and property all need breathing room.

Splitting "Body & finances" is the most important flow change. A body page can use clear physical-characteristic labels. A finance page can focus on currency and income bands without sharing space with weight or height. Putting quirky questions between them makes the transition less severe and gives the user a small sense of variety before answering money questions.

The flow should also distinguish country-like fields:

- **Country or region you live in**: current residence for geographic aggregation.
- **Country of birth**: birthplace, not identity.
- **Nationality**: the identity/legal-nationality field the user explicitly called out. Avoid presenting it as just "country".

Research notes used for this recommendation:

- Nielsen Norman Group treats demographic items such as age, sex, gender, race/ethnicity, religion, and income as potentially sensitive and recommends careful wording and context: <https://www.nngroup.com/articles/sensitive-questions/>.
- Pew Research Center questionnaire guidance emphasizes deliberate question wording and question order, especially when measuring attitudes and identity over time: <https://www.pewresearch.org/writing-survey-questions/>.
- ONS guidance separates ethnic group, national identity, religion, country of birth, nationality, and passports held, which supports keeping these labels distinct: <https://www.ons.gov.uk/peoplepopulationandcommunity/populationandmigration/internationalmigration/methodologies/guidanceonusingcountryofbirthnationalityandpassportshelddata>.

## Implementation Notes

- Update `STEP_META` to 12 entries and rename "Body & finances" to "Body".
- Move the current quirky step from index 7 to index 7 after body, then add a new finance step at index 8.
- Move `currency`, `personalIncomeRange`, and `householdIncomeRange` out of the body render block into the new finance render block.
- Update the step comments near the state declarations from `// Finances & body` to separate `// Body`, `// Finances`, and `// Quirky questions` sections.
- Change the user-facing label from "Citizenship / nationality *" to "Nationality *". If the backend field remains `citizenship`, document that mapping so analytics users do not confuse it with residence or country of birth.
- Keep income as bands, not exact values. When income options are updated, split the current `20k-50k` band into narrower bands because this range covers very different lived circumstances. A practical first pass is `20k-30k`, `30k-40k`, and `40k-50k`, with enum names that preserve ordering.
- Do not add "Prefer not to say" to any page (see ADR-016). Every characteristic is a required real choice; keep the privacy note persistent and use broad bands.
- Do not report combinations that create tiny groups. The UI can collect more detail than dashboards expose, but aggregate outputs should enforce minimum group sizes before showing a characteristic breakdown.

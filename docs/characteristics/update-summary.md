# Characteristics Update Summary

This is the implementation-oriented summary of characteristics that need updating after the research pass.

## Flow and Grouping

- Split the current `Body & finances` step into separate `Body basics` and `Finances` steps.
- Move `Quirky questions` between `Body basics` and `Finances` to break up sensitive sections.
- Rename the final `Property` step to `Housing`.
- Move parent/caregiver status out of body/finance and into a family/household context.
- Keep `News habits` after finances and before neurodiversity/disability.

## Location and Nationality

- Rename `Country or region you live in` to `Country of residence`.
- Store country of residence as a stable code/enum instead of only a display label.
- Keep `Country of birth` as a distinct country-list field.
- Rename `Citizenship / nationality` to `Nationality` in the UI.
- Consider renaming the backend/payload field from `citizenship` to `nationality` if the product means identity rather than legal citizenship.
- Make nationality multi-select if possible.
- Add Irish prominently alongside British, English, Scottish, Welsh, and Northern Irish.
- Create a dedicated `NATIONALITY_OPTIONS` list with demonym-style labels instead of deriving directly from country names.
- Add search aliases for common country abbreviations such as UK, USA, and UAE.
- Align frontend and backend country values, including Korea naming, Kosovo, Palestine, and Cote d'Ivoire.

## Identity and Demographics

- Ask for current age as a number (minimum 16), not an age band. Store the derived birth year so age auto-increments each year; derive reporting bands (`16-17`, `18-19`, `20-24`, … `85+`) from the computed age. Keep exact DOB out of characteristics.
- `Prefer not to say` is never an option on any characteristic (see ADR-016).
- Add a self-describe gender option and wire the existing `genderSelfDescribe` field instead of always submitting it empty.
- Rename `Sex assigned at birth` to `Sex registered at birth`.
- There is no intersex option for sex registered at birth: keep it Female / Male only.
- Replace the `HOMOSEXUAL` enum with a clearer `GAY_LESBIAN` value, with migration/compatibility handling.
- Add sexual-orientation options for queer and questioning/unsure, plus self-describe.
- Rename `Race / ethnicity` to `Ethnic background`.
- Replace the current US-leaning race list with broad, globally recognisable ethnicity groups (no country-specific census subgroups) that people in any country can identify with.
- Separate legal marital/civil partnership status from relationship status, or clearly choose one framing.
- Add relationship-status options for cohabiting/living with partner, separated, dissolved civil partnership, and surviving civil partner.

## Beliefs and Politics

- Keep the actual religion names (Christianity, Islam, Hinduism, Buddhism, Judaism, Sikhism, Other religion, No religion); do not relabel them to census-style adjectives.
- Reword religiosity options to match the question: not at all important, not very important, somewhat important, very important.
- Keep political leaning as a broad left/right scale, not party-specific.
- Add `Not political` and `Not sure` to political persuasion (no `Prefer not to say`).


## Education and Work

- Rename user-facing `occupation` to `Current work or study status`.
- Expand education using internationally recognisable levels (no GCSE/A-level/T-level or other country-specific wording): no formal qualifications, primary/basic schooling, secondary school, vocational/technical qualification, higher education below degree, bachelor's degree, master's degree, doctorate, other, not sure.
- Rename `No formal education` to `No formal qualifications`.
- Add work-status options for carer/homemaker, unable to work because of health/disability, casual/gig/temporary work, unemployed and looking, and not working/not looking.
- Replace `Employed full-time / studying part-time` with a broader `Working and studying`.
- Align employment sectors to broad, globally recognisable industry groups.
- Add missing employment sectors such as mining/quarrying, real estate, professional services, admin/support, social care, and personal services.
- Gate university subject behind higher-education answers instead of offering `N/A` as a subject.
- Broaden university subjects into broad, internationally recognisable subject groups.
- Add the missing frontend `Architecture`/architecture-building-planning option to match backend coverage.
- Rename `Theater` to `Theatre / drama`.

## Income and Finance

- Keep separate personal and household income fields.
- Reword the labels to `Your annual personal income before tax` and `Total annual household income before tax`.
- Split `20k-50k` into `20k-30k`, `30k-40k`, and `40k-50k`.
- Consider splitting `50k-100k` into `50k-75k` and `75k-100k`.
- Keep very high income bands broad
- Prefer clear labels such as `Under {currency} 20k` and `{currency} 1M or more` instead of `<20k` and `1M+`.
- Add finance-specific reassurance copy near the income fields.
- Add migration/legacy handling for existing `BETWEEN_20K_AND_50K` values, which cannot be accurately split after the fact.
- Consider whether to rename `BETWEEN_151K_AND_200K` to `BETWEEN_150K_AND_200K`; if not, update the label only.

## Body and Health

- Fix the frontend/backend height mismatch before shipping:
  - Replace frontend `FEET_5_0_TO_5_3` with backend `FEET_5_1_TO_5_3`.
  - Split frontend `FEET_6_7_TO_7_0` into `FEET_6_7_TO_6_9` and `FEET_6_10_TO_7_0`.
  - Add missing frontend `FEET_4_10_TO_5_0`, or intentionally update backend/frontend to a single chosen banding.
- Add metric equivalents alongside imperial height labels.
- Keep weight in broad bands; consider adding `Under 40 kg`.
- Add eye-colour options for amber, black/very dark brown, and other/not sure.
- Standardise the eye-colour label on `Grey`.
- Keep neurodiversity and disability separate from body and finance.
- Reword neurodiversity around `neurodivergent, neurodiverse, or learning difference`.
- Consider making neurodivergence type multi-select.
- Reword disability around long-term condition, illness, impairment, or day-to-day activity limitation.
- Consider making disability type multi-select.


## Family and Lifestyle

- Replace inferred `MUM`/`DAD` parent status with direct parent/caregiver options.
- Use `Are you a parent or caregiver?` instead of `Are you a parent?`.
- Add parent/caregiver options for not a parent/caregiver, parent/caregiver of child under 18, parent/caregiver of adult child only, and expecting/soon to be parent.
- Prefer multi-select pet types.
- Add pet types for rabbit, small mammal, horse/pony, amphibian, invertebrate, and other.
- Add `Multiple pet types` if pet type must remain single-select.
- Relabel chronotype to `Mostly morning`, `Mostly evening/night`, and `Mixed / depends`.
- Reword outlook to `Mostly optimistic`, `Mostly pessimistic`, and `Mixed / depends`.
- Consider adding `Unsure` to outlook.

## News Habits

- Replace or anchor the 1-10 news-frequency scale with clear frequency bands.
- Keep the balanced-news viewpoint as a yes/no question (not an agreement scale).
- Reword the balanced-news question as `Do you regularly see more than one viewpoint on the news stories you follow?`.
- Define `mainstream news` and `social media` near the slider.
- Bucket mainstream/social results for aggregation rather than exposing exact percentages.
- Reword the representative-data belief question to avoid leading the user toward the product premise.
- Use a neutral statement such as `Representative public opinion data can help people understand society better`.
- Persist all news-habit fields if they remain required in onboarding.

## Housing and Property

- Rename `Property` to `Housing`.
- Replace `LIVE_WITH_PARENTS`, `RENT`, and `OWN` with clearer tenure buckets.
- Add tenure options for own outright, own with mortgage, shared ownership, private rent, social rent, live with parents/family, live rent-free, student/university accommodation, temporary/no fixed address, and other.
- Ask accommodation/property type for everyone with a current home, not only owners.
- Rename `Do you own a house or a flat?` to `What type of home do you live in?`.
- Expand property type beyond `HOUSE` and `FLAT`.
- Add property/accommodation options for detached, semi-detached, terraced, flat/apartment, room in shared house, student halls, mobile/temporary home, and other/unknown.
- Treat temporary/no-fixed-address responses as sensitive and suppress small result buckets.

## Implementation Gaps Found

- `balancedNewsViewpoint`, `mainstreamNewsPercent`, and `betterWorldWithData` are collected and validated but are not included in `CharacteristicAnswers`.
- `CountryOfBirth.fromValue` appears to return `Race.valueOf(...)`; this should be checked and fixed before relying on enum parsing.
- Frontend `COUNTRY_OF_BIRTH_OPTIONS` and backend `CountryOfBirth` have inconsistent values for several countries/territories.
- Frontend height options include values not present in the backend `Height` enum.
- Backend `UniversitySubject` includes `ARCHITECTURE`, but the frontend subject options do not expose it.
- `genderSelfDescribe` exists but is always submitted as an empty string.
- Parent status is currently derived as `MUM` or `DAD` from sex at birth, which should be replaced with direct user-selected parent/caregiver status.

# Body & Health Characteristics

| Area | Current state | Recommended change | Priority | Implementation impact |
| --- | --- | --- | --- | --- |
| Step grouping | Body, income, eye colour and parent status are all on "Body & finances". | Split into separate "Body basics" and "Finances" steps, with "Quirky questions" between them to reduce sensitivity fatigue. Move parent status away from body, ideally to family/household. | High | Frontend step order/copy change; no data model change required. |
| Height | Frontend has 10 bands; backend has 12. Two frontend values do not match backend enum values. | Align frontend to backend and show metric equivalents alongside imperial labels. | High | Fix `HEIGHT_OPTIONS` labels/values; no schema change if backend enum remains. |
| Weight | Current 10 kg bands from 30 kg to 150+ kg are usable but abrupt and sensitive. | Keep broad 10 kg bands and clarify that this is grouped for aggregate analysis. Add `Under 40 kg` at the low end. | Medium | Requires an `Under 40 kg` enum value if adopted. |
| Eye colour | Brown, blue, green, hazel, gray. | Add `Amber`, `Black / very dark brown`, and `Other / not sure`; standardise the label on `Grey`. | Medium | Requires backend `EyeColor` enum expansion and frontend option update. |
| Sex registered at birth | Required; Male/Female only. | Reword to "Sex registered at birth" and keep it Female / Male only — no intersex option and no "Prefer not to say". | High | Label change only; belongs in the identity step rather than body. |
| Disability | Separate later step: yes/no, then one disability type. | Keep away from body/finance. Reword around long-term condition and day-to-day activity limitation; allow multi-select impairment/disability types later. | Medium | Current backend supports only one `DisabilityType`; multi-select needs schema/API change. |
| Neurodiversity | Separate later step: yes/no, then one type. | Keep separate from body. Consider "neurodivergent, neurodiverse, or have a learning difference" wording; allow multi-select if used for segmentation. | Medium | Current backend supports only one `NeurodivergenceType`; multi-select needs schema/API change. |
| Privacy/aggregation | Persistent privacy note exists, but body fields are required and grouped with income. | Add short local copy on sensitive steps: "Shown only in broad groups once enough people have answered." Avoid exact measurements. | High | Copy/front-end only unless adding minimum cohort thresholds elsewhere. |

## Current State

The live onboarding screen currently uses an eleven-step wizard. Step 7 is titled "Body & finances" and contains height, weight, currency, personal income, household income, eye colour and parent status. This mixes two sensitive categories and makes the step feel heavier than necessary.

Current body-related option sets:

- `SEX_AT_BIRTH_OPTIONS`: Male, Female.
- `HEIGHT_OPTIONS`: 4'0"-4'4", 4'5"-4'9", 5'0"-5'3", 5'4"-5'6", 5'7"-5'9", 5'10"-6'0", 6'1"-6'3", 6'4"-6'6", 6'7"-7'0", 7'1"+.
- `WEIGHT_OPTIONS`: 30-39 kg through 140-149 kg, then 150+ kg.
- `EYE_COLOR_OPTIONS`: Brown, Blue, Green, Hazel, Gray.
- Disability and neurodiversity are already separated into a later step, which is a better grouping than placing them under body.

There is one concrete mismatch to fix: the backend `Height` enum includes `FEET_4_10_TO_5_0`, `FEET_5_1_TO_5_3`, `FEET_6_7_TO_6_9`, and `FEET_6_10_TO_7_0`, but the frontend uses `FEET_5_0_TO_5_3` and `FEET_6_7_TO_7_0`. Those frontend values will not deserialize against the current backend enum.

## Recommended Option Set

### Body Basics Step

Recommended step title: `Body basics`

Recommended subtitle: `Broad physical traits, used only for anonymous group comparisons.`

Fields:

| Field | Recommended options |
| --- | --- |
| Height | Under 4'10" / under 147 cm; 4'10"-5'0" / 147-152 cm; 5'1"-5'3" / 155-160 cm; 5'4"-5'6" / 163-168 cm; 5'7"-5'9" / 170-175 cm; 5'10"-6'0" / 178-183 cm; 6'1"-6'3" / 185-191 cm; 6'4"-6'6" / 193-198 cm; 6'7"-6'9" / 201-206 cm; 6'10"-7'0" / 208-213 cm; 7'1"+ / 216 cm+ |
| Weight | Under 40 kg; 40-49 kg; 50-59 kg; 60-69 kg; 70-79 kg; 80-89 kg; 90-99 kg; 100-109 kg; 110-119 kg; 120-129 kg; 130-139 kg; 140-149 kg; 150 kg+ |
| Eye colour | Brown; Blue; Green; Hazel; Grey; Amber; Black / very dark brown; Other / not sure |

If the product wants minimum backend change, keep the existing weight enum and add only the missing height alignment. If the product is willing to change the enum, add `KG_BELOW_40` instead of forcing lighter users into `30-39 kg`.

### Identity Step

Sex registered at birth is more sensitive than height or eye colour and overlaps with gender identity. Keep it near gender, not in body.

Recommended field label: `Sex registered at birth *`

Recommended options:

- Female
- Male

Keep it Female / Male only. There is no intersex option and no "Prefer not to say" (see ADR-016). It is a required real choice.

### Neurodiversity & Disability Step

Keep this separate from body and finance. The current separation is good; the copy should be clearer and less diagnosis-heavy.

Recommended labels:

- `Are you neurodivergent, neurodiverse, or do you have a learning difference?`
- `Which apply?` if changing to multi-select.
- `Do you have a long-term physical or mental health condition, illness, impairment, or disability?`
- `Does it reduce your ability to carry out day-to-day activities?` if following a more formal disability measure.

Recommended disability buckets if keeping a single follow-up:

- Mobility or dexterity
- Vision
- Hearing
- Learning, understanding or concentrating
- Memory
- Mental health
- Stamina, breathing or fatigue
- Social or behavioural
- Long-term pain or chronic illness
- Other

This should stay broad and non-medical. Avoid asking for diagnoses unless there is a clear product need and consent model.

## Wording & UX Notes

Use neutral, low-friction wording. Height, weight, sex at birth, disability and income are all sensitive in different ways, so the UI should make it obvious that users are choosing broad buckets rather than exposing precise details.

Recommended flow around the current concern:

1. Body basics
2. Quirky questions
3. Finances
4. News habits
5. Neurodiversity & disability
6. Property

This places a lighter step between body and finance, and keeps disability/neurodiversity away from appearance-focused body traits. If finance needs to appear earlier for completion logic, still avoid the same screen as body.

Suggested microcopy:

- Body basics subtitle: `Broad physical traits, used only for anonymous group comparisons.`
- Weight helper: `Choose the closest range. We never show exact measurements.`
- Sex registered at birth helper: `This is separate from gender and is only used in aggregate.`
- Disability helper: `Answer in the way that best reflects your everyday experience.`

Every field is a required real choice. There is no `Skip` or `Prefer not to say` on any characteristic (see ADR-016); keep the copy honest and mark required fields required.

## Implementation Notes

- Create a standalone body step and a standalone finance step in `OnboardingScreen.tsx`; update `STEP_META`, `TOTAL_STEPS`, step indexes and comments together.
- Move the existing "Quirky questions" step between body and finance as requested.
- Move parent status out of the body/finance step. It is a family/household characteristic, not a body trait.
- Fix the height frontend/backend mismatch before shipping the current flow:
  - Replace frontend `FEET_5_0_TO_5_3` with backend `FEET_5_1_TO_5_3`.
  - Split frontend `FEET_6_7_TO_7_0` into backend `FEET_6_7_TO_6_9` and `FEET_6_10_TO_7_0`.
  - Add the missing frontend `FEET_4_10_TO_5_0` band, or intentionally update the backend to match the desired product bands.
- Add backend enum values before adding new frontend options for eye colour, sex at birth, disability or neurodiversity.
- Consider minimum cohort thresholds for any published breakdown using sensitive axes. The collection UI already avoids PII, but aggregation needs guardrails so rare combinations are not exposed.

## Research Notes

- The UK Government Analysis Function disability harmonised guidance frames disability through long-term conditions and day-to-day activity limitation, rather than asking only for a diagnosis or label: https://analysisfunction.civilservice.gov.uk/policy-store/measuring-disability-for-the-equality-act-2010/
- The Government Statistical Service review notes that impairment categories should describe activities or functions affected, and that users may misunderstand diagnosis-style categories: https://analysisfunction.civilservice.gov.uk/policy-store/review-of-disability-data-harmonised-standards/
- ONS/GSS gender identity guidance treats gender identity separately from sex registered at birth, supporting keeping sex-at-birth wording separate from gender copy: https://analysisfunction.civilservice.gov.uk/policy-store/gender-identity/
- CDC NHANES uses body measurements such as height and weight for population-level analysis, supporting broad grouped body measures but not exact app-level disclosure: https://wwwn.cdc.gov/Nchs/Data/Nhanes/Public/2021/DataFiles/BMX_L.htm
- Cleveland Clinic lists six common eye colour categories: amber, blue, brown, gray, green and hazel. That supports adding amber and keeping grey/gray as a standard category: https://my.clevelandclinic.org/health/articles/21576-eye-colors

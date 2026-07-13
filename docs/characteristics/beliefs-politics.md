# Beliefs and Politics Characteristics

| Characteristic | Current state | Recommended change | Why / source basis | Implementation impact |
| --- | --- | --- | --- | --- |
| Religion | `Christianity`, `Islam`, `Hinduism`, `Buddhism`, `Judaism`, `Sikhism`, `Other religion`, `No religion`. Required. | Keep the actual religion names as-is; do not relabel to census-style adjectives. Consider reordering to put `No religion` first. | Real religion names are clear and internationally recognisable. | No option changes; only optional reordering. |
| Religiosity | `Not religious`, `Slightly`, `Moderately`, `Very`. Required. UI label is `How important is religion to you?`. | Reword options to match the question: `Not at all important`, `Not very important`, `Somewhat important`, `Very important`. | The field measures importance, so the labels should be importance phrases. | Enum rename/migration recommended because current enum names mix identity wording with importance wording. |
| Political persuasion | `Left`, `Centre-left`, `Centre`, `Centre-right`, `Right`, `Apolitical`. Required. UI label is `Political leaning`. | Keep the five-point left-right scale, but add `Not political` and `Not sure`. Do not add `Prefer not to say`. | Left-right self-placement is broad and internationally comparable; `Not sure` captures genuine uncertainty. | Add enum value for `NOT_SURE`; optionally rename `APOLITICAL` to visible label `Not political`. |
| Privacy / aggregation | All three fields are collected as required signup characteristics and used as public breakdown axes. | Treat these as sensitive. Suppress small buckets and avoid publishing rare combinations such as religion + ethnicity + location + political leaning when counts are low. | Religion or belief is a protected characteristic; politics and religion can be sensitive in public contexts. | Aggregation layer should enforce minimum group sizes and combine rare buckets into `Not enough responses` or broader groups. |

## Current State

The onboarding wizard currently asks beliefs and politics on step 4, after nationality/country of birth and before education/work.

Code checked:

- `frontend/mobile/your-say-news/features/user-characteristics/data/options.ts`
- `frontend/mobile/your-say-news/features/user-characteristics/components/OnboardingScreen.tsx`
- `user-service/src/main/java/com/yoursay/usercharacteristic/model/Enums/Religion.java`
- `user-service/src/main/java/com/yoursay/usercharacteristic/model/Enums/Religiosity.java`
- `user-service/src/main/java/com/yoursay/usercharacteristic/model/Enums/PoliticalPersuasion.java`

Current frontend options:

### Religion

| Label | Value |
| --- | --- |
| Christianity | `CHRISTIANITY` |
| Islam | `ISLAM` |
| Hinduism | `HINDUISM` |
| Buddhism | `BUDDHISM` |
| Judaism | `JUDAISM` |
| Sikhism | `SIKHISM` |
| Other religion | `OTHER_RELIGION` |
| No religion | `NO_RELIGION` |

### Religiosity

| Label | Value |
| --- | --- |
| Not religious | `NOT_RELIGIOUS` |
| Slightly | `SLIGHTLY_RELIGIOUS` |
| Moderately | `MODERATELY_RELIGIOUS` |
| Very | `VERY_RELIGIOUS` |

### Political Persuasion

| Label | Value |
| --- | --- |
| Left | `LEFT` |
| Centre-left | `CENTRE_LEFT` |
| Centre | `CENTRE` |
| Centre-right | `CENTRE_RIGHT` |
| Right | `RIGHT` |
| Apolitical | `APOLITICAL` |

The current sets are usable for an MVP. The main issues are wording consistency, missing non-disclosure/uncertainty options, and the religiosity labels not matching the "How important is religion to you?" question.

## Recommended Option Set

### Religion

Recommended question label:

`What is your religion?`

Recommended helper copy, if a short explanation is allowed:

`Choose the religion you identify with, if any.`

Recommended options:

| Label | Suggested enum | Notes |
| --- | --- | --- |
| No religion | `NO_RELIGION` | Consider putting first. |
| Christianity | `CHRISTIANITY` | Keep the actual religion name. |
| Islam | `ISLAM` | Keep the actual religion name. |
| Hinduism | `HINDUISM` | Keep the actual religion name. |
| Buddhism | `BUDDHISM` | Keep the actual religion name. |
| Judaism | `JUDAISM` | Keep the actual religion name. |
| Sikhism | `SIKHISM` | Keep the actual religion name. |
| Other religion | `OTHER_RELIGION` | Keep. |

Do not split religions into denominations in the main signup unless there is a specific product need; keep top-level reporting stable and privacy-preserving.

### Religiosity

Recommended question label:

`How important is religion or faith in your life?`

Recommended options:

| Label | Suggested enum | Notes |
| --- | --- | --- |
| Not at all important | `NOT_AT_ALL_IMPORTANT` | Clearer than `Not religious` because the field measures importance, not affiliation. |
| Not very important | `NOT_VERY_IMPORTANT` | Fills the current gap between "not" and "slightly". |
| Somewhat important | `SOMEWHAT_IMPORTANT` | Common survey wording and easier to understand than `Moderately`. |
| Very important | `VERY_IMPORTANT` | Current `Very` should become a complete phrase. |

If enum churn needs to be minimised, keep the current enum values initially and only change labels:

| Current enum | Better label |
| --- | --- |
| `NOT_RELIGIOUS` | Not at all important |
| `SLIGHTLY_RELIGIOUS` | Not very important |
| `MODERATELY_RELIGIOUS` | Somewhat important |
| `VERY_RELIGIOUS` | Very important |

Longer term, rename the enum values so saved data reads like the measured concept. The current names imply "how religious are you?", while the UI asks about importance.

### Political Persuasion

Recommended question label:

`Where would you place yourself politically?`

Recommended helper copy, if needed:

`Use your overall political leaning, not necessarily a party you vote for.`

Recommended options:

| Label | Suggested enum | Notes |
| --- | --- | --- |
| Left | `LEFT` | Keep existing. |
| Centre-left | `CENTRE_LEFT` | Keep existing. |
| Centre | `CENTRE` | Keep existing. |
| Centre-right | `CENTRE_RIGHT` | Keep existing. |
| Right | `RIGHT` | Keep existing. |
| Not political | `APOLITICAL` or `NOT_POLITICAL` | Better visible wording than `Apolitical`. |
| Not sure | `NOT_SURE` | Useful because many users will not confidently self-place. |

Optional expanded set:

| Label | Suggested enum | When to use |
| --- | --- | --- |
| Far left | `FAR_LEFT` | Only if results volume supports small-bucket privacy. |
| Left | `LEFT` | Existing middle-left option becomes less overloaded. |
| Centre-left | `CENTRE_LEFT` | Existing. |
| Centre | `CENTRE` | Existing. |
| Centre-right | `CENTRE_RIGHT` | Existing. |
| Right | `RIGHT` | Existing middle-right option becomes less overloaded. |
| Far right | `FAR_RIGHT` | Only if moderation/reporting policy is comfortable with this label. |
| Not political | `NOT_POLITICAL` | Separate from ideology. |
| Not sure | `NOT_SURE` | Genuine uncertainty. |

For the first production pass, keep the five-point left-right scale. It is easier to answer and safer to aggregate than a seven-point ideological scale.

## Wording / UX Notes

- Keep religion and religiosity separate. A user can identify with a religion while saying religion is not very important in daily life, and a user with no formal religion may still hold spiritual beliefs.
- Put religion affiliation before religiosity. The importance question makes more sense after the affiliation question.
- Do not ask "Do you practise?" in signup. The GSS guidance notes that practice varies by religion and can introduce bias if asked too broadly.
- Keep the actual religion names (Christianity, Islam, Hinduism, Buddhism, Judaism, Sikhism, Other religion, No religion). Do not swap them for census-style adjectives.
- If `Other religion` later opens a text input, do not expose raw text as a public sentiment bucket. Map to broad groups or suppress until volume is safe.
- Use `Not political` rather than `Apolitical` in the UI. It is plainer language.
- Keep `Not sure` as a genuine-uncertainty answer. Do not add `Prefer not to say` (see ADR-016).
- Avoid party-specific choices in signup. Party support changes more often than broad leaning and is harder to generalise outside the UK.
- Consider a future second political dimension only if the product needs it. UK politics can involve economic left-right and liberal-authoritarian/social dimensions, but adding both in signup may feel too heavy.

## Implementation Notes

- Keep the current religion labels (Christianity, Islam, Hinduism, Buddhism, Judaism, Sikhism, Other religion, No religion). The only immediate label change is `APOLITICAL`: `Not political`.
- Recommended backend enum additions:
  - `Religiosity`: ideally migrate existing values to `NOT_AT_ALL_IMPORTANT`, `NOT_VERY_IMPORTANT`, `SOMEWHAT_IMPORTANT`, `VERY_IMPORTANT`
  - `PoliticalPersuasion`: `NOT_SURE`; optionally `NOT_POLITICAL` as a clearer replacement for `APOLITICAL`
- No characteristic gets a `PREFER_NOT_TO_SAY` value (see ADR-016).
- If renaming enum values, add a migration/backfill path and keep old values readable during the transition.
- Update seeded user characteristics and tests after enum changes. Current tests seed `NO_RELIGION`, `NOT_RELIGIOUS`, and `CENTRE_LEFT`.
- Public vote breakdowns should enforce minimum bucket sizes. For sparse buckets, combine into a broader result or show `Not enough responses`.
- Avoid publishing multi-axis combinations involving religion and politics unless there is a strong privacy threshold. Religion + political leaning + ethnicity + location can identify small communities quickly.

## Sources

- [Government Analysis Function, Religion harmonised standard](https://analysisfunction.civilservice.gov.uk/policy-store/religion/)
- [UK Data Service, Religion in the 2021/22 Census](https://ukdataservice.ac.uk/learning-hub/census/census-explainers/religion/)
- [ONS, Religion, England and Wales: Census 2021](https://www.ons.gov.uk/peoplepopulationandcommunity/culturalidentity/religion/bulletins/religionenglandandwales/census2021)
- [Pew Research Center, How Pew surveys religion](https://www.pewresearch.org/religion/2018/07/05/how-does-pew-research-center-measure-the-religious-composition-of-the-u-s-answers-to-frequently-asked-questions/)
- [European Social Survey, political issues core questionnaire](https://www.europeansocialsurvey.org/sites/default/files/2023-06/ESS_core_questionnaire_political_issues.pdf)
- [British Election Study, ideological landscape note](https://www.britishelectionstudy.com/uncategorized/how-coronavirus-attitudes-fit-into-britains-ideological-landscape/)

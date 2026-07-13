# News Habits Characteristics

| Area | Current state | Recommended change | Priority | Implementation impact |
| --- | --- | --- | --- | --- |
| News frequency | Collected as a 1-10 `WizardScale` with no visible anchors in the field label. | Replace or label the scale with clear frequency bands: never, less than weekly, 1-2 days/week, 3-5 days/week, daily, several times/day. | High | Frontend option/UI change; backend can keep numeric if mapped deliberately, or add an enum for clearer analytics. |
| Balanced viewpoint | Required yes/no: "Do you feel you see a balanced viewpoint of news?" | Keep it a yes/no question (not an agreement scale). Reword to an exposure question: "Do you regularly see more than one viewpoint on the news stories you follow?" | High | No storage change; stays yes/no. |
| Mainstream vs social | Slider collects exact `mainstreamNewsPercent`, defaulting to 50. | Keep the visual slider if liked, but define terms and bucket results for analysis: mostly social, more social, mixed, more mainstream, mostly mainstream. | Medium | Currently not submitted in `buildCharacteristicAnswers`; needs DTO/API persistence before it can be used. |
| Representative data belief | Required yes/no with leading wording about the world being better. | Use a neutral agreement statement: "Representative public opinion data can help people understand society better." | High | Requires backend support if moving beyond yes/no. |
| Payload persistence | `balancedNewsViewpoint`, `mainstreamNewsPercent`, and `betterWorldWithData` exist in form state and validation, but not in `CharacteristicAnswers`. | Add these fields to frontend types, payload builder, backend DTO/entity/storage, or remove them from required onboarding until supported. | High | Frontend/backend contract and persistence change. |
| Privacy | News habits are less sensitive than income/body, but can still reveal political or media identity when crossed with other fields. | Aggregate display buckets and suppress small groups; do not expose exact slider percentages in public breakdowns. | Medium | Analytics/display-layer rule; optional backend bucketing. |

## Current State

The onboarding "News habits" step currently asks four questions:

| Field | Current UI | Current value shape |
| --- | --- | --- |
| `balancedNewsViewpoint` | `Do you feel you see a balanced viewpoint of news? *` | `YES` / `NO` |
| `mainstreamNewsPercent` | `What percentage of the news you absorb comes from mainstream news vs social media? *` | Number from 0 to 100, default `50` |
| `newsFrequencyScore` | `How often do you follow the news? *` | Number from `WizardScale`, currently required |
| `betterWorldWithData` | `Do you think the world will be a better place if we can see real representative data on how society feels about topics? *` | `YES` / `NO` |

Implementation issue: `answers.ts` validates all four fields, but `buildCharacteristicAnswers` only sends `newsFrequency` in the submitted payload. `balancedNewsViewpoint`, `mainstreamNewsPercent`, and `betterWorldWithData` are not present in `CharacteristicAnswers` in `types.ts`, so the app currently appears to collect those answers without persisting them.

## Recommended Option Set

### News Frequency

Recommended field label: `How often do you follow news or current affairs?`

Recommended helper text: `Include news you read, watch, listen to, or come across on social platforms.`

| Label | Suggested value | Notes |
| --- | --- | --- |
| `Never or almost never` | `NEVER_OR_ALMOST_NEVER` | Better than forcing low-engagement users onto a scale. |
| `Less than once a week` | `LESS_THAN_WEEKLY` | Captures occasional users. |
| `1-2 days a week` | `ONE_TO_TWO_DAYS_WEEKLY` | Useful low/medium band. |
| `3-5 days a week` | `THREE_TO_FIVE_DAYS_WEEKLY` | Captures regular but not daily users. |
| `About once a day` | `DAILY` | Common news-consumption boundary. |
| `Several times a day` | `SEVERAL_TIMES_DAILY` | Captures heavy/current-affairs users. |

Minimum-change mapping if the backend must keep `newsFrequency` numeric:

| Numeric score | Meaning |
| --- | --- |
| `0` | Never or almost never |
| `1` | Less than once a week |
| `2` | 1-2 days a week |
| `3` | 3-5 days a week |
| `4` | About once a day |
| `5` | Several times a day |

Avoid an unanchored 1-10 scale for frequency. It is hard for users to know whether "7" means daily, high interest, or several checks per day.

### Balanced Viewpoint Exposure

Keep this as a yes/no question, not an agreement scale.

Recommended field label: `Do you regularly see more than one viewpoint on the news stories you follow?`

| Label | Suggested value |
| --- | --- |
| `Yes` | `YES` |
| `No` | `NO` |

This measures the user's perceived exposure to multiple viewpoints without implying the app can verify whether the viewpoint mix is objectively balanced. A binary keeps the existing storage and is easy to answer.

### Mainstream vs Social Media Mix

Recommended field label: `Where does most of your news come from?`

Recommended helper text: `Mainstream means news organisations such as TV, radio, newspapers, news websites and news apps. Social means platforms, creators, feeds, groups and messaging apps.`

If keeping the slider:

| Slider range | Suggested analytics bucket | Meaning |
| --- | --- | --- |
| `0-20% mainstream` | `MOSTLY_SOCIAL` | Mostly social/platform-led. |
| `21-40% mainstream` | `MORE_SOCIAL_THAN_MAINSTREAM` | More social than mainstream. |
| `41-60% mainstream` | `MIXED_MAINSTREAM_AND_SOCIAL` | Roughly mixed. |
| `61-80% mainstream` | `MORE_MAINSTREAM_THAN_SOCIAL` | More mainstream than social. |
| `81-100% mainstream` | `MOSTLY_MAINSTREAM` | Mostly mainstream/news-organisation-led. |

If replacing the slider with chips, use the same five bucket labels:

- `Mostly social media or creator/platform feeds`
- `More social media than mainstream news`
- `About half and half`
- `More mainstream news than social media`
- `Mostly mainstream news`

The exact percentage can be useful for a private internal model, but public/user-facing result breakdowns should use buckets to avoid thin, overly precise groups.

### Belief In Representative Data

Current wording is too leading because it asks whether "the world will be a better place" if the app's core proposition exists. Make it a neutral attitude measure.

Recommended field label: `How much do you agree with this statement?`

Statement: `Representative public opinion data can help people understand society better.`

Recommended options:

| Label | Suggested value |
| --- | --- |
| `Strongly agree` | `STRONGLY_AGREE` |
| `Somewhat agree` | `SOMEWHAT_AGREE` |
| `Neither agree nor disagree` | `NEITHER_AGREE_NOR_DISAGREE` |
| `Somewhat disagree` | `SOMEWHAT_DISAGREE` |
| `Strongly disagree` | `STRONGLY_DISAGREE` |
| `Not sure` | `NOT_SURE` |

If product wants a shorter chip set, use:

- `Agree`
- `Unsure`
- `Disagree`

Do not keep a forced yes/no here. It turns an attitude into a binary and may push users toward agreeing with the product premise.

## Wording and UX Notes

- Page title: `News habits`
- Page subtitle: `How you follow news and how varied your sources feel.`
- Keep definitions close to the mainstream/social question. Users may consider BBC, YouTube clips from newspapers, podcasts, newsletters, creators, and WhatsApp links differently unless the UI defines the categories.
- Put the frequency question first. It is concrete and easier than an attitude question.
- Put the representative-data belief question last. It is more about the product mission than the user's media diet.
- Use `current affairs` alongside `news` so users include politics, local affairs, public issues, economics, world events, and civic topics rather than only formal bulletins.
- Use `Not sure` on the representative-data question, where the user is judging social value and many users will not have a firm answer. The balanced-viewpoint question stays a simple yes/no.
- Do not add `Prefer not to say` anywhere (see ADR-016). `Not sure` is allowed only as a genuine-uncertainty answer, not a refusal.

Recommended order:

1. `How often do you follow news or current affairs?`
2. `Where does most of your news come from?`
3. `Do you regularly see more than one viewpoint on the news stories you follow?`
4. `Representative public opinion data can help people understand society better.`

## Implementation Notes

| File | Change |
| --- | --- |
| `frontend/mobile/your-say-news/features/user-characteristics/types.ts` | Add `balancedNewsViewpoint`, `mainstreamNewsPercent` or source bucket, and `betterWorldWithData` if these are meant to persist. |
| `frontend/mobile/your-say-news/features/user-characteristics/answers.ts` | Include the three missing news-habit fields in `buildCharacteristicAnswers`; update tests. |
| `frontend/mobile/your-say-news/features/user-characteristics/components/OnboardingScreen.tsx` | Update field wording, order, and option controls. |
| `frontend/mobile/your-say-news/features/user-characteristics/components/NewsSourceSlider.tsx` | Add clearer endpoint/helper labels if keeping the slider. Consider snapping to 5 or 10 point increments. |
| Backend DTO/entity/migration | Add matching persistence for the three currently omitted fields, or intentionally remove them from onboarding until backend support exists. |

Privacy and aggregation:

- Store exact `mainstreamNewsPercent` only if there is a real product need. Otherwise store the five buckets.
- If exact percentage is stored, aggregate and display only bucketed values.
- Suppress or merge small cells when news-source buckets are crossed with age, politics, nationality, location, ethnicity, income or religion.
- Treat "balanced viewpoint" as perceived exposure, not proof of media quality or political moderation.

Suggested enum names:

| Concept | Suggested enum |
| --- | --- |
| News frequency | `NewsFrequency` |
| Viewpoint exposure | Boolean yes/no (unchanged) |
| Mainstream/social mix | `NewsSourceMix` |
| Representative data belief | `AgreementLevel` or `RepresentativeDataBelief` |

The balanced-viewpoint answer stays a boolean; only the representative-data question uses an agreement scale, and no characteristic uses `PREFER_NOT_TO_SAY` (see ADR-016).

## Sources

- Pew Research Center, `Social Media and News Fact Sheet`, 2025: https://www.pewresearch.org/journalism/fact-sheet/social-media-and-news-fact-sheet/
- Pew Research Center, `Assessing different survey measurement approaches for news consumption`, 2020: https://www.pewresearch.org/journalism/2020/12/08/assessing-different-survey-measurement-approaches-for-news-consumption/
- Ofcom, `News Consumption in the UK 2025 Research Findings`: https://www.ofcom.org.uk/siteassets/resources/documents/research-and-data/online-research/adult-and-teen-news-consumption-survey/news-consumption-in-the-uk-2025-research-findings.pdf?v=400636
- Reuters Institute for the Study of Journalism, `Digital News Report 2025`: https://reutersinstitute.politics.ox.ac.uk/digital-news-report/2025/dnr-executive-summary
- AAPOR, `Best Practices for Survey Research`: https://aapor.org/standards-and-ethics/best-practices/

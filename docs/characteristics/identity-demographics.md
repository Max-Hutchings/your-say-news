# Identity and Demographic Characteristics

| Characteristic | Current state | Recommended change | Why / source basis | Implementation impact |
| --- | --- | --- | --- | --- |
| Age | `AgeRange` band enum (`13-17`, broad 10-year bands, `65-75`, `75+`) — a static band that goes stale. | Ask current age as a number (minimum 16). Store the derived birth year so age auto-increments each year; derive reporting bands (`16-17`, `18-19`, `20-24`, … `85+`) from the computed age. | A stored band never updates; a number/birth year stays correct every year, and the app is open from age 16. | Replace the `AgeRange` enum input with an integer age + stored `birthYear`; keep bands only as a derived reporting axis. |
| Gender | Woman, Man, Non-binary / gender diverse. `genderSelfDescribe` exists but is always submitted as empty string. | Add `Prefer to self-describe`; wire the existing self-describe field only when selected. | Gender identity collection should support self-description where needed. | Add enum value and conditional text input; suppress self-describe from public labels unless safely aggregated. |
| Sex registered at birth | Male, Female. Required. | Reword to `Sex registered at birth`. Keep it Female / Male only — no intersex option. | Clear collection purpose; the field is a binary birth record kept separate from gender identity. | Label change only; no enum expansion. Keep separate from gender in analytics. |
| Sexual orientation | Heterosexual, Gay / lesbian, Bisexual, Pansexual, Asexual, Other. Uses backend value `HOMOSEXUAL`. Required. | Rename `HETEROSEXUAL` label to `Straight / heterosexual`; replace `HOMOSEXUAL` with `GAY_LESBIAN` in a breaking enum migration; add `Queer`, `Questioning / unsure`, `Prefer to self-describe`. | Extra product categories improve clarity for community use. | Enum migration; keep old values readable during transition if users already saved answers. |
| Race / ethnicity | Asian, Black, White, Hispanic, Native American, Pacific Islander, Other. Multi-select. | Rename field to `Ethnic background`. Use broad, globally recognisable groups that people in any country can identify with, rather than one country's census categories. | The app runs in many countries; a single national taxonomy excludes most users. | Replace `Race` enum with globally-neutral ethnicity values; multi-select remains sensible. |
| Marital / relationship status | Label says `Relationship status`; enum is `MaritalStatus`; options mix legal status and relationship state. | Choose one purpose. For opinion slicing, prefer `Relationship status`: Single, Dating / in a relationship, Cohabiting / living with partner, Married, Civil partnership, Separated, Divorced / dissolved civil partnership, Widowed / surviving civil partner. If legal comparability is needed, ask `Legal marital or registered civil partnership status` separately. | Marital status separates legal marital/civil partnership status from relationship status such as "in a relationship". | Rename enum/domain if using relationship status; otherwise reword UI and expand legal statuses. |

## Current State

The onboarding wizard currently asks these identity/demographic fields across two steps:

- `Age range`, `Gender`, and `Sex assigned at birth` on "A little about you".
- `Race / ethnicity`, `Sexual orientation`, and `Relationship status` on "How you identify".

Frontend options live in `frontend/mobile/your-say-news/features/user-characteristics/data/options.ts`. Backend enum-backed fields live under `user-service/src/main/java/com/yoursay/usercharacteristic/model/Enums/`.

The current option sets are workable for an MVP, but the main issue is inconsistent framing: some fields are UK-first concepts, others are US-first, and marital status currently mixes a legal classification with relationship wording.

## Recommended Option Set

### Age

Do not ask for an age band (see ADR-017). A stored band goes stale — someone who picks `18-19` is
silently wrong a year later. Instead ask for the user's **current age as a number** and store a
value that lets the app keep it correct every year.

- **Collect:** current age as an integer, minimum 16 (the app is open from age 16).
- **Store:** the derived **birth year** (`birthYear = currentYear - age`), not a band and not an
  exact date of birth. Birth year alone is not PII; keep exact DOB out of characteristics entirely
  (see the privacy rule in `CLAUDE.md`).
- **Auto-increment:** compute `age = currentYear - birthYear` on read, so it advances by one every
  year with no user action.
- **Report:** derive aggregate bands from the computed age purely as a reporting axis:
  `16-17`, `18-19`, `20-24`, `25-34`, `35-44`, `45-54`, `55-64`, `65-74`, `75-84`, `85+`.

Reject ages under 16. If under-16s are ever in scope, that needs a separate safeguarding/product
decision, not just a lower minimum.

### Gender

| Label | Suggested enum |
| --- | --- |
| Woman | `WOMAN` |
| Man | `MAN` |
| Non-binary | `NON_BINARY` |
| Another gender identity | `SELF_DESCRIBE` |

Use the existing `genderSelfDescribe` field only when `SELF_DESCRIBE` is selected. In analytics, display self-described answers only after moderation/normalisation and only when the aggregate group is large enough.

### Sex Registered at Birth

| Label | Suggested enum |
| --- | --- |
| Female | `FEMALE` |
| Male | `MALE` |

There is no intersex option; sex registered at birth is Female / Male only. Keep this separate from gender.

### Sexual Orientation

| Label | Suggested enum |
| --- | --- |
| Straight / heterosexual | `STRAIGHT_HETEROSEXUAL` |
| Gay or lesbian | `GAY_LESBIAN` |
| Bisexual | `BISEXUAL` |
| Pansexual | `PANSEXUAL` |
| Asexual | `ASEXUAL` |
| Queer | `QUEER` |
| Questioning / unsure | `QUESTIONING` |
| Another orientation | `SELF_DESCRIBE` |

Avoid the enum name `HOMOSEXUAL`; it is clinical/dated even if the visible label is acceptable. Use `GAY_LESBIAN` or equivalent and migrate stored values.

### Ethnic Background

Recommended broad, globally recognisable set that people in any country can identify with:

| Label | Suggested enum |
| --- | --- |
| White / European descent | `WHITE_EUROPEAN` |
| Black / African descent | `BLACK_AFRICAN` |
| East Asian | `EAST_ASIAN` |
| South Asian | `SOUTH_ASIAN` |
| Southeast Asian | `SOUTHEAST_ASIAN` |
| Middle Eastern / North African | `MIDDLE_EASTERN_NORTH_AFRICAN` |
| Hispanic / Latino | `HISPANIC_LATINO` |
| Indigenous / First Nations | `INDIGENOUS` |
| Pacific Islander | `PACIFIC_ISLANDER` |
| Mixed or multiple backgrounds | `MIXED_MULTIPLE` |
| Other ethnic background | `OTHER_ETHNIC_GROUP` |
| Prefer to self-describe | `SELF_DESCRIBE` |

Keep the field multi-select — it is realistic for mixed or multiple backgrounds and for global users whose identities do not fit one national taxonomy. Do not impose any single country's census subcategories.

### Relationship Status

Recommended if the product wants social context for opinion breakdowns:

| Label | Suggested enum |
| --- | --- |
| Single | `SINGLE` |
| Dating / in a relationship | `IN_RELATIONSHIP` |
| Living with a partner | `COHABITING` |
| Married | `MARRIED` |
| Civil partnership | `CIVIL_PARTNERSHIP` |
| Separated | `SEPARATED` |
| Divorced / dissolved civil partnership | `DIVORCED_OR_DISSOLVED` |
| Widowed / surviving civil partner | `WIDOWED_OR_SURVIVING_PARTNER` |

Alternative if the product wants official comparability:

Question label: `Legal marital or registered civil partnership status`

Options: Never married and never in a civil partnership; Married; In a registered civil partnership; Separated but still legally married; Separated but still legally in a civil partnership; Divorced; Formerly in a civil partnership now legally dissolved; Widowed; Surviving civil partner.

Do not use the legal version while labelling it "Relationship status"; those are different questions.

## Wording / UX Notes

- Do not offer a "Prefer not to say" option; every identity field is a required real choice (see ADR-016).
- Use `Ethnic background` rather than `Race / ethnicity` in onboarding copy.
- Use `Sex registered at birth` rather than `Sex assigned at birth`.
- Keep "Gender" and "Sex registered at birth" adjacent only if the app explains why both are needed. Otherwise it may feel repetitive or suspicious.
- Use "Nationality" in user-facing copy when asking citizenship/nationality. Do not label that question "Country"; country of residence, country of birth, and nationality are different concepts.
- Keep the current multi-select behaviour for ethnicity. It is realistic for mixed or multiple ethnic backgrounds and for global users whose identities do not fit one national taxonomy.
- Avoid exposing rare self-describe values directly in public breakdown labels. Normalise, moderate, or group them.

## Implementation Notes

- Any new option backed by Java enums needs matching updates in:
  - `frontend/mobile/your-say-news/features/user-characteristics/data/options.ts`
  - `user-service/src/main/java/com/yoursay/usercharacteristic/model/Enums/*.java`
  - Liquibase migrations if persisted enum values are constrained or existing stored values need migration
  - frontend tests around `isRequiredComplete` and `buildCharacteristicAnswers`
  - vote axes if these fields are used for public breakdowns
- Existing saved value migration to plan:
  - `HOMOSEXUAL` -> `GAY_LESBIAN`
  - `NON_BINARY` label can stay but should display as `Non-binary`
  - `RACE` enum should become ethnicity-oriented, or at least frontend labels should move to ethnic-background wording
  - Replace the `AgeRange` band enum with an integer age input and a stored `birthYear`. Existing band answers cannot be converted to an exact age, so keep them as legacy reporting-band values or prompt those users to re-enter their age; new answers store `birthYear`.
- For analytics, preserve raw user choices but publish only aggregate groups above a minimum threshold. For sparse categories, show `Not enough responses` or combine into a broader bucket.
- Consider a two-layer ethnicity model later: top-level aggregation enum plus optional detail enum/free-text. That keeps reporting stable while allowing richer identity capture.

## Sources

- [GSS gender identity harmonised standard](https://analysisfunction.civilservice.gov.uk/policy-store/gender-identity/)
- [GSS sexual orientation harmonised standard](https://analysisfunction.civilservice.gov.uk/policy-store/sexual-orientation/)
- [GSS marital or civil partnership status harmonised standard](https://analysisfunction.civilservice.gov.uk/policy-store/marital-or-partnership-status/)
- [GSS age and date of birth harmonised standard](https://analysisfunction.civilservice.gov.uk/policy-store/age-and-date-of-birth/)
- [ONS Census 2021 age extended classifications](https://www.ons.gov.uk/census/census2021dictionary/variablesbytopic/demographyvariablescensus2021/ageextended/classifications)
- [GOV.UK list of ethnic groups, 2021 Census](https://www.ethnicity-facts-figures.service.gov.uk/style-guide/ethnic-groups/)
- [Office for Statistics Regulation guidance on sex and gender identity data](https://osr.statisticsauthority.gov.uk/guidance/collecting-and-reporting-data-about-sex-and-gender-identity-in-official-statistics-a-guide-for-official-statistics-producers/pages/2/)

# Family and Lifestyle Characteristics

| Area | Current state | Recommended change | Priority | Notes |
| --- | --- | --- | --- | --- |
| Parent status | UI asks `Are you a parent?` as Yes/No, then derives backend `MUM` or `DAD` from sex at birth. | Replace with direct parent/caregiver buckets: `Not a parent/caregiver`, `Parent/caregiver of child under 18`, `Parent/caregiver of adult child only`, `Expecting/soon to be parent`. | High | Avoids deriving mum/dad from sex at birth and captures more useful family context. |
| Parent wording | "Parent" can exclude guardians, step-parents, foster parents, adoptive parents and kinship carers. | Use "Are you a parent or caregiver?" in the UI. | High | More inclusive and clearer for users who provide day-to-day care but may not use the word parent. |
| Pet ownership | `hasPet` is Yes/No and pet type is single-select only when Yes. | Keep conditional ownership question, but allow multi-select pet type if possible. | Medium | Many owners have more than one type of pet; single-select forces lossy answers. |
| Pet type | Current values are Dog, Cat, Fish, Bird, Reptile. | Add Rabbit, Small mammal, Horse, Amphibian, Invertebrate, Other. Consider "Multiple pet types" if multi-select is not practical. | Medium | More realistic coverage while keeping buckets broad enough for aggregation. |
| Chronotype | Morning lark, Night owl, In between. | Keep three buckets but relabel as `Mostly morning`, `Mostly evening/night`, `Mixed / depends`. | Low | Three-way morning/intermediate/evening is a standard lightweight classification. |
| Outlook | Glass half full, Glass half empty, Depends on the day. | Reword to `Mostly optimistic`, `Mostly pessimistic`, `Mixed / depends`, optionally add `Unsure`. | Medium | Keeps the playful question useful without idioms that may not translate or feel clear. |
| Privacy | Lifestyle fields are low sensitivity alone, but can become identifying when combined with location, income, age and nationality. | Apply the same minimum-bucket aggregation and suppression rules as other characteristics. | High | Pet type plus rare location or family status should not create small reportable groups. |

## Current State

The current frontend options live in `frontend/mobile/your-say-news/features/user-characteristics/data/options.ts`:

```ts
export const PARENT_OPTIONS: Option[] = [
    { label: "Yes", value: "YES" },
    { label: "No", value: "NO" },
];

export const PET_TYPE_OPTIONS: Option[] = [
    { label: "Dog", value: "DOG" },
    { label: "Cat", value: "CAT" },
    { label: "Fish", value: "FISH" },
    { label: "Bird", value: "BIRD" },
    { label: "Reptile", value: "REPTILE" },
];

export const CHRONOTYPE_OPTIONS: Option[] = [
    { label: "Morning lark", value: "MORNING_LARK" },
    { label: "Night owl", value: "NIGHT_OWL" },
    { label: "In between", value: "IN_BETWEEN" },
];

export const OUTLOOK_OPTIONS: Option[] = [
    { label: "Glass half full", value: "OPTIMIST" },
    { label: "Glass half empty", value: "PESSIMIST" },
    { label: "Depends on the day", value: "DEPENDS" },
];
```

`OnboardingScreen.tsx` currently asks parent status on the combined body/finance page, then asks pets, chronotype and outlook on the quirky questions page. That placement should change: parent status belongs with family/household questions, not body or finance.

The backend enum for parent status is:

```java
public enum Parent {
    MUM,
    DAD,
    NO;
}
```

The frontend does not ask whether the user is a mum or dad. Instead, `answers.ts` maps `YES` to `MUM` when `sexAtBirth` is `FEMALE`, otherwise `DAD`. That is brittle and will mislabel some parents. It also turns a family-status answer into an inferred gendered label.

## Recommended Option Set

### Parent or Caregiver Status

Recommended UI label:

> Are you a parent or caregiver?

Recommended single-select options:

| Label | Suggested value | Include now? | Rationale |
| --- | --- | --- | --- |
| Not a parent or caregiver | `NOT_PARENT_CAREGIVER` | Yes | Clear negative answer. |
| Parent/caregiver of a child under 18 | `PARENT_CAREGIVER_UNDER_18` | Yes | Most useful split for current family responsibilities. |
| Parent/caregiver of adult child only | `PARENT_CAREGIVER_ADULT_CHILD_ONLY` | Yes | Separates parents whose day-to-day responsibilities may be different. |
| Expecting or soon to become a parent/caregiver | `EXPECTING_OR_SOON_PARENT_CAREGIVER` | Optional | Useful but may be a small bucket; include only if aggregation suppression is ready. |

Do not keep `MUM` and `DAD` as the primary buckets. They are less useful for sentiment slicing than actual caring responsibility and they create avoidable inclusivity issues.

If the backend needs a smaller first implementation, use:

| Label | Suggested value |
| --- | --- |
| Yes, for a child under 18 | `YES_UNDER_18` |
| Yes, adult child only | `YES_ADULT_CHILD_ONLY` |
| No | `NO` |

### Pet Ownership

Recommended UI label:

> Do you currently have any pets?

Recommended options:

| Label | Suggested value |
| --- | --- |
| Yes | `YES` |
| No | `NO` |

Current yes/no is workable, but "currently" makes the question clearer. If the goal is sentiment analysis, current pet ownership is more actionable than "have you ever owned a pet?"

### Pet Type

Recommended UI label:

> What kind of pet or pets do you have?

Recommended multi-select options:

| Label | Suggested value | Notes |
| --- | --- | --- |
| Dog | `DOG` | Keep. |
| Cat | `CAT` | Keep. |
| Fish | `FISH` | Keep. |
| Bird | `BIRD` | Keep. |
| Reptile | `REPTILE` | Keep. |
| Rabbit | `RABBIT` | Common enough to deserve its own bucket. |
| Small mammal | `SMALL_MAMMAL` | Covers guinea pigs, hamsters, gerbils, rats, mice, chinchillas and similar. |
| Horse or pony | `HORSE_PONY` | Useful where rural/urban and income correlations matter, but may be a small bucket. |
| Amphibian | `AMPHIBIAN` | Broad bucket for frogs, newts and similar. |
| Invertebrate | `INVERTEBRATE` | Broad bucket for spiders, insects, snails and similar. |
| Other | `OTHER` | Necessary catch-all. |

If the product must stay single-select for now, add `Multiple pet types` as an option. Multi-select is better because "dog and cat" is a common real answer and should not be collapsed arbitrarily.

### Chronotype

Recommended UI label:

> When do you usually feel most alert?

Recommended single-select options:

| Label | Suggested value | Notes |
| --- | --- | --- |
| Mostly morning | `MORNING` or keep `MORNING_LARK` | Clearer than "lark". |
| Mostly evening/night | `EVENING` or keep `NIGHT_OWL` | Keeps the common meaning without relying only on idiom. |
| Mixed / depends | `INTERMEDIATE` or keep `IN_BETWEEN` | Better aligned with standard chronotype language. |

Keep this as three buckets. Formal chronotype questionnaires often split into definite/moderate morning and evening types, but the signup flow does not need that precision.

### Outlook

Recommended UI label:

> How do you generally feel about the future?

Recommended single-select options:

| Label | Suggested value | Notes |
| --- | --- | --- |
| Mostly optimistic | `OPTIMISTIC` or keep `OPTIMIST` | Clear and plain. |
| Mostly pessimistic | `PESSIMISTIC` or keep `PESSIMIST` | Clear and plain. |
| Mixed / depends | `MIXED_DEPENDS` or keep `DEPENDS` | Avoids forcing a false binary. |
| Unsure | `UNSURE` | Optional; useful if users do not identify with either direction. |

Avoid "glass half full" and "glass half empty" as option labels. They are friendly, but idiomatic and less precise than the data being stored.

## Wording and UX Notes

- Move parent/caregiver status out of the body/finance page. Put it on a family/household page or near housing status.
- Keep pets, chronotype and outlook on the quirky questions page. They work well as a lighter page between sensitive sections.
- Avoid deriving gendered parent labels from sex at birth. Ask the intended characteristic directly.
- Use `caregiver` as well as `parent` so guardians, kinship carers, foster parents, adoptive parents and step-parents can answer comfortably.
- For pet type, either use multi-select or add a `Multiple pet types` option. Single-select with no multiple option creates predictable bad data.
- Use broad pet buckets for privacy and product usefulness. Do not collect breed, exact animal count or pet names.
- Keep chronotype and outlook lightweight. These are aggregate flavour dimensions, not clinical measurements.
- Do not add `Prefer not to say` to any of these questions (see ADR-016). `Unsure` / `Mixed / depends` are allowed as genuine answers, not refusals.

## Implementation Notes

- `Parent` should be remodelled before adding more frontend labels. Current backend values `MUM`, `DAD`, `NO` do not match the question being asked.
- Remove `deriveParent(form.parent, form.sexAtBirth)` from `answers.ts` once parent/caregiver values are explicit.
- If pet type becomes multi-select, update API shape from `petType: string | null` to either `petTypes: string[]` or a join table/array depending on persistence preference.
- If multi-select is too large a change, add `MULTIPLE` and `OTHER` to `PetType` as a minimal bridge.
- `Chronotype` can keep existing enum values while changing labels. If changing enum names, migrate `MORNING_LARK` to `MORNING`, `NIGHT_OWL` to `EVENING`, and `IN_BETWEEN` to `INTERMEDIATE`.
- `Outlook` can keep existing enum values while changing labels. If changing enum names, migrate `OPTIMIST` to `OPTIMISTIC`, `PESSIMIST` to `PESSIMISTIC`, and `DEPENDS` to `MIXED_DEPENDS`.
- Apply minimum group-size suppression in results for combinations such as pet type + small location + income + age band.
- Seed data currently contains `MUM`, `DAD` and `NO`; update seeds when the parent enum changes.

## Sources

- [ONS: Families in England and Wales, Census 2021](https://www.ons.gov.uk/peoplepopulationandcommunity/birthsdeathsandmarriages/families/articles/familiesinenglandandwales/census2021) - useful for treating parenting/caregiving as family composition, including lone parents, dependent children, non-dependent children and children with second parent/guardian addresses.
- [PDSA: Choosing a pet](https://www.pdsa.org.uk/pet-help-and-advice/choosing-a-pet) - supports broader pet categories beyond dog/cat and frames ownership by home, time, exercise, money and knowledge.
- [AVMA: U.S. pet ownership statistics](https://www.avma.org/resources-tools/reports-statistics/us-pet-ownership-statistics) - useful reference for common pet categories including fish, reptiles, birds, small mammals and rabbits.
- [Morningness-Eveningness Questionnaire scoring reference](https://reference.medscape.com/calculator/829/morningness-eveningness-questionnaire-meq) - supports morning, intermediate and evening chronotype grouping.
- [Life Orientation Test-Revised, Carnegie Mellon PDF](https://www.cmu.edu/dietrich/psychology/pdf/scales/LOTR_Scale.pdf) - supports outlook as generalized optimism/pessimism about future outcomes, though the app should not use a clinical scale in signup.

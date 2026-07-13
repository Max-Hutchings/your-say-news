# Education and Work Characteristics

| Area | Current issue | Recommended change | Priority |
| --- | --- | --- | --- |
| Education level | Five options skip common qualification levels and vocational routes. | Ask "Highest qualification completed" and use internationally recognisable levels (no GCSE/A-level/T-level or other country-specific wording): schooling, vocational/technical, higher education below degree, bachelor's, master's, doctorate, other, not sure. | High |
| Occupation / employment status | `occupation` is actually employment status; it misses homemaker/carer, unable to work, temporary/casual work, and looking for work nuance. | Rename user-facing label to "Current work or study status" and add realistic labour-status options without pretending to capture job title. | High |
| Employment sector | Good broad start, but not aligned cleanly to SIC sections and misses mining, real estate, professional services, admin/support, social care, and personal services. | Keep broad aggregated sectors, but map them to SIC-style groups and add the missing high-level sectors. | Medium |
| University subject | Mixes granular and broad subjects, has "N/A" as a selectable subject, and frontend omits backend `ARCHITECTURE`. | Use a searchable list of broad, internationally recognisable subject groups; gate the question behind degree/higher-education answers and replace `N/A` with a skip/not-applicable path. | High |
| Privacy | Education + sector + location can create small cohorts, especially specialist subjects and rare industries. | Keep published analytics aggregated, suppress small buckets, and merge specialist sectors/subjects into "Other / specialist" when sample size is low. | High |

## Current State

Frontend options are defined in `frontend/mobile/your-say-news/features/user-characteristics/data/options.ts`.

The onboarding screen asks these four fields together on the "Education & work" step:

- `Highest education *`
- `Employment status *`
- `Industry / sector *`
- `University subject (if applicable)`

Backend enum files checked:

- `EducationLevel.java`: `NO_FORMAL_EDUCATION`, `HIGH_SCHOOL`, `BACHELORS`, `MASTERS`, `DOCTORATE`
- `OccupationStatus.java`: `STUDENT`, `EMPLOYED_FULL_TIME`, `EMPLOYED_AND_STUDYING`, `EMPLOYED_PART_TIME`, `SELF_EMPLOYED`, `UNEMPLOYED`, `RETIRED`
- `EmploymentSector.java`: broad 20-option industry list
- `UniversitySubject.java`: broad/mixed subject list including `ARCHITECTURE`

Implementation mismatch found: backend `UniversitySubject` includes `ARCHITECTURE`, but `UNIVERSITY_SUBJECT_OPTIONS` in the frontend does not include an Architecture option. The frontend also has `Theater`, while the app otherwise uses UK spelling such as `colour`; use `Theatre / drama` for UK clarity.

## Recommended Option Set

### Education level

Question wording: "What is the highest qualification you have completed?"

Recommended enum-style values:

| Label | Suggested value | Notes |
| --- | --- | --- |
| No formal qualifications | `NO_FORMAL_QUALIFICATIONS` | Clearer than "No formal education"; avoids implying no schooling. |
| Primary or basic schooling | `PRIMARY_SCHOOLING` | Internationally recognisable lowest schooling level. |
| Secondary school | `SECONDARY_SCHOOL` | Country-neutral bucket for end-of-school qualifications. |
| Vocational or technical qualification | `VOCATIONAL_TECHNICAL` | Covers apprenticeships and trade/technical training worldwide. |
| Higher education below degree | `HIGHER_EDUCATION_BELOW_DEGREE` | Diplomas, foundation/associate-level study below a full degree. |
| Bachelor's degree or equivalent | `BACHELORS_OR_EQUIVALENT` | Existing `BACHELORS`, but label can be clearer. |
| Master's degree or equivalent | `MASTERS_OR_EQUIVALENT` | Existing `MASTERS`, but label can be clearer. |
| Doctorate | `DOCTORATE` | Keep. |
| Other qualification | `OTHER_QUALIFICATION` | Needed for professional and unusual qualifications. |
| Not sure | `NOT_SURE` | More realistic than forcing a wrong level. |

No `Prefer not to say` (see ADR-016). These levels avoid country-specific labels (no GCSE/A-level/T-level) so users in any country can identify with them.

If the team wants less migration cost, a smaller first pass is:

- Keep the existing five backend values.
- Rename `NO_FORMAL_EDUCATION` label to "No formal qualifications".
- Add `VOCATIONAL_TECHNICAL`, `HIGHER_EDUCATION_BELOW_DEGREE`, `OTHER`, `NOT_SURE`.

### Occupation / employment status

Question wording: "Which best describes what you do at the moment?"

This should be labelled as work/study status, not occupation. If the product later wants actual occupation, add a separate "Job role" or "Occupation group" field based on SOC-style groups.

Recommended values:

| Label | Suggested value | Notes |
| --- | --- | --- |
| Employed full-time | `EMPLOYED_FULL_TIME` | Keep. |
| Employed part-time | `EMPLOYED_PART_TIME` | Keep. |
| Self-employed / freelancer | `SELF_EMPLOYED_FREELANCER` | Current `SELF_EMPLOYED`; label should include freelance. |
| Casual, temporary or gig work | `CASUAL_TEMPORARY_GIG` | Common and analytically distinct from stable employment. |
| Student | `STUDENT` | Keep. |
| Working and studying | `WORKING_AND_STUDYING` | Replace "Employed full-time / studying part-time"; allow any mix. |
| Looking for work | `LOOKING_FOR_WORK` | Clearer than broad `UNEMPLOYED`. |
| Not working and not currently looking | `NOT_WORKING_NOT_LOOKING` | Captures economically inactive without forcing a reason. |
| Looking after home or family | `LOOKING_AFTER_HOME_FAMILY` | Common survey category; important social lens. |
| Unable to work because of health or disability | `UNABLE_TO_WORK_HEALTH_DISABILITY` | Keep distinct from disability characteristic; make optional/sensitive. |
| Retired | `RETIRED` | Keep. |
| Other | `OTHER` | Needed. |

Avoid asking for exact employer or exact job title during signup. It raises privacy risk and creates sparse buckets.

### Employment sector

Question wording: "Which sector do you mainly work in?"

Show this only when work status is employed, self-employed/freelance, casual/gig, or working and studying. For students, retired users, carers, and people not working, default to "Not applicable" or hide the field.

Recommended broad sectors:

| Label | Suggested value | Notes |
| --- | --- | --- |
| Agriculture, forestry and fishing | `AGRICULTURE_FORESTRY_FISHING` | Aligns with SIC Section A. |
| Mining, quarrying and extraction | `MINING_QUARRYING_EXTRACTION` | Missing from current list; can be merged into energy for small samples. |
| Manufacturing | `MANUFACTURING` | Keep. |
| Energy, water and utilities | `ENERGY_WATER_UTILITIES` | Broaden current energy/utilities. |
| Construction | `CONSTRUCTION` | Keep. |
| Retail, wholesale and motor trade | `RETAIL_WHOLESALE_MOTOR` | More SIC-like than retail only. |
| Hospitality, accommodation and food | `HOSPITALITY_ACCOMMODATION_FOOD` | Broaden current hospitality. |
| Transport, logistics and postal | `TRANSPORT_LOGISTICS_POSTAL` | Broaden current transport/logistics. |
| Aviation | `AVIATION` | Covers airlines, airports, air traffic services, aircraft operations, and the wider aviation industry. |
| Information, media and communications | `INFORMATION_MEDIA_COMMUNICATIONS` | Merge current IT/media boundary, or split tech below. |
| Technology and software | `TECHNOLOGY_SOFTWARE` | Useful modern app bucket; can map to SIC information/communication. |
| Finance, insurance and accounting | `FINANCE_INSURANCE_ACCOUNTING` | Current finance/insurance; accounting often fits professional services too. |
| Real estate and property | `REAL_ESTATE_PROPERTY` | Missing. |
| Professional, scientific and technical services | `PROFESSIONAL_SCIENTIFIC_TECHNICAL` | Missing broad SIC area; includes consulting, engineering, design, R&D. |
| Legal services | `LEGAL_SERVICES` | Current `LEGAL`; can be a child/alias of professional services if simplifying. |
| Admin, support and customer services | `ADMIN_SUPPORT_CUSTOMER_SERVICES` | Missing; common employment area. |
| Public administration and government | `PUBLIC_ADMINISTRATION_GOVERNMENT` | Current government/public; clearer label. |
| Education | `EDUCATION` | Keep. |
| Health care | `HEALTH_CARE` | Keep, but split from social care. |
| Social care | `SOCIAL_CARE` | Missing; important UK sector. |
| Arts, culture, entertainment and sport | `ARTS_CULTURE_ENTERTAINMENT_SPORT` | Broaden current arts/culture. |
| Charity, voluntary and community | `CHARITY_VOLUNTARY_COMMUNITY` | Current `NONPROFIT`; UK wording clearer. |
| Defence, military and emergency services | `DEFENCE_MILITARY_EMERGENCY_SERVICES` | Broaden current military/defence. |
| Personal services | `PERSONAL_SERVICES` | Hair/beauty, repair, wellness, domestic services; missing. |
| Other | `OTHER` | Keep. |
| Not applicable | `NOT_APPLICABLE` | Keep, but prefer conditional hiding. |

For analytics, these can be rolled up to fewer headings when counts are low: for example combine mining/energy/utilities, combine legal/professional/science, or combine arts/charity/personal services.

### University subject

Question wording: "What was your main higher-education subject?"

Only show this if education is `HIGHER_EDUCATION_BELOW_DEGREE`, `BACHELORS_OR_EQUIVALENT`, `MASTERS_OR_EQUIVALENT`, or `DOCTORATE`. If a user studied multiple subjects, prompt them to pick the main or most recent one.

Recommended searchable subject list:

| Label | Suggested value | Notes |
| --- | --- | --- |
| Medicine and dentistry | `MEDICINE_DENTISTRY` | CAH-style broad group. |
| Subjects allied to medicine | `ALLIED_HEALTH` | Nursing, pharmacy, therapy, etc.; better than separate one-off `NURSING` only. |
| Nursing | `NURSING` | Optional separate app-relevant bucket if health comparisons matter. |
| Biological and sport sciences | `BIOLOGICAL_SPORT_SCIENCES` | Replaces separate biology-only framing. |
| Psychology | `PSYCHOLOGY` | Keep; common and analytically useful. |
| Veterinary sciences | `VETERINARY_SCIENCES` | Missing. |
| Agriculture, food and related studies | `AGRICULTURE_FOOD_RELATED` | Broaden current agriculture. |
| Physical sciences | `PHYSICAL_SCIENCES` | Covers physics, chemistry, astronomy, earth science; reduce over-granularity. |
| Mathematical sciences | `MATHEMATICAL_SCIENCES` | Keep as broad group. |
| Engineering and technology | `ENGINEERING_TECHNOLOGY` | Broaden engineering. |
| Computing | `COMPUTING` | Rename from Computer Science for broader CS/IT/software. |
| Architecture, building and planning | `ARCHITECTURE_BUILDING_PLANNING` | Fixes current frontend/backend gap around `ARCHITECTURE`. |
| Business and management | `BUSINESS_MANAGEMENT` | Broaden business. |
| Economics | `ECONOMICS` | Optional separate high-signal bucket. |
| Law | `LAW` | Keep. |
| Social sciences | `SOCIAL_SCIENCES` | Covers sociology, anthropology, politics, human geography where not separate. |
| Politics and international relations | `POLITICS_INTERNATIONAL_RELATIONS` | Clearer UK label than "Political Science"; optional if keeping list shorter. |
| Education and teaching | `EDUCATION_TEACHING` | Keep. |
| Languages and linguistics | `LANGUAGES_LINGUISTICS` | Broaden linguistics/literature boundary. |
| English, literature and creative writing | `ENGLISH_LITERATURE_CREATIVE_WRITING` | Broaden current literature. |
| History, philosophy and theology | `HISTORY_PHILOSOPHY_THEOLOGY` | Broaden current history/philosophy. |
| Geography, earth and environmental studies | `GEOGRAPHY_EARTH_ENVIRONMENTAL` | Can absorb current geography/environmental science if shorter list is needed. |
| Media, journalism and communications | `MEDIA_JOURNALISM_COMMUNICATIONS` | Broaden journalism/media. |
| Creative arts and design | `CREATIVE_ARTS_DESIGN` | Better than separate arts/fine arts only. |
| Performing arts, music and theatre | `PERFORMING_ARTS_MUSIC_THEATRE` | Use UK spelling. |
| Combined, general or interdisciplinary studies | `COMBINED_GENERAL_INTERDISCIPLINARY` | Important for joint honours/liberal arts. |
| Other subject | `OTHER` | Keep. |

No `Prefer not to say` (see ADR-016).

If migration cost matters, keep existing backend values but add frontend `Architecture`, rename labels, and add at least:

- `ALLIED_HEALTH`
- `VETERINARY_SCIENCES`
- `ARCHITECTURE`
- `COMPUTING`
- `BUSINESS_MANAGEMENT`
- `MEDIA_COMMUNICATIONS`
- `CREATIVE_ARTS_DESIGN`
- `COMBINED_INTERDISCIPLINARY`

## Wording and UX Notes

- Rename the step subtitle from "What you studied and the work you do" to "Your education and current work or study situation."
- Avoid "occupation" in user-facing text unless collecting actual job role. Current data is status, not occupation.
- Add short helper text under education: "Pick the nearest equivalent, including qualifications gained in any country."
- Gate sector behind relevant work statuses. A mandatory sector for retired, student-only, carer, or not-working users creates noisy data.
- Gate university subject behind higher-education answers. A visible `N/A` option in the subject search makes the list feel like data plumbing rather than a question.
- Do not add "Prefer not to say" to any of these fields (see ADR-016). `Not sure` on education is allowed as a genuine answer.
- Keep free-text out of signup unless heavily moderated and never displayed directly. Use "Other" rather than collecting exact unusual subject/employer text at this stage.

## Implementation Notes

- Frontend/backend enum parity must be checked before implementation. `UniversitySubject.ARCHITECTURE` exists in the backend but is absent from `UNIVERSITY_SUBJECT_OPTIONS`.
- Existing `OccupationStatus.UNEMPLOYED` may be too broad. If preserving the enum, label it "Looking for work" and add separate values for "Not working and not currently looking", "Looking after home or family", and "Unable to work because of health or disability".
- No characteristic gets a `PREFER_NOT_TO_SAY` value (see ADR-016); every field is a required real choice.
- If hiding conditional fields, update `isRequiredComplete` so `employmentSector` is not required for not-working statuses and `universitySubject` is not required unless education implies higher education.
- Consider storing old enum values as aliases during migration, especially `HIGH_SCHOOL`, `BACHELORS`, `MASTERS`, `SELF_EMPLOYED`, `UNEMPLOYED`, `IT_TECHNOLOGY`, `GOVERNMENT_PUBLIC`, and `NONPROFIT`.
- Do not expose combinations such as exact region + doctorate + niche subject + small sector unless the cohort clears a minimum count threshold.

## Sources

- UK Government Analysis Function, [Qualifications harmonised standard](https://analysisfunction.civilservice.gov.uk/policy-store/qualifications-harmonised-standard/). Used for the idea of highest-qualification levels, UK qualification complexity, and output groupings such as degree equivalent, higher education, A level/equivalent, GCSE/equivalent, no qualifications, and unclassified.
- ONS, [Highest level of qualification variable: Census 2021](https://www.ons.gov.uk/census/census2021dictionary/variablesbytopic/educationvariablescensus2021/highestlevelofqualification). Used for Census categories including no qualifications, Level 1/2/3, apprenticeship, Level 4+, and other.
- ONS, [Economic inactivity](https://www.ons.gov.uk/employmentandlabourmarket/peoplenotinwork/economicinactivity). Used for labour-status language around not working, looking after family/home, long-term sickness/disability, and not in education, employment or training.
- Companies House / ONS, [Standard Industrial Classification (SIC) codes](https://resources.companieshouse.gov.uk/sic/). Used to align sector coverage with broad industry areas such as agriculture, manufacturing, construction, accommodation/food, information/communication, finance, real estate, professional services, education, health/social work, arts, and other services.
- HESA, [Higher Education Classification of Subjects Common Aggregation Hierarchy](https://www.hesa.ac.uk/support/documentation/hecos/cah). Used as the model for broad, searchable higher-education subject groupings.

# Required output contract

- Return exactly {{pageCount}} pages.
- Return pages in this exact `optionId` order: {{optionIds}}.
- Include every `optionId` exactly once; do not merge or omit options.
- When an option supplies cohort candidates, select one or two of their IDs and name a
  selected cohort in the headline using its supplied `displayName` or governed dimension label.
- When an option supplies no cohort candidates, return an empty `selectedCohortIds` list,
  write the strongest general researched case for that option, and do not invent a cohort.
- Headlines must be catchy, 6 to 18 words, and must not use agreement or disagreement.
- Write two or three paragraphs totalling 50 to 100 words for every page.
- In those paragraphs, explain why the selected cohort, or voters choosing the option
  when no cohort is supplied, are likely to favour that option.
- Use the same group-led paragraph format for every voting option.
- Include each selected characteristic group exactly once.
- Start each selected characteristic group's explanation with its exact supplied `displayName` in bold on its own line.
- Explain why that specific group differs from another relevant group.
- Do not repeat the page headline before each paragraph.
- When selected characteristic groups are available, do not add a generic argument paragraph that lacks a group.
- Direct explanations using words such as because, led or drove are allowed.
- Do not claim direct knowledge of every individual voter's private motivation.
- Do not identify individual voters using personal names, email addresses, exact dates of birth or identity-linked vote claims.
- You must call web search before drafting any page.
- Give every paragraph one or more `sourceIds`; empty `sourceIds` are forbidden.
- Include every referenced source exactly once in `sources`; empty `sources` are forbidden.
- Include no more than 20 sources in total.
- Copy each source URL exactly from an HTTPS URL returned by web search in this same call.
- Do not include a source unless it directly supports context used in a paragraph.
- Every caveat must be exactly: This analysis describes patterns among people who voted on this post; it cannot know every individual's reason.

Each selected-group paragraph's `text` must begin in this Markdown shape:

**People aged 18 to 24**

Compared with older adults, people aged 18 to 24 may be more concerned because...

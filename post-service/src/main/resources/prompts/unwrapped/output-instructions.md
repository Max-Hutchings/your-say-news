# Required output contract

- Return exactly {{pageCount}} pages.
- Return pages in this exact `optionId` order: {{optionIds}}.
- Include every `optionId` exactly once; do not merge or omit options.
- When an option supplies cohort candidates, select one or two of their IDs and name a
  selected cohort in the headline using its supplied `displayName`.
- When an option supplies no cohort candidates, return an empty `selectedCohortIds` list,
  write the strongest general researched case for that option, and do not invent a cohort.
- Headlines must be catchy, 6 to 10 words, and must not use agreement or disagreement.
- Write two or three paragraphs totalling 50 to 100 words for every page.
- In those paragraphs, explain why the selected cohort, or voters choosing the option
  when no cohort is supplied, are likely to favour that option.
- Direct explanations using words such as because, led or drove are allowed.
- Do not claim direct knowledge of every individual voter's private motivation.
- You must call web search before drafting any page.
- Give every paragraph one or more `sourceIds`; empty `sourceIds` are forbidden.
- Include every referenced source exactly once in `sources`; empty `sources` are forbidden.
- Copy each source URL exactly from a URL returned by web search in this same call.
- Do not include a source unless it directly supports context used in a paragraph.
- Every caveat must be exactly: This analysis describes patterns among people who voted on this post; it cannot know every individual's reason.

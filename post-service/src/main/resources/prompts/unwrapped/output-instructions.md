# Required output contract

* Return exactly {{pageCount}} pages.
* Return pages in this exact `optionId` order: {{optionIds}}.
* Include every `optionId` exactly once; do not merge or omit options.
* When an option supplies cohort candidates, select one or two of their IDs and name a
*   selected cohort in the headline using its supplied `displayName`.
* When an option supplies no cohort candidates, return an empty `selectedCohortIds` list,
*   write the strongest general researched case for that option, and do not invent a cohort.
* Headlines must be catchy, 6 to 10 words, and must not use agreement or disagreement.
* Write two or three paragraphs totalling 50 to 100 words for every page.
* In those paragraphs, explain why the selected cohort, or voters choosing the option
*   when no cohort is supplied, are likely to favour that option.
* Direct explanations using words such as because, led or drove are allowed.
* Do not claim direct knowledge of every individual voter's private motivation.
* You must call web search before drafting any page.
* Give every paragraph one or more `sourceIds`; empty `sourceIds` are forbidden.
* Include every referenced source exactly once in `sources`; empty `sources` are forbidden.
* Include no more than 20 sources in total.
* Copy each source URL exactly from a URL returned by web search in this same call.
* Do not include a source unless it directly supports context used in a paragraph.
* Every caveat must be exactly: This analysis describes patterns among people who voted on this post; it cannot know every individual's reason.
*   Use the same format for both the “for” and “against” choices.
*   Include each supplied characteristic group exactly once.
*   Put the group name in bold once, immediately before its explanation.
*   Explain why that specific group differs from another group.
*   Do not repeat the heading before every paragraph.
*   Do not add a generic argument that lacks a characteristic group.

The output main paragraphs should appear like the example below:

**People in personal income tier 1**

Compared with people in higher income tiers, people in personal income tier 1 may oppose spending cuts because they depend more heavily on public healthcare, transport and social support. Income-tax reductions may also provide them with smaller cash savings than the value of services they could lose. [1] [2]

**People aged 18 to 24**

Compared with older adults, people aged 18 to 24 may be more concerned about reduced spending because they are more likely to rely on education, early-career support and affordable public transport while having less accumulated wealth available to absorb higher private costs. [2] [3]

**People who rent their home**

Compared with homeowners, people who rent their home may resist spending cuts because housing costs consume more of their disposable income. Reduced public services could introduce additional transport, healthcare or childcare costs that renters have less financial security to manage. [3] [4]
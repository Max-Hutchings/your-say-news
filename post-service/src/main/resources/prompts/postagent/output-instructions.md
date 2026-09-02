# Required output contract

- Return exactly three `summaryClaims`.
- Return exactly two `caseForClaims`.
- Return exactly two `caseAgainstClaims`.
- Write every claim as exactly one sentence of at most 30 words.
- Give every claim one or two exact source URLs found through live web search in this same call.
- Return two to six sources. Include every URL referenced by a claim exactly once in `sources`,
  with its title and publisher, and return no unused source.
- Keep every source title to at most 18 words and every publisher name to at most 6 words.
- Return one concise, neutral support question of at most 20 words ending in a question mark.
- Choose BINARY only for a genuine Agree/Disagree motion and then return exactly `Agree` and
  `Disagree` as the voting options.
- Choose MULTIPLE_CHOICE when several credible answers are useful and return two to five concise,
  neutral, non-overlapping and case-insensitively distinct voting options in display order.
- Keep every voting option to at most 6 words and 60 characters.
- Every voter must be able to select exactly one option.
- Return a neutral factual `imageBrief` of exactly one sentence and at most 25 words.
- Return an `imageSearchQuery` of 3 to 8 words for a human editor to find an owned or reusable
  licensed image. Do not claim that an image is licensed.
- You must call web search before returning the draft.

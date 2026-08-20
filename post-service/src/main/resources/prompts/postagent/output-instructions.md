# Required output contract

- Return neutral `summaryClaims`, the strongest `caseForClaims`, and the strongest
  `caseAgainstClaims`.
- Return one concise, neutral support question.
- Give every claim one or more exact source URLs found through live search.
- Return every cited URL once in `sources`, with its title and publisher, and return no unused source.
- Choose BINARY only for a genuine Agree/Disagree motion and then return exactly `Agree` and
  `Disagree` as the voting options.
- Choose MULTIPLE_CHOICE when several credible answers are useful and return two to five concise,
  neutral, non-overlapping and case-insensitively distinct voting options in display order.
- Every voter must be able to select exactly one option.
- Return a factual `imageBrief` and an `imageSearchQuery` for a human editor.
- You must call web search before returning the draft.

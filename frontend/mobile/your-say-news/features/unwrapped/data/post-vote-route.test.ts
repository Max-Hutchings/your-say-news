import { postVoteHref } from "./post-vote-route";

describe("postVoteHref", () => {
  it("opens Unwrapped straight after voting only when both flags are off", () => {
    expect(postVoteHref(7, { enabled: true, unwrapButton: false })).toBe("/posts/7/unwrapped");
  });

  it("opens the results page when the Unwrap button flag is on", () => {
    expect(postVoteHref(7, { enabled: true, unwrapButton: true })).toBe("/posts/7/results");
  });

  it("opens the results page when Unwrapped is switched off, whatever the button flag says", () => {
    expect(postVoteHref(7, { enabled: false, unwrapButton: false })).toBe("/posts/7/results");
  });

  it("opens the results page while the flags are still unknown", () => {
    expect(postVoteHref(7, null)).toBe("/posts/7/results");
  });
});

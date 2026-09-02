import { test } from "@playwright/test";
import { returningReader, votePopulation, votingPosts } from "../fixtures/test-data";
import { AuthenticationPage } from "../pages/authentication-page";
import { PostVotePage } from "../pages/post-vote-page";
import { ApiGuards } from "../support/api-guards";
import { VotePopulation } from "../support/vote-population";

/**
 * The two voting journeys.
 *
 * The first proves aggregation across a real population: twenty seeded accounts vote, the reader
 * adds theirs, and the breakdown they are shown carries the exact per-cohort numbers those votes
 * imply. The second proves the plainer thing — that results are locked until you vote, and that
 * having voted gets you to your data.
 *
 * Neither touches Post Unwrapped's generated story: both posts stay far below the hundred-vote
 * milestone that would queue a Grok job.
 */

test("a reader sees how twenty other people voted, broken down by political leaning", async ({
  page,
  request,
}) => {
  const authentication = new AuthenticationPage(page);
  const voting = new PostVotePage(page);
  const population = new VotePopulation(request);
  const post = votingPosts.population;

  await population.castAllVotes(post.id);

  await page.goto("/");
  await authentication.expectSignedOut();
  await authentication.signIn(returningReader);

  await voting.openPostInFeed(post);
  await voting.vote(post.id, "agree");

  const { overall, breakdown } = await voting.openFactualResults();

  await voting.expectOverallTally(overall, votePopulation.expectedTotalsWithReader);
  await voting.expectPoliticalBreakdown(
    breakdown,
    votePopulation.expectedPoliticalBreakdownWithReader
  );
});

test("voting unlocks the results view for the reader's own post", async ({ page, request }) => {
  const authentication = new AuthenticationPage(page);
  const voting = new PostVotePage(page);
  const guards = new ApiGuards(request);
  const post = votingPosts.resultsAccess;

  // Establish the lock first, otherwise "unlocks" is never actually demonstrated.
  await guards.expectSentimentLocked(returningReader, post.id);

  await page.goto("/");
  await authentication.expectSignedOut();
  await authentication.signIn(returningReader);

  await voting.openPostInFeed(post);
  await voting.vote(post.id, "disagree");

  const { overall } = await voting.openFactualResults();

  // The reader is the only voter, and they disagreed. The aggregator only surfaces options that
  // received a vote, so Agree should have no row at all rather than a zero.
  await voting.expectOverallTally(overall, { agree: null, disagree: 1, total: 1 });

  // Back on the card, the vote is spent and the results route stays available. The sentiment call
  // above is what proves it reached the server: it would have been refused otherwise.
  await voting.returnToFeed();
  await voting.expectVoteLocked(post.id, "Disagree");
});

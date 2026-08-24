import { expect, type Page, type Response } from "@playwright/test";
import { scrollFeedToPost, showArticleFeed } from "./feed-navigation";

type SentimentChoice = {
  optionId: number;
  count: number;
  percentage: number;
};

type SentimentBucket = {
  bucket: string;
  label: string | null;
  total: number;
  choices: SentimentChoice[];
};

type SentimentBreakdown = {
  options: Array<{ id: number; label: string; semanticKey: string | null }>;
  buckets: SentimentBucket[];
  suppressedBuckets: number;
};

/**
 * Voting on a post in the feed and reading the results behind that vote.
 *
 * A post is located by its own card rather than by feed position: the ranker boosts posts by
 * followed authors, so a position assertion would break whenever another journey changed who the
 * reader follows.
 *
 * Voting navigates to Post Unwrapped, which is Grok-generated. These journeys use posts held well
 * below the hundred-vote generation milestone, so no story is ever built and the screen offers its
 * deterministic "See factual results" route into the factual sentiment view. That keeps the suite
 * off the model entirely.
 */
export class PostVotePage {
  constructor(private readonly page: Page) {}

  /** Brings a specific post's card into view and confirms it is the post the journey wants. */
  async openPostInFeed(post: { id: number; supportQuestion: string }): Promise<void> {
    await showArticleFeed(this.page);
    await scrollFeedToPost(this.page, post.id);
    await expect(
      this.card(post.id).getByText(post.supportQuestion, { exact: true })
    ).toBeVisible();
  }

  /** Casts the reader's own vote through the on-card control. */
  async vote(postId: number, stance: "agree" | "disagree"): Promise<void> {
    const recorded = this.page.waitForResponse(
      (response) =>
        response.request().method() === "POST" &&
        new URL(response.url()).pathname === "/votes"
    );

    await this.card(postId).getByTestId(`vote-${stance}`).click();

    const response = await recorded;
    expect(response.status(), "the reader's vote should be recorded").toBe(201);
  }

  /**
   * Follows the post-vote route into the results. Voting already navigated to Unwrapped; with no
   * generated story the screen offers the factual results directly.
   */
  async openFactualResults(axis = "politicalPersuasion"): Promise<{
    overall: Response;
    breakdown: Response;
  }> {
    await expect(this.page.getByText("POST UNWRAPPED")).toBeVisible({ timeout: 20_000 });

    const overall = this.page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname.endsWith("/sentiment") && response.status() === 200
    );
    const breakdown = this.page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname.endsWith(`/sentiment/${axis}`) &&
        response.status() === 200
    );

    await this.page.getByRole("button", { name: "See factual results" }).click();

    await expect(this.page.getByText("How people voted")).toBeVisible();
    await expect(this.page.getByTestId("sentiment-results-scroll")).toBeVisible();

    return { overall: await overall, breakdown: await breakdown };
  }

  /**
   * Asserts the headline split, tying each count to the option it belongs to.
   *
   * The aggregator only returns options that actually received a vote (`SentimentTally`
   * `activeOptions`), so an option with no votes has no row at all — asserting "0 of N votes" would
   * be asserting something the product never renders.
   */
  async expectOverallTally(
    overallResponse: Response,
    expected: { agree: number | null; disagree: number | null; total: number }
  ): Promise<void> {
    const body = (await overallResponse.json()) as SentimentBreakdown;
    const results = this.page.getByTestId("sentiment-results-scroll");
    await expect(results.getByText("How everyone voted")).toBeVisible();

    for (const [semanticKey, count] of [
      ["AGREE", expected.agree],
      ["DISAGREE", expected.disagree],
    ] as const) {
      const option = body.options.find((item) => item.semanticKey === semanticKey);

      if (count === null) {
        expect(
          option,
          `${semanticKey} received no votes, so it should not be surfaced at all`
        ).toBeUndefined();
        continue;
      }

      expect(option, `${semanticKey} should be surfaced`).toBeDefined();
      expect(
        body.buckets[0].choices.find((choice) => choice.optionId === option!.id)?.count,
        `${semanticKey} count in the response`
      ).toBe(count);
      await expect(
        results.getByTestId(`sentiment-choice-count-${option!.id}`),
        `${semanticKey} count as the reader sees it`
      ).toHaveText(`${count} of ${expected.total} votes`);
    }
  }

  /**
   * Asserts the characteristic breakdown the aggregation actually produced, then that the browser
   * rendered those buckets. The response carries the numbers; the DOM proves they reached a reader.
   */
  async expectPoliticalBreakdown(
    breakdownResponse: Response,
    expected: ReadonlyArray<{
      readonly bucket: string;
      readonly label: string;
      readonly agree: number;
      readonly disagree: number;
    }>
  ): Promise<void> {
    const breakdown = (await breakdownResponse.json()) as SentimentBreakdown;

    // The product's central promise: a breakdown reports cohorts, never people. Twenty identifiable
    // accounts voted here, so if identity ever leaked into the aggregate this is where it would show.
    const raw = JSON.stringify(breakdown);
    for (const identifier of ["@example.com", "smoke.voter", "Smoke Voter", "riley", "Riley"]) {
      expect(
        raw,
        `a sentiment breakdown must never carry "${identifier}"`
      ).not.toContain(identifier);
    }
    expect(raw, "a breakdown must not carry a userId").not.toMatch(/"userId"/);
    expect(raw, "a breakdown must not carry a date of birth").not.toMatch(/"dateOfBirth"/);

    const agreeOption = breakdown.options.find((option) => option.semanticKey === "AGREE");
    const disagreeOption = breakdown.options.find((option) => option.semanticKey === "DISAGREE");
    expect(agreeOption && disagreeOption).toBeTruthy();

    const actual = new Map(breakdown.buckets.map((bucket) => [bucket.bucket, bucket]));
    expect(
      [...actual.keys()].sort(),
      "every seeded political leaning should surface as a bucket"
    ).toEqual(expected.map((entry) => entry.bucket).sort());

    for (const entry of expected) {
      const bucket = actual.get(entry.bucket)!;
      expect(bucket.total, `${entry.bucket} total`).toBe(entry.agree + entry.disagree);
      expect(
        bucket.choices.find((choice) => choice.optionId === agreeOption!.id)?.count ?? 0,
        `${entry.bucket} agree count`
      ).toBe(entry.agree);
      expect(
        bucket.choices.find((choice) => choice.optionId === disagreeOption!.id)?.count ?? 0,
        `${entry.bucket} disagree count`
      ).toBe(entry.disagree);
    }

    // The response carries the numbers; these assertions prove those same numbers were drawn.
    // Asserting only the bucket names would survive an off-by-one in the chart itself.
    const chart = this.page.getByTestId("sentiment-results-scroll");
    await expect(chart.getByTestId("breakdown-axis-title")).toHaveText("Political leaning");
    for (const entry of expected) {
      const group = chart.getByTestId(`breakdown-bucket-${entry.bucket}`);
      await expect(group, `${entry.label} should be drawn`).toBeVisible();
      await expect(
        group.getByTestId(`breakdown-count-${entry.bucket}-${agreeOption!.id}`),
        `${entry.label} agree count as drawn`
      ).toHaveText(String(entry.agree));
      await expect(
        group.getByTestId(`breakdown-count-${entry.bucket}-${disagreeOption!.id}`),
        `${entry.label} disagree count as drawn`
      ).toHaveText(String(entry.disagree));
    }
  }

  /** The reader's vote is final: the card must show it is locked rather than offer another. */
  async expectVoteLocked(postId: number, stance: "Agree" | "Disagree"): Promise<void> {
    await expect(this.card(postId).getByTestId("vote-status")).toHaveText(`You voted — ${stance}`);
    await expect(this.card(postId).getByTestId("see-results")).toBeVisible();
  }

  async returnToFeed(): Promise<void> {
    await this.page.getByRole("button", { name: "Back to feed" }).click();
  }

  private card(postId: number) {
    return this.page.getByTestId(`post-card-${postId}`);
  }
}

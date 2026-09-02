import { expect, type Page, type Response } from "@playwright/test";
import { expectedFeed } from "../fixtures/test-data";

type FeedPost = {
  id: number;
  supportQuestion: string;
  summary: string;
  media: Array<{
    mediaType: "VIDEO" | "IMAGE";
    url: string;
  }>;
};

/** The cursor-paged feed envelope (ADR-042); `nextCursor` is null at the end of the feed. */
type FeedPageResponse = {
  posts: FeedPost[];
  nextCursor: string | null;
};

/**
 * The two reader-facing feed splits.
 *
 * Assertions pin a known seeded post's content wherever it lands on the page rather than pinning
 * it to position zero. Publishing and following both reorder the feed by design, so a position
 * assertion would fail for reasons that have nothing to do with the feed being broken.
 */
export class FeedPage {
  constructor(private readonly page: Page) {}

  waitForVideoFeed(): Promise<Response> {
    return this.page.waitForResponse(
      (response) =>
        response.request().method() === "GET" &&
        response.url().includes("/feed") &&
        response.url().includes("type=VIDEO") &&
        response.status() === 200
    );
  }

  waitForExpectedVideoMedia(): Promise<Response> {
    return this.page.waitForResponse(
      (response) =>
        response.url().includes(expectedFeed.video.mediaKey) &&
        [200, 206].includes(response.status())
    );
  }

  async expectVideoFeed(
    feedResponse: Response,
    mediaResponse: Response
  ): Promise<void> {
    const { posts } = (await feedResponse.json()) as FeedPageResponse;
    expect(posts.length, "the video feed should return seeded video posts").toBeGreaterThan(0);
    expect(
      posts.every((post) => post.media.some((item) => item.mediaType === "VIDEO")),
      "every post in the video split carries a video"
    ).toBe(true);

    const expected = posts.find((post) => post.id === expectedFeed.video.id);
    expect(expected, `seeded video post ${expectedFeed.video.id} should be on the first page`)
      .toBeDefined();
    expect(expected).toMatchObject({
      supportQuestion: expectedFeed.video.supportQuestion,
    });
    expect(expected!.media[0].mediaType).toBe("VIDEO");
    expect(expected!.media[0].url).toContain(expectedFeed.video.mediaKey);
    expect([200, 206]).toContain(mediaResponse.status());

    const videoFilter = this.page.getByRole("button", {
      name: "Video posts",
    });
    await expect(videoFilter).toHaveAttribute("aria-pressed", "true");
    await expect(
      this.page.getByRole("button", { name: "Unmute video" }).first()
    ).toBeVisible();

    const renderedVideo = this.page.locator("video").first();
    await expect(renderedVideo).toBeVisible();
    await expect(renderedVideo).toHaveAttribute("src", /.+/);
    await expect(this.page.getByText("Video unavailable")).toHaveCount(0);
  }

  async switchToAndExpectArticleFeed(): Promise<void> {
    const articleResponse = this.page.waitForResponse(
      (response) =>
        response.request().method() === "GET" &&
        response.url().includes("/feed") &&
        response.url().includes("type=ARTICLE") &&
        response.status() === 200
    );

    await this.page
      .getByRole("button", { name: "Article posts" })
      .click();

    const response = await articleResponse;
    const { posts } = (await response.json()) as FeedPageResponse;
    expect(posts.length, "the article feed should return seeded article posts").toBeGreaterThan(0);
    expect(
      posts.every((post) => post.media.every((item) => item.mediaType !== "VIDEO")),
      "the article split never carries a video"
    ).toBe(true);

    const expected = posts.find((post) => post.id === expectedFeed.article.id);
    expect(
      expected,
      `seeded article post ${expectedFeed.article.id} should be on the first page`
    ).toBeDefined();
    expect(expected).toMatchObject({
      supportQuestion: expectedFeed.article.supportQuestion,
      summary: expect.stringContaining(expectedFeed.article.summary),
    });
    expect(expected!.media, "the seeded article post is text-only").toEqual([]);

    const articleFilter = this.page.getByRole("button", {
      name: "Article posts",
    });
    await expect(articleFilter).toHaveAttribute("aria-pressed", "true");
    await expect(this.page.locator("video")).toHaveCount(0);
    await expect(
      this.page.getByRole("button", { name: "Unmute video" })
    ).toHaveCount(0);
  }
}

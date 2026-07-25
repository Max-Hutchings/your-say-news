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
    const posts = (await feedResponse.json()) as FeedPost[];
    expect(posts).toHaveLength(5);
    expect(
      posts.every((post) =>
        post.media.some((item) => item.mediaType === "VIDEO")
      )
    ).toBe(true);
    expect(posts[0]).toMatchObject({
      id: expectedFeed.video.id,
      supportQuestion: expectedFeed.video.supportQuestion,
      media: [
        {
          mediaType: "VIDEO",
        },
      ],
    });
    expect(posts[0].media[0].url).toContain(
      expectedFeed.video.mediaKey
    );
    expect([200, 206]).toContain(mediaResponse.status());

    const videoFilter = this.page.getByRole("button", {
      name: "Video posts",
    });
    await expect(videoFilter).toHaveAttribute("aria-pressed", "true");
    await expect(
      this.page.getByText(expectedFeed.video.supportQuestion, {
        exact: true,
      })
    ).toBeVisible();
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
    const posts = (await response.json()) as FeedPost[];
    expect(posts).toHaveLength(5);
    expect(
      posts.every((post) =>
        post.media.every((item) => item.mediaType !== "VIDEO")
      )
    ).toBe(true);
    expect(posts[0]).toMatchObject({
      id: expectedFeed.article.id,
      supportQuestion: expectedFeed.article.supportQuestion,
      summary: expect.stringContaining(expectedFeed.article.summary),
    });
    expect(posts[0].media).toEqual([]);

    const articleFilter = this.page.getByRole("button", {
      name: "Article posts",
    });
    await expect(articleFilter).toHaveAttribute("aria-pressed", "true");
    await expect(
      this.page.getByText(expectedFeed.article.supportQuestion, {
        exact: true,
      })
    ).toBeVisible();
    await expect(
      this.page.getByText(new RegExp(expectedFeed.article.summary))
    ).toBeVisible();
    await expect(this.page.locator("video")).toHaveCount(0);
    await expect(
      this.page.getByRole("button", { name: "Unmute video" })
    ).toHaveCount(0);
  }
}

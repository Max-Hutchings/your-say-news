import { expect, type Page } from "@playwright/test";

type PostContent = {
  supportQuestion: string;
  summary: string;
};

type PublishedPost = {
  id: number;
  supportQuestion: string;
  summary: string;
  media: Array<{ mediaType: "IMAGE" | "VIDEO"; url: string }>;
};

/**
 * The compose screen: the manual publish path from an empty form to a post in the feed.
 *
 * Media is attached through the real picker. On web `expo-image-picker` opens a file input, so
 * the journey answers the browser's own file chooser rather than reaching into component state —
 * the upload then travels the genuine presign-and-PUT path to object storage.
 */
export class ComposePage {
  constructor(private readonly page: Page) {}

  /** Opens the composer from the feed's publish control, which only an official publisher sees. */
  async openFromFeed(): Promise<void> {
    const newPost = this.page.getByRole("button", { name: "New post" });
    await expect(
      newPost,
      "an active official publisher should see the publish control"
    ).toBeVisible();
    await newPost.click();
    await expect(this.page.getByTestId("compose-support-question")).toBeVisible();
  }

  async expectPublishingUnavailable(): Promise<void> {
    await expect(
      this.page.getByRole("button", { name: "New post" }),
      "an account without publisher rights should not be offered the composer"
    ).toHaveCount(0);
  }

  async fillPost(content: PostContent): Promise<void> {
    await this.page.getByTestId("compose-support-question").fill(content.supportQuestion);
    await this.page.getByTestId("compose-summary").fill(content.summary);
  }

  async attachImage(filePath: string): Promise<void> {
    await this.attach("Add up to 5 photos", filePath);
    await expect(this.page.getByRole("button", { name: "Remove image 1" })).toBeVisible();
  }

  async attachVideo(filePath: string): Promise<void> {
    await this.page.getByRole("button", { name: "Video", exact: true }).click();
    await this.attach("Record or upload a clip", filePath);
    await expect(this.page.getByRole("button", { name: "Remove video" })).toBeVisible();
  }

  /** Publishes and returns the created post as the backend recorded it. */
  async publish(): Promise<PublishedPost> {
    const created = this.page.waitForResponse(
      (response) =>
        response.request().method() === "POST" &&
        new URL(response.url()).pathname === "/posts"
    );

    await this.page.getByRole("button", { name: "Post", exact: true }).click();

    const response = await created;
    expect(response.status(), "publishing should be accepted").toBe(201);
    return (await response.json()) as PublishedPost;
  }

  /**
   * Proves the uploaded object is actually fetchable, not merely that a URL was returned. A
   * presign for the wrong key, or an expired one, would still look like a valid URL.
   */
  async expectMediaRetrievable(url: string): Promise<void> {
    const response = await this.page.request.get(url);
    expect(
      [200, 206],
      `the published media at ${new URL(url).pathname} should be retrievable`
    ).toContain(response.status());
  }

  private async attach(dropzoneName: string, filePath: string): Promise<void> {
    const chooser = this.page.waitForEvent("filechooser");
    await this.page.getByRole("button", { name: dropzoneName }).click();
    await (await chooser).setFiles(filePath);
  }
}

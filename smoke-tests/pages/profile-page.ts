import { expect, type Page } from "@playwright/test";
import { scrollFeedToPost, showArticleFeed } from "./feed-navigation";

type FollowTarget = {
  id: number;
  displayName: string;
  handle: string;
  /** A seeded post this user authors. Profiles are reached by tapping a post's author. */
  postId: number;
};

/**
 * A viewed user's public profile and the follow relationship on it.
 *
 * Profiles are opened by tapping the author on one of their posts, not by navigating straight to
 * `/profiles/{id}`. The root layout gates the protected stack behind a login guard, and a cold
 * deep-linked page load lands on the signed-out page instead — so a deep link would test the guard,
 * not the profile.
 *
 * For the same reason persistence is proven by leaving the screen and coming back through the UI
 * rather than by reloading: the follow button updates optimistically from local state, so without a
 * genuine round trip a test would pass on a request that never reached the database.
 */
export class ProfilePage {
  constructor(private readonly page: Page) {}

  async openFromFeed(target: FollowTarget): Promise<void> {
    await showArticleFeed(this.page);
    await scrollFeedToPost(this.page, target.postId);

    await this.page
      .getByTestId(`post-card-${target.postId}`)
      .getByRole("button", { name: "Open author profile" })
      .click();

    await this.expectShowing(target);
  }

  async expectShowing(target: FollowTarget): Promise<void> {
    await expect(
      this.page.getByText(target.displayName, { exact: true })
    ).toBeVisible({ timeout: 20_000 });
    await expect(this.page.getByText(`@${target.handle}`)).toBeVisible();
  }

  async expectFollowing(target: FollowTarget, following: boolean): Promise<void> {
    await expect(this.followToggle()).toHaveAccessibleName(
      following ? `Unfollow ${target.handle}` : `Follow ${target.handle}`
    );
    await expect(this.followToggle()).toHaveText(following ? "Following" : "Follow");
  }

  async expectFollowerCount(count: number): Promise<void> {
    await expect(this.page.getByTestId("profile-stat-followers")).toHaveText(String(count));
  }

  async followerCount(): Promise<number> {
    const text = await this.page.getByTestId("profile-stat-followers").textContent();
    return Number(text?.trim());
  }

  /** Clicks the toggle and waits for the write to be accepted, not merely dispatched. */
  async toggleFollow(target: FollowTarget, expected: "follow" | "unfollow"): Promise<void> {
    const call = this.page.waitForResponse(
      (response) =>
        response.url().includes(`/social/follows/${target.id}`) &&
        response.request().method() === (expected === "follow" ? "POST" : "DELETE")
    );

    await this.followToggle().click();

    const response = await call;
    expect(response.status()).toBe(200);
    await expect(response.json()).resolves.toMatchObject({
      following: expected === "follow",
    });
  }

  /** Leaves the profile and opens it again, so the state read back comes from the server. */
  async expectPersistedFollowState(
    target: FollowTarget,
    following: boolean,
    followerCount: number
  ): Promise<void> {
    const reloaded = this.page.waitForResponse(
      (response) =>
        response.request().method() === "GET" &&
        new URL(response.url()).pathname.endsWith(`/profiles/${target.id}`)
    );

    await this.page.getByRole("button", { name: "Back" }).click();
    await this.openFromFeed(target);

    expect((await reloaded).status()).toBe(200);
    await this.expectFollowing(target, following);
    await this.expectFollowerCount(followerCount);
  }

  private followToggle() {
    return this.page.getByTestId("profile-follow-toggle");
  }
}

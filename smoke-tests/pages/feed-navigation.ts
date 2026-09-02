import { expect, type Page } from "@playwright/test";

/**
 * Shared feed navigation.
 *
 * The feed is one post per viewport with cursor pagination, and what sits on the first page moves
 * whenever anything is published or followed — the ranker boosts posts by followed authors. So a
 * journey scrolls until it finds the card it wants rather than assuming a position.
 */
export async function scrollFeedToPost(
  page: Page,
  postId: number,
  maxScrolls = 12
): Promise<void> {
  const viewport = page.getByTestId("home-feed-viewport");
  await expect(viewport).toBeVisible();

  const card = page.getByTestId(`post-card-${postId}`);
  for (let attempt = 0; attempt < maxScrolls; attempt += 1) {
    if (await card.isVisible().catch(() => false)) break;
    await viewport.hover();
    await page.mouse.wheel(0, (await viewport.boundingBox())?.height ?? 800);
    await page.waitForTimeout(400);
  }

  await expect(
    card,
    `post ${postId} should be reachable in the feed within ${maxScrolls} scrolls`
  ).toBeVisible({ timeout: 20_000 });
  await card.scrollIntoViewIfNeeded();
}

export async function showArticleFeed(page: Page): Promise<void> {
  await page.getByRole("button", { name: "Article posts" }).click();
}

export async function showVideoFeed(page: Page): Promise<void> {
  await page.getByRole("button", { name: "Video posts" }).click();
}

/**
 * Forces a feed split to refetch from the server, then leaves that split selected.
 *
 * Returning from the composer does not refresh the list, so a just-published post is not in it.
 * Changing the type filter calls the feed's own reset-and-refetch, and going via the other filter
 * first guarantees two real changes rather than a no-op click on the filter already selected.
 *
 * The split matters: `FeedPostType` puts anything carrying a video in VIDEO and everything else —
 * text and images alike — in ARTICLE. A published video post is only ever found in the former.
 */
export async function refreshFeed(page: Page, split: "article" | "video"): Promise<void> {
  if (split === "article") {
    await showVideoFeed(page);
    await page.waitForTimeout(300);
    await showArticleFeed(page);
    return;
  }
  await showArticleFeed(page);
  await page.waitForTimeout(300);
  await showVideoFeed(page);
}

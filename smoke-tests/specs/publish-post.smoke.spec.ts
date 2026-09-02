import { expect, test, type Page } from "@playwright/test";
import {
  composeMedia,
  newPostContent,
  publisherAccount,
  returningReader,
} from "../fixtures/test-data";
import { AuthenticationPage } from "../pages/authentication-page";
import { ComposePage } from "../pages/compose-page";
import { refreshFeed, scrollFeedToPost } from "../pages/feed-navigation";
import { ApiGuards } from "../support/api-guards";

/**
 * Publishing journeys for the three post shapes. Each signs in as an active official publisher,
 * because `PostServiceImpl` rejects any other author, and each asserts the post the backend
 * actually recorded rather than only that the composer closed.
 */

async function signInAsPublisher(page: Page) {
  const authentication = new AuthenticationPage(page);
  await page.goto("/");
  await authentication.expectSignedOut();
  await authentication.signIn(publisherAccount);
}

test("a publisher can publish a post with an image", async ({ page }) => {
  const compose = new ComposePage(page);
  const content = newPostContent("image");

  await signInAsPublisher(page);
  await compose.openFromFeed();
  await compose.fillPost(content);
  await compose.attachImage(composeMedia.image);

  const published = await compose.publish();

  expect(published).toMatchObject({
    supportQuestion: content.supportQuestion,
    summary: content.summary,
  });
  expect(published.media).toHaveLength(1);
  expect(published.media[0].mediaType).toBe("IMAGE");
  await compose.expectMediaRetrievable(published.media[0].url);

  await refreshFeed(page, "article");
  await scrollFeedToPost(page, published.id);
  await expect(
    page.getByTestId(`post-card-${published.id}`).getByText(content.supportQuestion, { exact: true })
  ).toBeVisible();
});

test("a publisher can publish a post with a video", async ({ page }) => {
  const compose = new ComposePage(page);
  const content = newPostContent("video");

  await signInAsPublisher(page);
  await compose.openFromFeed();
  await compose.fillPost(content);
  await compose.attachVideo(composeMedia.video);

  const published = await compose.publish();

  expect(published).toMatchObject({
    supportQuestion: content.supportQuestion,
    summary: content.summary,
  });
  expect(published.media).toHaveLength(1);
  expect(published.media[0].mediaType).toBe("VIDEO");
  await compose.expectMediaRetrievable(published.media[0].url);

  await refreshFeed(page, "video");
  await scrollFeedToPost(page, published.id);
  await expect(
    page.getByTestId(`post-card-${published.id}`).getByText(content.supportQuestion, { exact: true })
  ).toBeVisible();
});

test("a publisher can publish a text-only post", async ({ page }) => {
  const compose = new ComposePage(page);
  const content = newPostContent("text");

  await signInAsPublisher(page);
  await compose.openFromFeed();
  await compose.fillPost(content);

  const published = await compose.publish();

  expect(published).toMatchObject({
    supportQuestion: content.supportQuestion,
    summary: content.summary,
  });
  expect(published.media, "a text post carries no media").toEqual([]);

  await refreshFeed(page, "article");
  await scrollFeedToPost(page, published.id);
  await expect(
    page.getByTestId(`post-card-${published.id}`).getByText(content.supportQuestion, { exact: true })
  ).toBeVisible();
});

test("a reader without publisher rights cannot publish", async ({ page, request }) => {
  const authentication = new AuthenticationPage(page);
  const compose = new ComposePage(page);
  const guards = new ApiGuards(request);

  await page.goto("/");
  await authentication.expectSignedOut();
  await authentication.signIn(returningReader);

  await compose.expectPublishingUnavailable();

  // The absent button is only an affordance. This is the part that would fail if the server-side
  // active-official-publisher check in PostServiceImpl were removed.
  await guards.expectPublishForbidden(returningReader);
});

import { test } from "@playwright/test";
import { returningReader } from "../fixtures/test-data";
import { AuthenticationPage } from "../pages/authentication-page";
import { FeedPage } from "../pages/feed-page";

test("a returning reader can sign in and load video and article posts", async ({
  page,
}) => {
  const authentication = new AuthenticationPage(page);
  const feed = new FeedPage(page);

  const videoFeedResponse = feed.waitForVideoFeed();
  const videoMediaResponse = feed.waitForExpectedVideoMedia();

  await page.goto("/");
  await authentication.expectSignedOut();
  await authentication.signIn(returningReader);

  await feed.expectVideoFeed(
    await videoFeedResponse,
    await videoMediaResponse
  );
  await feed.switchToAndExpectArticleFeed();
});

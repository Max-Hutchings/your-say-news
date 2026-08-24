import { test } from "@playwright/test";
import { followTarget, returningReader, unfollowTarget } from "../fixtures/test-data";
import { AuthenticationPage } from "../pages/authentication-page";
import { ProfilePage } from "../pages/profile-page";

/**
 * The two follow directions are separate journeys with separate seeded preconditions, so neither
 * depends on the other having run: Riley starts following Maya and not following Theo.
 */

test("a reader can follow another user and the follow persists", async ({ page }) => {
  const authentication = new AuthenticationPage(page);
  const profile = new ProfilePage(page);

  await page.goto("/");
  await authentication.expectSignedOut();
  await authentication.signIn(returningReader);

  await profile.openFromFeed(followTarget);
  await profile.expectFollowing(followTarget, false);
  const before = await profile.followerCount();

  await profile.toggleFollow(followTarget, "follow");

  await profile.expectPersistedFollowState(followTarget, true, before + 1);
});

test("a reader can unfollow a user they already follow and it persists", async ({ page }) => {
  const authentication = new AuthenticationPage(page);
  const profile = new ProfilePage(page);

  await page.goto("/");
  await authentication.expectSignedOut();
  await authentication.signIn(returningReader);

  await profile.openFromFeed(unfollowTarget);
  await profile.expectFollowing(unfollowTarget, true);
  const before = await profile.followerCount();

  await profile.toggleFollow(unfollowTarget, "unfollow");

  await profile.expectPersistedFollowState(unfollowTarget, false, before - 1);
});

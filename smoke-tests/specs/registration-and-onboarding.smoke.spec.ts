import { test } from "@playwright/test";
import { newRegistrationIdentity } from "../fixtures/test-data";
import { AuthenticationPage } from "../pages/authentication-page";
import { CharacteristicsPage } from "../pages/characteristics-page";

test("a visitor can register and complete characteristics onboarding", async ({
  page,
}) => {
  const authentication = new AuthenticationPage(page);
  const characteristics = new CharacteristicsPage(page);

  await page.goto("/");
  await authentication.expectSignedOut();
  await authentication.register(newRegistrationIdentity());
  await characteristics.acceptPrivacyPromise();
  await characteristics.completeRepresentativeProfile();
  await characteristics.finishAndExpectFeed();
});

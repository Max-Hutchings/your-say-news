import { expect, type Page } from "@playwright/test";
import type {
  RegistrationIdentity,
  SignInIdentity,
} from "../fixtures/test-data";

/**
 * Product-level authentication driver.
 *
 * Specs depend only on register/sign-in outcomes. The selectors below implement the configured
 * local provider and are intentionally contained here so another hosted provider can supply the
 * same operations without changing a journey.
 */
export class AuthenticationPage {
  constructor(
    private readonly page: Page,
    private readonly applicationOrigin = process.env.SMOKE_BASE_URL ??
      "http://localhost:5173",
    private readonly providerOrigin = process.env.SMOKE_AUTH_ORIGIN ??
      "http://localhost:8080"
  ) {}

  async expectSignedOut(): Promise<void> {
    await expect(
      this.page.getByRole("button", { name: "Continue securely" })
    ).toBeEnabled();
    await expect(
      this.page.getByRole("button", { name: "Create an account" })
    ).toBeEnabled();
  }

  async register(identity: RegistrationIdentity): Promise<void> {
    await this.page
      .getByRole("button", { name: "Create an account" })
      .click();
    await this.expectProviderPage();

    await this.page.locator("#kc-registration a").click();
    await expect(this.page.locator("#kc-register-form")).toBeVisible();

    await this.page.locator("#firstName").fill(identity.firstName);
    await this.page.locator("#lastName").fill(identity.lastName);
    await this.page.locator("#email").fill(identity.email);
    await this.page.locator("#username").fill(identity.username);
    await this.page.locator("#password").fill(identity.password);
    await this.page.locator("#password-confirm").fill(identity.password);
    await this.page
      .locator('#kc-register-form input[type="submit"]')
      .click();

    await this.expectApplicationReturn();
  }

  async signIn(identity: SignInIdentity): Promise<void> {
    const currentUser = this.page.waitForResponse(
      (response) =>
        response.request().method() === "GET" &&
        new URL(response.url()).pathname === "/your-say-user"
    );

    await this.page
      .getByRole("button", { name: "Continue securely" })
      .click();
    await this.expectProviderPage();

    await this.page.locator("#username").fill(identity.username);
    await this.page.locator("#password").fill(identity.password);
    await this.page.locator("#kc-login").click();

    await this.expectApplicationReturn();

    const response = await currentUser;
    expect(response.status()).toBe(200);
    await expect(response.json()).resolves.toMatchObject({
      email: identity.email,
    });
  }

  private async expectProviderPage(): Promise<void> {
    const provider = new URL(this.providerOrigin);
    await this.page.waitForURL(
      (url) =>
        url.protocol === provider.protocol &&
        url.host === provider.host
    );
  }

  private async expectApplicationReturn(): Promise<void> {
    const application = new URL(this.applicationOrigin);
    await this.page.waitForURL(
      (url) =>
        url.protocol === application.protocol &&
        url.host === application.host
    );
  }
}

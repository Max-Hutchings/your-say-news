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
      this.page.getByRole("button", { name: "Sign in" })
    ).toBeEnabled();
  }

  async register(identity: RegistrationIdentity): Promise<void> {
    const signUp = await this.page.request.post(
      `${this.providerOrigin}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=local`,
      { data: { email: identity.email, password: identity.password, returnSecureToken: true } },
    );
    expect(signUp.status()).toBe(200);
    const { idToken } = await signUp.json() as { idToken: string };
    const profile = await this.page.request.post(
      `${this.providerOrigin}/identitytoolkit.googleapis.com/v1/accounts:update?key=local`,
      {
        data: {
          idToken,
          displayName: `${identity.firstName} ${identity.lastName}`,
          returnSecureToken: true,
        },
      },
    );
    expect(profile.status()).toBe(200);
    await this.signIn(identity);
  }

  async signIn(identity: SignInIdentity): Promise<void> {
    const currentUser = this.page.waitForResponse(
      (response) =>
        response.request().method() === "GET" &&
        new URL(response.url()).pathname === "/your-say-user"
    );

    await this.page.getByLabel("Email").fill(identity.email);
    await this.page.getByLabel("Password").fill(identity.password);
    await this.page.getByRole("button", { name: "Sign in" }).click();

    const response = await currentUser;
    expect(response.status()).toBe(200);
    await expect(response.json()).resolves.toMatchObject({
      email: identity.email,
    });
  }

}

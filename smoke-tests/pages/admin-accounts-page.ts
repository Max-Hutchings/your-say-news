import { expect, type Locator, type Page, type Response } from "@playwright/test";
import type { SignInIdentity } from "../fixtures/test-data";

type PersistedAccount = {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  displayName: string;
  createdDate: string;
  accountType: "USER" | "OFFICIAL" | "ADMIN";
  active: boolean;
};

type ManagedAccount = Omit<PersistedAccount, "accountType" | "active">;

function expectedAccount(
  account: ManagedAccount,
  accountType: PersistedAccount["accountType"],
  active: boolean
): PersistedAccount {
  return {
    id: account.id,
    email: account.email,
    firstName: account.firstName,
    lastName: account.lastName,
    displayName: account.displayName,
    createdDate: account.createdDate,
    accountType,
    active,
  };
}

export class AdminAccountsPage {
  constructor(
    private readonly page: Page,
    private readonly adminOrigin = process.env.SMOKE_ADMIN_URL ??
      "http://localhost:58083",
    private readonly providerOrigin = process.env.SMOKE_AUTH_ORIGIN ??
      "http://localhost:58080"
  ) {}

  async signIn(
    identity: SignInIdentity,
    managedAccount: ManagedAccount
  ): Promise<void> {
    await this.page.goto(`${this.adminOrigin}/admin/`);
    await this.expectProviderPage();

    const accountsResponse = this.waitForAccounts();
    await this.page.locator("#username").fill(identity.username);
    await this.page.locator("#password").fill(identity.password);
    await this.page.locator("#kc-login").click();

    await this.expectAdminPage();
    const response = await accountsResponse;
    expect(response.status()).toBe(200);

    const accounts = await response.json() as PersistedAccount[];
    // 12 hand-maintained development accounts (including the application-owned
    // official@yoursay.com author) plus the 20-strong smoke voting population
    // (0010-seed-smoke-vote-population.yaml). Update this when the seeded account set changes.
    expect(accounts).toHaveLength(32);
    expect(
      accounts.filter((account) => account.email.startsWith("smoke.voter.")),
      "the seeded voting population should be administrable like any other account"
    ).toHaveLength(20);
    expect(accounts.find((account) => account.email === identity.email)).toEqual({
      id: 11,
      email: identity.email,
      firstName: "YourSay",
      lastName: "Admin",
      displayName: "YourSay Admin",
      createdDate: "2024-06-07",
      accountType: "ADMIN",
      active: true,
    });
    expect(
      accounts.find((account) => account.email === managedAccount.email)
    ).toEqual(expectedAccount(managedAccount, "USER", true));
  }

  async changeAccountType(
    account: ManagedAccount,
    accountType: PersistedAccount["accountType"],
    active: boolean
  ): Promise<void> {
    const row = this.accountRow(account.email);
    await row
      .getByLabel(`Account type for ${account.displayName}`)
      .selectOption(accountType);

    const response = await this.save(account.id, row);
    await this.expectPersistedResponse(
      response,
      expectedAccount(account, accountType, active)
    );
  }

  async changeActiveState(
    account: ManagedAccount,
    active: boolean
  ): Promise<void> {
    const row = this.accountRow(account.email);
    const activeSwitch = row.getByRole("switch", {
      name: `Account active for ${account.displayName}`,
    });

    if ((await activeSwitch.isChecked()) !== active) {
      await row.locator(".account-toggle__track").click();
    }

    const response = await this.save(account.id, row);
    const accountType = await row
      .getByLabel(`Account type for ${account.displayName}`)
      .inputValue() as PersistedAccount["accountType"];
    await this.expectPersistedResponse(
      response,
      expectedAccount(account, accountType, active)
    );
  }

  async expectPersistedState(
    account: {
      displayName: string;
      email: string;
      accountType: PersistedAccount["accountType"];
      active: boolean;
    }
  ): Promise<void> {
    const accountsResponse = this.waitForAccounts();
    await this.page.reload();
    await this.expectAdminPage();
    expect((await accountsResponse).status()).toBe(200);

    const row = this.accountRow(account.email);
    await expect(
      row.getByLabel(`Account type for ${account.displayName}`)
    ).toHaveValue(account.accountType);
    await expect(
      row.getByRole("switch", {
        name: `Account active for ${account.displayName}`,
      })
    ).toHaveJSProperty("checked", account.active);
    await expect(row).toContainText(account.active ? "Active" : "Inactive");
    await expect(row.getByRole("button", { name: "Saved" })).toBeDisabled();
  }

  private accountRow(email: string): Locator {
    return this.page.getByRole("listitem").filter({ hasText: email });
  }

  private async save(userId: number, row: Locator): Promise<Response> {
    const response = this.page.waitForResponse(
      (candidate) =>
        candidate.request().method() === "PUT" &&
        new URL(candidate.url()).pathname === `/api/admin/users/${userId}`
    );
    await row.getByRole("button", { name: "Save changes" }).click();
    return response;
  }

  private async expectPersistedResponse(
    response: Response,
    expected: PersistedAccount
  ): Promise<void> {
    expect(response.status()).toBe(200);
    await expect(response.json()).resolves.toEqual(expected);
  }

  private waitForAccounts(): Promise<Response> {
    return this.page.waitForResponse(
      (response) =>
        response.request().method() === "GET" &&
        new URL(response.url()).pathname === "/api/admin/users"
    );
  }

  private async expectProviderPage(): Promise<void> {
    const provider = new URL(this.providerOrigin);
    await expect.poll(() => new URL(this.page.url()).origin).toBe(provider.origin);
  }

  private async expectAdminPage(): Promise<void> {
    const admin = new URL(this.adminOrigin);
    await expect.poll(() => new URL(this.page.url()).origin).toBe(admin.origin);
    await expect(
      this.page.getByRole("heading", { name: "Accounts desk" })
    ).toBeVisible();
  }
}

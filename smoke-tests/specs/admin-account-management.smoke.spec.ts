import { test } from "@playwright/test";
import {
  adminAccount,
  managedAccount,
} from "../fixtures/test-data";
import { AdminAccountsPage } from "../pages/admin-accounts-page";

test("an administrator can persist account type and active changes", async ({
  page,
}) => {
  const accounts = new AdminAccountsPage(page);

  await accounts.signIn(adminAccount, managedAccount);

  await accounts.changeAccountType(
    managedAccount,
    managedAccount.changedAccountType,
    managedAccount.initialActive
  );
  await accounts.expectPersistedState({
    ...managedAccount,
    accountType: managedAccount.changedAccountType,
    active: managedAccount.initialActive,
  });

  await accounts.changeActiveState(managedAccount, false);
  await accounts.expectPersistedState({
    ...managedAccount,
    accountType: managedAccount.changedAccountType,
    active: false,
  });

  await accounts.changeAccountType(
    managedAccount,
    managedAccount.initialAccountType,
    false
  );
  await accounts.changeActiveState(
    managedAccount,
    managedAccount.initialActive
  );
  await accounts.expectPersistedState({
    ...managedAccount,
    accountType: managedAccount.initialAccountType,
    active: managedAccount.initialActive,
  });
});

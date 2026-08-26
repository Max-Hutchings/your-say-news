import { readFile } from "node:fs/promises";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";

const projectId = process.env.FIREBASE_PROJECT_ID ?? "demo-your-say-news";
const accountsPath = new URL("./test-accounts.json", import.meta.url);
const { accounts } = JSON.parse(await readFile(accountsPath, "utf8"));

initializeApp({ projectId });
const auth = getAuth();

for (const account of accounts) {
  const properties = {
    email: account.email,
    password: account.password,
    displayName: account.displayName,
    emailVerified: account.emailVerified,
    disabled: account.disabled,
  };

  try {
    await auth.getUser(account.uid);
    await auth.updateUser(account.uid, properties);
  } catch (error) {
    if (error?.code !== "auth/user-not-found") {
      throw error;
    }
    await auth.createUser({ uid: account.uid, ...properties });
  }
}

process.stdout.write(`Seeded ${accounts.length} Firebase Authentication test accounts.\n`);

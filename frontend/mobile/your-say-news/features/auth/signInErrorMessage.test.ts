import { signInErrorMessage } from "./signInErrorMessage";

test("a successful sign-in shows no error", () => {
    expect(signInErrorMessage({ status: "signed-in" }, true)).toBeNull();
});

test("emulator builds keep the test-account message whatever failed", () => {
    expect(signInErrorMessage({ status: "failed", stage: "firebase", code: "invalid_credentials" }, false))
        .toBe("That test account could not be signed in.");
});

test("a cancelled Google sign-in is not reported as a rejection", () => {
    expect(signInErrorMessage({ status: "cancelled" }, true)).toBe("Google sign-in was cancelled.");
});

test("each hosted failure stage names where sign-in stopped and its code", () => {
    expect(signInErrorMessage({ status: "failed", stage: "google", code: "10" }, true))
        .toBe("Google rejected this sign-in (google: 10).");
    expect(signInErrorMessage({ status: "failed", stage: "firebase", code: "auth/invalid-credential" }, true))
        .toBe("Firebase rejected this Google account (firebase: auth/invalid-credential).");
    expect(signInErrorMessage({ status: "failed", stage: "server", code: "user_unavailable" }, true))
        .toBe("Your Say News could not load your account (server: user_unavailable).");
});

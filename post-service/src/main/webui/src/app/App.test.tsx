import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AdminAuthState } from "../features/auth";

const { useAdminAuth } = vi.hoisted(() => ({ useAdminAuth: vi.fn() }));

vi.mock("../features/auth", async (importOriginal) => ({
  ...await importOriginal<typeof import("../features/auth")>(),
  AuthProvider: ({ children }: { children: React.ReactNode }) => children,
  useAdminAuth,
}));
vi.mock("../pages/users", () => ({ UsersPage: () => <h1>Accounts</h1> }));

import { App } from "./App";

function signedOut(overrides: Partial<AdminAuthState>): AdminAuthState {
  return {
    status: "unauthenticated",
    identity: null,
    error: null,
    signInMethod: "PASSWORD",
    login: vi.fn(),
    loginWithGoogle: vi.fn(),
    logout: vi.fn(),
    ...overrides,
  };
}

describe("admin sign-in screen", () => {
  beforeEach(() => vi.clearAllMocks());

  it("offers Google sign-in on a hosted environment and starts it on click", async () => {
    const state = signedOut({ signInMethod: "GOOGLE", error: "This account does not have active administrator access." });
    useAdminAuth.mockReturnValue(state);
    render(<App />);

    await userEvent.click(screen.getByRole("button", { name: "Sign in with Google" }));

    expect(state.loginWithGoogle).toHaveBeenCalledTimes(1);
    expect(screen.queryByLabelText("Password")).not.toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("This account does not have active administrator access.");
  });

  it("keeps the emulator email and password form on a laptop", async () => {
    const state = signedOut({ signInMethod: "PASSWORD" });
    useAdminAuth.mockReturnValue(state);
    render(<App />);

    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    expect(state.login).toHaveBeenCalledWith("admin@yoursay.com", "password123");
    expect(screen.queryByRole("button", { name: "Sign in with Google" })).not.toBeInTheDocument();
  });
});

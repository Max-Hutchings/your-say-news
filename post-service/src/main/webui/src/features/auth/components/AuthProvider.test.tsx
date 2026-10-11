import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { initializeAdminSession, loadAdminWebConfig, loginAdmin, loginAdminWithGoogle, logoutAdmin } = vi.hoisted(() => ({
  initializeAdminSession: vi.fn(),
  loadAdminWebConfig: vi.fn(),
  loginAdmin: vi.fn(),
  loginAdminWithGoogle: vi.fn(),
  logoutAdmin: vi.fn(),
}));

vi.mock("../services/adminSession", () => ({
  initializeAdminSession,
  loadAdminWebConfig,
  loginAdmin,
  loginAdminWithGoogle,
  logoutAdmin,
}));

import { AuthProvider, useAdminAuth } from "./AuthProvider";

const admin = { email: "admin@yoursay.com", name: "YourSay Admin" };

function wrapper({ children }: { children: ReactNode }) {
  return <AuthProvider>{children}</AuthProvider>;
}

describe("AuthProvider", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    initializeAdminSession.mockResolvedValue(admin);
    loadAdminWebConfig.mockResolvedValue({ signInMethod: "PASSWORD" });
  });

  it("offers the environment's sign-in method and completes a Google sign-in", async () => {
    initializeAdminSession.mockResolvedValue(null);
    loadAdminWebConfig.mockResolvedValue({ signInMethod: "GOOGLE" });
    loginAdminWithGoogle.mockResolvedValue(admin);
    const { result } = renderHook(() => useAdminAuth(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe("unauthenticated"));
    expect(result.current.signInMethod).toBe("GOOGLE");

    await act(() => result.current.loginWithGoogle());

    expect(result.current.status).toBe("authenticated");
    expect(result.current.identity).toEqual(admin);
  });

  it("shows why a Google sign-in was refused and stays signed out", async () => {
    initializeAdminSession.mockResolvedValue(null);
    loadAdminWebConfig.mockResolvedValue({ signInMethod: "GOOGLE" });
    loginAdminWithGoogle.mockRejectedValue(new Error("This account does not have active administrator access."));
    const { result } = renderHook(() => useAdminAuth(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe("unauthenticated"));

    await act(() => result.current.loginWithGoogle());

    expect(result.current.status).toBe("unauthenticated");
    expect(result.current.error).toBe("This account does not have active administrator access.");
  });

  it("reports an error when the sign-in settings cannot be loaded", async () => {
    loadAdminWebConfig.mockRejectedValue(new Error("The admin sign-in settings could not be loaded."));
    const { result } = renderHook(() => useAdminAuth(), { wrapper });

    await waitFor(() => expect(result.current.status).toBe("error"));
    expect(result.current.error).toBe("The admin sign-in settings could not be loaded.");
  });

  it("keeps the administrator signed in when the backend fails to clear the cookie", async () => {
    logoutAdmin.mockRejectedValue(new Error("The admin session could not be ended."));
    const { result } = renderHook(() => useAdminAuth(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe("authenticated"));

    await expect(act(() => result.current.logout())).rejects.toThrow(
      "The admin session could not be ended.",
    );

    expect(result.current.status).toBe("authenticated");
    expect(result.current.identity).toEqual(admin);
  });
});

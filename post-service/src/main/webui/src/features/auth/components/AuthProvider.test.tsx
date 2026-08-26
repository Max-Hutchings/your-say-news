import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { initializeAdminSession, loginAdmin, logoutAdmin } = vi.hoisted(() => ({
  initializeAdminSession: vi.fn(),
  loginAdmin: vi.fn(),
  logoutAdmin: vi.fn(),
}));

vi.mock("../services/adminSession", () => ({
  initializeAdminSession,
  loginAdmin,
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

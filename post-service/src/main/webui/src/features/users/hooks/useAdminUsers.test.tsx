import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getAdminUsers, updateAdminUser } from "../services/userAdminApi";
import { useAdminUsers } from "./useAdminUsers";

vi.mock("../services/userAdminApi", async (importOriginal) => {
  const original = await importOriginal<typeof import("../services/userAdminApi")>();
  return {
    ...original,
    getAdminUsers: vi.fn(),
    updateAdminUser: vi.fn(),
  };
});

const riley = {
  id: 10,
  email: "riley.reader@example.com",
  firstName: "Riley",
  lastName: "Reader",
  displayName: "Riley Reader",
  createdDate: "2024-06-06",
  active: true,
  accountType: "USER" as const,
};

describe("useAdminUsers", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getAdminUsers).mockResolvedValue([riley]);
  });

  it("replaces the saved row with the server response and clears saving state", async () => {
    const promoted = { ...riley, accountType: "ADMIN" as const, active: false };
    vi.mocked(updateAdminUser).mockResolvedValue(promoted);
    const { result } = renderHook(() => useAdminUsers());

    await waitFor(() => expect(result.current.users).toEqual([riley]));

    await act(async () => {
      await result.current.update(10, { accountType: "ADMIN", active: false });
    });

    expect(updateAdminUser).toHaveBeenCalledWith(10, {
      accountType: "ADMIN",
      active: false,
    });
    expect(result.current.users).toEqual([promoted]);
    expect(result.current.savingUserIds).toEqual(new Set());
    expect(result.current.error).toBeNull();
  });

  it("keeps the existing row and exposes a retryable error when saving fails", async () => {
    vi.mocked(updateAdminUser).mockRejectedValue(new Error("network unavailable"));
    const { result } = renderHook(() => useAdminUsers());

    await waitFor(() => expect(result.current.users).toEqual([riley]));

    await act(async () => {
      await expect(
        result.current.update(10, { accountType: "ADMIN", active: false }),
      ).rejects.toThrow("network unavailable");
    });

    expect(result.current.users).toEqual([riley]);
    expect(result.current.error).toEqual({
      status: null,
      message: "network unavailable",
    });
    expect(result.current.savingUserIds).toEqual(new Set());
  });
});

import { getOnboardingStatus, getUser, verifySession } from "./UserService";
import type { OnboardingStatus, User } from "../types";

jest.mock("expo-constants", () => ({
  __esModule: true,
  default: {
    expoConfig: {
      extra: {
        USER_SERVICE_HOST: "http://users.local:",
        USER_SERVICE_PORT: "8082",
      },
    },
  },
}));

const mockGet = jest.fn();
jest.mock("./requests", () => ({
  __esModule: true,
  default: { getSecure: () => ({ get: (...args: unknown[]) => mockGet(...args) }) },
}));

beforeEach(() => {
  jest.clearAllMocks();
  jest.spyOn(console, "info").mockImplementation(() => undefined);
});

afterEach(() => {
  jest.restoreAllMocks();
});

test("getUser returns the authenticated user from the configured service", async () => {
  const user: User = {
    id: 42,
    email: "reader@example.org",
    firstName: "Amina",
    lastName: "Khan",
    dateOfBirth: "1991-08-17",
    consentedAt: "2026-07-24T14:30:00Z",
    accountType: "OFFICIAL",
    publisherStatus: "ACTIVE",
    canPublish: true,
  };
  mockGet.mockResolvedValue({ status: 200, data: user });

  await expect(getUser()).resolves.toEqual(user);
  expect(mockGet).toHaveBeenCalledWith("http://users.local:8082/your-say-user");
});

test("getUser reports non-success and network failures as null", async () => {
  mockGet.mockResolvedValueOnce({ status: 503, statusText: "Service Unavailable" });
  await expect(getUser()).resolves.toBeNull();
  expect(console.info).toHaveBeenLastCalledWith(
    "Failed to authenticate user:",
    "Service Unavailable",
  );

  mockGet.mockRejectedValueOnce(new Error("connection refused"));
  await expect(getUser()).resolves.toBeNull();
  expect(console.info).toHaveBeenLastCalledWith(
    "Network/request error:",
    "connection refused",
  );
});

test("getOnboardingStatus returns the server's routing flags", async () => {
  const status: OnboardingStatus = {
    consented: true,
    hasCharacteristics: false,
    onboarded: false,
  };
  mockGet.mockResolvedValue({ status: 200, data: status });

  await expect(getOnboardingStatus()).resolves.toEqual(status);
  expect(mockGet).toHaveBeenCalledWith(
    "http://users.local:8082/your-say-user/onboarding",
  );
});

test("getOnboardingStatus returns null on non-success and request errors", async () => {
  mockGet.mockResolvedValueOnce({ status: 401, statusText: "Unauthorized" });
  await expect(getOnboardingStatus()).resolves.toBeNull();
  expect(console.info).toHaveBeenLastCalledWith(
    "Failed to fetch onboarding status:",
    "Unauthorized",
  );

  mockGet.mockRejectedValueOnce(new Error("timeout"));
  await expect(getOnboardingStatus()).resolves.toBeNull();
  expect(console.info).toHaveBeenLastCalledWith("Network/request error:", "timeout");
});

describe("verifySession", () => {
  const riley: User = {
    id: 10,
    email: "riley.reader@example.com",
    firstName: "Riley",
    lastName: "Reader",
    dateOfBirth: "1993-09-14",
    consentedAt: "2026-07-21T09:00:00Z",
    accountType: "USER",
    publisherStatus: "NONE",
    canPublish: false,
  };

  test("reports a live session with the identity the server recognises", async () => {
    mockGet.mockResolvedValue({ status: 200, data: riley });

    await expect(verifySession()).resolves.toEqual({ state: "valid", user: riley });
    expect(mockGet).toHaveBeenCalledWith("http://users.local:8082/your-say-user");
  });

  test("reports an expired or revoked token as unauthenticated so the session is wiped", async () => {
    mockGet.mockRejectedValue({ response: { status: 401 } });

    await expect(verifySession()).resolves.toEqual({ state: "unauthenticated" });
  });

  test("reports a forbidden response as unauthenticated", async () => {
    mockGet.mockRejectedValue({ response: { status: 403 } });

    await expect(verifySession()).resolves.toEqual({ state: "unauthenticated" });
  });

  test("reports an unreachable server separately so a good session is not thrown away", async () => {
    mockGet.mockRejectedValue({ message: "Network Error" });

    await expect(verifySession()).resolves.toEqual({ state: "unreachable" });
  });

  test("treats a non-200 body-less success as unauthenticated rather than a live session", async () => {
    mockGet.mockResolvedValue({ status: 204, data: null });

    await expect(verifySession()).resolves.toEqual({ state: "unauthenticated" });
  });

  test.each([500, 502, 503])(
    "treats a %i server fault as unreachable, so a transient outage never destroys credentials",
    async (status) => {
      mockGet.mockRejectedValue({ response: { status } });

      await expect(verifySession()).resolves.toEqual({ state: "unreachable" });
    }
  );
});

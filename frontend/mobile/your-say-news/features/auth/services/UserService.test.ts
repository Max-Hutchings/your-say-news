import { getOnboardingStatus, getUser } from "./UserService";
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
